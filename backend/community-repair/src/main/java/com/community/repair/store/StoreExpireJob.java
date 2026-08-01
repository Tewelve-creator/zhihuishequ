package com.community.repair.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreExpireJob {

    private final StoreService storeService;

    @Scheduled(fixedDelay = 60000)
    public void closeUnpaid() {
        try {
            int n = storeService.closeExpiredUnpaid();
            if (n > 0) log.info("store close expired unpaid: {}", n);
        } catch (Exception e) {
            log.warn("store expire job: {}", e.getMessage());
        }
    }
}
