package com.community.repair.util;

import com.community.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 车牌号格式校验与归一化（大陆民用/新能源常见格式）
 */
public final class PlateNoUtils {

    private PlateNoUtils() {
    }

    /**
     * 例：京A12345、京AD12345、沪A1234学
     */
    private static final Pattern PLATE = Pattern.compile(
            "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领]"
                    + "[A-HJ-NP-Z]"
                    + "[A-HJ-NP-Z0-9]{4,6}"
                    + "[A-HJ-NP-Z0-9挂学警港澳]?$");

    public static String normalize(String plateNo) {
        if (!StringUtils.hasText(plateNo)) {
            return null;
        }
        return plateNo.trim().toUpperCase().replace(" ", "").replace("·", "");
    }

    public static void validate(String plateNo) {
        String p = normalize(plateNo);
        if (!StringUtils.hasText(p)) {
            throw new BizException("请填写车牌号");
        }
        if (p.length() < 7 || p.length() > 8 || !PLATE.matcher(p).matches()) {
            throw new BizException("车牌号格式不正确，例：京A12345 或 京AD12345");
        }
    }

    /** 可选：空则跳过 */
    public static String normalizeOptional(String plateNo) {
        if (!StringUtils.hasText(plateNo)) {
            return null;
        }
        validate(plateNo);
        return normalize(plateNo);
    }
}
