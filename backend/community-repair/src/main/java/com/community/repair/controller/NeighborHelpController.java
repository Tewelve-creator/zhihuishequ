package com.community.repair.controller;

import com.community.common.result.R;
import com.community.repair.service.NeighborHelpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 邻里互助（独立于 /appeal 邻里倾诉）
 */
@RestController
@RequestMapping("/neighbor-help")
@RequiredArgsConstructor
public class NeighborHelpController {

    private final NeighborHelpService neighborHelpService;

    @GetMapping
    public R<List<Map<String, Object>>> publicList(
            @RequestParam(required = false) String helpType) {
        return R.ok(neighborHelpService.listPublic(helpType));
    }

    @GetMapping("/admin/list")
    public R<List<Map<String, Object>>> list(
            @RequestParam(required = false) String helpType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(neighborHelpService.list(helpType, status, keyword));
    }

    @GetMapping("/admin/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(neighborHelpService.detail(id));
    }

    @PutMapping("/admin/{id}/off")
    public R<Map<String, Object>> takeDown(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return R.ok(neighborHelpService.takeDown(id, reason));
    }

    @PutMapping("/admin/{id}/restore")
    public R<Map<String, Object>> restore(@PathVariable Long id) {
        return R.ok(neighborHelpService.restore(id));
    }

    @PostMapping
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return R.ok(neighborHelpService.create(body));
    }

    /** 居民：我来帮忙 */
    @PostMapping("/{id}/offer")
    public R<Map<String, Object>> offer(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(neighborHelpService.offerHelp(id, body));
    }
}
