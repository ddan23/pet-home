package com.hnist.common.constant;

/** AI 养宠顾问的会话模式，作为请求参数与 ai 会话的落库值，决定调用哪个生成服务。 */
public final class AiChatMode {

    /** 健康问答：走 HealthAssistantService，主题拦截、检索与引用校验都在下游完成。 */
    public static final String QA = "QA";
    /** 健康报告：用户消息固定为内部提示语，按宠物档案生成结构化报告。 */
    public static final String REPORT = "REPORT";
    /** 养护推荐：基于档案与近期健康数据给出建议，支持流式增量输出。 */
    public static final String RECOMMENDATION = "RECOMMENDATION";

    private AiChatMode() {
    }

    /** 白名单校验：未知模式在创建会话与发起对话时即被拒，避免写入无法渲染的历史记录。 */
    public static boolean isValid(String mode) {
        return QA.equals(mode) || REPORT.equals(mode) || RECOMMENDATION.equals(mode);
    }
}
