package com.hnist.service;

import com.hnist.dto.AiStreamChatRequest;
import com.hnist.dto.AiStreamRecommendationRequest;
import com.hnist.dto.AiStreamReportRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** AI 养宠顾问流式响应服务。 */
public interface AiAdvisorStreamService {

    // 返回的 SseEmitter 在异步线程里按 start → delta* → citations → recommendations → replace → done 推送；失败只发 error 事件，不改成 5xx 断流。
    SseEmitter streamChat(Integer userId, AiStreamChatRequest request);

    // 报告没有增量生成，正文以 delta 分片补发，事件类型与问答保持一致，前端只实现一套解析。
    SseEmitter streamReport(Integer userId, AiStreamReportRequest request);

    // 推荐模式边生成边推 delta，推荐条目在 done 之前的 recommendations 事件里下发。
    SseEmitter streamRecommendation(
            Integer userId, AiStreamRecommendationRequest request);
}
