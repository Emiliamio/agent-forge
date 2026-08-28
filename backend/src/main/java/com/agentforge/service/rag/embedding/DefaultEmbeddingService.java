package com.agentforge.service.rag.embedding;

import cn.hutool.core.util.StrUtil;
import com.agentforge.service.rag.VectorUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认 Embedding 向量化实现类
 * 优先调用配置的 OpenAI/DeepSeek 兼容接口，在未配置 Key 时平滑回退到确定性本地向量生成器
 */
@Slf4j
@Service
public class DefaultEmbeddingService implements EmbeddingService {

    @Value("${agentforge.llm.openai.api-key:sk-placeholder}")
    private String apiKey;

    @Value("${agentforge.llm.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${agentforge.rag.default-embedding-dim:1536}")
    private int dimension;

    private EmbeddingModel embeddingModel;
    private boolean isOnlineModelAvailable = false;

    @PostConstruct
    public void init() {
        if (StrUtil.isNotBlank(apiKey) && !apiKey.contains("placeholder")) {
            try {
                this.embeddingModel = OpenAiEmbeddingModel.builder()
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .modelName("text-embedding-3-small")
                        .timeout(Duration.ofSeconds(15))
                        .logRequests(true)
                        .logResponses(true)
                        .build();
                this.isOnlineModelAvailable = true;
                log.info("Embedding 在线模型初始化成功: model=text-embedding-3-small, baseUrl={}", baseUrl);
            } catch (Exception e) {
                log.warn("Embedding 在线模型初始化失败，将回退至本地向量模式: {}", e.getMessage());
            }
        } else {
            log.info("未检测到有效 Embedding API Key，自动启用本地确定性向量生成器 (1536维)");
        }
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimension];
        }

        if (isOnlineModelAvailable && embeddingModel != null) {
            try {
                Embedding embedding = embeddingModel.embed(text).content();
                float[] vector = embedding.vector();
                return VectorUtils.l2Normalize(vector);
            } catch (Exception e) {
                log.warn("在线 Embedding 接口调用失败，回退至本地向量: text={}, error={}", StrUtil.sub(text, 0, 30), e.getMessage());
            }
        }

        return generateDeterministicVector(text, dimension);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        if (isOnlineModelAvailable && embeddingModel != null) {
            try {
                List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                return embeddings.stream().map(e -> VectorUtils.l2Normalize(e.vector())).toList();
            } catch (Exception e) {
                log.warn("批量在线 Embedding 调用失败，回退至本地逐条生成: {}", e.getMessage());
            }
        }

        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    /**
     * 确定性高维语义哈希向量生成 (用于离线开发、测试与兜底)
     * 基于 SHA-256 + 伪随机种子保证相同文本始终生成完全相同的 1536 维归一化向量
     */
    public static float[] generateDeterministicVector(String text, int dim) {
        float[] vector = new float[dim];
        try {
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(textBytes);

            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (hash[i] & 0xFF);
            }

            java.util.Random random = new java.util.Random(seed);
            for (int i = 0; i < dim; i++) {
                vector[i] = (float) (random.nextGaussian());
            }
            return VectorUtils.l2Normalize(vector);
        } catch (Exception e) {
            return vector;
        }
    }
}
