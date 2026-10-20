package com.agentforge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 混合检索与重排序参数配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "agentforge.rag.search")
public class SearchProperties {

    /**
     * 正常模式下稠密向量检索权重 (默认 0.5)
     */
    private double denseWeight = 0.5;

    /**
     * 正常模式下稀疏全文检索权重 (默认 0.5)
     */
    private double sparseWeight = 0.5;

    /**
     * 降级模式下稠密向量检索权重 (默认 0.0，避免确定性哈希伪向量底噪污染真实排名)
     */
    private double denseDegradedWeight = 0.0;

    /**
     * 降级模式下稀疏全文检索权重 (默认 1.0)
     */
    private double sparseDegradedWeight = 1.0;

    /**
     * RRF 倒数排名平滑常数 K (默认 60)
     */
    private double rrfK = 60.0;

    /**
     * 最终打分最低截断阈值 (默认 0.01)
     */
    private double scoreThreshold = 0.01;

    /**
     * 初筛候选池扩增倍数 (topK * multiplier 召回候选，默认 2)
     */
    private int candidateMultiplier = 2;

    /**
     * 初筛候选池下限（与 topK * multiplier 取较大值，默认 60）
     */
    private int candidateFloor = 60;

    /**
     * 是否启用 Cross-Encoder 二次重排序 (默认 true)
     */
    private boolean rerankEnabled = true;

    /**
     * 重排模型交叉注意力得分在最终混合得分中的权重 (默认 0.5)
     */
    private double rerankModelWeight = 0.5;

    /**
     * RRF 分归一化系数（RRF 单路满分约 1/(K+1)，默认 30.0）
     */
    private double rrfNormalizeFactor = 30.0;
}
