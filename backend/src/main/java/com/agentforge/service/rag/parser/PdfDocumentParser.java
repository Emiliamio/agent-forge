package com.agentforge.service.rag.parser;

import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 格式文档解析器 (基于 Apache PDFBox 3.0)
 * 支持逐页解析、提取页码与全文字符统计
 */
@Slf4j
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "PDF".equalsIgnoreCase(fileType);
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                int totalPages = document.getNumberOfPages();
                List<ParsedDocument.PageSection> sections = new ArrayList<>(totalPages);
                StringBuilder fullTextBuilder = new StringBuilder();

                PDFTextStripper stripper = new PDFTextStripper();

                for (int page = 1; page <= totalPages; page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String pageText = stripper.getText(document).trim();

                    if (!pageText.isEmpty()) {
                        sections.add(ParsedDocument.PageSection.builder()
                                .pageNumber(page)
                                .sectionTitle("第 " + page + " 页")
                                .content(pageText)
                                .build());

                        fullTextBuilder.append(pageText).append("\n\n");
                    }
                }

                String fullText = fullTextBuilder.toString().trim();

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("fileName", fileName);
                metadata.put("fileType", "PDF");
                metadata.put("totalPages", totalPages);

                return ParsedDocument.builder()
                        .fullText(fullText)
                        .charCount(fullText.length())
                        .sections(sections)
                        .metadata(metadata)
                        .build();
            }
        } catch (Exception e) {
            log.error("PDF 文档解析失败: fileName={}", fileName, e);
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "PDF 文档解析失败: " + e.getMessage());
        }
    }
}
