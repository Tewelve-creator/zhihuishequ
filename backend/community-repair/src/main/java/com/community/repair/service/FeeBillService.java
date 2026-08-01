package com.community.repair.service;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import com.community.repair.dto.FeeBatchRequest;
import com.community.repair.dto.FeeBillSaveRequest;
import com.community.repair.dto.FeePayActionRequest;
import com.community.repair.dto.FeePayCreateRequest;
import com.community.repair.dto.FeeReceiptNoticeRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeeBillService {

    public static final String ST_UNPAID = "UNPAID";
    public static final String ST_PAYING = "PAYING";
    public static final String ST_PAID = "PAID";
    public static final String PAY_PAYING = "PAYING";
    public static final String PAY_PAID = "PAID";
    public static final String PAY_FAILED = "FAILED";
    public static final String PAY_CLOSED = "CLOSED";

    private final JdbcTemplate jdbc;
    private final AlipaySandboxService alipaySandboxService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> manageList(String status, String keyword, String period) {
        assertCanView();
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder(
                "select id, tenant_id as tenantId, house_id as houseId, house_label as houseLabel, "
                        + "house_label as room, user_id as userId, payer_name as payerName, payer_name as owner, "
                        + "payer_phone as payerPhone, fee_type as feeType, fee_type_name as feeTypeName, "
                        + "fee_type_name as item, plate_no as plateNo, amount, period, due_date as dueDate, status, "
                        + "paid_at as paidAt, remark, create_time as createTime "
                        + "from fee_bill where tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(status)) {
            sql.append("and status=? ");
            args.add(status);
        }
        if (StringUtils.hasText(period)) {
            sql.append("and period=? ");
            args.add(period);
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (payer_name like ? or payer_phone like ? or house_label like ? or fee_type_name like ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
            args.add(k);
            args.add(k);
        }
        sql.append("order by id desc limit 500");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        rows.forEach(this::decorateBill);
        return rows;
    }

    @Transactional
    public Map<String, Object> saveBill(FeeBillSaveRequest req) {
        assertCanManage();
        Long tid = requireTenant();
        validateSave(req);
        Map<String, Object> house = requireHouseWithResident(tid, req.getHouseId());
        String feeType = normalizeFeeType(req.getFeeType());
        String feeName = feeTypeName(feeType);
        String payerName = StringUtils.hasText(req.getPayerName())
                ? req.getPayerName().trim()
                : str(house.get("payerName"));
        String payerPhone = StringUtils.hasText(req.getPayerPhone())
                ? req.getPayerPhone().trim()
                : str(house.get("payerPhone"));
        Long userId = req.getUserId() != null ? req.getUserId() : toLong(house.get("userId"));
        String houseLabel = str(house.get("houseLabel"));
        Date due = parseDate(req.getDueDate());
        String plateNo = null;
        if ("PARKING".equals(feeType) && StringUtils.hasText(req.getPlateNo())) {
            plateNo = com.community.repair.util.PlateNoUtils.normalizeOptional(req.getPlateNo());
        }

        if (req.getId() != null) {
            Map<String, Object> old = getBillRaw(tid, req.getId());
            if (ST_PAID.equals(str(old.get("status")))) {
                throw new BizException("已缴账单不可编辑");
            }
            jdbc.update(
                    "update fee_bill set house_id=?, house_label=?, user_id=?, payer_name=?, payer_phone=?, "
                            + "fee_type=?, fee_type_name=?, plate_no=?, amount=?, period=?, due_date=?, remark=?, update_time=? "
                            + "where id=? and tenant_id=?",
                    req.getHouseId(), houseLabel, userId, payerName, payerPhone,
                    feeType, feeName, plateNo, req.getAmount(), req.getPeriod().trim(), due, req.getRemark(),
                    Timestamp.valueOf(LocalDateTime.now()), req.getId(), tid);
            return getBill(tid, req.getId());
        }

        KeyHolder kh = new GeneratedKeyHolder();
        Long createBy = TenantContext.getUserId();
        String finalPlate = plateNo;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into fee_bill(tenant_id,house_id,house_label,user_id,payer_name,payer_phone,"
                            + "fee_type,fee_type_name,plate_no,amount,period,due_date,status,remark,create_by,create_time) "
                            + "values(?,?,?,?,?,?,?,?,?,?,?,?,'UNPAID',?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setLong(i++, tid);
            ps.setLong(i++, req.getHouseId());
            ps.setString(i++, houseLabel);
            if (userId == null) ps.setObject(i++, null);
            else ps.setLong(i++, userId);
            ps.setString(i++, payerName);
            ps.setString(i++, payerPhone);
            ps.setString(i++, feeType);
            ps.setString(i++, feeName);
            ps.setString(i++, finalPlate);
            ps.setBigDecimal(i++, req.getAmount());
            ps.setString(i++, req.getPeriod().trim());
            ps.setDate(i++, due);
            ps.setString(i++, req.getRemark());
            if (createBy == null) ps.setObject(i++, null);
            else ps.setLong(i++, createBy);
            ps.setTimestamp(i, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, kh);
        Number key = kh.getKey();
        return getBill(tid, key == null ? null : key.longValue());
    }

    @Transactional
    public int batchCreate(FeeBatchRequest req) {
        assertCanManage();
        Long tid = requireTenant();
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("金额必须为正数");
        }
        if (!StringUtils.hasText(req.getPeriod())) {
            throw new BizException("请填写缴费周期");
        }
        String feeType = normalizeFeeType(req.getFeeType());
        List<Map<String, Object>> residents;
        if (req.getHouseIds() != null && !req.getHouseIds().isEmpty()) {
            String in = req.getHouseIds().stream().map(String::valueOf).collect(Collectors.joining(","));
            residents = jdbc.queryForList(
                    "select id, house_id as houseId, user_id as userId, name as payerName, phone as payerPhone, "
                            + "house_label as houseLabel from household_resident "
                            + "where tenant_id=? and status='ACTIVE' and house_id in (" + in + ")",
                    tid);
        } else {
            residents = jdbc.queryForList(
                    "select id, house_id as houseId, user_id as userId, name as payerName, phone as payerPhone, "
                            + "house_label as houseLabel from household_resident where tenant_id=? and status='ACTIVE'",
                    tid);
        }
        if (residents.isEmpty()) {
            throw new BizException("没有可生成账单的有效住户");
        }
        int n = 0;
        for (Map<String, Object> r : residents) {
            FeeBillSaveRequest one = new FeeBillSaveRequest();
            one.setHouseId(toLong(r.get("houseId")));
            one.setUserId(toLong(r.get("userId")));
            one.setPayerName(str(r.get("payerName")));
            one.setPayerPhone(str(r.get("payerPhone")));
            one.setFeeType(feeType);
            one.setAmount(req.getAmount());
            one.setPeriod(req.getPeriod());
            one.setDueDate(req.getDueDate());
            one.setRemark(req.getRemark());
            saveBill(one);
            n++;
        }
        return n;
    }

    @Transactional
    public void deleteBill(Long id) {
        assertCanManage();
        Long tid = requireTenant();
        Map<String, Object> bill = getBillRaw(tid, id);
        if (ST_PAID.equals(str(bill.get("status")))) {
            throw new BizException("已缴账单禁止删除");
        }
        if (ST_PAYING.equals(str(bill.get("status")))) {
            throw new BizException("支付中的账单暂不可删除");
        }
        jdbc.update("delete from fee_bill where id=? and tenant_id=?", id, tid);
    }

    public List<Map<String, Object>> houseOptions() {
        assertCanView();
        Long tid = requireTenant();
        return jdbc.queryForList(
                "select id, tenant_id as tenantId, community_name as communityName, building, unit, room_no as roomNo, "
                        + "concat(community_name,' · ',building,unit,room_no) as label "
                        + "from community_house where tenant_id=? and status=1 "
                        + "order by building, unit, room_no",
                tid);
    }

    public List<Map<String, Object>> payersByHouse(Long houseId) {
        assertCanView();
        Long tid = requireTenant();
        return jdbc.queryForList(
                "select id, user_id as userId, name as payerName, phone as payerPhone, identity_type as identityType, "
                        + "house_label as houseLabel, status "
                        + "from household_resident where tenant_id=? and house_id=? and status='ACTIVE' order by id",
                tid, houseId);
    }

    /** PC：实收缴费流水 */
    public List<Map<String, Object>> managePayments(String startDate, String endDate, String keyword, String payChannel) {
        assertCanView();
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder(
                "select p.id, p.order_no as orderNo, p.user_id as userId, p.house_id as houseId, "
                        + "p.house_label as houseLabel, p.house_label as room, p.amount, "
                        + "p.pay_channel as payChannel, p.status, p.pay_trade_no as payTradeNo, "
                        + "p.bill_ids as billIds, p.paid_at as paidAt, p.create_time as createTime, "
                        + "(select b.payer_name from fee_bill b where b.tenant_id=p.tenant_id "
                        + " and find_in_set(b.id, p.bill_ids) limit 1) as payerName, "
                        + "(select b.payer_phone from fee_bill b where b.tenant_id=p.tenant_id "
                        + " and find_in_set(b.id, p.bill_ids) limit 1) as payerPhone "
                        + "from fee_payment p where p.tenant_id=? and p.status='PAID' ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(startDate)) {
            sql.append("and p.paid_at >= ? ");
            args.add(Timestamp.valueOf(LocalDate.parse(startDate.substring(0, 10)).atStartOfDay()));
        }
        if (StringUtils.hasText(endDate)) {
            sql.append("and p.paid_at < ? ");
            args.add(Timestamp.valueOf(LocalDate.parse(endDate.substring(0, 10)).plusDays(1).atStartOfDay()));
        }
        if (StringUtils.hasText(payChannel)) {
            sql.append("and p.pay_channel=? ");
            args.add(payChannel.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (p.order_no like ? or p.house_label like ? or p.pay_trade_no like ? "
                    + "or exists (select 1 from fee_bill b where b.tenant_id=p.tenant_id "
                    + "and find_in_set(b.id, p.bill_ids) and (b.payer_name like ? or b.payer_phone like ?))) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
            args.add(k);
            args.add(k);
            args.add(k);
        }
        sql.append("order by p.paid_at desc, p.id desc limit 1000");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) {
            String ch = str(r.get("payChannel"));
            r.put("payChannelLabel", "ALIPAY".equals(ch) ? "支付宝" : "WECHAT".equals(ch) ? "微信支付" : ch);
            r.put("statusLabel", "已支付");
        }
        return rows;
    }

    /** PC：欠费汇总（楼栋聚合 + 住户明细） */
    public Map<String, Object> arrearsSummary(String building, Boolean overdueOnly, String keyword) {
        assertCanView();
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder(
                "select b.id as billId, b.house_id as houseId, b.house_label as houseLabel, "
                        + "b.user_id as userId, b.payer_name as payerName, b.payer_phone as payerPhone, "
                        + "b.fee_type_name as feeTypeName, b.amount, b.period, b.due_date as dueDate, b.status, "
                        + "h.building as building, h.unit as unit, h.room_no as roomNo "
                        + "from fee_bill b "
                        + "left join community_house h on h.id=b.house_id and h.tenant_id=b.tenant_id "
                        + "where b.tenant_id=? and b.status in ('UNPAID','PAYING') ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(building)) {
            sql.append("and h.building=? ");
            args.add(building.trim());
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (b.payer_name like ? or b.payer_phone like ? or b.house_label like ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
            args.add(k);
        }
        sql.append("order by h.building, h.unit, h.room_no, b.id");
        List<Map<String, Object>> bills = jdbc.queryForList(sql.toString(), args.toArray());
        Map<String, Map<String, Object>> houseMap = new HashMap<>();
        Map<String, Map<String, Object>> buildingMap = new HashMap<>();
        for (Map<String, Object> bill : bills) {
            decorateBill(bill);
            boolean overdue = "OVERDUE".equals(str(bill.get("status")));
            if (Boolean.TRUE.equals(overdueOnly) && !overdue) {
                continue;
            }
            String bld = StringUtils.hasText(str(bill.get("building"))) ? str(bill.get("building")) : "未分楼栋";
            Long houseId = toLong(bill.get("houseId"));
            String houseKey = houseId != null ? String.valueOf(houseId) : str(bill.get("houseLabel"));
            Map<String, Object> house = houseMap.computeIfAbsent(houseKey, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("houseId", houseId);
                m.put("building", bld);
                m.put("room", bill.get("houseLabel"));
                m.put("owner", bill.get("payerName"));
                m.put("phone", bill.get("payerPhone"));
                m.put("userId", bill.get("userId"));
                m.put("unpaidCount", 0);
                m.put("unpaidAmount", BigDecimal.ZERO);
                m.put("overdueCount", 0);
                m.put("overdueAmount", BigDecimal.ZERO);
                m.put("periods", new ArrayList<String>());
                return m;
            });
            BigDecimal amt = toDecimal(bill.get("amount"));
            house.put("unpaidCount", ((Number) house.get("unpaidCount")).intValue() + 1);
            house.put("unpaidAmount", toDecimal(house.get("unpaidAmount")).add(amt));
            @SuppressWarnings("unchecked")
            List<String> periods = (List<String>) house.get("periods");
            String period = str(bill.get("period"));
            if (StringUtils.hasText(period) && !periods.contains(period)) {
                periods.add(period);
            }
            if (overdue) {
                house.put("overdueCount", ((Number) house.get("overdueCount")).intValue() + 1);
                house.put("overdueAmount", toDecimal(house.get("overdueAmount")).add(amt));
            }
            // 优先保留有 userId / 手机号的业主信息
            if (house.get("userId") == null && bill.get("userId") != null) {
                house.put("userId", bill.get("userId"));
            }
            if (!StringUtils.hasText(str(house.get("owner"))) && StringUtils.hasText(str(bill.get("payerName")))) {
                house.put("owner", bill.get("payerName"));
            }
            if (!StringUtils.hasText(str(house.get("phone"))) && StringUtils.hasText(str(bill.get("payerPhone")))) {
                house.put("phone", bill.get("payerPhone"));
            }

            Map<String, Object> bd = buildingMap.computeIfAbsent(bld, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("building", bld);
                m.put("houseCount", 0);
                m.put("unpaidCount", 0);
                m.put("unpaidAmount", BigDecimal.ZERO);
                m.put("overdueCount", 0);
                m.put("overdueAmount", BigDecimal.ZERO);
                return m;
            });
            bd.put("unpaidCount", ((Number) bd.get("unpaidCount")).intValue() + 1);
            bd.put("unpaidAmount", toDecimal(bd.get("unpaidAmount")).add(amt));
            if (overdue) {
                bd.put("overdueCount", ((Number) bd.get("overdueCount")).intValue() + 1);
                bd.put("overdueAmount", toDecimal(bd.get("overdueAmount")).add(amt));
            }
        }
        // 楼栋房屋数 = 去重后该楼栋欠费户数
        Map<String, Integer> houseCnt = new HashMap<>();
        for (Map<String, Object> h : houseMap.values()) {
            String bld = str(h.get("building"));
            houseCnt.merge(bld, 1, Integer::sum);
        }
        for (Map.Entry<String, Map<String, Object>> e : buildingMap.entrySet()) {
            e.getValue().put("houseCount", houseCnt.getOrDefault(e.getKey(), 0));
        }

        List<Map<String, Object>> houses = new ArrayList<>(houseMap.values());
        houses.sort((a, b) -> {
            int c = str(a.get("building")).compareTo(str(b.get("building")));
            if (c != 0) return c;
            return str(a.get("room")).compareTo(str(b.get("room")));
        });
        List<Map<String, Object>> buildings = new ArrayList<>(buildingMap.values());
        buildings.sort((a, b) -> str(a.get("building")).compareTo(str(b.get("building"))));

        List<String> buildingOptions = jdbc.queryForList(
                "select distinct building from community_house where tenant_id=? and status=1 and building is not null "
                        + "and building<>'' order by building",
                String.class, tid);

        Map<String, Object> result = new HashMap<>();
        result.put("buildings", buildings);
        result.put("houses", houses);
        result.put("buildingOptions", buildingOptions);
        result.put("totalHouses", houses.size());
        result.put("totalUnpaidAmount", houses.stream()
                .map(h -> toDecimal(h.get("unpaidAmount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.put("totalOverdueHouses", houses.stream()
                .filter(h -> ((Number) h.get("overdueCount")).intValue() > 0)
                .count());
        return result;
    }

    public List<Map<String, Object>> listReceiptNotices() {
        assertCanView();
        ensureReceiptNoticeTable();
        Long tid = requireTenant();
        return jdbc.queryForList(
                "select id, title, content, scope, amount_hint as amountHint, push_count as pushCount, "
                        + "status, send_at as sendAt, create_time as createTime, target_json as targetJson "
                        + "from fee_receipt_notice where tenant_id=? order by id desc limit 200",
                tid);
    }

    @Transactional
    public Map<String, Object> createReceiptNotice(FeeReceiptNoticeRequest req) {
        assertCanManage();
        ensureReceiptNoticeTable();
        Long tid = requireTenant();
        if (req == null || !StringUtils.hasText(req.getTitle())) {
            throw new BizException("请填写通知标题");
        }
        String title = req.getTitle().trim();
        String content = StringUtils.hasText(req.getContent()) ? req.getContent().trim() : title;
        String scope = StringUtils.hasText(req.getScope()) ? req.getScope().trim() : "指定业主";
        String amountHint = StringUtils.hasText(req.getAmountHint()) ? req.getAmountHint().trim() : "";
        List<Map<String, Object>> targets = req.getTargets() != null ? req.getTargets() : List.of();
        int pushCount = 0;
        LocalDateTime now = LocalDateTime.now();
        java.util.Set<Long> pushedUsers = new java.util.HashSet<>();
        for (Map<String, Object> t : targets) {
            List<Long> uids = resolveTargetUserIds(tid, t);
            if (uids.isEmpty()) continue;
            String room = str(t.get("room"));
            String owner = str(t.get("owner"));
            Object amt = t.get("unpaidAmount");
            String amtText = amt == null ? "" : ("欠费金额 ¥" + toDecimal(amt).setScale(2, java.math.RoundingMode.HALF_UP));
            String msg = content;
            if (StringUtils.hasText(room) || StringUtils.hasText(owner) || StringUtils.hasText(amtText)) {
                msg = content + "\n【" + room + " " + owner + "】" + amtText;
            }
            if (msg.length() > 900) {
                msg = msg.substring(0, 900);
            }
            for (Long uid : uids) {
                if (uid == null || !pushedUsers.add(uid)) continue;
                jdbc.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,0,?)",
                        tid, uid, title, msg, "fee_remind", Timestamp.valueOf(now));
                pushCount++;
            }
        }
        if (targets.isEmpty()) {
            // 无精确对象时：推送本社区全部已绑定 App 账号的住户
            List<Map<String, Object>> users = jdbc.queryForList(
                    "select distinct user_id as userId from ("
                            + " select user_id from household_resident where tenant_id=? and status='ACTIVE' and user_id is not null"
                            + " union"
                            + " select user_id from user_house_bind where tenant_id=? and user_id is not null"
                            + ") t",
                    tid, tid);
            for (Map<String, Object> u : users) {
                Long uid = toLong(u.get("userId"));
                if (uid == null || !pushedUsers.add(uid)) continue;
                String msg = content.length() > 900 ? content.substring(0, 900) : content;
                jdbc.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,0,?)",
                        tid, uid, title, msg, "fee_remind", Timestamp.valueOf(now));
                pushCount++;
            }
            if (!StringUtils.hasText(req.getScope())) {
                scope = "全小区业主";
            }
        }
        if (pushCount == 0) {
            throw new BizException("未匹配到可接收提醒的 App 住户账号，请确认业主已绑定房屋");
        }
        String targetJson;
        try {
            targetJson = objectMapper.writeValueAsString(targets);
        } catch (JsonProcessingException e) {
            targetJson = "[]";
        }
        final String finalTargetJson = targetJson;
        KeyHolder kh = new GeneratedKeyHolder();
        String finalScope = scope;
        int finalPush = pushCount;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into fee_receipt_notice(tenant_id,title,content,scope,amount_hint,target_json,push_count,status,send_at,create_by,create_time) "
                            + "values(?,?,?,?,?,?,?,'SENT',?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setString(2, title);
            ps.setString(3, content);
            ps.setString(4, finalScope);
            ps.setString(5, amountHint);
            ps.setString(6, finalTargetJson);
            ps.setInt(7, finalPush);
            ps.setTimestamp(8, Timestamp.valueOf(now));
            Long uid = TenantContext.getUserId();
            if (uid == null) ps.setObject(9, null);
            else ps.setLong(9, uid);
            ps.setTimestamp(10, Timestamp.valueOf(now));
            return ps;
        }, kh);
        Number key = kh.getKey();
        Map<String, Object> m = new HashMap<>();
        m.put("id", key == null ? null : key.longValue());
        m.put("title", title);
        m.put("scope", scope);
        m.put("amountHint", amountHint);
        m.put("pushCount", pushCount);
        m.put("status", "SENT");
        m.put("sendAt", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return m;
    }

    private void ensureReceiptNoticeTable() {
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS fee_receipt_notice ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "tenant_id BIGINT NOT NULL,"
                        + "title VARCHAR(128) NOT NULL,"
                        + "content VARCHAR(2000) NULL,"
                        + "scope VARCHAR(255) NULL,"
                        + "amount_hint VARCHAR(128) NULL,"
                        + "target_json TEXT NULL,"
                        + "push_count INT NOT NULL DEFAULT 0,"
                        + "status VARCHAR(16) NOT NULL DEFAULT 'SENT',"
                        + "send_at DATETIME NULL,"
                        + "create_by BIGINT NULL,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "KEY idx_tenant_time (tenant_id, send_at)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    /** 解析催缴对象对应的 App 用户 ID（账单 userId / 住户档案 / 房屋绑定 / 手机号） */
    private List<Long> resolveTargetUserIds(Long tid, Map<String, Object> t) {
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        Long uid = toLong(t.get("userId"));
        if (uid != null) ids.add(uid);
        Long houseId = toLong(t.get("houseId"));
        String phone = str(t.get("phone"));
        if (houseId != null) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select distinct user_id as userId from ("
                            + " select user_id from household_resident where tenant_id=? and house_id=? and status='ACTIVE' and user_id is not null"
                            + " union"
                            + " select user_id from user_house_bind where tenant_id=? and house_id=? and user_id is not null"
                            + " union"
                            + " select user_id from fee_bill where tenant_id=? and house_id=? and user_id is not null"
                            + ") x",
                    tid, houseId, tid, houseId, tid, houseId);
            for (Map<String, Object> r : rows) {
                Long id = toLong(r.get("userId"));
                if (id != null) ids.add(id);
            }
        }
        if (StringUtils.hasText(phone)) {
            List<Map<String, Object>> byPhone = jdbc.queryForList(
                    "select distinct user_id as userId from household_resident "
                            + "where tenant_id=? and phone=? and status='ACTIVE' and user_id is not null",
                    tid, phone.trim());
            for (Map<String, Object> r : byPhone) {
                Long id = toLong(r.get("userId"));
                if (id != null) ids.add(id);
            }
            try {
                List<Map<String, Object>> users = jdbc.queryForList(
                        "select id as userId from sys_user where tenant_id=? and phone=? and status=1 limit 5",
                        tid, phone.trim());
                for (Map<String, Object> r : users) {
                    Long id = toLong(r.get("userId"));
                    if (id != null) ids.add(id);
                }
            } catch (Exception ignored) {
                // sys_user 可能不在本库，忽略
            }
        }
        return new ArrayList<>(ids);
    }

    /* ---------------- resident ---------------- */

    public Map<String, Object> residentSummary(Long houseId) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        if (houseId == null) {
            houseId = TenantContext.getHouseId();
        }
        houseId = requireBoundHouse(tid, uid, houseId);
        List<Map<String, Object>> unpaid = jdbc.queryForList(
                "select amount, due_date as dueDate, status from fee_bill "
                        + "where tenant_id=? and house_id=? and status in ('UNPAID','PAYING')",
                tid, houseId);
        BigDecimal total = BigDecimal.ZERO;
        int overdue = 0;
        LocalDate today = LocalDate.now();
        String earliest = null;
        for (Map<String, Object> b : unpaid) {
            total = total.add(toDecimal(b.get("amount")));
            Date due = (Date) b.get("dueDate");
            if (due != null) {
                String ds = due.toLocalDate().toString();
                if (earliest == null || ds.compareTo(earliest) < 0) earliest = ds;
                if (due.toLocalDate().isBefore(today)) overdue++;
            }
        }
        Map<String, Object> m = new HashMap<>();
        m.put("unpaidAmount", total);
        m.put("unpaidCount", unpaid.size());
        m.put("earliestDueDate", earliest);
        m.put("overdueCount", overdue);
        return m;
    }

    public List<Map<String, Object>> residentBills(Long houseId, String tab) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        if (houseId == null) {
            houseId = TenantContext.getHouseId();
        }
        houseId = requireBoundHouse(tid, uid, houseId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, tenant_id as tenantId, house_id as houseId, house_label as houseLabel, "
                        + "payer_name as payerName, payer_phone as payerPhone, fee_type as feeType, "
                        + "fee_type_name as feeTypeName, fee_type_name as itemName, amount, amount as payable, "
                        + "0 as discount, 0 as lateFee, period, due_date as dueDate, status, paid_at as paidAt, "
                        + "remark, create_time as createTime, "
                        + "concat(fee_type_name, period, '账单') as title, "
                        + "concat('BILL', lpad(id, 8, '0')) as billNo "
                        + "from fee_bill where tenant_id=? and house_id=? order by id desc",
                tid, houseId);
        rows.forEach(r -> {
            r.put("rawStatus", r.get("status"));
            decorateBill(r);
            if (!StringUtils.hasText(str(r.get("title")))) {
                r.put("title", str(r.get("feeTypeName")) + str(r.get("period")) + "账单");
            }
        });
        if ("unpaid".equalsIgnoreCase(tab)) {
            return rows.stream()
                    .filter(r -> {
                        String rs = str(r.get("rawStatus"));
                        return ST_UNPAID.equals(rs) || ST_PAYING.equals(rs);
                    })
                    .collect(Collectors.toList());
        }
        if ("paid".equalsIgnoreCase(tab)) {
            return rows.stream().filter(r -> ST_PAID.equals(r.get("rawStatus"))).collect(Collectors.toList());
        }
        return rows;
    }

    public Map<String, Object> residentBillDetail(Long id) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        Map<String, Object> bill = getBill(tid, id);
        requireBoundHouse(tid, uid, toLong(bill.get("houseId")));
        bill.put("title", str(bill.get("feeTypeName")) + str(bill.get("period")) + "账单");
        bill.put("billNo", "BILL" + String.format("%08d", id));
        bill.put("itemName", bill.get("feeTypeName"));
        bill.put("payable", bill.get("amount"));
        bill.put("discount", 0);
        bill.put("lateFee", 0);
        bill.put("chargeUnit", "物业管理处");
        bill.put("chargeBasis", "按收费标准计费");
        bill.put("items", List.of(Map.of("name", bill.get("feeTypeName"), "amount", bill.get("amount"))));
        return bill;
    }

    @Transactional
    public Map<String, Object> createOrder(FeePayCreateRequest req) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        if (req.getBillIds() == null || req.getBillIds().isEmpty()) {
            throw new BizException("请选择账单");
        }
        Long houseId = req.getHouseId() != null ? req.getHouseId() : TenantContext.getHouseId();
        houseId = requireBoundHouse(tid, uid, houseId);
        String channel = "ALIPAY".equalsIgnoreCase(req.getPayChannel()) ? "ALIPAY" : "WECHAT";
        BigDecimal amount = BigDecimal.ZERO;
        List<Map<String, Object>> bills = new ArrayList<>();
        for (Long bid : req.getBillIds()) {
            Map<String, Object> mapped = mapBill(getBillRaw(tid, bid));
            Long bHouse = toLong(mapped.get("houseId"));
            if (!Objects.equals(bHouse, houseId)) {
                throw new BizException(403, "账单不属于当前房屋");
            }
            String st = str(mapped.get("status"));
            if (!ST_UNPAID.equals(st) && !ST_PAYING.equals(st)) {
                throw new BizException("账单状态不可支付：" + bid);
            }
            amount = amount.add(toDecimal(mapped.get("amount")));
            bills.add(decorateBill(mapped));
            jdbc.update("update fee_bill set status=?, update_time=? where id=? and tenant_id=?",
                    ST_PAYING, Timestamp.valueOf(LocalDateTime.now()), bid, tid);
        }
        String orderNo = "FO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + (int) (Math.random() * 900 + 100);
        String billIds = req.getBillIds().stream().map(String::valueOf).collect(Collectors.joining(","));
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(bills);
        } catch (JsonProcessingException e) {
            snapshot = "[]";
        }
        String houseLabel = str(bills.get(0).get("houseLabel"));
        jdbc.update(
                "insert into fee_payment(tenant_id,order_no,user_id,house_id,house_label,amount,pay_channel,status,bill_ids,bill_snapshot,create_time) "
                        + "values(?,?,?,?,?,?,?,'PAYING',?,?,?)",
                tid, orderNo, uid, houseId, houseLabel, amount, channel, billIds, snapshot,
                Timestamp.valueOf(LocalDateTime.now()));
        Map<String, Object> order = new HashMap<>();
        order.put("orderId", orderNo);
        order.put("orderNo", orderNo);
        order.put("amount", amount);
        order.put("payChannel", channel);
        order.put("status", PAY_PAYING);
        order.put("houseId", houseId);
        order.put("houseLabel", houseLabel);
        order.put("billIds", req.getBillIds());
        List<Map<String, Object>> billViews = new ArrayList<>();
        for (Map<String, Object> b : bills) {
            Map<String, Object> bv = new HashMap<>();
            bv.put("id", b.get("id"));
            bv.put("billNo", "BILL" + String.format("%08d", toLong(b.get("id"))));
            bv.put("title", str(b.get("feeTypeName")) + str(b.get("period")) + "账单");
            bv.put("payable", b.get("amount"));
            billViews.add(bv);
        }
        order.put("bills", billViews);
        order.put("createTime", LocalDateTime.now().toString());
        return order;
    }

    @Transactional
    public Map<String, Object> payAction(String orderNo, FeePayActionRequest req) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        Map<String, Object> pay = getPayment(tid, orderNo);
        if (!Objects.equals(toLong(pay.get("userId")), uid)) {
            throw new BizException(403, "无权操作该订单");
        }
        String action = req == null || !StringUtils.hasText(req.getAction()) ? "success" : req.getAction();
        if ("cancel".equalsIgnoreCase(action)) {
            return finalizePayment(tid, pay, PAY_CLOSED, null);
        }
        if ("fail".equalsIgnoreCase(action)) {
            return finalizePayment(tid, pay, PAY_FAILED, null);
        }
        if ("pending".equalsIgnoreCase(action)) {
            return getOrderView(tid, orderNo);
        }
        // 支付宝已接沙盒：禁止前端模拟 success 直接落账，避免绕过真实支付
        if ("ALIPAY".equalsIgnoreCase(str(pay.get("payChannel"))) && alipaySandboxService.ready()) {
            throw new BizException(400, "请使用支付宝收银台完成支付，勿模拟成功");
        }
        String tradeNo = "TX" + System.currentTimeMillis();
        return finalizePayment(tid, pay, PAY_PAID, tradeNo);
    }

    /**
     * 生成支付宝沙盒收银台表单（page=电脑网站 / wap=手机网站）
     */
    public Map<String, Object> createAlipayPay(String orderNo, String product) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        Map<String, Object> pay = getPayment(tid, orderNo);
        if (!Objects.equals(toLong(pay.get("userId")), uid)) {
            throw new BizException(403, "无权操作该订单");
        }
        if (!"ALIPAY".equalsIgnoreCase(str(pay.get("payChannel")))) {
            throw new BizException(400, "该订单不是支付宝支付渠道");
        }
        if (PAY_PAID.equals(str(pay.get("status")))) {
            throw new BizException(400, "订单已支付");
        }
        if (PAY_CLOSED.equals(str(pay.get("status")))) {
            throw new BizException(400, "订单已关闭");
        }
        BigDecimal amount = toDecimal(pay.get("amount"));
        String subject = "物业缴费-" + str(pay.get("houseLabel"));
        String form = alipaySandboxService.buildPayForm(orderNo, amount, subject, product);
        Map<String, Object> m = new HashMap<>();
        m.put("orderNo", orderNo);
        m.put("orderId", orderNo);
        m.put("amount", amount);
        m.put("payChannel", "ALIPAY");
        m.put("payForm", form);
        m.put("product", "wap".equalsIgnoreCase(product) ? "wap" : "page");
        return m;
    }

    /** 异步通知：验签 + 落账，返回 success/failure 纯文本给支付宝 */
    @Transactional
    public String handleAlipayNotify(Map<String, String> params) {
        if (!alipaySandboxService.verifyNotify(params)) {
            return "failure";
        }
        String orderNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        String tradeNo = params.get("trade_no");
        if (!StringUtils.hasText(orderNo)) {
            return "failure";
        }
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            return "success";
        }
        markPaidByAlipay(orderNo, tradeNo, params.get("total_amount"));
        return "success";
    }

    /**
     * 同步回跳：尽量查单落账，再跳转前端结果页 URL
     */
    @Transactional
    public String handleAlipayReturn(Map<String, String> params) {
        String orderNo = params.get("out_trade_no");
        if (!StringUtils.hasText(orderNo)) {
            return alipaySandboxService.frontendResultRedirect("");
        }
        // 同步回跳参数也可验签（有 sign 时）
        if (params.containsKey("sign")) {
            try {
                alipaySandboxService.verifyNotify(params);
            } catch (Exception ignored) {
            }
        }
        try {
            syncAlipayPaid(orderNo);
        } catch (Exception ignored) {
        }
        return alipaySandboxService.frontendResultRedirect(orderNo);
    }

    /** 前端轮询前主动向支付宝查单并落账（本地无公网 notify 时的兜底） */
    @Transactional
    public Map<String, Object> syncAlipayPaid(String orderNo) {
        Map<String, Object> pay = getPaymentByOrderNo(orderNo);
        Long tid = toLong(pay.get("tenantId"));
        if (PAY_PAID.equals(str(pay.get("status")))) {
            return getOrderView(tid, orderNo);
        }
        if (!"ALIPAY".equalsIgnoreCase(str(pay.get("payChannel")))) {
            return getOrderView(tid, orderNo);
        }
        if (!alipaySandboxService.ready()) {
            return getOrderView(tid, orderNo);
        }
        var resp = alipaySandboxService.queryTrade(orderNo);
        if (alipaySandboxService.isTradeSuccess(resp)) {
            markPaidByAlipay(orderNo, resp.getTradeNo(), resp.getTotalAmount());
        }
        return getOrderView(tid, orderNo);
    }

    private void markPaidByAlipay(String orderNo, String tradeNo, String totalAmount) {
        Map<String, Object> pay = getPaymentByOrderNo(orderNo);
        if (PAY_PAID.equals(str(pay.get("status")))) {
            return;
        }
        BigDecimal expect = toDecimal(pay.get("amount")).setScale(2, java.math.RoundingMode.HALF_UP);
        if (StringUtils.hasText(totalAmount)) {
            BigDecimal paid = new BigDecimal(totalAmount).setScale(2, java.math.RoundingMode.HALF_UP);
            if (paid.compareTo(expect) != 0) {
                throw new BizException(400, "支付金额与订单不一致");
            }
        }
        Long tid = toLong(pay.get("tenantId"));
        finalizePayment(tid, pay, PAY_PAID, StringUtils.hasText(tradeNo) ? tradeNo : ("ALI" + System.currentTimeMillis()));
    }

    private Map<String, Object> getPaymentByOrderNo(String orderNo) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "select id, tenant_id as tenantId, order_no as orderNo, user_id as userId, house_id as houseId, "
                        + "house_label as houseLabel, amount, pay_channel as payChannel, status, "
                        + "pay_trade_no as payTradeNo, bill_ids as billIds, bill_snapshot as billSnapshot, "
                        + "paid_at as paidAt, create_time as createTime "
                        + "from fee_payment where order_no=? limit 1",
                orderNo);
        if (list.isEmpty()) {
            throw new BizException(404, "订单不存在");
        }
        return list.get(0);
    }

    /** 取消「支付中」：关闭未完成订单，账单恢复待缴 */
    @Transactional
    public Map<String, Object> cancelPayingBill(Long billId) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        Map<String, Object> bill = getBillRaw(tid, billId);
        requireBoundHouse(tid, uid, toLong(bill.get("house_id") != null ? bill.get("house_id") : bill.get("houseId")));
        if (!ST_PAYING.equals(str(bill.get("status")))) {
            throw new BizException(400, "当前账单不是支付中状态");
        }
        // 关闭包含该账单的未完成支付单
        List<Map<String, Object>> pays = jdbc.queryForList(
                "select id, order_no as orderNo, bill_ids as billIds from fee_payment "
                        + "where tenant_id=? and user_id=? and status='PAYING' order by id desc limit 20",
                tid, uid);
        for (Map<String, Object> p : pays) {
            String ids = str(p.get("billIds"));
            boolean hit = Arrays.stream(ids.split(","))
                    .filter(StringUtils::hasText)
                    .map(Long::valueOf)
                    .anyMatch(id -> id.equals(billId));
            if (hit) {
                Map<String, Object> pay = getPayment(tid, str(p.get("orderNo")));
                finalizePayment(tid, pay, PAY_CLOSED, null);
            }
        }
        // 兜底：确保本账单回到待缴
        jdbc.update(
                "update fee_bill set status=?, update_time=? where id=? and tenant_id=? and status=?",
                ST_UNPAID, Timestamp.valueOf(LocalDateTime.now()), billId, tid, ST_PAYING);
        return residentBillDetail(billId);
    }

    public Map<String, Object> getOrder(String orderNo) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        Map<String, Object> pay = getPayment(tid, orderNo);
        if (!Objects.equals(toLong(pay.get("userId")), uid)
                && !RoleConstants.isPropertyAdmin(TenantContext.getRole())) {
            throw new BizException(403, "无权查看该订单");
        }
        return getOrderView(tid, orderNo);
    }

    public List<Map<String, Object>> listOrders(Long houseId, String status) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        if (houseId == null) {
            houseId = TenantContext.getHouseId();
        }
        houseId = requireBoundHouse(tid, uid, houseId);
        StringBuilder sql = new StringBuilder(
                "select order_no as orderId, order_no as orderNo, house_id as houseId, house_label as houseLabel, "
                        + "amount, pay_channel as payChannel, status, pay_trade_no as payTradeNo, "
                        + "bill_ids as billIds, paid_at as paidAt, create_time as createTime "
                        + "from fee_payment where tenant_id=? and user_id=? and house_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        args.add(uid);
        args.add(houseId);
        if (StringUtils.hasText(status)) {
            sql.append("and status=? ");
            args.add(status);
        }
        sql.append("order by id desc limit 100");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) {
            r.put("bills", parseBillSnapshot(str(getPayment(tid, str(r.get("orderNo"))).get("billSnapshot"))));
        }
        return rows;
    }

    public Map<String, Object> receipt(String orderNo) {
        Map<String, Object> order = getOrder(orderNo);
        if (!PAY_PAID.equals(str(order.get("status")))) {
            throw new BizException("订单未支付成功，暂无凭证");
        }
        Map<String, Object> rc = new HashMap<>(order);
        rc.put("receiptNo", "RC" + orderNo);
        rc.put("payChannelLabel", "ALIPAY".equals(order.get("payChannel")) ? "支付宝" : "微信支付");
        rc.put("payer", TenantContext.getUserId());
        return rc;
    }

    /* ---------------- helpers ---------------- */

    private Map<String, Object> finalizePayment(Long tid, Map<String, Object> pay, String payStatus, String tradeNo) {
        String orderNo = str(pay.get("orderNo"));
        if (PAY_PAID.equals(str(pay.get("status"))) || PAY_CLOSED.equals(str(pay.get("status")))) {
            return getOrderView(tid, orderNo);
        }
        String billIds = str(pay.get("billIds"));
        List<Long> ids = Arrays.stream(billIds.split(","))
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .collect(Collectors.toList());
        if (PAY_PAID.equals(payStatus)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            jdbc.update(
                    "update fee_payment set status=?, pay_trade_no=?, paid_at=? where order_no=? and tenant_id=?",
                    PAY_PAID, tradeNo, now, orderNo, tid);
            for (Long id : ids) {
                jdbc.update(
                        "update fee_bill set status=?, paid_at=?, update_time=? where id=? and tenant_id=?",
                        ST_PAID, now, now, id, tid);
            }
        } else {
            jdbc.update("update fee_payment set status=? where order_no=? and tenant_id=?",
                    payStatus, orderNo, tid);
            for (Long id : ids) {
                jdbc.update(
                        "update fee_bill set status=?, update_time=? where id=? and tenant_id=? and status=?",
                        ST_UNPAID, Timestamp.valueOf(LocalDateTime.now()), id, tid, ST_PAYING);
            }
        }
        return getOrderView(tid, orderNo);
    }

    private Map<String, Object> getOrderView(Long tid, String orderNo) {
        Map<String, Object> pay = getPayment(tid, orderNo);
        Map<String, Object> order = new HashMap<>();
        order.put("orderId", pay.get("orderNo"));
        order.put("orderNo", pay.get("orderNo"));
        order.put("amount", pay.get("amount"));
        order.put("payChannel", pay.get("payChannel"));
        order.put("status", pay.get("status"));
        order.put("payTradeNo", pay.get("payTradeNo"));
        order.put("paidAt", pay.get("paidAt"));
        order.put("houseId", pay.get("houseId"));
        order.put("houseLabel", pay.get("houseLabel"));
        order.put("createTime", pay.get("createTime"));
        List<Long> ids = Arrays.stream(str(pay.get("billIds")).split(","))
                .filter(StringUtils::hasText).map(Long::valueOf).collect(Collectors.toList());
        order.put("billIds", ids);
        List<Map<String, Object>> snap = parseBillSnapshot(str(pay.get("billSnapshot")));
        if (snap.isEmpty()) {
            snap = ids.stream().map(id -> {
                Map<String, Object> b = getBill(tid, id);
                return Map.<String, Object>of(
                        "id", id,
                        "billNo", "BILL" + String.format("%08d", id),
                        "title", str(b.get("feeTypeName")) + str(b.get("period")) + "账单",
                        "payable", b.get("amount"));
            }).collect(Collectors.toList());
        }
        order.put("bills", snap);
        return order;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseBillSnapshot(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> getPayment(Long tid, String orderNo) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "select id, tenant_id as tenantId, order_no as orderNo, user_id as userId, house_id as houseId, "
                        + "house_label as houseLabel, amount, pay_channel as payChannel, status, "
                        + "pay_trade_no as payTradeNo, bill_ids as billIds, bill_snapshot as billSnapshot, "
                        + "paid_at as paidAt, create_time as createTime "
                        + "from fee_payment where tenant_id=? and order_no=? limit 1",
                tid, orderNo);
        if (list.isEmpty()) throw new BizException(404, "订单不存在");
        return list.get(0);
    }

    private Map<String, Object> getBill(Long tid, Long id) {
        return decorateBill(mapBill(getBillRaw(tid, id)));
    }

    private Map<String, Object> getBillRaw(Long tid, Long id) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "select * from fee_bill where tenant_id=? and id=? limit 1", tid, id);
        if (list.isEmpty()) throw new BizException(404, "账单不存在");
        return list.get(0);
    }

    private Map<String, Object> mapBill(Map<String, Object> raw) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", raw.get("id"));
        m.put("tenantId", raw.get("tenant_id") != null ? raw.get("tenant_id") : raw.get("tenantId"));
        m.put("houseId", raw.get("house_id") != null ? raw.get("house_id") : raw.get("houseId"));
        m.put("houseLabel", raw.get("house_label") != null ? raw.get("house_label") : raw.get("houseLabel"));
        m.put("room", m.get("houseLabel"));
        m.put("userId", raw.get("user_id") != null ? raw.get("user_id") : raw.get("userId"));
        m.put("payerName", raw.get("payer_name") != null ? raw.get("payer_name") : raw.get("payerName"));
        m.put("owner", m.get("payerName"));
        m.put("payerPhone", raw.get("payer_phone") != null ? raw.get("payer_phone") : raw.get("payerPhone"));
        m.put("feeType", raw.get("fee_type") != null ? raw.get("fee_type") : raw.get("feeType"));
        m.put("feeTypeName", raw.get("fee_type_name") != null ? raw.get("fee_type_name") : raw.get("feeTypeName"));
        m.put("item", m.get("feeTypeName"));
        m.put("itemName", m.get("feeTypeName"));
        m.put("plateNo", raw.get("plate_no") != null ? raw.get("plate_no") : raw.get("plateNo"));
        m.put("amount", raw.get("amount"));
        m.put("payable", raw.get("amount"));
        m.put("period", raw.get("period"));
        Object due = raw.get("due_date") != null ? raw.get("due_date") : raw.get("dueDate");
        m.put("dueDate", due == null ? null : String.valueOf(due).substring(0, Math.min(10, String.valueOf(due).length())));
        m.put("status", raw.get("status"));
        m.put("rawStatus", raw.get("status"));
        Object paid = raw.get("paid_at") != null ? raw.get("paid_at") : raw.get("paidAt");
        m.put("paidAt", paid);
        m.put("remark", raw.get("remark"));
        m.put("createTime", raw.get("create_time") != null ? raw.get("create_time") : raw.get("createTime"));
        return m;
    }

    private Map<String, Object> decorateBill(Map<String, Object> m) {
        String st = str(m.get("rawStatus") != null ? m.get("rawStatus") : m.get("status"));
        m.put("rawStatus", st);
        String due = str(m.get("dueDate"));
        if (ST_UNPAID.equals(st) && StringUtils.hasText(due)) {
            try {
                if (LocalDate.parse(due.substring(0, 10)).isBefore(LocalDate.now())) {
                    m.put("status", "OVERDUE");
                    m.put("statusLabel", "已逾期");
                    return m;
                }
            } catch (Exception ignored) {
            }
        }
        m.put("status", st);
        if (ST_PAID.equals(st)) m.put("statusLabel", "已缴");
        else if (ST_PAYING.equals(st)) m.put("statusLabel", "支付中");
        else m.put("statusLabel", "未缴");
        return m;
    }

    private Map<String, Object> requireHouseWithResident(Long tid, Long houseId) {
        List<Map<String, Object>> houses = jdbc.queryForList(
                "select id, concat(community_name,' · ',building,unit,room_no) as houseLabel "
                        + "from community_house where tenant_id=? and id=? and status=1",
                tid, houseId);
        if (houses.isEmpty()) throw new BizException("房屋不存在或不属于本社区");
        List<Map<String, Object>> residents = jdbc.queryForList(
                "select user_id as userId, name as payerName, phone as payerPhone, house_label as houseLabel "
                        + "from household_resident where tenant_id=? and house_id=? and status='ACTIVE' order by id limit 1",
                tid, houseId);
        if (residents.isEmpty()) {
            throw new BizException("该房屋无有效住户，不允许生成账单");
        }
        Map<String, Object> m = new HashMap<>(residents.get(0));
        if (!StringUtils.hasText(str(m.get("houseLabel")))) {
            m.put("houseLabel", houses.get(0).get("houseLabel"));
        }
        return m;
    }

    private Long requireBoundHouse(Long tid, Long uid, Long houseId) {
        if (houseId == null) {
            throw new BizException(400, "请选择房屋");
        }
        Long cnt = jdbc.queryForObject(
                "select count(1) from user_house_bind where tenant_id=? and user_id=? and house_id=?",
                Long.class, tid, uid, houseId);
        if (cnt == null || cnt == 0) {
            throw new BizException(403, "未绑定该房屋，无权操作账单");
        }
        return houseId;
    }

    private void validateSave(FeeBillSaveRequest req) {
        if (req.getHouseId() == null) throw new BizException("请选择房屋");
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("应收金额必须为正数");
        }
        if (!StringUtils.hasText(req.getPeriod())) throw new BizException("请填写缴费周期");
        String ft = normalizeFeeType(req.getFeeType());
        if ("PARKING".equals(ft) && StringUtils.hasText(req.getPlateNo())) {
            com.community.repair.util.PlateNoUtils.validate(req.getPlateNo());
        }
    }

    private String normalizeFeeType(String t) {
        if (t == null) return "PROPERTY";
        String u = t.trim().toUpperCase();
        if (u.contains("停车") || "PARKING".equals(u)) return "PARKING";
        if (u.contains("公摊") || "SHARE".equals(u)) return "SHARE";
        if (u.contains("其他") || "OTHER".equals(u)) return "OTHER";
        return "PROPERTY";
    }

    private String feeTypeName(String type) {
        return switch (type) {
            case "PARKING" -> "停车费";
            case "SHARE" -> "公摊水电费";
            case "OTHER" -> "其他";
            default -> "物业费";
        };
    }

    private void assertCanView() {
        String role = TenantContext.getRole();
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isGridWorker(role)
                || RoleConstants.isPlatformAdmin(role)) {
            return;
        }
        throw new BizException(403, "无权查看缴费账单");
    }

    private void assertCanManage() {
        String role = TenantContext.getRole();
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isPlatformAdmin(role)) {
            return;
        }
        throw new BizException(403, "网格员仅可查看，不可新增/编辑/删除账单");
    }

    private void assertResident() {
        if (TenantContext.getUserId() == null) {
            throw new BizException(401, "未登录");
        }
        String role = TenantContext.getRole();
        if (RoleConstants.RESIDENT.equals(role)
                || RoleConstants.isPropertyAdmin(role)
                || RoleConstants.isPlatformAdmin(role)) {
            return;
        }
        throw new BizException(403, "仅居民可进行缴费操作");
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(40001, "缺少租户上下文");
        return tid;
    }

    private Long requireUser() {
        Long uid = TenantContext.getUserId();
        if (uid == null) throw new BizException(401, "未登录");
        return uid;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        return new BigDecimal(String.valueOf(o));
    }

    private static Date parseDate(String s) {
        if (!StringUtils.hasText(s)) return null;
        try {
            return Date.valueOf(s.substring(0, 10));
        } catch (Exception e) {
            throw new BizException("截止日期格式错误，应为 yyyy-MM-dd");
        }
    }
}
