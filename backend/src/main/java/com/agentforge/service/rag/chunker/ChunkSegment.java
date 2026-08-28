package com.agentforge.service.rag.chunker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 语义切片单元模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkSegment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 切片索引序号 (从 1 开始)
     */
    private int chunkIndex;

    /**
     * 切片文本正文
     */
    private String content;

    /**
     * 估算 Token 数量 (或字符数)
     */
    private int tokenCount;

    /**
     * 结构化元数据 (页码、章节标题、原始文件名等)
     */
    private Map<String, Object> metadata;
}
