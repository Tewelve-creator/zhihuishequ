package com.community.repair.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreNotifyService {

    private final JdbcTemplate jdbc;

    public void notifyNewOrder(Long tenantId, String orderNo, BigDecimal amount) {
        notifyNewOrder(tenantId, null, orderNo, amount);
    }

    public void notifyNewOrder(Long tenantId, Long shopId, String orderNo, BigDecimal amount) {
        String title = "社区超市新订单";
        String content = "新订单 " + orderNo + "，金额 ¥" + amount + "，请及时接单";
        pushStaff(tenantId, title, content, "store_order");
        pushMerchantIfEnabled(shopId, "notify_new_order", title, content, "store_order");
    }

    public void notifyOrderCancel(Long tenantId, Long shopId, String orderNo, String reason) {
        String title = "订单取消提醒";
        String content = "订单 " + orderNo + " 已取消"
                + (reason == null || reason.isBlank() ? "" : "，原因：" + reason);
        pushMerchantIfEnabled(shopId, "notify_order_cancel", title, content, "store_order_cancel");
    }

    public void notifyRefund(Long tenantId, String orderNo) {
        pushStaff(tenantId, "超市退款申请", "订单 " + orderNo + " 申请退款，请管理员审核", "store_refund");
    }

    /** 平台公告：向开启订阅的商户账号推送站内信 */
    public void notifyPlatformNoticeToMerchants(Long tenantId, String title, String content) {
        if (tenantId == null) return;
        try {
            List<Map<String, Object>> shops = jdbc.queryForList(
                    "select owner_user_id from store_shop where tenant_id=? and deleted=0 and status=1 "
                            + "and ifnull(notify_platform_notice,1)=1 and owner_user_id is not null",
                    tenantId);
            for (Map<String, Object> s : shops) {
                Object uid = s.get("owner_user_id");
                if (uid == null) continue;
                jdbc.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) "
                                + "values(?,?,?,?,?,0,now())",
                        tenantId, uid, title, content, "platform_notice");
            }
        } catch (Exception e) {
            log.warn("platform notice to merchants skip: {}", e.getMessage());
        }
    }

    private void pushMerchantIfEnabled(Long shopId, String flagCol, String title, String content, String msgType) {
        if (shopId == null) return;
        String col = switch (flagCol) {
            case "notify_new_order", "notify_order_cancel", "notify_platform_notice", "notify_stock_low" -> flagCol;
            default -> null;
        };
        if (col == null) return;
        try {
            List<Map<String, Object>> shops = jdbc.queryForList(
                    "select tenant_id, owner_user_id, ifnull(" + col + ",1) as enabled "
                            + "from store_shop where id=? and deleted=0",
                    shopId);
            if (shops.isEmpty()) return;
            Map<String, Object> shop = shops.get(0);
            if (toInt(shop.get("enabled"), 1) != 1) return;
            Object uid = shop.get("owner_user_id");
            if (uid == null) return;
            jdbc.update(
                    "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) "
                            + "values(?,?,?,?,?,0,now())",
                    shop.get("tenant_id"), uid, title, content, msgType);
        } catch (Exception e) {
            log.warn("merchant notify skip: {}", e.getMessage());
        }
    }

    private void pushStaff(Long tenantId, String title, String content, String msgType) {
        try {
            List<Map<String, Object>> users = jdbc.queryForList(
                    "select id from sys_user where tenant_id=? and role in ('property_admin','grid_worker') and status=1",
                    tenantId);
            for (Map<String, Object> u : users) {
                jdbc.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,0,now())",
                        tenantId, u.get("id"), title, content, msgType);
            }
        } catch (Exception e) {
            log.warn("store notify skip: {}", e.getMessage());
        }
    }

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }
}
