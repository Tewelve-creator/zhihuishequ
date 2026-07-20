package com.community.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.auth.dto.PackageSaveRequest;
import com.community.auth.entity.SysPackage;
import com.community.auth.entity.SysPackageFeature;
import com.community.auth.mapper.SysPackageFeatureMapper;
import com.community.auth.mapper.SysPackageMapper;
import com.community.common.constant.FeatureCodes;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.exception.ErrorCode;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PackageService {

    private static final Map<String, String> FEATURE_NAMES = Map.of(
            FeatureCodes.BASIC_REPAIR, "基础报修",
            FeatureCodes.BASIC_NOTICE, "基础公告",
            FeatureCodes.AI_CHAT, "AI问答",
            FeatureCodes.AI_IMAGE_REPAIR, "识图报修",
            FeatureCodes.AI_EMOTION, "情绪倾诉",
            FeatureCodes.AI_LOST_MATCH, "失物匹配",
            FeatureCodes.DATA_DASHBOARD, "数据大屏",
            FeatureCodes.DATA_EXPORT, "数据导出",
            FeatureCodes.VENDOR_MANAGE, "服务商管理",
            FeatureCodes.MULTI_COMMUNITY, "多社区管理"
    );

    private final SysPackageMapper packageMapper;
    private final SysPackageFeatureMapper featureMapper;

    public List<SysPackage> listPublic() {
        List<SysPackage> list = packageMapper.selectList(new LambdaQueryWrapper<SysPackage>()
                .eq(SysPackage::getStatus, 1)
                .orderByAsc(SysPackage::getSortNo)
                .orderByAsc(SysPackage::getId));
        list.forEach(this::fillFeatures);
        return list;
    }

    public List<SysPackage> listAll() {
        assertPlatform();
        List<SysPackage> list = packageMapper.selectList(new LambdaQueryWrapper<SysPackage>()
                .orderByAsc(SysPackage::getSortNo)
                .orderByAsc(SysPackage::getId));
        list.forEach(this::fillFeatures);
        return list;
    }

    public SysPackage getById(Long id) {
        SysPackage pkg = packageMapper.selectById(id);
        if (pkg == null) {
            throw new BizException(ErrorCode.PACKAGE_NOT_FOUND);
        }
        fillFeatures(pkg);
        return pkg;
    }

    @Transactional(rollbackFor = Exception.class)
    public SysPackage save(PackageSaveRequest req) {
        assertPlatform();
        if (!StringUtils.hasText(req.getName()) || !StringUtils.hasText(req.getCode())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "套餐名称和编码必填");
        }
        SysPackage pkg;
        if (req.getId() == null) {
            Long cnt = packageMapper.selectCount(new LambdaQueryWrapper<SysPackage>()
                    .eq(SysPackage::getCode, req.getCode()));
            if (cnt != null && cnt > 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "套餐编码已存在");
            }
            pkg = new SysPackage();
            pkg.setCreateTime(LocalDateTime.now());
        } else {
            pkg = packageMapper.selectById(req.getId());
            if (pkg == null) {
                throw new BizException(ErrorCode.PACKAGE_NOT_FOUND);
            }
            pkg.setUpdateTime(LocalDateTime.now());
        }
        pkg.setName(req.getName());
        pkg.setCode(req.getCode());
        pkg.setPriceMonth(req.getPriceMonth());
        pkg.setPriceQuarter(req.getPriceQuarter());
        pkg.setPriceYear(req.getPriceYear());
        pkg.setAiQuota(req.getAiQuota() == null ? 50 : req.getAiQuota());
        pkg.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        pkg.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        pkg.setRemark(req.getRemark());
        if (pkg.getId() == null) {
            packageMapper.insert(pkg);
        } else {
            packageMapper.updateById(pkg);
        }
        replaceFeatures(pkg.getId(), req.getFeatureCodes());
        return getById(pkg.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        assertPlatform();
        featureMapper.delete(new LambdaQueryWrapper<SysPackageFeature>()
                .eq(SysPackageFeature::getPackageId, id));
        packageMapper.deleteById(id);
    }

    public String featureCsv(Long packageId) {
        List<SysPackageFeature> list = featureMapper.selectList(new LambdaQueryWrapper<SysPackageFeature>()
                .eq(SysPackageFeature::getPackageId, packageId));
        return list.stream().map(SysPackageFeature::getFeatureCode).collect(Collectors.joining(","));
    }

    public List<String> featureCodes(Long packageId) {
        return featureMapper.selectList(new LambdaQueryWrapper<SysPackageFeature>()
                        .eq(SysPackageFeature::getPackageId, packageId))
                .stream().map(SysPackageFeature::getFeatureCode).toList();
    }

    public SysPackage findFreePackage() {
        SysPackage free = packageMapper.selectOne(new LambdaQueryWrapper<SysPackage>()
                .eq(SysPackage::getCode, "free")
                .last("limit 1"));
        if (free != null) {
            fillFeatures(free);
        }
        return free;
    }

    public Map<String, String> allFeatureDict() {
        return FEATURE_NAMES;
    }

    private void replaceFeatures(Long packageId, List<String> codes) {
        featureMapper.delete(new LambdaQueryWrapper<SysPackageFeature>()
                .eq(SysPackageFeature::getPackageId, packageId));
        if (codes == null || codes.isEmpty()) {
            return;
        }
        Set<String> uniq = new LinkedHashSet<>(codes);
        for (String code : uniq) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            SysPackageFeature f = new SysPackageFeature();
            f.setPackageId(packageId);
            f.setFeatureCode(code.trim());
            f.setFeatureName(FEATURE_NAMES.getOrDefault(code.trim(), code.trim()));
            featureMapper.insert(f);
        }
    }

    private void fillFeatures(SysPackage pkg) {
        List<SysPackageFeature> list = featureMapper.selectList(new LambdaQueryWrapper<SysPackageFeature>()
                .eq(SysPackageFeature::getPackageId, pkg.getId()));
        pkg.setFeatureList(list);
        pkg.setFeatureCodes(list.stream().map(SysPackageFeature::getFeatureCode).toList());
    }

    private void assertPlatform() {
        if (!RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅运营可管理套餐");
        }
    }
}
