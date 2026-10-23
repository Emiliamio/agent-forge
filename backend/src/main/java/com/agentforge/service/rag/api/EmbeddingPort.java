package com.agentforge.service.rag.api;

import java.util.List;

/**
 * 六边形架构 - 文本向量嵌入计算端口 (R6 Port)
 * 隔离底层三方 Embedding 模型 API / 本地嵌入模型，核心业务只面向该端口
 */
public interface EmbeddingPort {

    float[] embed(String text);

    List<float[]> embedBatch(List<String> texts);

    int getDimension();

    boolean degraded();

    String providerName();
}
