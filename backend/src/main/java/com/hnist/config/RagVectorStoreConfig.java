package com.hnist.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 官方组件装配：仅在 app.ai.rag.enabled=true 时创建。
 * 两个组件都是 Spring AI 官方类——EmbeddingModel 走百炼的 OpenAI 兼容端点，
 * VectorStore 用官方 MilvusVectorStore（starter 自动配置被排除，因为它无条件建立 Milvus 连接，
 * 违背「RAG 可选、关闭时零外部依赖」的降级语义，这里按开关手动装配达到同等效果）。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class RagVectorStoreConfig {

    /**
     * Milvus 连接：官方 MilvusServiceClient，连接参数来自 app.ai.rag.*（MILVUS_URI/MILVUS_TOKEN）。
     * 集合结构（doc_id/content/metadata/embedding）由 MilvusVectorStore 的 initializeSchema 官方逻辑创建。
     */
    @Bean
    public MilvusServiceClient ragMilvusServiceClient(RagProperties properties) {
        return new MilvusServiceClient(ConnectParam.newBuilder()
                .withUri(properties.getMilvusUri())
                .withToken(properties.getMilvusToken())
                .withDatabaseName(properties.getDatabaseName())
                .build());
    }

    /**
     * Milvus VectorStore：autoId=false 使 Document id（a{articleId}_c{chunkOrder}）成为主键，
     * 同步与删除都按该稳定 id 进行，重复同步天然幂等；维度必须与 EmbeddingModel 输出一致。
     */
    @Bean
    public VectorStore ragVectorStore(MilvusServiceClient ragMilvusServiceClient,
                                      EmbeddingModel ragEmbeddingModel, RagProperties properties) {
        return MilvusVectorStore.builder(ragMilvusServiceClient, ragEmbeddingModel)
                .collectionName(properties.getCollectionName())
                .databaseName(properties.getDatabaseName())
                .embeddingDimension(properties.getDimension())
                .metricType(MetricType.COSINE)
                .indexType(IndexType.AUTOINDEX)
                .initializeSchema(true)
                .build();
    }

    /**
     * 百炼 Embedding：官方 OpenAiEmbeddingModel 指向百炼 OpenAI 兼容端点；
     * 检索与建索引共用同一实例，保证两侧向量落在同一语义空间。
     */
    @Bean
    public EmbeddingModel ragEmbeddingModel(RagProperties properties) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(properties.getEmbeddingBaseUrl())
                .apiKey(properties.getEmbeddingApiKey())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(properties.getEmbeddingModel())
                        .dimensions(properties.getDimension())
                        .build());
    }
}
