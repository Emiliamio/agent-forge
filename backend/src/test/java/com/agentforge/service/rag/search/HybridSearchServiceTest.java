package com.agentforge.service.rag.search;

import com.agentforge.config.SearchProperties;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.service.rag.embedding.EmbeddingService;
import com.agentforge.vo.ChunkSearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HybridSearchService 混合检索服务及降级向量隔离测试")
class HybridSearchServiceTest {

    @Mock
    private DocumentChunkMapper chunkMapper;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private RerankerService rerankerService;

    private SearchProperties searchProperties;
    private RrfFusionEngine rrfFusionEngine;
    private HybridSearchService hybridSearchService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        searchProperties = new SearchProperties();
        searchProperties.setDenseWeight(0.5);
        searchProperties.setSparseWeight(0.5);
        searchProperties.setDenseDegradedWeight(0.0);
        searchProperties.setSparseDegradedWeight(1.0);
        searchProperties.setScoreThreshold(0.01);
        searchProperties.setCandidateMultiplier(2);

        rrfFusionEngine = new RrfFusionEngine(searchProperties);
        hybridSearchService = new HybridSearchService(
                chunkMapper,
                embeddingService,
                rrfFusionEngine,
                rerankerService,
                searchProperties
        );
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("降级模式验证：degraded=true 时，严禁调用 pgvector 稠密向量查询")
    void testDegradedModeSkipsDenseRetrieval() {
        // Arrange
        when(embeddingService.degraded()).thenReturn(true);

        ChunkSearchResult keywordChunk = ChunkSearchResult.builder()
                .id(1L)
                .content("降级模式下的真实全文匹配切片")
                .score(0.85)
                .build();
        when(chunkMapper.searchByKeyword(anyLong(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of(keywordChunk));
        when(rerankerService.rerank(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(keywordChunk));

        // Act
        List<ChunkSearchResult> results = hybridSearchService.hybridSearch(
                List.of(1L),
                "测试查询",
                5,
                0.0
        );

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getId());

        // 核心门禁断言：pgvector 稠密向量查询严禁发生！
        verify(chunkMapper, never()).searchByVector(anyLong(), anyList(), anyString(), anyDouble(), anyInt());
        verify(embeddingService, never()).embed(anyString());

        // 稀疏全文检索正常驱动
        verify(chunkMapper, times(1)).searchByKeyword(eq(1L), eq(List.of(1L)), anyString(), anyInt());
    }

    @Test
    @DisplayName("正常模式验证：degraded=false 时，双路均正常调用并融合")
    void testNormalModePerformsBothRetrievals() {
        // Arrange
        when(embeddingService.degraded()).thenReturn(false);
        when(embeddingService.embed(anyString())).thenReturn(new float[1536]);

        ChunkSearchResult vChunk = ChunkSearchResult.builder().id(10L).content("向量命中").build();
        ChunkSearchResult kChunk = ChunkSearchResult.builder().id(20L).content("关键词命中").build();

        when(chunkMapper.searchByVector(anyLong(), anyList(), anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of(vChunk));
        when(chunkMapper.searchByKeyword(anyLong(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of(kChunk));
        when(rerankerService.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(invocation -> {
                    ChunkSearchResult r1 = ChunkSearchResult.builder().id(10L).content("向量命中").score(0.85).build();
                    ChunkSearchResult r2 = ChunkSearchResult.builder().id(20L).content("关键词命中").score(0.75).build();
                    return List.of(r1, r2);
                });

        // Act
        List<ChunkSearchResult> results = hybridSearchService.hybridSearch(
                List.of(1L),
                "正常混合查询",
                5,
                0.0
        );

        // Assert
        assertEquals(2, results.size());
        verify(embeddingService, times(1)).embed("正常混合查询");
        verify(chunkMapper, times(1)).searchByVector(eq(1L), eq(List.of(1L)), anyString(), eq(0.0), anyInt());
        verify(chunkMapper, times(1)).searchByKeyword(eq(1L), eq(List.of(1L)), anyString(), anyInt());
    }

    @Test
    @DisplayName("阈值过滤测试：低于有效阈值的切片被过滤")
    void testScoreThresholdFiltering() {
        when(embeddingService.degraded()).thenReturn(true);

        ChunkSearchResult c = ChunkSearchResult.builder().id(1L).content("测试").build();
        when(chunkMapper.searchByKeyword(anyLong(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of(c));
        when(rerankerService.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(invocation -> {
                    ChunkSearchResult high = ChunkSearchResult.builder().id(1L).content("高分").score(0.7).build();
                    ChunkSearchResult low = ChunkSearchResult.builder().id(2L).content("低分").score(0.005).build();
                    return List.of(high, low);
                });

        // 默认 scoreThreshold=0.01，低分切片 (0.005) 应被截断
        List<ChunkSearchResult> results = hybridSearchService.hybridSearch(
                List.of(1L),
                "查询",
                5,
                0.0
        );

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getId());
    }

    @Test
    @DisplayName("空入参边界防护")
    void testEmptyParameters() {
        assertTrue(hybridSearchService.hybridSearch(null, "query", 5, 0.0).isEmpty());
        assertTrue(hybridSearchService.hybridSearch(List.of(), "query", 5, 0.0).isEmpty());
        assertTrue(hybridSearchService.hybridSearch(List.of(1L), "", 5, 0.0).isEmpty());
        assertTrue(hybridSearchService.hybridSearch(List.of(1L), "   ", 5, 0.0).isEmpty());
    }
}
