package com.community.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.community.auth.dto.PackageSaveRequest;
import com.community.auth.entity.SysPackage;
import com.community.auth.service.PackageService;
import com.community.common.constant.RoleConstants;
import com.community.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth/packages")
@RequiredArgsConstructor
public class PackageController {

    private final PackageService packageService;

    @GetMapping("/public")
    public R<List<SysPackage>> listPublic() {
        return R.ok(packageService.listPublic());
    }

    @GetMapping("/features")
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    public R<Map<String, String>> featureDict() {
        return R.ok(packageService.allFeatureDict());
    }

    @GetMapping
    @SaCheckLogin
    public R<List<SysPackage>> listAll() {
        return R.ok(packageService.listAll());
    }

    @GetMapping("/{id}")
    @SaCheckLogin
    public R<SysPackage> detail(@PathVariable Long id) {
        return R.ok(packageService.getById(id));
    }

    @PostMapping
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    @SaCheckPermission("auth:package:edit")
    public R<SysPackage> create(@RequestBody PackageSaveRequest req) {
        req.setId(null);
        return R.ok(packageService.save(req));
    }

    @PutMapping
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    @SaCheckPermission("auth:package:edit")
    public R<SysPackage> update(@RequestBody PackageSaveRequest req) {
        return R.ok(packageService.save(req));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    @SaCheckPermission("auth:package:edit")
    public R<Void> remove(@PathVariable Long id) {
        packageService.remove(id);
        return R.ok();
    }
}
