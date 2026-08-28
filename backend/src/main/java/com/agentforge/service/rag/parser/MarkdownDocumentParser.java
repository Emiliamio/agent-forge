package com.agentforge.service.rag.parser;

import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Markdown (.md) 格式文档解析器
 * 支持代码块防护过滤与按标题层级 (#, ##, ###) 进行章节语义结构化切分
 */
@Slf4j
@Component
public class MarkdownDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "MD".equalsIgnoreCase(fileType) || "MARKDOWN".equalsIgnoreCase(fileType);
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder fullText = new StringBuilder();
            List<ParsedDocument.PageSection> sections = new ArrayList<>();

            String line;
            String currentTitle = "引言 / 概述";
            StringBuilder currentSectionContent = new StringBuilder();
            boolean inCodeBlock = false; // 代码块防护状态机

            while ((line = reader.readLine()) != null) {
                fullText.append(line).append("\n");

                String trimmed = line.trim();

                // 遇到 ``` 代码块标记，翻转状态
                if (trimmed.startsWith("```")) {
                    inCodeBlock = !inCodeBlock;
                    currentSectionContent.append(line).append("\n");
                    continue;
                }

                // 仅在非代码块内部时，才识别 Markdown 标题 (# 一级标题, ## 二级标题)
                if (!inCodeBlock && trimmed.startsWith("#")) {
                    if (currentSectionContent.length() > 0) {
                        sections.add(ParsedDocument.PageSection.builder()
                                .pageNumber(1)
                                .sectionTitle(currentTitle)
                                .content(currentSectionContent.toString().trim())
                                .build());
                        currentSectionContent.setLength(0);
                    }
                    currentTitle = trimmed.replaceAll("^#+\\s*", "");
                } else {
                    currentSectionContent.append(line).append("\n");
                }
            }

            if (currentSectionContent.length() > 0) {
                sections.add(ParsedDocument.PageSection.builder()
                        .pageNumber(1)
                        .sectionTitle(currentTitle)
                        .content(currentSectionContent.toString().trim())
                        .build());
            }

            String contentStr = fullText.toString().trim();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileName", fileName);
            metadata.put("fileType", "MD");

            return ParsedDocument.builder()
                    .fullText(contentStr)
                    .charCount(contentStr.length())
                    .sections(sections)
                    .metadata(metadata)
                    .build();
        } catch (Exception e) {
            log.error("Markdown 解析失败: fileName={}", fileName, e);
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "Markdown 解析失败: " + e.getMessage());
        }
    }
}
