package com.community.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.auth.entity.SysMessage;
import com.community.auth.entity.SysUser;
import com.community.auth.mapper.SysMessageMapper;
import com.community.auth.mapper.SysUserMapper;
import com.community.common.exception.BizException;
import com.community.common.exception.ErrorCode;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final SysMessageMapper messageMapper;
    private final SysUserMapper userMapper;

    public List<Map<String, Object>> myMessages() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return List.of();
        }
        try {
            List<SysMessage> list = messageMapper.selectList(new LambdaQueryWrapper<SysMessage>()
                    .eq(SysMessage::getUserId, userId)
                    .orderByDesc(SysMessage::getId)
                    .last("limit 100"));
            return list.stream().map(m -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", m.getId());
                row.put("title", m.getTitle());
                row.put("content", m.getContent());
                row.put("msgType", m.getMsgType());
                row.put("typeLabel", typeLabel(m.getMsgType()));
                row.put("readFlag", m.getReadFlag());
                row.put("unread", m.getReadFlag() == null || m.getReadFlag() == 0);
                row.put("createTime", m.getCreateTime());
                return row;
            }).toList();
        } catch (Exception e) {
            // 表未初始化时返回空，避免首页 500
            return List.of();
        }
    }

    public int unreadCount() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return 0;
        try {
            Long cnt = messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                    .eq(SysMessage::getUserId, userId)
                    .and(w -> w.eq(SysMessage::getReadFlag, 0).or().isNull(SysMessage::getReadFlag)));
            return cnt == null ? 0 : cnt.intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    public void markRead(Long id) {
        Long userId = TenantContext.getUserId();
        SysMessage m = messageMapper.selectById(id);
        if (m == null || userId == null || !userId.equals(m.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        m.setReadFlag(1);
        messageMapper.updateById(m);
    }

    public int markAllRead() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return 0;
        List<SysMessage> list = messageMapper.selectList(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getUserId, userId)
                .and(w -> w.eq(SysMessage::getReadFlag, 0).or().isNull(SysMessage::getReadFlag)));
        for (SysMessage m : list) {
            m.setReadFlag(1);
            messageMapper.updateById(m);
        }
        return list.size();
    }

    private static String typeLabel(String msgType) {
        if (msgType == null) return "系统消息";
        return switch (msgType) {
            case "store_order" -> "新订单通知";
            case "store_order_cancel" -> "订单取消提醒";
            case "platform_notice" -> "平台公告";
            case "store_refund" -> "退款提醒";
            default -> "系统消息";
        };
    }

    public void pushToPropertyAdmins(Long tenantId, String title, String content, String msgType) {
        TenantContext.setSkipTenant(true);
        try {
            List<SysUser> admins = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getTenantId, tenantId)
                    .eq(SysUser::getRole, "property_admin")
                    .eq(SysUser::getStatus, 1));
            for (SysUser u : admins) {
                insertMessage(tenantId, u.getId(), title, content, msgType);
            }
        } finally {
            TenantContext.setSkipTenant(true);
        }
    }

    /** 通知平台运营人员（套餐升级申请等） */
    public void pushToPlatformAdmins(String title, String content, String msgType) {
        TenantContext.setSkipTenant(true);
        try {
            List<SysUser> admins = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRole, "platform_admin")
                    .eq(SysUser::getStatus, 1));
            for (SysUser u : admins) {
                insertMessage(u.getTenantId(), u.getId(), title, content, msgType);
            }
        } finally {
            TenantContext.setSkipTenant(true);
        }
    }

    private void insertMessage(Long tenantId, Long userId, String title, String content, String msgType) {
        SysMessage m = new SysMessage();
        m.setTenantId(tenantId);
        m.setUserId(userId);
        m.setTitle(title);
        m.setContent(content);
        m.setMsgType(msgType == null ? "system" : msgType);
        m.setReadFlag(0);
        m.setCreateTime(LocalDateTime.now());
        messageMapper.insert(m);
    }

    public boolean existsToday(Long tenantId, String title) {
        Long cnt = messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getTenantId, tenantId)
                .eq(SysMessage::getTitle, title)
                .apply("date(create_time) = curdate()"));
        return cnt != null && cnt > 0;
    }
}
