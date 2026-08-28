package com.agentforge.service.rag;

import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;

import java.util.List;

/**
 * 高维向量数学运算与 pgvector 格式转换工具类
 */
public class VectorUtils {

    /**
     * 将 float[] 转换为 pgvector 字符串格式: "[0.123, -0.456, 0.789]"
     */
    public static String toString(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * 将 List<Float> 转换为 pgvector 字符串格式
     */
    public static String toString(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder(vector.size() * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * 将 pgvector 字符串 "[0.1, 0.2]" 解析为 float[]
     */
    public static float[] toFloatArray(String vectorStr) {
        if (vectorStr == null || vectorStr.length() < 2) {
            return new float[0];
        }
        String clean = vectorStr.trim();
        if (clean.startsWith("[") && clean.endsWith("]")) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        if (clean.isEmpty()) {
            return new float[0];
        }
        String[] parts = clean.split(",");
        float[] res = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            res[i] = Float.parseFloat(parts[i].trim());
        }
        return res;
    }

    /**
     * 计算两个等长向量的余弦相似度 (Cosine Similarity)
     * 范围: [-1.0, 1.0]，越接近 1.0 语义越相似
     */
    public static double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length || v1.length == 0) {
            throw new IllegalArgumentException("向量必须非空且具有相同的维度");
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * L2 向量归一化 (L2 Normalization)
     */
    public static float[] l2Normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return vector;
        }
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0) {
            return vector;
        }
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    /**
     * 校验向量维度一致性
     */
    public static void validateDimension(float[] vector, int expectedDim) {
        if (vector == null || vector.length != expectedDim) {
            int actual = vector == null ? 0 : vector.length;
            throw new BusinessException(ErrorCode.EMBEDDING_FAILED,
                    String.format("向量维度不匹配: 期望 %d 维，实际为 %d 维", expectedDim, actual));
        }
    }
}
