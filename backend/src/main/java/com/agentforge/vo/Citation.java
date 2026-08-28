package com.agentforge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * RAG 可溯源引用模型 (Citations)
 * 供大模型提示词注入与前端高亮定位使用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RAG 引用溯源模型")
public class Citation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "引用序号", example = "1")
    private int index;

    @Schema(description = "知识库切片 ID")
    private Long chunkId;

    @Schema(description = "所属文档 ID")
    private Long documentId;

    @Schema(description = "文档名称", example = "企业研发规范.md")
    private String documentName;

    @Schema(description = "所在页码", example = "3")
    private Integer pageNumber;

    @Schema(description = "所在章节标题", example = "三、代码安全规范")
    private String sectionTitle;

    @Schema(description = "引用的文本片段")
    private String snippet;

    @Schema(description = "命中的高亮关键词列表 (供前端渲染高亮背景色)", example = "[\"租户隔离\", \"pgvector\"]")
    private List<String> highlightKeywords;

    @Schema(description = "相关度评分", example = "0.92")
    private Double score;

    /**
     * 将引用列表格式化为提供给 LLM System Prompt 的上下文注入文本
     */
    public static String formatPromptContext(List<Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return "【暂无相关参考文档】";
        }
        StringBuilder sb = new StringBuilder();
        for (Citation c : citations) {
            sb.append(String.format("------\n[引用 #%d] 来源文档：《%s》（%s）\n内容：%s\n",
                    c.getIndex(),
                    c.getDocumentName() != null ? c.getDocumentName() : "未知文档",
                    c.getSectionTitle() != null ? c.getSectionTitle() : "正文",
                    c.getSnippet()
            ));
        }
        sb.append("------\n");
        return sb.toString();
    }
}
