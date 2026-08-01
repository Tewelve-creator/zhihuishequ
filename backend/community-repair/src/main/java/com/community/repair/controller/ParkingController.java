package com.community.repair.controller;

import com.community.common.result.R;
import com.community.repair.dto.*;
import com.community.repair.entity.*;
import com.community.repair.service.ParkingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 车辆停车管理（统一返回 {@link R}，即业务文档中的 Result&lt;T&gt;）
 * <p>前缀 /parking，经网关转发至 community-repair:8083</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/parking")
public class ParkingController {

    private final ParkingService parkingService;

    /* ---------- 车辆台账 ---------- */

    /** 车辆列表（业主看本人；物业看本社区） */
    @GetMapping("/vehicles")
    public R<List<ParkVehicle>> vehicles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String listType) {
        return R.ok(parkingService.listVehicles(keyword, listType));
    }

    /** 新增/变更车辆（业主录入白名单；物业维护黑白绿名单） */
    @PostMapping("/vehicles")
    public R<ParkVehicle> saveVehicle(@RequestBody ParkVehicleSaveRequest req) {
        req.setId(null);
        return R.ok(parkingService.saveVehicle(req));
    }

    @PutMapping("/vehicles/{id}")
    public R<ParkVehicle> updateVehicle(@PathVariable Long id, @RequestBody ParkVehicleSaveRequest req) {
        req.setId(id);
        return R.ok(parkingService.saveVehicle(req));
    }

    /** 解绑车辆 */
    @DeleteMapping("/vehicles/{id}")
    public R<Void> unbind(@PathVariable Long id) {
        parkingService.unbindVehicle(id);
        return R.ok();
    }

    /* ---------- 车位 ---------- */

    @GetMapping("/spaces")
    public R<List<ParkSpace>> spaces(
            @RequestParam(required = false) String areaCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String spaceType) {
        return R.ok(parkingService.listSpaces(areaCode, status, spaceType));
    }

    /** 空余车位分布图 */
    @GetMapping("/spaces/map")
    public R<Map<String, Object>> spaceMap() {
        return R.ok(parkingService.spaceMap());
    }

    @PostMapping("/spaces")
    public R<ParkSpace> saveSpace(@RequestBody ParkSpaceSaveRequest req) {
        req.setId(null);
        return R.ok(parkingService.saveSpace(req));
    }

    @PutMapping("/spaces/{id}")
    public R<ParkSpace> updateSpace(@PathVariable Long id, @RequestBody ParkSpaceSaveRequest req) {
        req.setId(id);
        return R.ok(parkingService.saveSpace(req));
    }

    @DeleteMapping("/spaces/{id}")
    public R<Void> deleteSpace(@PathVariable Long id) {
        parkingService.deleteSpace(id);
        return R.ok();
    }

    /** 绑定业主车辆到车位 */
    @PutMapping("/spaces/{id}/bind-car")
    public R<ParkSpace> bindCar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long carId = body.get("carId") == null ? null : Long.valueOf(String.valueOf(body.get("carId")));
        return R.ok(parkingService.bindResidentCar(id, carId));
    }

    /** 地磁/AI视频车位状态上报 */
    @PostMapping("/spaces/occupy")
    public R<ParkSpace> reportOccupy(@RequestBody ParkSpaceOccupyRequest req) {
        return R.ok(parkingService.reportOccupy(req));
    }

    /* ---------- 道闸通行 ---------- */

    /** 车牌识别抬杆决策 */
    @PostMapping("/gate/pass")
    public R<Map<String, Object>> gatePass(@RequestBody ParkGatePassRequest req) {
        return R.ok(parkingService.gatePass(req));
    }

    @GetMapping("/access-logs")
    public R<List<ParkAccessLog>> accessLogs(
            @RequestParam(required = false) String plateNo,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String passResult) {
        return R.ok(parkingService.listAccessLogs(plateNo, from, to, direction, passResult));
    }

    /** 停车收费台账（月租+临停） */
    @GetMapping("/fee-ledger")
    public R<List<Map<String, Object>>> feeLedger(
            @RequestParam(required = false) String feeKind,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return R.ok(parkingService.feeLedger(feeKind, keyword, status));
    }

    /** 同步长期月租欠费限行 */
    @PostMapping("/arrears/sync-restrict")
    public R<Map<String, Object>> syncArrearsRestrict() {
        return R.ok(parkingService.syncArrearsRestrict());
    }

    /* ---------- 访客授权 ---------- */

    @GetMapping("/visitor-auths")
    public R<List<ParkVisitorAuth>> visitorAuths(@RequestParam(required = false) String status) {
        return R.ok(parkingService.listVisitorAuth(status));
    }

    /** 业主生成临时通行码 / 门岗登记 */
    @PostMapping("/visitor-auths")
    public R<ParkVisitorAuth> createVisitorAuth(@RequestBody ParkVisitorAuthRequest req) {
        return R.ok(parkingService.createVisitorAuth(req));
    }

    /** 访客自助扫码补录车牌 */
    @PostMapping("/visitor-auths/self-register")
    public R<ParkVisitorAuth> selfRegister(@RequestBody ParkVisitorAuthRequest req) {
        return R.ok(parkingService.selfRegister(req));
    }

    @PostMapping("/visitor-auths/{id}/revoke")
    public R<Void> revoke(@PathVariable Long id) {
        parkingService.revokeVisitorAuth(id);
        return R.ok();
    }

    /* ---------- 订单缴费 ---------- */

    @GetMapping("/orders")
    public R<List<ParkOrder>> orders(
            @RequestParam(required = false) String plateNo,
            @RequestParam(required = false) String status) {
        return R.ok(parkingService.listOrders(plateNo, status));
    }

    /** 离场前试算费用 */
    @GetMapping("/orders/calc")
    public R<ParkOrder> calc(@RequestParam String plateNo) {
        return R.ok(parkingService.calcFee(plateNo));
    }

    /** 小程序/出口扫码缴费 */
    @PostMapping("/orders/pay")
    public R<ParkOrder> pay(@RequestBody ParkPayRequest req) {
        return R.ok(parkingService.pay(req));
    }

    /* ---------- 配套 ---------- */

    /** 匿名一键挪车 */
    @PostMapping("/move-car")
    public R<Map<String, Object>> moveCar(@RequestBody ParkMoveCarRequest req) {
        return R.ok(parkingService.moveCar(req));
    }

    /** 物业后台统计大屏 */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(parkingService.stats());
    }

    /** 名单/车位状态预热到 Redis */
    @PostMapping("/cache/warm")
    public R<Map<String, Object>> warmCache() {
        return R.ok(parkingService.warmCache());
    }
}
