package com.hnist.support;

import com.hnist.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Pattern;

/** 收货地址 JSON 校验器：字段、长度与手机号格式校验，失败抛用户可见的 {@link BusinessException}。 */
public final class ShippingAddressValidator {

    private static final int MAX_RECEIVER_NAME_LENGTH = 50;
    private static final int MAX_REGION_LENGTH = 100;
    private static final int MAX_DETAIL_LENGTH = 200;
    /** 中国大陆手机号：1 开头，第二位 3-9，共 11 位。 */
    private static final Pattern MOBILE_PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private ShippingAddressValidator() {
    }

    /** 校验单个地址 JSON：必须是 JSON 对象，name/phone 必填且限长，手机号须匹配大陆号段。 */
    public static void validate(String addressJson, ObjectMapper objectMapper) {
        JsonNode address = parse(addressJson, objectMapper);
        if (address == null || !address.isObject()) {
            throw new BusinessException("收货地址格式不正确");
        }
        validateAddress(address);
    }

    /** 校验地址数组 JSON：整体必须是数组且每个元素都通过单地址校验，任一非法即抛异常。 */
    public static void validateList(String addressesJson, ObjectMapper objectMapper) {
        JsonNode addresses = parse(addressesJson, objectMapper);
        if (addresses == null || !addresses.isArray()) {
            throw new BusinessException("地址信息格式不正确");
        }
        for (JsonNode address : addresses) {
            if (!address.isObject()) {
                throw new BusinessException("地址信息格式不正确");
            }
            validateAddress(address);
        }
    }

    private static JsonNode parse(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            throw new BusinessException("地址信息不能为空");
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("地址信息格式不正确");
        }
    }

    private static void validateAddress(JsonNode address) {
        String receiverName = requiredText(address, "name", "收货人",
                MAX_RECEIVER_NAME_LENGTH);
        String phone = requiredText(address, "phone", "手机号", 11);
        if (!MOBILE_PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException("手机号格式不正确");
        }
        String region = firstText(address, "region", "city");
        if (region.isEmpty()) {
            throw new BusinessException("请填写省市区");
        }
        if (region.length() > MAX_REGION_LENGTH) {
            throw new BusinessException("省市区不能超过" + MAX_REGION_LENGTH + "个字符");
        }
        String detail = firstText(address, "detail", "address");
        if (detail.isEmpty()) {
            throw new BusinessException("请填写详细地址");
        }
        if (detail.length() > MAX_DETAIL_LENGTH) {
            throw new BusinessException("详细地址不能超过" + MAX_DETAIL_LENGTH + "个字符");
        }
    }

    private static String requiredText(JsonNode node, String fieldName,
                                       String label, int maxLength) {
        String value = text(node, fieldName);
        if (value.isEmpty()) {
            throw new BusinessException("请填写" + label);
        }
        if (value.length() > maxLength) {
            throw new BusinessException(label + "不能超过" + maxLength + "个字符");
        }
        return value;
    }

    private static String firstText(JsonNode node, String primaryField,
                                    String fallbackField) {
        String value = text(node, primaryField);
        return value.isEmpty() ? text(node, fallbackField) : value;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value != null && value.isTextual() ? value.asText().trim() : "";
    }
}
