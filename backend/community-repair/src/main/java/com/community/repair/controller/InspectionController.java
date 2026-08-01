package com.community.repair.controller;

import com.community.common.result.R;
import com.community.repair.dto.InspCheckinRequest;
import com.community.repair.dto.InspPointSaveRequest;
import com.community.repair.dto.InspTaskSaveRequest;
import com.community.repair.service.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inspection")
public class InspectionController {

    private final InspectionService inspectionService;

    /* ---- 点位 ---- */

    @GetMapping("/points")
    public R<List<Map<String, Object>>> points(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return R.ok(inspectionService.listPoints(keyword, status));
    }

    @PostMapping("/points")
    public R<Map<String, Object>> createPoint(@RequestBody InspPointSaveRequest req) {
        req.setId(null);
        return R.ok(inspectionService.savePoint(req));
    }

    @PutMapping("/points/{id}")
    public R<Map<String, Object>> updatePoint(@PathVariable Long id, @RequestBody InspPointSaveRequest req) {
        req.setId(id);
        return R.ok(inspectionService.savePoint(req));
    }

    @DeleteMapping("/points/{id}")
    public R<Void> deletePoint(@PathVariable Long id) {
        inspectionService.deletePoint(id);
        return R.ok();
    }

    @PostMapping("/points/{id}/regen-qr")
    public R<Map<String, Object>> regenQr(@PathVariable Long id) {
        return R.ok(inspectionService.regenQr(id));
    }

    /* ---- 任务 PC ---- */

    @GetMapping("/tasks")
    public R<List<Map<String, Object>>> tasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(inspectionService.listTasks(status, keyword));
    }

    @GetMapping("/tasks/{id}")
    public R<Map<String, Object>> taskDetail(@PathVariable Long id) {
        return R.ok(inspectionService.getTaskDetail(id));
    }

    @PostMapping("/tasks")
    public R<Map<String, Object>> createTask(@RequestBody InspTaskSaveRequest req) {
        req.setId(null);
        return R.ok(inspectionService.saveTask(req));
    }

    @PutMapping("/tasks/{id}")
    public R<Map<String, Object>> updateTask(@PathVariable Long id, @RequestBody InspTaskSaveRequest req) {
        req.setId(id);
        return R.ok(inspectionService.saveTask(req));
    }

    @DeleteMapping("/tasks/{id}")
    public R<Void> deleteTask(@PathVariable Long id) {
        inspectionService.deleteTask(id);
        return R.ok();
    }

    @GetMapping("/hazards")
    public R<List<Map<String, Object>>> hazards(@RequestParam(required = false) String status) {
        return R.ok(inspectionService.listHazards(status));
    }

    @PostMapping("/hazards/{checkinId}/fix")
    public R<Void> fixHazard(@PathVariable Long checkinId) {
        inspectionService.fixHazard(checkinId);
        return R.ok();
    }

    /** 巡检打卡台账 */
    @GetMapping("/checkins")
    public R<List<Map<String, Object>>> checkins(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Long taskId,
            @RequestParam(defaultValue = "200") int limit) {
        return R.ok(inspectionService.listCheckins(keyword, result, taskId, limit));
    }

    /* ---- App 执行 ---- */

    @GetMapping("/mine/todo-summary")
    public R<Map<String, Object>> mineTodoSummary() {
        return R.ok(inspectionService.mineTodoSummary());
    }

    @GetMapping("/mine/tasks")
    public R<List<Map<String, Object>>> myTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scope) {
        return R.ok(inspectionService.myTasks(status, scope));
    }

    @GetMapping("/mine/hazards")
    public R<List<Map<String, Object>>> myHazards(@RequestParam(required = false) String status) {
        return R.ok(inspectionService.myHazards(status));
    }

    @GetMapping("/mine/tasks/{id:\\d+}")
    public R<Map<String, Object>> myTaskDetail(@PathVariable Long id) {
        return R.ok(inspectionService.myTaskDetail(id));
    }

    @PostMapping("/mine/tasks/{id:\\d+}/checkin")
    public R<Map<String, Object>> checkin(@PathVariable Long id, @RequestBody InspCheckinRequest req) {
        return R.ok(inspectionService.checkin(id, req));
    }
}
