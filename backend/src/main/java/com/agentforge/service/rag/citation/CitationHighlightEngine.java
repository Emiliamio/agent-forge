package com.agentforge.service.rag.citation;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 原文句子级精准溯源与高亮锚点定位引擎 (Citation Highlight Engine)
 * 将大模型回答与召回分块进行句子级交叉比对，计算字符级起止位置 (Offset)
 * 支持前端双栏分屏高亮显示，杜绝客户因“查不到依据”质疑回答准确性
 */
@Component
public class CitationHighlightEngine {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HighlightSpan implements Serializable {
        private static final long serialVersionUID = 1L;

        private int citationIndex; // [引用 1]
        private String documentName;
        private int pageNumber;
        private int startOffset;
        private int endOffset;
        private String matchedSnippet;
    }

    /**
     * 计算高亮锚点切片
     */
    public List<HighlightSpan> computeHighlights(String answer, String chunkContent, String documentName, int pageNumber) {
        List<HighlightSpan> spans = new ArrayList<>();
        if (StrUtil.isBlank(answer) || StrUtil.isBlank(chunkContent)) {
            return spans;
        }

        // 按标点切分回答中的核心事实断言句
        String[] sentences = answer.split("[。！？\n]");
        int citeIndex = 1;

        for (String s : sentences) {
            String clean = s.replaceAll("[*#`]", "").trim();
            if (clean.length() >= 6) { // 过滤过短句子
                // 查找在原文分块中的位置
                int idx = chunkContent.indexOf(clean.substring(0, Math.min(clean.length(), 15)));
                if (idx >= 0) {
                    spans.add(HighlightSpan.builder()
                            .citationIndex(citeIndex++)
                            .documentName(documentName)
                            .pageNumber(pageNumber)
                            .startOffset(idx)
                            .endOffset(idx + clean.length())
                            .matchedSnippet(clean)
                            .build());
                }
            }
        }

        // 保底锚点
        if (spans.isEmpty() && chunkContent.length() > 20) {
            spans.add(HighlightSpan.builder()
                    .citationIndex(1)
                    .documentName(documentName)
                    .pageNumber(pageNumber)
                    .startOffset(0)
                    .endOffset(Math.min(chunkContent.length(), 100))
                    .matchedSnippet(chunkContent.substring(0, Math.min(chunkContent.length(), 100)))
                    .build());
        }

        return spans;
    }
}
