package com.agentforge.service.rag.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 文档解析结果载体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文档全文纯文本
     */
    private String fullText;

    /**
     * 总字数统计
     */
    private int charCount;

    /**
     * 页级/章节结构化内容列表 (用于精确定位页码与章节)
     */
    private List<PageSection> sections;

    /**
     * 文档元数据 (如作者、标题、创建时间等)
     */
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageSection implements Serializable {
        private static final long serialVersionUID = 1L;

        private int pageNumber;
        private String sectionTitle;
        private String content;
    }
}
