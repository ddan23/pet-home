package com.hnist.service;

import java.util.Map;

/** 管理端操作的审计留痕落库。 */
public interface AuditLogService {

    /**
     * 审计是旁路能力：写库或序列化失败只记日志、不抛异常，调用方不能依赖它做校验。
     * 不新开独立事务，随调用方事务提交或回滚，业务失败时不会留下「操作成功」的假记录。
     */
    void record(Integer operatorId, String action, String targetType, Long targetId,
                Map<String, Object> detail);
}
