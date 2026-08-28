package com.agentforge.service.rag.version;

import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.Document;
import com.agentforge.entity.DocumentChunk;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.mapper.DocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 知识库文档版本原子热替换与旧向量即时清理服务 (Document Version Hot-Swap)
 * 解决用户上传新版文件覆盖旧版时，旧分块残留导致新旧制度矛盾、问答幻觉问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVersionHotSwapService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 执行文档版本原子热替换 (Purge & Replace)
     * 1. 事务级硬删除旧文档所有历史分块
     * 2. 即时驱逐租户级 Redis 语义缓存，防止旧答案串味
     * 3. 重置文档状态与版本元数据
     */
    @Transactional(rollbackFor = Exception.class)
    public int purgeOldVersionAndPrepare(Long documentId) {
        Long tenantId = TenantContextHolder.getTenantId();

        Document doc = documentMapper.selectById(documentId);
        if (doc == null || !doc.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("文档不存在或跨租户越权: docId=" + documentId);
        }

        // 1. 物理删除所有历史旧分块
        int deletedChunks = chunkMapper.delete(
                new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getTenantId, tenantId)
                        .eq(DocumentChunk::getDocumentId, documentId)
        );

        // 2. 清理 Redis 语义缓存 (按租户广播淘汰)
        String cachePattern = "agentforge:cache:" + tenantId + ":*";
        try {
            Set<String> keys = redisTemplate.keys(cachePattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("🧹 已淘汰租户关联 Redis 语义缓存: tenantId={}, keysCount={}", tenantId, keys.size());
            }
        } catch (Exception e) {
            log.warn("Redis 缓存清理异常 (不影响主流程): error={}", e.getMessage());
        }

        // 3. 更新文档状态为重新分块中
        doc.setParseStatus("PARSING");
        doc.setChunkCount(0);
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

        log.info("🔄 文档版本原子换血与旧向量清理完成: docId={}, 已清理旧分块数={}", documentId, deletedChunks);
        return deletedChunks;
    }
}
