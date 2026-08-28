package com.agentforge.service.rag.chunk;

import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.DocumentChunk;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.service.rag.VectorUtils;
import com.agentforge.service.rag.embedding.EmbeddingService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 知识库切片单条精细化热编辑与一键禁用服务 (Chunk Hot-Editing & Toggle Service)
 * 支持管理员在后台直接修改某条切片、实时重算向量并淘汰语义缓存，无需重新切分 500 页大文档
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkManagementService {

    private final DocumentChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 在线热编辑单条切片文本 (自动重算向量并驱逐缓存)
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentChunk updateChunkContent(Long chunkId, String newContent) {
        Long tenantId = TenantContextHolder.getTenantId();

        DocumentChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null || !chunk.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("切片不存在或跨租户越权: chunkId=" + chunkId);
        }

        // 1. 重新计算 1536 维向量
        float[] newVector = embeddingService.embed(newContent);

        // 2. 更新切片数据
        chunk.setContent(newContent);
        chunk.setEmbedding(VectorUtils.toString(newVector));
        chunk.setTokenCount(newContent.length());
        chunkMapper.updateById(chunk);

        // 3. 驱逐该租户的 Redis 语义缓存，防止旧答案缓存残留
        evictTenantCache(tenantId);

        log.info("✏️ 知识库切片在线热编辑成功: chunkId={}, docId={}, newLength={}",
                chunkId, chunk.getDocumentId(), newContent.length());

        return chunk;
    }

    /**
     * 一键启用/禁用切片 (在 metadata 中设置 isActive 属性，禁用后检索时自动排除)
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleChunkStatus(Long chunkId, boolean isActive) {
        Long tenantId = TenantContextHolder.getTenantId();

        DocumentChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null || !chunk.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("切片不存在或跨租户越权: chunkId=" + chunkId);
        }

        JSONObject meta = chunk.getMetadata() != null ? JSON.parseObject(chunk.getMetadata()) : new JSONObject();
        meta.put("isActive", isActive);
        chunk.setMetadata(meta.toJSONString());
        chunkMapper.updateById(chunk);

        evictTenantCache(tenantId);
        log.info("🔘 知识库切片状态切换: chunkId={}, isActive={}", chunkId, isActive);
    }

    /**
     * 分页查询指定文档的分块列表
     */
    public Page<DocumentChunk> listChunks(Long documentId, int pageNum, int pageSize) {
        Long tenantId = TenantContextHolder.getTenantId();
        return chunkMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getTenantId, tenantId)
                        .eq(DocumentChunk::getDocumentId, documentId)
                        .orderByAsc(DocumentChunk::getChunkIndex)
        );
    }

    private void evictTenantCache(Long tenantId) {
        try {
            String pattern = "agentforge:cache:" + tenantId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }
}
