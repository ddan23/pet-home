package com.hnist.service.impl;

import com.hnist.mapper.AuditLogMapper;
import com.hnist.entity.AuditLog;
import com.hnist.service.AuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/** 管理端操作的审计留痕落库。 */
@Slf4j
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditLogServiceImpl(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(Integer operatorId, String action, String targetType, Long targetId,
                       Map<String, Object> detail) {
        try {
            // 审计只固化四元组：operator_id 是谁、action 做了什么、target_type/target_id 对哪条记录，其余上下文进 detail_json。
            AuditLog auditLog = new AuditLog();
            auditLog.setOperatorId(operatorId);
            auditLog.setAction(action);
            auditLog.setTargetType(targetType);
            auditLog.setTargetId(targetId);
            // 变更明细以 JSON 快照保存，事后可按原始结构还原；detail 为空时保持 NULL，不写入字符串 "null"。
            auditLog.setDetailJson(detail == null ? null : objectMapper.writeValueAsString(detail));
            // 请求号取网关透传的 X-Request-Id，可与访问日志对齐；ip 取 remoteAddr，内部调用没有请求上下文时两者留空。
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setRequestId(request.getHeader("X-Request-Id"));
                auditLog.setIpAddress(request.getRemoteAddr());
            }
            auditLogMapper.insert(auditLog);
        } catch (RuntimeException exception) {
            // 审计是旁路能力：写库失败只告警，不能反向阻断管理操作；本方法不开独立事务，随调用方事务提交或回滚，
            // 业务失败时日志一并回滚，不会留下「操作成功」的假记录。
            log.warn("写入审计日志失败，操作：{}，目标：{}:{}", action, targetType, targetId, exception);
        } catch (JsonProcessingException exception) {
            // 明细里混入不可序列化对象时同样只丢这一条日志，不把序列化异常抛回业务。
            log.warn("序列化审计日志失败，操作：{}", action, exception);
        }
    }
}
