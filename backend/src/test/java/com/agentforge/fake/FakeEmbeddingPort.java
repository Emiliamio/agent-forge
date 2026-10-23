package com.agentforge.fake;

import com.agentforge.service.rag.api.EmbeddingPort;

import java.util.Arrays;
import java.util.List;

/**
 * FakeEmbeddingPort: 确定性向量嵌入测试替身 (R6 Fake 实现)
 * 允许在无 OpenAI / 外部向量模型环境下验证 RAG 嵌入计算与检索
 */
public class FakeEmbeddingPort implements EmbeddingPort {

    private final int dimension;
    private int callCount = 0;

    public FakeEmbeddingPort() {
        this(1536);
    }

    public FakeEmbeddingPort(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        callCount++;
        float[] vector = new float[dimension];
        if (text != null) {
            float val = (float) (Math.abs(text.hashCode()) % 100) / 100.0f;
            Arrays.fill(vector, val);
        }
        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public boolean degraded() {
        return true;
    }

    @Override
    public String providerName() {
        return "fake-embedding-deterministic";
    }

    public int getCallCount() {
        return callCount;
    }
}
