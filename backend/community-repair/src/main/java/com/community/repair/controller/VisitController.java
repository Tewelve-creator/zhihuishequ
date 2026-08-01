package com.community.repair.controller;

import com.community.common.result.R;
import com.community.repair.dto.VisitCreateRequest;
import com.community.repair.entity.VisitorAppointment;
import com.community.repair.service.VisitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 临时访客（免审核；物业核销 / 扫码通行；居民提交撤销）
 * 通行二维码：业主只转发链接；访客打开 H5 后由服务端实时校验并签发短时载荷。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/visit")
public class VisitController {

    private final VisitService visitService;

    @PostMapping
    public R<VisitorAppointment> create(@RequestBody VisitCreateRequest req) {
        return R.ok(visitService.create(req));
    }

    @GetMapping
    public R<List<VisitorAppointment>> list(@RequestParam(required = false) String status) {
        return R.ok(visitService.list(status));
    }

    /** 物业后台分页 */
    @GetMapping("/admin")
    public R<Map<String, Object>> adminPage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(visitService.adminPage(status, phone, keyword, startTime, endTime, page, size));
    }

    /** 物业详情（含通行/打开日志） */
    @GetMapping("/admin/{id}")
    public R<Map<String, Object>> adminDetail(@PathVariable Long id) {
        return R.ok(visitService.adminDetail(id));
    }

    /** 物业删除预约 */
    @DeleteMapping("/admin/{id}")
    public R<Void> adminDelete(@PathVariable Long id) {
        visitService.adminDelete(id);
        return R.ok();
    }

    @PutMapping("/{id}/checkin")
    public R<VisitorAppointment> checkin(@PathVariable Long id) {
        return R.ok(visitService.checkin(id));
    }

    @PutMapping("/{id}/cancel")
    public R<VisitorAppointment> cancel(@PathVariable Long id) {
        return R.ok(visitService.cancel(id));
    }

    @GetMapping("/todo-summary")
    public R<Map<String, Object>> todoSummary() {
        return R.ok(visitService.todoSummary());
    }

    /** 通行页信息（匿名）：写打开日志 + 实时签发短时二维码 */
    @GetMapping("/pass/info")
    public R<Map<String, Object>> passInfo(
            @RequestParam String token,
            @RequestParam(required = false) String phone,
            HttpServletRequest request) {
        return R.ok(visitService.passInfo(token, phone, clientIp(request)));
    }

    /** 刷新短时二维码载荷（定时刷新） */
    @GetMapping("/pass/qr")
    public R<Map<String, Object>> passQr(
            @RequestParam String token,
            @RequestParam(required = false) String phone,
            HttpServletRequest request) {
        return R.ok(visitService.passQrRefresh(token, phone, clientIp(request)));
    }

    /** 扫码通行校验（匿名门岗）：body 优先传 qrPayload */
    @PostMapping("/pass/verify")
    public R<Map<String, Object>> passVerify(@RequestBody Map<String, String> body) {
        String token = body == null ? null : body.get("token");
        String qrPayload = body == null ? null : body.get("qrPayload");
        return R.ok(visitService.passVerify(token, qrPayload));
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return request.getRemoteAddr();
    }
}
