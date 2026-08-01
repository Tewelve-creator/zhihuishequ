package com.community.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import com.community.repair.entity.*;
import com.community.repair.mapper.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 邻里倾诉 · 社区朋友圈
 * <p>
 * 复用 emotion_appeal 表（status=normal 为动态）；附属表：评论/点赞/禁言/操作日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppealMomentsService {

    public static final String MUTE_TIP = "您已被物业禁言，暂时无法发布动态";
    public static final String MUTE_COMMENT_TIP = "您已被物业禁言，暂时无法评论";

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EmotionAppealMapper appealMapper;
    private final AppealCommentMapper commentMapper;
    private final AppealLikeMapper likeMapper;
    private final AppealUserMuteMapper muteMapper;
    private final AppealOpLogMapper opLogMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /* ===================== 住户端：动态流 ===================== */

    /**
     * GET /appeal/feed — 邻里圈动态流。
     * 可见范围：当前社区（tenantId）内全部正常动态，本社区任意登录用户（居民/物业）均可看到，
     * 不按发布人过滤；仅排除已删除/已屏蔽内容，置顶优先。
     */
    public List<Map<String, Object>> feed(int limit) {
        requireLogin();
        ensureMomentExt();
        int lim = Math.min(Math.max(limit, 1), 100);
        Long uid = TenantContext.getUserId();
        Long tid = TenantContext.getTenantId();
        if (tid == null) {
            throw new BizException(400, "请先选择社区");
        }
        List<EmotionAppeal> list = appealMapper.selectList(new LambdaQueryWrapper<EmotionAppeal>()
                .eq(EmotionAppeal::getTenantId, tid)
                .eq(EmotionAppeal::getStatus, "normal")
                .eq(EmotionAppeal::getDeleted, 0)
                .and(w -> w.isNull(EmotionAppeal::getHidden).or().eq(EmotionAppeal::getHidden, 0))
                .orderByDesc(EmotionAppeal::getPinned)
                .orderByDesc(EmotionAppeal::getId)
                .last("limit " + lim));
        Set<Long> liked = loadLikedAppealIds(tid, uid, list);
        List<Map<String, Object>> out = new ArrayList<>();
        for (EmotionAppeal a : list) {
            out.add(toFeedItem(a, liked.contains(a.getId()), false));
        }
        return out;
    }

    /** POST /appeal — 发布动态（兼容旧路径；强制 status=normal） */
    @Transactional
    public Map<String, Object> publish(Map<String, Object> body) {
        requireLogin();
        assertNotMuted(MUTE_TIP);
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();

        String content = str(body.get("content"));
        if (!StringUtils.hasText(content)) {
            throw new BizException(400, "请填写动态内容");
        }
        if (content.length() > 1000) {
            throw new BizException(400, "内容过长");
        }

        List<String> images = parseImages(body.get("imageUrls") != null ? body.get("imageUrls") : body.get("images"));
        if (images.size() > 9) {
            throw new BizException(400, "最多上传9张图片");
        }

        Long houseId = parseLong(body.get("houseId"));
        if (houseId == null) {
            houseId = TenantContext.getHouseId();
        }
        String houseLabel = str(body.get("houseLabel"));
        if (!StringUtils.hasText(houseLabel) && houseId != null) {
            houseLabel = resolveHouseLabel(tid, houseId);
        }
        // 昵称以账号资料为准，保证与「个人资料」一致
        String nickname = findNickname(uid);

        EmotionAppeal a = new EmotionAppeal();
        a.setTenantId(tid);
        a.setUserId(uid);
        a.setHouseId(houseId);
        a.setHouseLabel(houseLabel);
        a.setNickname(nickname);
        a.setContent(content.trim());
        a.setImageUrls(images.isEmpty() ? null : writeImages(images));
        a.setLikeCount(0);
        a.setCommentCount(0);
        a.setDeleted(0);
        a.setPinned(0);
        a.setHidden(0);
        a.setStatus("normal");
        a.setCreateTime(LocalDateTime.now());
        appealMapper.insert(a);
        return toFeedItem(a, false, false);
    }

    /** DELETE /appeal/{id} — 住户删除自己的动态 */
    @Transactional
    public void deleteOwnPost(Long id) {
        requireLogin();
        EmotionAppeal a = requirePost(id);
        Long uid = TenantContext.getUserId();
        if (!Objects.equals(a.getUserId(), uid) && !isStaff()) {
            throw new BizException(403, "只能删除自己发布的内容");
        }
        softDeletePost(a, "用户自行删除");
        writeOpLog("DELETE_POST", a.getUserId(), a.getId(), null, "住户删除自己的动态");
    }

    /* ===================== 评论 / 点赞 ===================== */

    public List<Map<String, Object>> listComments(Long appealId, boolean includeDeleted) {
        requireLogin();
        requirePost(appealId);
        LambdaQueryWrapper<AppealComment> q = new LambdaQueryWrapper<AppealComment>()
                .eq(AppealComment::getTenantId, TenantContext.getTenantId())
                .eq(AppealComment::getAppealId, appealId)
                .orderByAsc(AppealComment::getId);
        if (!includeDeleted || !isStaff()) {
            q.eq(AppealComment::getDeleted, 0);
        }
        Long uid = TenantContext.getUserId();
        List<Map<String, Object>> out = new ArrayList<>();
        for (AppealComment c : commentMapper.selectList(q)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("appealId", c.getAppealId());
            m.put("userId", c.getUserId());
            String liveNick = findNickname(c.getUserId());
            m.put("nickname", StringUtils.hasText(liveNick) ? liveNick
                    : (StringUtils.hasText(c.getNickname()) ? c.getNickname() : "住户"));
            m.put("avatar", findAvatar(c.getUserId()));
            m.put("houseLabel", c.getHouseLabel());
            m.put("content", c.getContent());
            m.put("deleted", c.getDeleted());
            m.put("createTime", fmt(c.getCreateTime()));
            m.put("mine", Objects.equals(c.getUserId(), uid));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> addComment(Long appealId, Map<String, Object> body) {
        requireLogin();
        assertNotMuted(MUTE_COMMENT_TIP);
        EmotionAppeal a = requirePost(appealId);
        if (Integer.valueOf(1).equals(a.getDeleted())) {
            throw new BizException("动态已删除，无法评论");
        }
        String content = str(body.get("content"));
        if (!StringUtils.hasText(content)) {
            throw new BizException(400, "请填写评论内容");
        }
        if (content.length() > 500) {
            throw new BizException(400, "评论过长");
        }
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        AppealComment c = new AppealComment();
        c.setTenantId(tid);
        c.setAppealId(appealId);
        c.setUserId(uid);
        c.setNickname(findNickname(uid));
        String hl = str(body.get("houseLabel"));
        if (!StringUtils.hasText(hl) && TenantContext.getHouseId() != null) {
            hl = resolveHouseLabel(tid, TenantContext.getHouseId());
        }
        c.setHouseLabel(hl);
        c.setContent(content.trim());
        c.setDeleted(0);
        c.setCreateTime(LocalDateTime.now());
        commentMapper.insert(c);

        a.setCommentCount(Optional.ofNullable(a.getCommentCount()).orElse(0) + 1);
        appealMapper.updateById(a);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("appealId", c.getAppealId());
        m.put("userId", c.getUserId());
        m.put("nickname", c.getNickname());
        m.put("avatar", findAvatar(uid));
        m.put("houseLabel", c.getHouseLabel());
        m.put("content", c.getContent());
        m.put("createTime", fmt(c.getCreateTime()));
        m.put("mine", true);
        m.put("commentCount", a.getCommentCount());
        return m;
    }

    @Transactional
    public void deleteComment(Long commentId) {
        requireLogin();
        AppealComment c = commentMapper.selectById(commentId);
        if (c == null || !Objects.equals(c.getTenantId(), TenantContext.getTenantId())) {
            throw new BizException("评论不存在");
        }
        Long uid = TenantContext.getUserId();
        boolean staff = isStaff();
        if (!Objects.equals(c.getUserId(), uid) && !staff) {
            throw new BizException(403, "只能删除自己的评论");
        }
        if (Integer.valueOf(1).equals(c.getDeleted())) {
            return;
        }
        c.setDeleted(1);
        c.setDeletedBy(uid);
        c.setDeletedTime(LocalDateTime.now());
        commentMapper.updateById(c);

        EmotionAppeal a = appealMapper.selectById(c.getAppealId());
        if (a != null) {
            int n = Optional.ofNullable(a.getCommentCount()).orElse(0);
            a.setCommentCount(Math.max(0, n - 1));
            appealMapper.updateById(a);
        }
        writeOpLog("DELETE_COMMENT", c.getUserId(), c.getAppealId(), c.getId(),
                staff ? "物业删除评论" : "用户删除自己的评论");
    }

    @Transactional
    public Map<String, Object> toggleLike(Long appealId) {
        requireLogin();
        EmotionAppeal a = requirePost(appealId);
        if (Integer.valueOf(1).equals(a.getDeleted())) {
            throw new BizException("动态已删除");
        }
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        AppealLike exists = likeMapper.selectOne(new LambdaQueryWrapper<AppealLike>()
                .eq(AppealLike::getTenantId, tid)
                .eq(AppealLike::getAppealId, appealId)
                .eq(AppealLike::getUserId, uid)
                .last("limit 1"));
        boolean liked;
        int count = Optional.ofNullable(a.getLikeCount()).orElse(0);
        if (exists != null) {
            likeMapper.deleteById(exists.getId());
            liked = false;
            count = Math.max(0, count - 1);
        } else {
            AppealLike like = new AppealLike();
            like.setTenantId(tid);
            like.setAppealId(appealId);
            like.setUserId(uid);
            like.setCreateTime(LocalDateTime.now());
            likeMapper.insert(like);
            liked = true;
            count = count + 1;
        }
        a.setLikeCount(count);
        appealMapper.updateById(a);
        return Map.of("liked", liked, "likeCount", count);
    }

    /** GET /appeal/mute/me */
    public Map<String, Object> myMuteStatus() {
        requireLogin();
        AppealUserMute mute = findActiveMute(TenantContext.getTenantId(), TenantContext.getUserId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("muted", mute != null);
        if (mute != null) {
            m.put("muteUntil", mute.getMuteUntil() == null ? null : fmt(mute.getMuteUntil()));
            m.put("reason", mute.getReason());
            m.put("permanent", mute.getMuteUntil() == null);
        }
        return m;
    }

    /* ===================== 物业后台 ===================== */

    /**
     * GET /appeal/admin/list
     * filter: all | normal | deleted | muted | hidden | pinned
     */
    public List<Map<String, Object>> adminList(String filter) {
        assertStaff();
        ensureMomentExt();
        Long tid = TenantContext.getTenantId();
        String f = filter == null || filter.isBlank() ? "all" : filter.trim();

        LambdaQueryWrapper<EmotionAppeal> q = new LambdaQueryWrapper<EmotionAppeal>()
                .eq(EmotionAppeal::getTenantId, tid)
                .eq(EmotionAppeal::getStatus, "normal")
                .orderByDesc(EmotionAppeal::getPinned)
                .orderByDesc(EmotionAppeal::getId)
                .last("limit 300");
        if ("normal".equals(f)) {
            q.eq(EmotionAppeal::getDeleted, 0)
                    .and(w -> w.isNull(EmotionAppeal::getHidden).or().eq(EmotionAppeal::getHidden, 0));
        } else if ("deleted".equals(f)) {
            q.eq(EmotionAppeal::getDeleted, 1);
        } else if ("hidden".equals(f)) {
            q.eq(EmotionAppeal::getHidden, 1).eq(EmotionAppeal::getDeleted, 0);
        } else if ("pinned".equals(f)) {
            q.eq(EmotionAppeal::getPinned, 1).eq(EmotionAppeal::getDeleted, 0);
        }

        List<EmotionAppeal> list = appealMapper.selectList(q);
        Set<Long> mutedUsers = loadActiveMutedUserIds(tid);

        if ("muted".equals(f)) {
            list = list.stream().filter(a -> mutedUsers.contains(a.getUserId())).collect(Collectors.toList());
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (EmotionAppeal a : list) {
            Map<String, Object> m = toFeedItem(a, false, true);
            boolean userMuted = mutedUsers.contains(a.getUserId());
            m.put("userMuted", userMuted);
            m.put("statusTag", Integer.valueOf(1).equals(a.getDeleted()) ? "已删除"
                    : (Integer.valueOf(1).equals(a.getHidden()) ? "已屏蔽"
                    : (userMuted ? "已禁言" : "正常")));
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> adminDetail(Long id) {
        assertStaff();
        EmotionAppeal a = requirePost(id);
        Map<String, Object> m = toFeedItem(a, false, true);
        m.put("comments", listComments(id, true));
        m.put("userMuted", findActiveMute(a.getTenantId(), a.getUserId()) != null);
        return m;
    }

    @Transactional
    public void adminDeletePost(Long id, String reason) {
        assertStaff();
        EmotionAppeal a = requirePost(id);
        softDeletePost(a, StringUtils.hasText(reason) ? reason : "违规删除");
        writeOpLog("DELETE_POST", a.getUserId(), a.getId(), null,
                "物业删除动态：" + (StringUtils.hasText(reason) ? reason : "违规"));
    }

    @Transactional
    public void adminDeleteComment(Long commentId, String reason) {
        assertStaff();
        AppealComment c = commentMapper.selectById(commentId);
        if (c == null || !Objects.equals(c.getTenantId(), TenantContext.getTenantId())) {
            throw new BizException("评论不存在");
        }
        if (!Integer.valueOf(1).equals(c.getDeleted())) {
            c.setDeleted(1);
            c.setDeletedBy(TenantContext.getUserId());
            c.setDeletedTime(LocalDateTime.now());
            commentMapper.updateById(c);
            EmotionAppeal a = appealMapper.selectById(c.getAppealId());
            if (a != null) {
                int n = Optional.ofNullable(a.getCommentCount()).orElse(0);
                a.setCommentCount(Math.max(0, n - 1));
                appealMapper.updateById(a);
            }
        }
        writeOpLog("DELETE_COMMENT", c.getUserId(), c.getAppealId(), c.getId(),
                "物业删除评论：" + (StringUtils.hasText(reason) ? reason : "违规"));
    }

    @Transactional
    public Map<String, Object> pinPost(Long id, boolean pinned) {
        assertStaff();
        ensureMomentExt();
        EmotionAppeal a = requirePost(id);
        a.setPinned(pinned ? 1 : 0);
        appealMapper.updateById(a);
        writeOpLog(pinned ? "PIN" : "UNPIN", a.getUserId(), a.getId(), null,
                pinned ? "置顶动态" : "取消置顶");
        return toFeedItem(a, false, true);
    }

    @Transactional
    public Map<String, Object> hidePost(Long id, boolean hidden, String reason) {
        assertStaff();
        ensureMomentExt();
        EmotionAppeal a = requirePost(id);
        a.setHidden(hidden ? 1 : 0);
        appealMapper.updateById(a);
        String detail = hidden
                ? ("屏蔽动态" + (StringUtils.hasText(reason) ? "：" + reason : ""))
                : "取消屏蔽";
        writeOpLog(hidden ? "HIDE" : "UNHIDE", a.getUserId(), a.getId(), null, detail);
        return toFeedItem(a, false, true);
    }

    /** 评论统一列表 */
    public List<Map<String, Object>> adminCommentList(String keyword, Boolean deletedOnly) {
        assertStaff();
        Long tid = TenantContext.getTenantId();
        StringBuilder sql = new StringBuilder(
                "select c.id, c.appeal_id as appealId, c.user_id as userId, c.nickname, c.house_label as houseLabel, "
                        + "c.content, c.deleted, c.deleted_by as deletedBy, c.deleted_time as deletedTime, "
                        + "c.create_time as createTime, a.content as postContent, a.nickname as postNickname "
                        + "from appeal_comment c left join emotion_appeal a on a.id=c.appeal_id "
                        + "where c.tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (Boolean.TRUE.equals(deletedOnly)) {
            sql.append("and c.deleted=1 ");
        } else if (Boolean.FALSE.equals(deletedOnly)) {
            sql.append("and c.deleted=0 ");
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (c.content like ? or c.nickname like ? or a.content like ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
            args.add(k);
        }
        sql.append("order by c.id desc limit 500");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) {
            r.put("deleted", r.get("deleted") == null ? 0 : ((Number) r.get("deleted")).intValue());
            r.put("createTime", r.get("createTime") == null ? null
                    : String.valueOf(r.get("createTime")).replace('T', ' ').substring(0, Math.min(19, String.valueOf(r.get("createTime")).length())));
        }
        return rows;
    }

    /* ---------- 举报 ---------- */
    @Transactional
    public Map<String, Object> submitReport(Map<String, Object> body) {
        requireLogin();
        ensureReportTable();
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        String targetType = str(body.get("targetType"));
        if (!"POST".equalsIgnoreCase(targetType) && !"COMMENT".equalsIgnoreCase(targetType)) {
            throw new BizException("举报类型无效");
        }
        targetType = targetType.toUpperCase();
        Long targetId = parseLong(body.get("targetId"));
        if (targetId == null) throw new BizException("缺少举报对象");
        String reason = str(body.get("reason"));
        if (!StringUtils.hasText(reason)) reason = "违规内容";

        Long appealId = null;
        String snapshot = "";
        if ("POST".equals(targetType)) {
            EmotionAppeal a = requirePost(targetId);
            appealId = a.getId();
            snapshot = a.getContent();
        } else {
            AppealComment c = commentMapper.selectById(targetId);
            if (c == null || !Objects.equals(c.getTenantId(), tid)) throw new BizException("评论不存在");
            appealId = c.getAppealId();
            snapshot = c.getContent();
        }
        if (snapshot != null && snapshot.length() > 900) snapshot = snapshot.substring(0, 900);

        jdbcTemplate.update(
                "insert into appeal_report(tenant_id,reporter_id,reporter_name,target_type,target_id,appeal_id,reason,content_snapshot,status,create_time) "
                        + "values(?,?,?,?,?,?,?,?,'PENDING',now())",
                tid, uid, findNickname(uid), targetType, targetId, appealId, reason, snapshot);
        writeOpLog("REPORT", uid, appealId, "COMMENT".equals(targetType) ? targetId : null,
                "提交举报：" + targetType + "#" + targetId + " " + reason);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("message", "举报已提交，物业将尽快处理");
        return m;
    }

    public List<Map<String, Object>> listReports(String status) {
        assertStaff();
        ensureReportTable();
        Long tid = TenantContext.getTenantId();
        StringBuilder sql = new StringBuilder(
                "select id, reporter_id as reporterId, reporter_name as reporterName, target_type as targetType, "
                        + "target_id as targetId, appeal_id as appealId, reason, content_snapshot as contentSnapshot, "
                        + "status, handle_result as handleResult, handler_name as handlerName, "
                        + "handle_time as handleTime, create_time as createTime "
                        + "from appeal_report where tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(status)) {
            sql.append("and status=? ");
            args.add(status.trim().toUpperCase());
        }
        sql.append("order by id desc limit 300");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) {
            String st = str(r.get("status"));
            r.put("statusLabel", "DONE".equals(st) ? "已处理" : "REJECTED".equals(st) ? "已驳回" : "待处理");
            r.put("targetTypeLabel", "COMMENT".equals(str(r.get("targetType"))) ? "评论" : "动态");
            if (r.get("createTime") != null) {
                r.put("createTime", String.valueOf(r.get("createTime")).replace('T', ' ').substring(0, 19));
            }
            if (r.get("handleTime") != null) {
                r.put("handleTime", String.valueOf(r.get("handleTime")).replace('T', ' ').substring(0, 19));
            }
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> handleReport(Long id, Map<String, Object> body) {
        assertStaff();
        ensureReportTable();
        Long tid = TenantContext.getTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from appeal_report where id=? and tenant_id=?", id, tid);
        if (rows.isEmpty()) throw new BizException("举报工单不存在");
        Map<String, Object> report = rows.get(0);
        String action = str(body.get("action")); // done | reject | delete_target
        String result = str(body.get("handleResult"));
        if (!StringUtils.hasText(result)) {
            result = "done".equalsIgnoreCase(action) || "delete_target".equalsIgnoreCase(action)
                    ? "已处理" : "已驳回举报";
        }
        String newStatus = "reject".equalsIgnoreCase(action) ? "REJECTED" : "DONE";

        if ("delete_target".equalsIgnoreCase(action)) {
            String tt = str(report.get("target_type"));
            Long targetId = parseLong(report.get("target_id"));
            if ("COMMENT".equalsIgnoreCase(tt)) {
                adminDeleteComment(targetId, "举报核实删除：" + result);
            } else {
                adminDeletePost(targetId, "举报核实删除：" + result);
            }
        }

        jdbcTemplate.update(
                "update appeal_report set status=?, handle_result=?, handler_id=?, handler_name=?, handle_time=now() where id=?",
                newStatus, result, TenantContext.getUserId(), findNickname(TenantContext.getUserId()), id);
        writeOpLog("HANDLE_REPORT", parseLong(report.get("reporter_id")),
                parseLong(report.get("appeal_id")), null,
                "处理举报#" + id + " → " + newStatus + "：" + result);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("status", newStatus);
        m.put("handleResult", result);
        return m;
    }

    /**
     * POST /appeal/admin/mute
     * body: userId, duration=forever|3d|7d, reason
     */
    @Transactional
    public Map<String, Object> muteUser(Map<String, Object> body) {
        assertStaff();
        Long targetUid = parseLong(body.get("userId"));
        if (targetUid == null) {
            throw new BizException(400, "缺少住户ID");
        }
        String duration = str(body.get("duration"));
        if (!StringUtils.hasText(duration)) duration = "forever";
        LocalDateTime until = switch (duration) {
            case "3d", "3", "3day" -> LocalDateTime.now().plusDays(3);
            case "7d", "7", "7day" -> LocalDateTime.now().plusDays(7);
            default -> null; // forever
        };
        String reason = str(body.get("reason"));
        Long tid = TenantContext.getTenantId();

        // 解除旧的 active
        List<AppealUserMute> actives = muteMapper.selectList(new LambdaQueryWrapper<AppealUserMute>()
                .eq(AppealUserMute::getTenantId, tid)
                .eq(AppealUserMute::getUserId, targetUid)
                .eq(AppealUserMute::getStatus, "active"));
        for (AppealUserMute old : actives) {
            old.setStatus("released");
            old.setUpdateTime(LocalDateTime.now());
            muteMapper.updateById(old);
        }

        AppealUserMute mute = new AppealUserMute();
        mute.setTenantId(tid);
        mute.setUserId(targetUid);
        mute.setMuteUntil(until);
        mute.setReason(reason);
        mute.setOperatorId(TenantContext.getUserId());
        mute.setOperatorName(findNickname(TenantContext.getUserId()));
        mute.setStatus("active");
        mute.setCreateTime(LocalDateTime.now());
        mute.setUpdateTime(LocalDateTime.now());
        muteMapper.insert(mute);

        String detail = until == null ? "永久禁言" : ("禁言至 " + fmt(until));
        if (StringUtils.hasText(reason)) detail = detail + "；原因：" + reason;
        writeOpLog("MUTE", targetUid, null, null, detail);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", targetUid);
        m.put("muteUntil", until == null ? null : fmt(until));
        m.put("permanent", until == null);
        m.put("reason", reason);
        return m;
    }

    @Transactional
    public void unmuteUser(Long userId) {
        assertStaff();
        if (userId == null) throw new BizException(400, "缺少住户ID");
        Long tid = TenantContext.getTenantId();
        List<AppealUserMute> actives = muteMapper.selectList(new LambdaQueryWrapper<AppealUserMute>()
                .eq(AppealUserMute::getTenantId, tid)
                .eq(AppealUserMute::getUserId, userId)
                .eq(AppealUserMute::getStatus, "active"));
        for (AppealUserMute old : actives) {
            old.setStatus("released");
            old.setUpdateTime(LocalDateTime.now());
            muteMapper.updateById(old);
        }
        writeOpLog("UNMUTE", userId, null, null, "解除禁言");
    }

    public List<Map<String, Object>> listOpLogs(int limit) {
        assertStaff();
        int lim = Math.min(Math.max(limit, 1), 200);
        List<AppealOpLog> logs = opLogMapper.selectList(new LambdaQueryWrapper<AppealOpLog>()
                .eq(AppealOpLog::getTenantId, TenantContext.getTenantId())
                .orderByDesc(AppealOpLog::getId)
                .last("limit " + lim));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AppealOpLog l : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("operatorId", l.getOperatorId());
            m.put("operatorName", l.getOperatorName());
            m.put("action", l.getAction());
            m.put("actionLabel", actionLabel(l.getAction()));
            m.put("targetUserId", l.getTargetUserId());
            m.put("targetAppealId", l.getTargetAppealId());
            m.put("targetCommentId", l.getTargetCommentId());
            m.put("detail", l.getDetail());
            m.put("createTime", fmt(l.getCreateTime()));
            out.add(m);
        }
        return out;
    }

    /* ===================== helpers ===================== */

    private void softDeletePost(EmotionAppeal a, String reason) {
        if (Integer.valueOf(1).equals(a.getDeleted())) return;
        a.setDeleted(1);
        a.setDeleteReason(reason);
        a.setDeletedBy(TenantContext.getUserId());
        a.setDeletedTime(LocalDateTime.now());
        appealMapper.updateById(a);
    }

    private Map<String, Object> toFeedItem(EmotionAppeal a, boolean liked, boolean admin) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("userId", a.getUserId());
        // 展示以账号最新资料为准（改昵称/头像后朋友圈同步更新）
        String liveNick = findNickname(a.getUserId());
        String liveAvatar = findAvatar(a.getUserId());
        m.put("nickname", StringUtils.hasText(liveNick) ? liveNick
                : (StringUtils.hasText(a.getNickname()) ? a.getNickname() : "住户"));
        m.put("avatar", liveAvatar);
        m.put("houseId", a.getHouseId());
        m.put("houseLabel", a.getHouseLabel());
        m.put("content", a.getContent());
        m.put("images", parseImages(a.getImageUrls()));
        m.put("imageUrls", a.getImageUrls());
        m.put("likeCount", Optional.ofNullable(a.getLikeCount()).orElse(0));
        m.put("commentCount", Optional.ofNullable(a.getCommentCount()).orElse(0));
        m.put("liked", liked);
        m.put("deleted", a.getDeleted());
        m.put("deleteReason", a.getDeleteReason());
        m.put("pinned", Integer.valueOf(1).equals(a.getPinned()));
        m.put("hidden", Integer.valueOf(1).equals(a.getHidden()));
        m.put("createTime", fmt(a.getCreateTime()));
        m.put("mine", Objects.equals(a.getUserId(), TenantContext.getUserId()));
        if (admin) {
            m.put("deletedBy", a.getDeletedBy());
            m.put("deletedTime", fmt(a.getDeletedTime()));
            m.put("status", a.getStatus());
        }
        return m;
    }

    private void ensureMomentExt() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name='emotion_appeal' and column_name='pinned'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("alter table emotion_appeal add column pinned tinyint not null default 0");
            }
        } catch (Exception ignored) {
        }
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name='emotion_appeal' and column_name='hidden'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("alter table emotion_appeal add column hidden tinyint not null default 0");
            }
        } catch (Exception ignored) {
        }
    }

    private void ensureReportTable() {
        try {
            jdbcTemplate.execute(
                    "create table if not exists appeal_report ("
                            + "id bigint primary key auto_increment,"
                            + "tenant_id bigint not null,"
                            + "reporter_id bigint not null,"
                            + "reporter_name varchar(64) null,"
                            + "target_type varchar(16) not null,"
                            + "target_id bigint not null,"
                            + "appeal_id bigint null,"
                            + "reason varchar(255) null,"
                            + "content_snapshot varchar(1000) null,"
                            + "status varchar(16) not null default 'PENDING',"
                            + "handle_result varchar(500) null,"
                            + "handler_id bigint null,"
                            + "handler_name varchar(64) null,"
                            + "handle_time datetime null,"
                            + "create_time datetime default current_timestamp,"
                            + "key idx_tenant_status (tenant_id, status)"
                            + ") engine=InnoDB default charset=utf8mb4");
        } catch (Exception ignored) {
        }
    }

    private EmotionAppeal requirePost(Long id) {
        if (id == null) throw new BizException(400, "缺少动态ID");
        EmotionAppeal a = appealMapper.selectById(id);
        if (a == null || !Objects.equals(a.getTenantId(), TenantContext.getTenantId())) {
            throw new BizException("动态不存在");
        }
        return a;
    }

    private void assertNotMuted(String tip) {
        AppealUserMute mute = findActiveMute(TenantContext.getTenantId(), TenantContext.getUserId());
        if (mute != null) {
            throw new BizException(403, tip);
        }
    }

    private AppealUserMute findActiveMute(Long tid, Long uid) {
        if (tid == null || uid == null) return null;
        List<AppealUserMute> list = muteMapper.selectList(new LambdaQueryWrapper<AppealUserMute>()
                .eq(AppealUserMute::getTenantId, tid)
                .eq(AppealUserMute::getUserId, uid)
                .eq(AppealUserMute::getStatus, "active")
                .orderByDesc(AppealUserMute::getId)
                .last("limit 5"));
        LocalDateTime now = LocalDateTime.now();
        for (AppealUserMute m : list) {
            if (m.getMuteUntil() == null || m.getMuteUntil().isAfter(now)) {
                return m;
            }
            // 过期自动解除
            m.setStatus("released");
            m.setUpdateTime(now);
            muteMapper.updateById(m);
        }
        return null;
    }

    private Set<Long> loadActiveMutedUserIds(Long tid) {
        List<AppealUserMute> list = muteMapper.selectList(new LambdaQueryWrapper<AppealUserMute>()
                .eq(AppealUserMute::getTenantId, tid)
                .eq(AppealUserMute::getStatus, "active"));
        LocalDateTime now = LocalDateTime.now();
        Set<Long> set = new HashSet<>();
        for (AppealUserMute m : list) {
            if (m.getMuteUntil() == null || m.getMuteUntil().isAfter(now)) {
                set.add(m.getUserId());
            }
        }
        return set;
    }

    private Set<Long> loadLikedAppealIds(Long tid, Long uid, List<EmotionAppeal> posts) {
        if (uid == null || posts == null || posts.isEmpty()) return Set.of();
        List<Long> ids = posts.stream().map(EmotionAppeal::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return Set.of();
        List<AppealLike> likes = likeMapper.selectList(new LambdaQueryWrapper<AppealLike>()
                .eq(AppealLike::getTenantId, tid)
                .eq(AppealLike::getUserId, uid)
                .in(AppealLike::getAppealId, ids));
        return likes.stream().map(AppealLike::getAppealId).collect(Collectors.toSet());
    }

    private void writeOpLog(String action, Long targetUserId, Long appealId, Long commentId, String detail) {
        try {
            AppealOpLog row = new AppealOpLog();
            row.setTenantId(TenantContext.getTenantId());
            row.setOperatorId(TenantContext.getUserId());
            row.setOperatorName(findNickname(TenantContext.getUserId()));
            row.setAction(action);
            row.setTargetUserId(targetUserId);
            row.setTargetAppealId(appealId);
            row.setTargetCommentId(commentId);
            row.setDetail(detail);
            row.setCreateTime(LocalDateTime.now());
            opLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("appeal_op_log skip: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseImages(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null && StringUtils.hasText(String.valueOf(o))) {
                    out.add(normalizeImage(String.valueOf(o)));
                }
            }
            return out;
        }
        String s = String.valueOf(raw).trim();
        if (!StringUtils.hasText(s)) return List.of();
        if (s.startsWith("[")) {
            try {
                List<String> arr = objectMapper.readValue(s, new TypeReference<List<String>>() {});
                return arr == null ? List.of() : arr.stream()
                        .filter(StringUtils::hasText)
                        .map(this::normalizeImage)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                return List.of();
            }
        }
        return List.of(normalizeImage(s));
    }

    private String writeImages(List<String> images) {
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            throw new BizException(400, "图片数据格式错误");
        }
    }

    private String normalizeImage(String url) {
        String u = url.trim();
        while (u.startsWith("/api/")) u = u.substring(4);
        if (u.startsWith("http://") || u.startsWith("https://")) {
            try {
                java.net.URI uri = java.net.URI.create(u);
                if (uri.getPath() != null) u = uri.getPath();
            } catch (Exception ignored) {
            }
        }
        if (!u.startsWith("/")) u = "/" + u;
        return u;
    }

    private String resolveHouseLabel(Long tid, Long houseId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select community_name as c, building, unit, room_no as r from community_house where tenant_id=? and id=? limit 1",
                    tid, houseId);
            if (rows.isEmpty()) return null;
            Map<String, Object> h = rows.get(0);
            String shortLabel = str(h.get("building")) + str(h.get("unit")) + str(h.get("r"));
            String c = str(h.get("c"));
            if (StringUtils.hasText(c) && StringUtils.hasText(shortLabel)) return c + " · " + shortLabel;
            return StringUtils.hasText(shortLabel) ? shortLabel : c;
        } catch (Exception e) {
            return null;
        }
    }

    private String findNickname(Long uid) {
        if (uid == null) return "住户";
        try {
            String n = jdbcTemplate.queryForObject("select nickname from sys_user where id=?", String.class, uid);
            return StringUtils.hasText(n) ? n : ("住户#" + uid);
        } catch (Exception e) {
            return "住户#" + uid;
        }
    }

    private String findAvatar(Long uid) {
        if (uid == null) return null;
        try {
            String a = jdbcTemplate.queryForObject("select avatar from sys_user where id=?", String.class, uid);
            return StringUtils.hasText(a) ? a.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void requireLogin() {
        if (TenantContext.getTenantId() == null || TenantContext.getUserId() == null) {
            throw new BizException(401, "请先登录");
        }
    }

    private void assertStaff() {
        requireLogin();
        if (!isStaff()) throw new BizException(403, "仅物业人员可操作");
    }

    private boolean isStaff() {
        String role = TenantContext.getRole();
        return RoleConstants.isTenantStaff(role) || RoleConstants.isPlatformAdmin(role);
    }

    private String actionLabel(String a) {
        return switch (a == null ? "" : a) {
            case "DELETE_POST" -> "删除动态";
            case "DELETE_COMMENT" -> "删除评论";
            case "MUTE" -> "禁言";
            case "UNMUTE" -> "解除禁言";
            case "PIN" -> "置顶动态";
            case "UNPIN" -> "取消置顶";
            case "HIDE" -> "屏蔽动态";
            case "UNHIDE" -> "取消屏蔽";
            case "REPORT" -> "提交举报";
            case "HANDLE_REPORT" -> "处理举报";
            default -> a;
        };
    }

    private String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DT);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private Long parseLong(Object o) {
        if (o == null) return null;
        try {
            return Long.valueOf(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
