package com.agentforge.service.rag.embedding;

import java.util.List;

/**
 * 文本向量化计算服务接口
 */
public interface EmbeddingService {

    /**
     * 单条文本 Embedding 向量化
     *
     * @param text 输入文本
     * @return 稠密高维向量 (如 1536 维)
     */
    float[] embed(String text);

    /**
     * 批量文本 Embedding 向量化
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 获取当前 Embedding 模型的向量维度 (如 1536, 1024, 768)
     */
    int getDimension();
}
