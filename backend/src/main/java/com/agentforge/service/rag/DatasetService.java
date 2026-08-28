package com.agentforge.service.rag;

import com.agentforge.entity.Dataset;
import com.agentforge.vo.PageResult;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 知识库数据集服务接口
 */
public interface DatasetService extends IService<Dataset> {

    /**
     * 分页查询当前租户的数据集列表
     */
    PageResult<Dataset> listDatasets(String keyword, long pageNum, long pageSize);

    /**
     * 创建知识库数据集
     */
    Dataset createDataset(Dataset dataset);

    /**
     * 删除知识库 (级联清理文档与切片)
     */
    void deleteDataset(Long datasetId);
}
