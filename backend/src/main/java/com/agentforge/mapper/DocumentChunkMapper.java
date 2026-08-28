package com.agentforge.mapper;

import com.agentforge.entity.DocumentChunk;
import com.agentforge.vo.ChunkSearchResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档切片与 pgvector 向量检索 Mapper 接口
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /**
     * 稠密向量余弦相似度检索 (基于 pgvector HNSW 索引)
     */
    @Select("""
            SELECT id, tenant_id, dataset_id, document_id, chunk_index, content, metadata, token_count, created_at,
                   (1 - (embedding <=> #{queryVector}::vector)) AS score
            FROM document_chunk
            WHERE tenant_id = #{tenantId}
              AND dataset_id IN
              <foreach collection="datasetIds" item="dId" open="(" separator="," close=")">
                  #{dId}
              </foreach>
              AND (1 - (embedding <=> #{queryVector}::vector)) >= #{minScore}
            ORDER BY embedding <=> #{queryVector}::vector ASC
            LIMIT #{topK}
            """)
    List<ChunkSearchResult> searchByVector(
            @Param("tenantId") Long tenantId,
            @Param("datasetIds") List<Long> datasetIds,
            @Param("queryVector") String queryVector,
            @Param("minScore") double minScore,
            @Param("topK") int topK
    );

    /**
     * 全文关键词检索 (基于 PostgreSQL BM25 / tsvector GIN 索引)
     */
    @Select("""
            SELECT id, tenant_id, dataset_id, document_id, chunk_index, content, metadata, token_count, created_at,
                   ts_rank(tsv_content, plainto_tsquery('simple', #{keyword})) AS score
            FROM document_chunk
            WHERE tenant_id = #{tenantId}
              AND dataset_id IN
              <foreach collection="datasetIds" item="dId" open="(" separator="," close=")">
                  #{dId}
              </foreach>
              AND tsv_content @@ plainto_tsquery('simple', #{keyword})
            ORDER BY score DESC
            LIMIT #{topK}
            """)
    List<ChunkSearchResult> searchByKeyword(
            @Param("tenantId") Long tenantId,
            @Param("datasetIds") List<Long> datasetIds,
            @Param("keyword") String keyword,
            @Param("topK") int topK
    );
}
