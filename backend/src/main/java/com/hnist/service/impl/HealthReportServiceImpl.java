package com.hnist.service.impl;

import com.hnist.service.KnowledgeRetrievalService;

import com.hnist.service.HealthReportService;

import com.hnist.support.HealthScopeGuard;
import com.hnist.support.PetHealthContextAssembler;
import com.hnist.domain.AiCompletionRequest;
import com.hnist.domain.AiMessage;
import com.hnist.client.AiModelClient;
import com.hnist.support.AiRateLimitService;
import com.hnist.common.exception.AiUnavailableException;
import com.hnist.domain.AiAdvisorResult;
import com.hnist.domain.HealthChatMessage;
import com.hnist.domain.HealthCitation;
import com.hnist.entity.KnowledgeChunk;
import com.hnist.entity.Pet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 按宠物档案和知识库证据一键生成 HEALTH_REPORT 正文与引用。 */
@Service
public class HealthReportServiceImpl implements HealthReportService {

    private static final String DISCLAIMER =
            "AI 健康分析不能替代执业兽医的诊断和治疗。";
    private static final String REPORT_TYPE = "HEALTH_REPORT";
    private static final int REPORT_MAX_TOKENS = 1800;
    // 报告结论要稳定：温度压到 0.1，降低同一份档案两次生成得出不同结论的概率。
    private static final double REPORT_TEMPERATURE = 0.1D;
    // 八个章节与 [S1] 引用格式由这段提示词固定，落库正文与后续 PDF 导出共用同一份文本，改结构只能改这里。
    private static final String SYSTEM_PROMPT = """
            你是“猫狗宠物之家”的专属健康报告生成器，只分析猫和狗。
            你必须严格依据用户当前宠物的真实档案和已审核知识证据生成报告，不得推测未记录信息。
            不得给出确定诊断、处方药剂量或人药建议。
            输出使用简洁中文，并严格包含以下章节：
            【基础概况】
            【体重与体况】
            【免疫与驱虫】
            【过敏与病史】
            【当前风险提示】
            【未来30天养护建议】
            【建议就医的情况】
            【资料来源】
            健康结论引用证据时必须使用 [S1] 形式；没有知识证据时明确写“知识库暂无匹配依据”，
            不得编造来源、链接或医疗结论。
            """;

    private final AiModelClient modelClient;
    private final HealthScopeGuard scopeGuard;
    private final AiRateLimitService rateLimitService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final PetHealthContextAssembler contextAssembler;

    public HealthReportServiceImpl(AiModelClient modelClient,
                               HealthScopeGuard scopeGuard,
                               AiRateLimitService rateLimitService,
                               KnowledgeRetrievalService knowledgeRetrievalService,
                               PetHealthContextAssembler contextAssembler) {
        this.modelClient = modelClient;
        this.scopeGuard = scopeGuard;
        this.rateLimitService = rateLimitService;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.contextAssembler = contextAssembler;
    }

    @Override
    public AiAdvisorResult generate(Integer userId, Pet pet,
                                    List<HealthChatMessage> history) {
        // 未配置密钥时直接失败：报告是流式接口的主内容，返回空正文会让前端把空白当成“生成完成”。
        if (!modelClient.isConfigured()) {
            throw new AiUnavailableException("DeepSeek API 尚未配置，请联系管理员");
        }
        // 一次报告最多消耗 1800 个输出 token，必须按 userId 过限流（daily-limit / minute-limit，超限抛 429），否则可被反复点击刷额度。
        rateLimitService.check(userId);
        // 一键报告没有用户输入，检索词固定为健康主题词加宠物物种：入口不带关键词，否则每次生成拿到的证据集都不一致。
        List<KnowledgeChunk> knowledge = knowledgeRetrievalService.retrieve(
                "犬猫 体重 疫苗 驱虫 过敏 营养 健康管理", pet.getSpecies());
        List<HealthCitation> citations = knowledgeRetrievalService.toCitations(knowledge);
        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", SYSTEM_PROMPT));
        appendHistory(messages, history);
        messages.add(new AiMessage("user", buildReportPrompt(pet, knowledge, citations)));
        String answer = modelClient.complete(new AiCompletionRequest(
                messages, "health_report", false, List.of(),
                REPORT_MAX_TOKENS, REPORT_TEMPERATURE), userId).content();
        // 正文再过一次 HealthScopeGuard.isAllowedAnswer（额外一次分类模型调用）：判定越界（人医建议、其他动物、跑题、泄露提示词）时
        // 整段替换为档案拼装的兜底报告，不能在原文上删句——章节结构是完整的，删一半比给安全版本更危险。
        if (!scopeGuard.isAllowedAnswer(answer)) {
            answer = buildSafeFallback(pet);
        }
        // messageType 固定为 REPORT_TYPE：会话层据此把消息标成可下载报告，PDF 导出的类型校验依赖这个值。
        return new AiAdvisorResult(
                answer.trim(), false, false, "PET_HEALTH", DISCLAIMER,
                REPORT_TYPE, citations, List.of());
    }

