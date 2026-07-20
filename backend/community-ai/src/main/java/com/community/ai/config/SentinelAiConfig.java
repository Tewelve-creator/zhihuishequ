package com.community.ai.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 简易限流：按资源名限制 AI 接口 QPS
 */
@Configuration
public class SentinelAiConfig {

    @Value("${ai.sentinel.qps:20}")
    private double qps;

    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        for (String res : List.of("ai_chat", "ai_image", "ai_emotion", "ai_audit", "ai_lost", "ai_summary")) {
            FlowRule rule = new FlowRule();
            rule.setResource(res);
            rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
            rule.setCount(qps);
            rules.add(rule);
        }
        FlowRuleManager.loadRules(rules);
    }
}
