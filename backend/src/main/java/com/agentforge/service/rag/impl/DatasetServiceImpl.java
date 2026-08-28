package com.agentforge.service.rag.impl;

import cn.hutool.core.util.StrUtil;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.Dataset;
import com.agentforge.entity.Document;
import com.agentforge.entity.DocumentChunk;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.DatasetMapper;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.mapper.DocumentMapper;
import com.agentforge.service.rag.DatasetService;
import com.agentforge.vo.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库数据集服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetServiceImpl extends ServiceImpl<DatasetMapper, Dataset> implements DatasetService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;

    @Override
    public PageResult<Dataset> listDatasets(String keyword, long pageNum, long pageSize) {
        Long tenantId = TenantContextHolder.getTenantId();
        LambdaQueryWrapper<Dataset> wrapper = new LambdaQueryWrapper<Dataset>()
                .eq(Dataset::getTenantId, tenantId)
                .eq(Dataset::getStatus, 1)
                .like(StrUtil.isNotBlank(keyword), Dataset::getName, keyword)
                .orderByDesc(Dataset::getId);

        Page<Dataset> page = page(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }

    @Override
    public Dataset createDataset(Dataset dataset) {
        dataset.setTenantId(TenantContextHolder.getTenantId());
        if (dataset.getEmbeddingDim() == null || dataset.getEmbeddingDim() <= 0) {
            dataset.setEmbeddingDim(1536);
        }
        if (dataset.getChunkSize() == null || dataset.getChunkSize() <= 0) {
            dataset.setChunkSize(500);
        }
        if (dataset.getChunkOverlap() == null) {
            dataset.setChunkOverlap(50);
        }
        dataset.setStatus(1);
        save(dataset);
        return dataset;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataset(Long datasetId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Dataset dataset = getById(datasetId);
        if (dataset == null || !dataset.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.DATASET_NOT_FOUND);
        }

        // 级联清理分块与文档
        chunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getTenantId, tenantId)
                .eq(DocumentChunk::getDatasetId, datasetId));

        documentMapper.delete(new LambdaQueryWrapper<Document>()
                .eq(Document::getTenantId, tenantId)
                .eq(Document::getDatasetId, datasetId));

        removeById(datasetId);
        log.info("成功级联删除知识库数据集: tenantId={}, datasetId={}", tenantId, datasetId);
    }
}