    private String buildReportPrompt(Pet pet, List<KnowledgeChunk> knowledge,
                                     List<HealthCitation> citations) {
        Map<String, Object> snapshot = contextAssembler.buildStructuredSnapshot(pet);
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下宠物档案生成健康报告。\n")
                .append("<pet_profile>\n")
                .append(contextAssembler.toJson(snapshot))
                .append("\n</pet_profile>\n\n<evidence>\n");
        // 没有检索结果时显式写入缺口：留空会让模型自行补来源，与提示词里“不得编造来源”冲突。
        if (knowledge.isEmpty()) {
            prompt.append("知识库暂无匹配依据。\n");
        } else {
            appendEvidence(prompt, knowledge, citations);
        }
        prompt.append("</evidence>\n")
                .append("档案缺失项必须写“未记录”，不得自行补齐。")
                .append("体重数据不能单独用于诊断疾病。");
        return prompt.toString();
    }

    private void appendEvidence(StringBuilder prompt, List<KnowledgeChunk> knowledge,
                                List<HealthCitation> citations) {
        int index = 0;
        for (KnowledgeChunk chunk : knowledge) {
            // 片段数与引用数由不同环节生成、可能不等长：下标夹到 citations.size()-1，保证每个片段都有可引用的 [Sx] 而不是越界。
            String reference = citations.get(Math.min(index, citations.size() - 1))
                    .getReferenceId();
            prompt.append('[').append(reference).append("] ")
                    .append(chunk.getTitle()).append(" | ")
                    .append(chunk.getPublisher()).append('\n')
                    .append(chunk.getContent()).append("\n\n");
            index++;
        }
    }

    private String buildSafeFallback(Pet pet) {
        Map<String, Object> snapshot = contextAssembler.buildStructuredSnapshot(pet);
        // 兜底报告只回填档案字段和就医阈值，不产生任何医学结论，章节结构与系统提示词保持一致，前端渲染无需特判。
        return "【基础概况】\n"
                + "姓名：" + snapshot.get("name")
                + "；物种：" + snapshot.get("species")
                + "；年龄：" + snapshot.get("age") + "\n\n"
                + "【体重与体况】\n当前记录体重："
                + snapshot.get("currentWeight") + " kg。\n\n"
                + "【免疫与驱虫】\n请查看档案中的疫苗和驱虫记录。\n\n"
                + "【过敏与病史】\n" + snapshot.get("medicalHistory") + "\n\n"
                + "【当前风险提示】\nAI 报告未通过安全检查，无法生成进一步医学分析。\n\n"
                + "【未来30天养护建议】\n请补充体重、疫苗、驱虫和病史记录后重新生成。\n\n"
                + "【建议就医的情况】\n出现呼吸困难、抽搐、中毒、大出血、无法排尿或意识异常时，"
                + "请立即联系急诊兽医。\n\n"
                + "【资料来源】\n当前回答未引用知识库来源。";
    }

    private void appendHistory(List<AiMessage> messages, List<HealthChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return;
        }
        // 只带最近 12 条历史：报告侧重当前档案，历史过长既挤占 token 预算，又会让旧对话覆盖本次档案结论。
        int start = Math.max(0, history.size() - 12);
        for (int index = start; index < history.size(); index++) {
            HealthChatMessage item = history.get(index);
            messages.add(new AiMessage(
                    "assistant".equalsIgnoreCase(item.getRole()) ? "assistant" : "user",
                    item.getContent()));
        }
    }
}
