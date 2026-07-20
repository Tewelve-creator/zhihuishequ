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
                row.put("readFlag", m.getReadFlag());
                row.put("createTime", m.getCreateTime());
                return row;
            }).toList();
        } catch (Exception e) {
            // 表未初始化时返回空，避免首页 500
            return List.of();
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

    public void pushToPropertyAdmins(Long tenantId, String title, String content, String msgType) {
        TenantContext.setSkipTenant(true);
        try {
            List<SysUser> admins = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getTenantId, tenantId)
                    .eq(SysUser::getRole, "property_admin")
                    .eq(SysUser::getStatus, 1));
            for (SysUser u : admins) {
                SysMessage m = new SysMessage();
                m.setTenantId(tenantId);
                m.setUserId(u.getId());
                m.setTitle(title);
                m.setContent(content);
                m.setMsgType(msgType == null ? "system" : msgType);
                m.setReadFlag(0);
                m.setCreateTime(LocalDateTime.now());
                messageMapper.insert(m);
            }
        } finally {
            TenantContext.setSkipTenant(true);
        }
    }

    public boolean existsToday(Long tenantId, String title) {
        Long cnt = messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getTenantId, tenantId)
                .eq(SysMessage::getTitle, title)
                .apply("date(create_time) = curdate()"));
        return cnt != null && cnt > 0;
    }
}
