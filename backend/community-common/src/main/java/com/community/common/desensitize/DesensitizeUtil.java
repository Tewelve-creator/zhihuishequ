package com.community.common.desensitize;

/**
 * 数据脱敏工具
 */
public final class DesensitizeUtil {
    private DesensitizeUtil() {
    }

    /** 手机号：138****0021 */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 姓名：张* / 欧** */
    public static String name(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    /** 身份证简单脱敏 */
    public static String idCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }
}
