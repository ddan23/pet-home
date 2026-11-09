package com.hnist.support;

/**
 * 管理端展示脱敏：手机号隐藏中间四位，邮箱只保留首字符与域名。
 * 仅用于管理后台列表/详情这类"查看他人资料"的场景，本人查看自己的资料不做处理。
 */
public final class DataMasking {

    private static final int PHONE_PREFIX_LENGTH = 3;
    private static final int PHONE_SUFFIX_LENGTH = 4;

    private DataMasking() {
    }

    /** 11 位手机号输出 138****0003；长度不足或为空时原样返回，避免把不完整数据切成乱码。 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() <= PHONE_PREFIX_LENGTH + PHONE_SUFFIX_LENGTH) {
            return phone;
        }
        return phone.substring(0, PHONE_PREFIX_LENGTH)
                + "****"
                + phone.substring(phone.length() - PHONE_SUFFIX_LENGTH);
    }

    /** 邮箱输出 u***@example.com；没有 @ 或 @ 在首位时原样返回，不猜测非法格式。 */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        String localPart = email.substring(0, atIndex);
        String maskedLocal = localPart.length() <= 1 ? "*" : localPart.charAt(0) + "***";
        return maskedLocal + email.substring(atIndex);
    }
}
