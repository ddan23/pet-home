package com.hnist.service;

/** 把已保存的 HEALTH_REPORT 消息渲染成可下载的 PDF 字节流。 */
public interface HealthReportPdfService {

    /**
     * 只渲染已落库的报告消息，不重新调用模型；宠物信息优先取消息里的生成时快照，档案后续改动不影响导出件。
     * 消息非本人所有、或不是 assistant/HEALTH_REPORT/COMPLETED 时抛 BusinessException（越权与不存在统一 404），渲染失败抛 500 而不降级出残缺文件。
     */
    byte[] export(Integer userId, Long messageId);
}
