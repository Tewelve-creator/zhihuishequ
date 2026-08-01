package com.community.auth.controller;

import com.community.auth.dto.HouseBindRequest;
import com.community.auth.dto.HouseholdSaveRequest;
import com.community.auth.dto.MoveAuditRequest;
import com.community.auth.service.HouseAdminService;
import com.community.auth.service.HouseMoveService;
import com.community.auth.service.HouseService;
import com.community.common.result.PageResult;
import com.community.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth/house")
@RequiredArgsConstructor
public class HouseController {

    private final HouseService houseService;
    private final HouseMoveService houseMoveService;
    private final HouseAdminService houseAdminService;

    @GetMapping("/mine")
    public R<List<Map<String, Object>>> mine() {
        return R.ok(houseService.listMine());
    }

    @GetMapping("/current")
    public R<Map<String, Object>> current() {
        return R.ok(houseService.current());
    }

    @GetMapping("/options/buildings")
    public R<List<String>> buildings() {
        return R.ok(houseService.listBuildings());
    }

    @GetMapping("/options/units")
    public R<List<String>> units(@RequestParam String building) {
        return R.ok(houseService.listUnits(building));
    }

    @GetMapping("/options/rooms")
    public R<List<Map<String, Object>>> rooms(
            @RequestParam String building,
            @RequestParam String unit) {
        return R.ok(houseService.listRooms(building, unit));
    }

    /** 本租户全部房源（物业手录住户选房） */
    @GetMapping("/catalog")
    public R<List<Map<String, Object>>> catalog() {
        return R.ok(houseService.listCatalog());
    }

    /** 居民提交迁入（绑定）申请 → 待物业审批 */
    @PostMapping("/bind")
    public R<Map<String, Object>> bind(@RequestBody HouseBindRequest req) {
        return R.ok(houseMoveService.applyMoveIn(req));
    }

    /** 居民提交迁出（解绑）申请 —— 路径勿与 /{houseId}/xxx 冲突 */
    @PostMapping("/apply/unbind")
    public R<Map<String, Object>> applyUnbind(@RequestBody Map<String, Object> body) {
        if (body == null || body.get("houseId") == null) {
            return R.fail("请选择要解绑的房屋");
        }
        Long houseId = Long.valueOf(String.valueOf(body.get("houseId")));
        String remark = null;
        if (body.get("remark") != null && !"null".equals(String.valueOf(body.get("remark")))) {
            remark = String.valueOf(body.get("remark"));
        }
        return R.ok(houseMoveService.applyMoveOut(houseId, remark));
    }

    @GetMapping("/apply/mine")
    public R<List<Map<String, Object>>> myApplies() {
        return R.ok(houseMoveService.myApplies());
    }

    @PutMapping("/{houseId}/default")
    public R<Map<String, Object>> setDefault(@PathVariable Long houseId) {
        return R.ok(houseService.setDefault(houseId));
    }

    // ---------- 物业：迁入迁出审批 ----------

    @GetMapping("/move/list")
    public R<PageResult<Map<String, Object>>> moveList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String applyType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(houseMoveService.pageApplies(status, applyType, keyword, page, size));
    }

    @GetMapping("/move/stats")
    public R<Map<String, Object>> moveStats() {
        return R.ok(houseMoveService.moveStats());
    }

    @PostMapping("/move/{id}/approve")
    public R<Map<String, Object>> approve(@PathVariable Long id, @RequestBody(required = false) MoveAuditRequest req) {
        return R.ok(houseMoveService.approve(id, req));
    }

    @PostMapping("/move/{id}/reject")
    public R<Map<String, Object>> reject(@PathVariable Long id, @RequestBody(required = false) MoveAuditRequest req) {
        return R.ok(houseMoveService.reject(id, req));
    }

    // ---------- 物业：住户管理 ----------

    @GetMapping("/household/list")
    public R<PageResult<Map<String, Object>>> householdList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String identityType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(houseMoveService.pageHousehold(status, identityType, keyword, page, size));
    }

    @PostMapping("/household")
    public R<Map<String, Object>> saveHousehold(@RequestBody HouseholdSaveRequest req) {
        return R.ok(houseMoveService.saveHousehold(req));
    }

    @PutMapping("/household/{id}")
    public R<Map<String, Object>> updateHousehold(@PathVariable Long id, @RequestBody HouseholdSaveRequest req) {
        req.setId(id);
        return R.ok(houseMoveService.saveHousehold(req));
    }

    @DeleteMapping("/household/{id}")
    public R<Void> removeHousehold(@PathVariable Long id) {
        houseMoveService.removeHousehold(id);
        return R.ok();
    }

    // ---------- 物业：房屋基础管理 ----------

    @GetMapping("/admin/list")
    public R<List<Map<String, Object>>> adminList(
            @RequestParam(required = false) String building,
            @RequestParam(required = false) String unit,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String occupyStatus) {
        return R.ok(houseAdminService.listHouses(building, unit, keyword, occupyStatus));
    }

    @GetMapping("/admin/building-summary")
    public R<List<Map<String, Object>>> buildingSummary() {
        return R.ok(houseAdminService.buildingSummary());
    }

    @GetMapping("/admin/buildings")
    public R<List<String>> adminBuildings() {
        return R.ok(houseAdminService.listBuildingsAdmin());
    }

    @GetMapping("/admin/units")
    public R<List<String>> adminUnits(@RequestParam String building) {
        return R.ok(houseAdminService.listUnitsAdmin(building));
    }

    @PostMapping("/admin/building")
    public R<Map<String, Object>> ensureBuilding(@RequestBody Map<String, Object> body) {
        return R.ok(houseAdminService.ensureBuilding(body));
    }

    @PostMapping("/admin/unit")
    public R<Map<String, Object>> ensureUnit(@RequestBody Map<String, Object> body) {
        return R.ok(houseAdminService.ensureUnit(body));
    }

    @PostMapping("/admin")
    public R<Map<String, Object>> saveHouse(@RequestBody Map<String, Object> body) {
        return R.ok(houseAdminService.saveHouse(body));
    }

    @PutMapping("/admin/{id}")
    public R<Map<String, Object>> updateHouse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return R.ok(houseAdminService.saveHouse(body));
    }

    @PostMapping("/admin/batch")
    public R<Map<String, Object>> batchHouses(@RequestBody Map<String, Object> body) {
        return R.ok(houseAdminService.batchImport(body));
    }

    @DeleteMapping("/admin/{id}")
    public R<Void> deleteHouse(@PathVariable Long id) {
        houseAdminService.deleteHouse(id);
        return R.ok();
    }
}
