package com.community.repair.job;

import com.community.repair.service.VisitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 访客预约过期状态定时刷新
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisitExpireJob {

    private final VisitService visitService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void tick() {
        int n = visitService.refreshAllExpired();
        if (n > 0) {
            log.info("访客预约过期刷新 {} 条", n);
        }
    }
}
