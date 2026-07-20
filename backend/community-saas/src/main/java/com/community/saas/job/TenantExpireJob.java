package com.community.saas.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.tenant.TenantContext;
import com.community.saas.entity.SysTenant;
import com.community.saas.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 租赁到期管控：到期降级 / 超期冻结 + 30/7/1 天续费提醒
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantExpireJob {

    private final SysTenantMapper tenantMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 每天凌晨 1 点；演示环境也可用 fixedDelay 手动触发接口 */
    @Scheduled(cron = "0 0 1 * * ?")
    public void scanExpire() {
        doScan();
    }

    public int doScan() {
        TenantContext.setSkipTenant(true);
        List<SysTenant> tenants = tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                .ne(SysTenant::getStatus, "disabled"));
        int changed = 0;
        LocalDateTime now = LocalDateTime.now();
        for (SysTenant t : tenants) {
            if (t.getExpireTime() == null) {
                continue;
            }
            String old = t.getStatus();
            long days = ChronoUnit.DAYS.between(LocalDate.now(), t.getExpireTime().toLocalDate());

            // 状态机
            if (t.getExpireTime().isBefore(now.minusDays(30))) {
                t.setStatus("frozen");
            } else if (t.getExpireTime().isBefore(now)) {
                t.setStatus("degraded");
            } else if (!"disabled".equals(t.getStatus()) && !"frozen".equals(t.getStatus())) {
                t.setStatus("active");
            }

            if (t.getStatus() != null && !t.getStatus().equals(old)) {
                tenantMapper.updateById(t);
                changed++;
                log.info("租户 {} 状态 {} -> {}", t.getId(), old, t.getStatus());
            }

            // 到期前提醒：30 / 7 / 1
            if (days == 30 || days == 7 || days == 1) {
                pushRemind(t, days);
            }
            if ("degraded".equals(t.getStatus()) || "frozen".equals(t.getStatus())) {
                pushRemind(t, days);
            }
        }
        TenantContext.clear();
        return changed;
    }

    private void pushRemind(SysTenant t, long days) {
        String title = days >= 0
                ? "租赁套餐即将到期提醒"
                : "租赁套餐已到期，请尽快续费";
        String content = days >= 0
                ? String.format("社区「%s」套餐将在 %d 天后到期（%s），请及时续费以免功能降级。",
                t.getName(), days, t.getExpireTime())
                : String.format("社区「%s」套餐已过期 %d 天，当前状态：%s。高级 AI/大屏/导出已受限。",
                t.getName(), Math.abs(days), t.getStatus());

        // 防重复：同租户同标题同天只发一次
        Integer exists = jdbcTemplate.queryForObject(
                "select count(1) from sys_message where tenant_id=? and title=? and date(create_time)=curdate()",
                Integer.class, t.getId(), title);
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update(
                "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) " +
                        "select ?, id, ?, ?, 'renew', 0, now() from sys_user " +
                        "where tenant_id=? and role='property_admin' and status=1",
                t.getId(), title, content, t.getId());
        log.info("已推送续费提醒 tenantId={} days={}", t.getId(), days);
    }
}
