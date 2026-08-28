package com.agentforge.service.rag.chunker;

import com.agentforge.service.rag.parser.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工业级语义分块引擎 (Semantic Chunking & Sliding Window Overlap)
 * 包含句子边界分割、滑动窗口重叠保护及超长无标点极端单句二级硬切分保护
 */
@Slf4j
@Component
public class ChunkerEngine {

    /**
     * 句子自然边界分割正则 (支持中英文标点与换行)
     */
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^。！？!?\\n]+[。！？!?\\n]*");

    /**
     * 对解析后的文档执行智能语义分块
     *
     * @param document     解析后的文档
     * @param chunkSize    单分块目标字符数 (如 500)
     * @param chunkOverlap 重叠窗口字符数 (如 50)
     * @param fileName     文件名
     * @return 分块结果列表
     */
    public List<ChunkSegment> split(ParsedDocument document, int chunkSize, int chunkOverlap, String fileName) {
        if (chunkSize <= 0) {
            chunkSize = 500;
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            chunkOverlap = chunkSize / 10;
        }

        List<ChunkSegment> result = new ArrayList<>();
        int chunkCounter = 1;

        List<ParsedDocument.PageSection> sections = document.getSections();
        if (sections == null || sections.isEmpty()) {
            sections = List.of(ParsedDocument.PageSection.builder()
                    .pageNumber(1)
                    .sectionTitle("全文")
                    .content(document.getFullText())
                    .build());
        }

        for (ParsedDocument.PageSection section : sections) {
            String text = section.getContent();
            if (text == null || text.trim().isEmpty()) {
                continue;
            }

            // 1. 若章节内容小于等于 chunkSize，直接作为一个独立切片
            if (text.length() <= chunkSize) {
                Map<String, Object> meta = createMetadata(fileName, section.getPageNumber(), section.getSectionTitle(), 1, 1);
                result.add(ChunkSegment.builder()
                        .chunkIndex(chunkCounter++)
                        .content(text.trim())
                        .tokenCount(estimateTokenCount(text))
                        .metadata(meta)
                        .build());
                continue;
            }

            // 2. 句子切分 (含超长无标点单句二级保护)
            List<String> sentences = splitIntoSentencesWithOversizeProtection(text, chunkSize);
            StringBuilder currentChunk = new StringBuilder();
            int subChunkIdx = 1;

            for (String sentence : sentences) {
                if (currentChunk.length() + sentence.length() > chunkSize && currentChunk.length() > 0) {
                    // 生成一个切片
                    String chunkContent = currentChunk.toString().trim();
                    Map<String, Object> meta = createMetadata(fileName, section.getPageNumber(), section.getSectionTitle(), subChunkIdx++, -1);
                    result.add(ChunkSegment.builder()
                            .chunkIndex(chunkCounter++)
                            .content(chunkContent)
                            .tokenCount(estimateTokenCount(chunkContent))
                            .metadata(meta)
                            .build());

                    // 保留 Overlap 重叠部分 (截取尾部 chunkOverlap 长度的内容作为下一块前缀)
                    String overlapStr = "";
                    if (currentChunk.length() > chunkOverlap) {
                        overlapStr = currentChunk.substring(currentChunk.length() - chunkOverlap);
                    } else {
                        overlapStr = currentChunk.toString();
                    }

                    currentChunk.setLength(0);
                    currentChunk.append(overlapStr);
                }

                currentChunk.append(sentence);
            }

            // 处理尾部剩余内容
            if (currentChunk.length() > 0) {
                String chunkContent = currentChunk.toString().trim();
                if (!chunkContent.isEmpty()) {
                    Map<String, Object> meta = createMetadata(fileName, section.getPageNumber(), section.getSectionTitle(), subChunkIdx, -1);
                    result.add(ChunkSegment.builder()
                            .chunkIndex(chunkCounter++)
                            .content(chunkContent)
                            .tokenCount(estimateTokenCount(chunkContent))
                            .metadata(meta)
                            .build());
                }
            }
        }

        return result;
    }

    /**
     * 按自然标点分割句子，并对超长无标点单句执行二级定长硬切分保护
     */
    private List<String> splitIntoSentencesWithOversizeProtection(String text, int maxSentenceLength) {
        List<String> rawSentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group();
            if (!sentence.isBlank()) {
                rawSentences.add(sentence);
            }
        }
        if (rawSentences.isEmpty()) {
            rawSentences.add(text);
        }

        // 二级定长硬切分校验
        List<String> finalSentences = new ArrayList<>();
        for (String s : rawSentences) {
            if (s.length() <= maxSentenceLength) {
                finalSentences.add(s);
            } else {
                // 超长单句二级硬切分
                int start = 0;
                while (start < s.length()) {
                    int end = Math.min(start + maxSentenceLength, s.length());
                    finalSentences.add(s.substring(start, end));
                    start = end;
                }
            }
        }
        return finalSentences;
    }

    /**
     * 估算 Token 消耗 (中文 1 字 ≈ 1 Token，英文 1 词 ≈ 1.3 Token)
     */
    public static int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseCount = 0;
        int otherCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            } else if (!Character.isWhitespace(c)) {
                otherCount++;
            }
        }
        return (int) (chineseCount + (otherCount * 0.5));
    }

    private Map<String, Object> createMetadata(String fileName, int pageNumber, String sectionTitle, int subIndex, int totalSub) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", fileName);
        meta.put("page", pageNumber);
        meta.put("title", sectionTitle != null ? sectionTitle : "正文");
        meta.put("subIndex", subIndex);
        return meta;
    }
}
