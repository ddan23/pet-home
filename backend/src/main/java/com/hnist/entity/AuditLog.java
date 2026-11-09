package com.hnist.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** audit_logs 表行映射：管理员操作审计日志。 */
@Data
public class AuditLog {

    private Long id;
    private Integer operatorId;
    private String action;
    private String targetType;
    private Long targetId;
    private String requestId;
    private String ipAddress;
    /** 操作详情 JSON */
    private String detailJson;
    private LocalDateTime createdAt;
}
