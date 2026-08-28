package com.agentforge.mapper;

import com.agentforge.entity.Document;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper 接口
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}
