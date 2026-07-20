package com.community.auth.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.auth.entity.SysTenant;
import com.community.auth.mapper.SysTenantMapper;
import com.community.auth.service.LeaseCacheService;
import com.community.auth.service.MessageService;
import com.community.auth.service.TenantService;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 到期前 30/7/1 天续费提醒；超期自动降级（仅公告+报修）；超期 30 天冻结
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantExpireJob {

    private static final Set<Long> REMIND_DAYS = Set.of(30L, 7L, 1L);

    private final SysTenantMapper tenantMapper;
    private final TenantService tenantService;
    private final MessageService messageService;
    private final LeaseCacheService leaseCacheService;

    @Scheduled(cron = "${auth.expire.cron:0 0 1 * * ?}")
    public void scanExpire() {
        doScan();
    }

    public int doScan() {
        TenantContext.setSkipTenant(true);
        int changed = 0;
        try {
            List<SysTenant> tenants = tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                    .ne(SysTenant::getStatus, "disabled"));
            LocalDateTime now = LocalDateTime.now();

            for (SysTenant t : tenants) {
                if (t.getExpireTime() == null) {
                    continue;
                }
                String old = t.getStatus();
                long days = ChronoUnit.DAYS.between(LocalDate.now(), t.getExpireTime().toLocalDate());
                boolean needUpdate = false;

                if (t.getExpireTime().isBefore(now.minusDays(30))) {
                    if (!"frozen".equals(t.getStatus())) {
                        t.setStatus("frozen");
                        needUpdate = true;
                    }
                    tenantService.applyBasicOnly(t);
                    needUpdate = true;
                } else if (t.getExpireTime().isBefore(now)) {
                    if (!"degraded".equals(t.getStatus())) {
                        t.setStatus("degraded");
                        needUpdate = true;
                    }
                    tenantService.applyBasicOnly(t);
                    needUpdate = true;
                }

                if (needUpdate) {
                    t.setUpdateTime(now);
                    tenantMapper.updateById(t);
                    leaseCacheService.publish(t);
                    if (old == null || !old.equals(t.getStatus())) {
                        changed++;
                        log.info("租户 {} 状态 {} -> {}（功能已降级为基础公告/报修）", t.getId(), old, t.getStatus());
                    }
                } else {
                    // 正常租户也刷新缓存，保证网关与库一致
                    leaseCacheService.publish(t);
                }

                // 到期前 30/7/1 天提醒
                if (REMIND_DAYS.contains(days)) {
                    pushRemind(t, days, false);
                }
                // 已降级/冻结：每日一条续费催促（标题区分，existsToday 防刷）
                if ("degraded".equals(t.getStatus()) || "frozen".equals(t.getStatus())) {
                    pushRemind(t, days, true);
                }
            }
        } finally {
            TenantContext.clear();
        }
        return changed;
    }

    private void pushRemind(SysTenant t, long days, boolean overdue) {
        String title = overdue
                ? "租赁套餐已到期请续费"
                : "租赁套餐续费提醒(剩余" + days + "天)";
        if (messageService.existsToday(t.getId(), title)) {
            return;
        }
        String content = overdue
                ? String.format("社区「%s」套餐已过期%s，当前状态：%s。已自动降级，仅保留公告与报修，请尽快续费解锁。",
                t.getName(),
                days < 0 ? Math.abs(days) + "天" : "",
                t.getStatus())
                : String.format("社区「%s」套餐将在 %d 天后到期（%s），请及时续费以免功能降级。",
                t.getName(), days, t.getExpireTime());
        messageService.pushToPropertyAdmins(t.getId(), title, content, "renew");
        log.info("已推送续费提醒 tenantId={} days={} overdue={}", t.getId(), days, overdue);
    }
}
