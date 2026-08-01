package com.community.auth.service;

import com.community.auth.entity.SysUser;
import com.community.auth.mapper.SysUserMapper;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 平台运营敏感操作审计（表缺失时静默跳过）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperLogService {

    private final JdbcTemplate jdbcTemplate;
    private final SysUserMapper userMapper;

    public void write(String module, String action, String detail) {
        write(module, action, detail, null);
    }

    public void write(String module, String action, String detail, String ip) {
        try {
            Long operatorId = TenantContext.getUserId();
            String operatorName = resolveOperatorName(operatorId);
            jdbcTemplate.update(
                    "insert into sys_oper_log(operator_id, operator_name, module, action, detail, ip, create_time) "
                            + "values(?,?,?,?,?,?,?)",
                    operatorId, operatorName, module, action, detail, ip, LocalDateTime.now());
        } catch (Exception e) {
            log.debug("oper log skipped: {}", e.getMessage());
        }
    }

    private String resolveOperatorName(Long operatorId) {
        if (operatorId == null) {
            return "system";
        }
        SysUser user = userMapper.selectById(operatorId);
        if (user == null) {
            return String.valueOf(operatorId);
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        if (StringUtils.hasText(user.getPhone())) {
            return user.getPhone();
        }
        return String.valueOf(operatorId);
    }
}
