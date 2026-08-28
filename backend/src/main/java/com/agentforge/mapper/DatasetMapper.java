package com.agentforge.mapper;

import com.agentforge.entity.Dataset;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库数据集 Mapper 接口
 */
@Mapper
public interface DatasetMapper extends BaseMapper<Dataset> {
}
