package com.community.ai.biz;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用 community-repair 业务接口（透传租户/用户/房屋上下文）
 */
@Slf4j
@Component
public class RepairApiClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.repair-base-url:http://127.0.0.1:8083}")
    private String repairBaseUrl;

    public Object createVisit(Map<String, Object> body) {
        return post("/visit", body);
    }

    public Object createRepair(Map<String, Object> body) {
        return post("/repair/orders", body);
    }

    public Object listUnpaidBills(Long houseId) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(repairBaseUrl + "/fee/resident/bills")
                .queryParam("tab", "unpaid");
        if (houseId != null) {
            b.queryParam("houseId", houseId);
        }
        return get(b.toUriString());
    }

    public Object listCars(Long houseId) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(repairBaseUrl + "/resident-car/list");
        if (houseId != null) {
            b.queryParam("houseId", houseId);
        }
        return get(b.toUriString());
    }

    public Object listApprovedVisits() {
        String url = UriComponentsBuilder
                .fromHttpUrl(repairBaseUrl + "/visit")
                .queryParam("status", "approved")
                .toUriString();
        return get(url);
    }

    private Object post(String path, Map<String, Object> body) {
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers());
            ResponseEntity<String> resp = restTemplate.exchange(
                    repairBaseUrl + path, HttpMethod.POST, entity, String.class);
            return unwrap(resp.getBody());
        } catch (RestClientException e) {
            log.warn("repair POST {} fail: {}", path, e.getMessage());
            throw new BizException("业务办理失败：" + e.getMessage());
        }
    }

    private Object get(String url) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers());
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return unwrap(resp.getBody());
        } catch (RestClientException e) {
            log.warn("repair GET {} fail: {}", url, e.getMessage());
            throw new BizException("查询失败：" + e.getMessage());
        }
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (TenantContext.getTenantId() != null) {
            h.set("X-Tenant-Id", String.valueOf(TenantContext.getTenantId()));
        }
        if (TenantContext.getUserId() != null) {
            h.set("X-User-Id", String.valueOf(TenantContext.getUserId()));
        }
        if (TenantContext.getRole() != null) {
            h.set("X-User-Role", TenantContext.getRole());
        }
        if (TenantContext.getHouseId() != null) {
            h.set("X-House-Id", String.valueOf(TenantContext.getHouseId()));
        }
        return h;
    }

    private Object unwrap(String body) {
        if (body == null) return null;
        JSONObject obj = JSON.parseObject(body);
        if (obj == null) return body;
        Integer code = obj.getInteger("code");
        if (code != null && code != 0) {
            throw new BizException(obj.getString("msg") == null ? "业务失败" : obj.getString("msg"));
        }
        return obj.get("data");
    }

    @SuppressWarnings("unchecked")
    public static String summarizeResult(BizIntent intent, Object data) {
        if (data == null) {
            return "操作已完成。";
        }
        if (intent == BizIntent.VISIT_CREATE) {
            Map<String, Object> m = asMap(data);
            StringBuilder sb = new StringBuilder("访客预约已生效（状态：已通过）");
            if (m.get("visitorName") != null) {
                sb.append("，访客「").append(m.get("visitorName")).append("」");
            }
            if (m.get("id") != null) {
                sb.append("，单号 #").append(m.get("id"));
            }
            sb.append("。\n请将下方通行链接或二维码转发给访客；门岗通行码需访客打开链接后实时生成，请勿截图转发门岗码。");
            Object passUrl = m.get("passUrl");
            if (passUrl != null && String.valueOf(passUrl).trim().length() > 0) {
                sb.append("\n通行链接：").append(passUrl);
            }
            return sb.toString();
        }
        if (intent == BizIntent.REPAIR_CREATE) {
            Map<String, Object> m = asMap(data);
            return "报修工单已提交" +
                    (m.get("id") != null ? "，单号 #" + m.get("id") : "") +
                    "，物业将尽快处理。";
        }
        if (intent == BizIntent.FEE_UNPAID) {
            List<?> list = asList(data);
            if (list.isEmpty()) {
                return "查询完成：您当前没有未缴账单。";
            }
            StringBuilder sb = new StringBuilder("查询到 " + list.size() + " 笔未缴账单：\n");
            int i = 0;
            for (Object row : list) {
                if (i++ >= 8) {
                    sb.append("…\n");
                    break;
                }
                Map<String, Object> m = asMap(row);
                sb.append("· ").append(str(m.get("feeTypeName"), str(m.get("feeType"), m.get("itemName"), "费用"), "费用"))
                        .append(" ¥").append(str(m.get("amount"), m.get("payableAmount"), "-"));
                String period = str(m.get("period"), null, null);
                if (period != null && !"-".equals(period) && !"null".equals(period)) {
                    sb.append("（账期 ").append(period).append("）");
                }
                String due = str(m.get("dueDate"), m.get("deadline"), null);
                if (due != null && !"-".equals(due) && !"null".equals(due)) {
                    sb.append("，截止日期 ").append(due.length() > 10 ? due.substring(0, 10) : due);
                }
                sb.append("\n");
            }
            sb.append("可在 App「缴费管理」在线缴纳。");
            return sb.toString().trim();
        }
        if (intent == BizIntent.CAR_LIST) {
            List<?> list = asList(data);
            if (list.isEmpty()) {
                return "您名下暂无常驻车辆登记。";
            }
            StringBuilder sb = new StringBuilder("您的常驻车辆共 " + list.size() + " 辆：\n");
            for (Object row : list) {
                Map<String, Object> m = asMap(row);
                sb.append("· ").append(str(m.get("plateNo"), "-", "-"))
                        .append("（").append(str(m.get("status"), "ACTIVE", "")).append("）\n");
            }
            return sb.toString().trim();
        }
        if (intent == BizIntent.VISIT_LIST) {
            List<?> list = asList(data);
            if (list.isEmpty()) {
                return "当前没有有效（已通过）的访客预约。";
            }
            StringBuilder sb = new StringBuilder("有效访客预约 " + list.size() + " 条：\n");
            int i = 0;
            for (Object row : list) {
                if (i++ >= 8) break;
                Map<String, Object> m = asMap(row);
                sb.append("· ").append(str(m.get("visitorName"), "访客", ""))
                        .append(" ").append(str(m.get("visitTime"), "", ""))
                        .append(m.get("plateNo") != null ? " 车" + m.get("plateNo") : "")
                        .append("\n");
            }
            return sb.toString().trim();
        }
        return "操作已完成。";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            m.forEach((k, v) -> r.put(String.valueOf(k), v));
            return r;
        }
        if (o instanceof JSONObject jo) {
            return new LinkedHashMap<>(jo);
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<?> asList(Object o) {
        if (o instanceof List<?> l) return l;
        return List.of();
    }

    private static String str(Object a, Object b, String def) {
        if (a != null && !String.valueOf(a).isBlank()) return String.valueOf(a);
        if (b != null && !String.valueOf(b).isBlank()) return String.valueOf(b);
        return def;
    }
}
