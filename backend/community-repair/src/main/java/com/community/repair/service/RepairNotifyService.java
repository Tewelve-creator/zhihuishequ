package com.community.repair.service;

import com.community.repair.entity.RepairOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 报修工单通知（简易 MQ 模拟：写入 sys_message 推送给本社区物业）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepairNotifyService {

    private final JdbcTemplate jdbcTemplate;

    public void notifyStaffNewOrder(RepairOrder order) {
        if (order == null || order.getTenantId() == null) {
            return;
        }
        try {
            List<Map<String, Object>> staff = jdbcTemplate.queryForList(
                    "select id from sys_user where tenant_id=? and role in ('property_admin','grid_worker') and status=1",
                    order.getTenantId());
            String title = "新报修工单待处理";
            String content = String.format("工单#%s「%s」%s，请尽快接单。",
                    order.getId(),
                    order.getTitle() == null ? "报修" : order.getTitle(),
                    order.getAiTag() == null ? "" : "，AI标签：" + order.getAiTag());
            for (Map<String, Object> u : staff) {
                jdbcTemplate.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                        order.getTenantId(),
                        u.get("id"),
                        title,
                        content,
                        "repair",
                        0);
            }
            log.info("repair notify staff tenantId={} orderId={} count={}",
                    order.getTenantId(), order.getId(), staff.size());
        } catch (Exception e) {
            log.warn("repair notify skip: {}", e.getMessage());
        }
    }

    /** 指派后提醒被指派的工作人员 */
    public void notifyAssigned(RepairOrder order, Long handlerId) {
        if (order == null || order.getTenantId() == null || handlerId == null) {
            return;
        }
        try {
            String title = "您有新的报修工单待处理";
            String content = String.format("工单#%s「%s」已指派给您，请尽快处理。",
                    order.getId(),
                    order.getTitle() == null ? "报修" : order.getTitle());
            jdbcTemplate.update(
                    "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                    order.getTenantId(),
                    handlerId,
                    title,
                    content,
                    "repair_assign",
                    0);
            log.info("repair assign notify tenantId={} orderId={} handlerId={}",
                    order.getTenantId(), order.getId(), handlerId);
        } catch (Exception e) {
            log.warn("repair assign notify skip: {}", e.getMessage());
        }
    }
}
