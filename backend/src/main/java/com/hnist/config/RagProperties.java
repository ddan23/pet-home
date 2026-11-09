package com.hnist.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 绑定 app.ai.rag.*：RAG 业务开关、百炼 Embedding（官方 OpenAI 兼容端点）与检索融合参数。
 * Milvus 连接与集合配置已迁移到 spring.ai.vectorstore.milvus.*（Spring AI 官方配置节）。
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app.ai.rag")
public class RagProperties {

    /** RAG 业务开关（RAG_ENABLED，默认 false）；仅置 true 不够，还需 embedding 密钥就绪才生效。 */
    private boolean enabled = false;

    /** Milvus 地址（MILVUS_URI，默认本机 19530）；仅开关打开时建立连接。 */
    private String milvusUri = "http://127.0.0.1:19530";

    /** Milvus 认证 token（MILVUS_TOKEN），形如 root:password；开关打开时必填。 */
    private String milvusToken;

    /** 向量集合名（MILVUS_COLLECTION_NAME，默认 pet_knowledge_chunks_v2），结构由 Spring AI 官方 schema 维护。 */
    private String collectionName = "pet_knowledge_chunks_v2";

    /** Milvus database（MILVUS_DATABASE，默认 default）。 */
    private String databaseName = "default";

    /** 百炼 Embedding API Key（DASHSCOPE_API_KEY），为空视为未就绪。 */
    private String embeddingApiKey;

    /** 百炼 OpenAI 兼容端点（DASHSCOPE_BASE_URL），由 Spring AI 的 OpenAiEmbeddingModel 官方类承接。 */
    private String embeddingBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** 向量化模型（EMBEDDING_MODEL，默认 text-embedding-v4，阿里百炼）。 */
    private String embeddingModel = "text-embedding-v4";

    /** 向量维度（EMBEDDING_DIMENSION，默认 1024），必须与 Milvus 集合维度一致；换维度等于换语义空间，必须重建集合。 */
    private int dimension = 1024;

    /** 每批送去做 embedding 的文本条数（EMBEDDING_BATCH_SIZE，默认 10），与百炼批量接口上限对齐。 */
    private int embeddingBatchSize = 10;

    /** 最终返回的检索条数（RAG_TOP_K，默认 4），作为提示语引用的素材数量。 */
    private int topK = 4;

    /** 候选扩大倍数（RAG_CANDIDATE_MULTIPLIER，默认 2）：两路召回各取 topK×倍数条候选，融合后再截断。 */
    private int candidateMultiplier = 2;

    /** RRF 重排平滑参数（RAG_RRF_K，默认 60），越大各路召回的排名差异越平缓。 */
    private int rrfK = 60;

    /** 三条件齐备才可调用：开关打开且 embedding 密钥非空白；调用方以此判断是否降级为 MySQL 全文检索。 */
    public boolean isReady() {
        return enabled && embeddingApiKey != null && !embeddingApiKey.isBlank()
                && milvusToken != null && !milvusToken.isBlank();
    }
}
