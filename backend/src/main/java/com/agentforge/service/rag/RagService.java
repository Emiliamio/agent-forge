package com.agentforge.service.rag;

import com.agentforge.vo.Citation;

import java.util.List;

/**
 * RAG 统一检索与提示词上下文装配服务接口
 */
public interface RagService {

    /**
     * 执行三路混合检索并组装为结构化溯源引用列表
     *
     * @param datasetIds 数据集 ID 列表
     * @param query      用户自然语言提问
     * @param topK       最大引用数
     * @param minScore   相似度阈值
     * @return 溯源引用列表
     */
    List<Citation> retrieveCitations(List<Long> datasetIds, String query, int topK, double minScore);

    /**
     * 检索相关切片并生成注入到 LLM Prompt 中的格式化上下文文本
     */
    String assemblePromptContext(List<Long> datasetIds, String query, int topK);
}
