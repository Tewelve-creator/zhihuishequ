package com.community.repair.controller;

import com.community.common.result.R;
import com.community.repair.dto.ResidentCarSaveRequest;
import com.community.repair.entity.ResidentCar;
import com.community.repair.service.ResidentCarService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 常驻住户车辆管理（与访客临时车牌、道闸 park_vehicle 模块区分）
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/resident-car")
public class ResidentCarController {

    private final ResidentCarService residentCarService;

    @GetMapping("/list")
    public R<List<ResidentCar>> list(
            @RequestParam(required = false) Long houseId,
            @RequestParam(required = false) Long residentId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(residentCarService.list(houseId, residentId, userId, status, keyword));
    }

    /** 停车账单选车下拉 */
    @GetMapping("/options")
    public R<Map<String, Object>> options(@RequestParam Long houseId) {
        return R.ok(residentCarService.optionsForHouse(houseId));
    }

    @PostMapping
    public R<ResidentCar> create(@RequestBody ResidentCarSaveRequest req) {
        req.setId(null);
        return R.ok(residentCarService.save(req));
    }

    @PutMapping("/{id}")
    public R<ResidentCar> update(@PathVariable Long id, @RequestBody ResidentCarSaveRequest req) {
        req.setId(id);
        return R.ok(residentCarService.save(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        residentCarService.deleteOrDisable(id);
        return R.ok();
    }

    /** 限制/恢复车辆进出（长期欠费） */
    @PutMapping("/{id}/access-restrict")
    public R<ResidentCar> accessRestrict(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean restricted = body != null && (
                Boolean.TRUE.equals(body.get("restricted"))
                        || "1".equals(String.valueOf(body.get("restricted")))
                        || "true".equalsIgnoreCase(String.valueOf(body.get("restricted"))));
        return R.ok(residentCarService.setAccessRestricted(id, restricted));
    }
}
