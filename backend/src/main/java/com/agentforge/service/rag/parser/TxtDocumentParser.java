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
 * 纯文本 (TXT) 格式解析器
 */
@Slf4j
@Component
public class TxtDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "TXT".equalsIgnoreCase(fileType) || "TEXT".equalsIgnoreCase(fileType) || "JSON".equalsIgnoreCase(fileType);
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder fullText = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                fullText.append(line).append("\n");
            }

            String contentStr = fullText.toString().trim();
            List<ParsedDocument.PageSection> sections = new ArrayList<>();
            sections.add(ParsedDocument.PageSection.builder()
                    .pageNumber(1)
                    .sectionTitle("正文")
                    .content(contentStr)
                    .build());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileName", fileName);
            metadata.put("fileType", "TXT");

            return ParsedDocument.builder()
                    .fullText(contentStr)
                    .charCount(contentStr.length())
                    .sections(sections)
                    .metadata(metadata)
                    .build();
        } catch (Exception e) {
            log.error("TXT 文档解析失败: fileName={}", fileName, e);
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "TXT 文档解析失败: " + e.getMessage());
        }
    }
}
