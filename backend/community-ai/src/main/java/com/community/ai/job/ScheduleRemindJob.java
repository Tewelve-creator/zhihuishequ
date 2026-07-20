package com.community.ai.job;

import com.community.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 便民日程提醒：每分钟扫描到期任务，写入站内消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleRemindJob {

    private final AiService aiService;

    @Scheduled(cron = "${ai.schedule.cron:0 * * * * ?}")
    public void tick() {
        int n = runOnce();
        if (n > 0) {
            log.info("日程提醒已推送 {} 条", n);
        }
    }

    public int runOnce() {
        return aiService.dispatchDueReminds();
    }
}
