package com.hnist.mapper;

import com.hnist.entity.AuditLog;

/** 管理员审计日志 Mapper。 */
public interface AuditLogMapper {

    int insert(AuditLog auditLog);
}
