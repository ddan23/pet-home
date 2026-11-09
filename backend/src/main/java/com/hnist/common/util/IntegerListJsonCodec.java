package com.hnist.common.util;

import com.hnist.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** users.liked_posts 这类整数 ID 数组列的 JSON 编解码，读失败降级为空列表，写失败抛业务异常。 */
public final class IntegerListJsonCodec {

    private IntegerListJsonCodec() {
    }

    /**
     * 读取 JSON 数组；null、空白、"null" 与解析失败都返回可修改的空列表，调用方按“没有记录”处理。
     * 解析失败不抛异常是刻意降级：历史脏数据不该让点赞这类读路径整体失败；返回 ArrayList 是因为调用方要原地增删。
     */
    public static List<Integer> read(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return new ArrayList<>();
        }
    }

    /**
     * 序列化为 JSON 数组；失败抛 500 业务异常让调用方事务回滚，避免把半截 JSON 写进用户行。
     * errorMessage 由调用方给出，用于区分具体业务场景的提示文案。
     */
    public static String write(ObjectMapper objectMapper, List<Integer> values,
                               String errorMessage) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, errorMessage);
        }
    }
}
