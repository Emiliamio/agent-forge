package com.agentforge.service.rag.parser;

import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Word (.docx) 格式文档解析器 (基于 Apache POI)
 * 支持段落与表格文本提取
 */
@Slf4j
@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "DOCX".equalsIgnoreCase(fileType) || "DOC".equalsIgnoreCase(fileType);
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            StringBuilder fullText = new StringBuilder();
            List<ParsedDocument.PageSection> sections = new ArrayList<>();
            int sectionIdx = 1;

            // 1. 提取段落内容
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                String text = paragraph.getText().trim();
                if (!text.isEmpty()) {
                    fullText.append(text).append("\n");
                    sections.add(ParsedDocument.PageSection.builder()
                            .pageNumber(1)
                            .sectionTitle("段落 " + (sectionIdx++))
                            .content(text)
                            .build());
                }
            }

            // 2. 提取表格内容
            for (XWPFTable table : doc.getTables()) {
                StringBuilder tableContent = new StringBuilder();
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cellTexts = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        cellTexts.add(cell.getText().trim());
                    }
                    tableContent.append(String.join(" | ", cellTexts)).append("\n");
                }
                if (tableContent.length() > 0) {
                    fullText.append("\n").append(tableContent);
                    sections.add(ParsedDocument.PageSection.builder()
                            .pageNumber(1)
                            .sectionTitle("表格 " + (sectionIdx++))
                            .content(tableContent.toString())
                            .build());
                }
            }

            String contentStr = fullText.toString().trim();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileName", fileName);
            metadata.put("fileType", "DOCX");

            return ParsedDocument.builder()
                    .fullText(contentStr)
                    .charCount(contentStr.length())
                    .sections(sections)
                    .metadata(metadata)
                    .build();
        } catch (Exception e) {
            log.error("DOCX 文档解析失败: fileName={}", fileName, e);
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "DOCX 文档解析失败: " + e.getMessage());
        }
    }
}
