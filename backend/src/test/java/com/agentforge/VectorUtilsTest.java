package com.agentforge;

import com.agentforge.service.rag.VectorUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

@DisplayName("向量运算与格式转换工具单元测试")
public class VectorUtilsTest {

    @Test
    @DisplayName("测试 float[] 数组与 pgvector 字符串互转")
    void testArrayToStringAndBack() {
        float[] original = new float[]{0.123f, -0.456f, 0.789f};
        String pgvectorStr = VectorUtils.toString(original);

        Assertions.assertEquals("[0.123,-0.456,0.789]", pgvectorStr);

        float[] parsed = VectorUtils.toFloatArray(pgvectorStr);
        Assertions.assertEquals(3, parsed.length);
        Assertions.assertEquals(0.123f, parsed[0], 0.0001f);
        Assertions.assertEquals(-0.456f, parsed[1], 0.0001f);
        Assertions.assertEquals(0.789f, parsed[2], 0.0001f);
    }

    @Test
    @DisplayName("测试 List<Float> 转换为 pgvector 字符串")
    void testListToString() {
        List<Float> list = List.of(1.0f, 2.0f, 3.0f);
        String pgvectorStr = VectorUtils.toString(list);
        Assertions.assertEquals("[1.0,2.0,3.0]", pgvectorStr);
    }

    @Test
    @DisplayName("测试余弦相似度计算与 L2 归一化")
    void testCosineSimilarityAndNormalize() {
        float[] v1 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v2 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v3 = new float[]{0.0f, 1.0f, 0.0f};

        // 相同向量余弦相似度应为 1.0
        double sim1 = VectorUtils.cosineSimilarity(v1, v2);
        Assertions.assertEquals(1.0, sim1, 0.00001);

        // 正交向量余弦相似度应为 0.0
        double sim2 = VectorUtils.cosineSimilarity(v1, v3);
        Assertions.assertEquals(0.0, sim2, 0.00001);

        // L2 归一化测试
        float[] raw = new float[]{3.0f, 4.0f};
        float[] normalized = VectorUtils.l2Normalize(raw);
        Assertions.assertEquals(0.6f, normalized[0], 0.0001f);
        Assertions.assertEquals(0.8f, normalized[1], 0.0001f);
    }
}
