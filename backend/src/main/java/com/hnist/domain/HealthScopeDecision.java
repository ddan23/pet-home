package com.hnist.domain;

/**
 * 健康范围判定。
 * allowed 需模型放行且 category 为 PET_HEALTH 或 EMERGENCY 同时成立；emergency 单独下传，分类为 EMERGENCY 时强制为 true。
 */
public record HealthScopeDecision(boolean allowed, boolean emergency, String category, String reason) {
}
