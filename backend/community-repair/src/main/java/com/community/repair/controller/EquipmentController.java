package com.community.repair.controller;

import com.community.common.result.R;
import com.community.repair.dto.EquipDeviceSaveRequest;
import com.community.repair.dto.EquipMaintRecordRequest;
import com.community.repair.service.EquipmentService;
import com.community.repair.service.EquipWorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 设备维保：计划台账 + 维修工单
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/equip")
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final EquipWorkOrderService workOrderService;

    @GetMapping("/devices")
    public R<List<Map<String, Object>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(equipmentService.listDevices(status, keyword));
    }

    @GetMapping("/devices/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(equipmentService.getDevice(id));
    }

    @PostMapping("/devices")
    public R<Map<String, Object>> create(@RequestBody EquipDeviceSaveRequest req) {
        req.setId(null);
        return R.ok(equipmentService.saveDevice(req));
    }

    @PutMapping("/devices/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody EquipDeviceSaveRequest req) {
        req.setId(id);
        return R.ok(equipmentService.saveDevice(req));
    }

    @DeleteMapping("/devices/{id}")
    public R<Void> delete(@PathVariable Long id) {
        equipmentService.deleteDevice(id);
        return R.ok();
    }

    @GetMapping("/devices/{id}/records")
    public R<List<Map<String, Object>>> records(@PathVariable Long id) {
        return R.ok(equipmentService.listRecords(id));
    }

    @PostMapping("/records")
    public R<Map<String, Object>> addRecord(@RequestBody EquipMaintRecordRequest req) {
        return R.ok(equipmentService.addRecord(req));
    }

    @GetMapping("/todo-summary")
    public R<Map<String, Object>> todoSummary() {
        return R.ok(workOrderService.todoSummary());
    }

    @GetMapping("/due-list")
    public R<List<Map<String, Object>>> dueList() {
        return R.ok(equipmentService.dueList());
    }

    /* ---- 维保工单 ---- */

    @GetMapping("/work-orders")
    public R<List<Map<String, Object>>> workOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String source) {
        return R.ok(workOrderService.list(status, keyword, source));
    }

    @GetMapping("/work-orders/{id}")
    public R<Map<String, Object>> workOrderDetail(@PathVariable Long id) {
        return R.ok(workOrderService.get(id));
    }

    @PostMapping("/work-orders")
    public R<Map<String, Object>> createWorkOrder(@RequestBody Map<String, Object> body) {
        return R.ok(workOrderService.save(body));
    }

    @PutMapping("/work-orders/{id}")
    public R<Map<String, Object>> updateWorkOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return R.ok(workOrderService.save(body));
    }

    @DeleteMapping("/work-orders/{id}")
    public R<Void> deleteWorkOrder(@PathVariable Long id) {
        workOrderService.delete(id);
        return R.ok();
    }

    @PostMapping("/work-orders/{id}/assign")
    public R<Map<String, Object>> assignWorkOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(workOrderService.assign(id, body));
    }

    @PostMapping("/work-orders/{id}/complete")
    public R<Map<String, Object>> completeWorkOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(workOrderService.complete(id, body));
    }

    /** 巡检异常 → 一键生成维保工单 */
    @PostMapping("/work-orders/from-checkin/{checkinId}")
    public R<Map<String, Object>> fromCheckin(
            @PathVariable Long checkinId,
            @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(workOrderService.createFromCheckin(checkinId, body == null ? Map.of() : body));
    }
}
