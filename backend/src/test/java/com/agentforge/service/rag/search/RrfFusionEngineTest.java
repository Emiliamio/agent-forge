package com.agentforge.service.rag.search;

import com.agentforge.config.SearchProperties;
import com.agentforge.vo.ChunkSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RrfFusionEngine RRF 倒数排名融合引擎单元测试")
class RrfFusionEngineTest {

    @Test
    @DisplayName("常规模式：Dense=0.5 与 Sparse=0.5 平衡融合")
    void testFuseConventionalWeights() {
        SearchProperties properties = new SearchProperties();
        properties.setDenseWeight(0.5);
        properties.setSparseWeight(0.5);
        properties.setRrfK(60.0);

        RrfFusionEngine engine = new RrfFusionEngine(properties);

        ChunkSearchResult c1 = ChunkSearchResult.builder().id(101L).content("内容A").build();
        ChunkSearchResult c2 = ChunkSearchResult.builder().id(102L).content("内容B").build();
        ChunkSearchResult c3 = ChunkSearchResult.builder().id(103L).content("内容C").build();

        // 向量路: [101 (rank 0), 102 (rank 1)]
        List<ChunkSearchResult> vectorResults = List.of(c1, c2);
        // 全文路: [102 (rank 0), 103 (rank 1)]
        List<ChunkSearchResult> keywordResults = List.of(c2, c3);

        List<ChunkSearchResult> fused = engine.fuse(vectorResults, keywordResults, 5, 0.5, 0.5, 60.0);

        assertNotNull(fused);
        assertEquals(3, fused.size());

        // c2 出现在双路中 (vector rank 1, keyword rank 0) -> RRF score = 0.5/(60+2) + 0.5/(60+1)
        ChunkSearchResult top = fused.get(0);
        assertEquals(102L, top.getId());
        assertEquals("HYBRID", top.getRecallType());
        assertTrue(top.getScore() > 0.0);

        // c1 只在 vector 路 (rank 0) -> 0.5/(60+1)
        // c3 只在 keyword 路 (rank 1) -> 0.5/(60+2)
        assertEquals(101L, fused.get(1).getId());
        assertEquals(103L, fused.get(2).getId());
    }

    @Test
    @DisplayName("降级模式：Dense=0.0 与 Sparse=1.0，稠密向量不污染排序")
    void testFuseDegradedWeights() {
        SearchProperties properties = new SearchProperties();
        properties.setDenseDegradedWeight(0.0);
        properties.setSparseDegradedWeight(1.0);
        properties.setRrfK(60.0);

        RrfFusionEngine engine = new RrfFusionEngine(properties);

        ChunkSearchResult vectorOnly = ChunkSearchResult.builder().id(999L).content("伪向量干扰块").build();
        ChunkSearchResult keywordChunk = ChunkSearchResult.builder().id(201L).content("真实关键词命中块").build();

        List<ChunkSearchResult> vectorResults = List.of(vectorOnly);
        List<ChunkSearchResult> keywordResults = List.of(keywordChunk);

        List<ChunkSearchResult> fused = engine.fuse(vectorResults, keywordResults, 5, 0.0, 1.0, 60.0);

        assertNotNull(fused);
        assertEquals(1, fused.size());
        assertEquals(201L, fused.get(0).getId());
        assertEquals("KEYWORD", fused.get(0).getRecallType());
        assertEquals(1.0 / 61.0, fused.get(0).getScore(), 1e-6);
    }

    @Test
    @DisplayName("属性默认值驱动的单参数与双参数重载融合")
    void testOverloadedFuseMethods() {
        SearchProperties properties = new SearchProperties();
        properties.setDenseWeight(0.6);
        properties.setSparseWeight(0.4);
        properties.setRrfK(50.0);

        RrfFusionEngine engine = new RrfFusionEngine(properties);

        ChunkSearchResult c1 = ChunkSearchResult.builder().id(1L).content("A").build();
        List<ChunkSearchResult> results = List.of(c1);

        // 使用属性默认
        List<ChunkSearchResult> fused1 = engine.fuse(results, List.of(), 5);
        assertEquals(1, fused1.size());
        assertEquals(0.6 / 51.0, fused1.get(0).getScore(), 1e-6);

        // 显式传入权重
        List<ChunkSearchResult> fused2 = engine.fuse(results, List.of(), 5, 0.8, 0.2);
        assertEquals(1, fused2.size());
        assertEquals(0.8 / 51.0, fused2.get(0).getScore(), 1e-6);
    }

    @Test
    @DisplayName("空列表边界保护")
    void testEmptyInputs() {
        RrfFusionEngine engine = new RrfFusionEngine(new SearchProperties());
        assertTrue(engine.fuse(null, null, 5).isEmpty());
        assertTrue(engine.fuse(List.of(), List.of(), 5).isEmpty());
    }
}
