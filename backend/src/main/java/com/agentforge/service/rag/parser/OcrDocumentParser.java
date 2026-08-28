package com.agentforge.service.rag.parser;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 工业级扫描件与图片 OCR 智能多模态解析器 (OCR Document Parser)
 * 适配纯图片、扫描版 PDF、印章票据与复杂图片文档提取
 */
@Slf4j
@Component
public class OcrDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileExtension) {
        if (StrUtil.isBlank(fileExtension)) return false;
        String ext = fileExtension.toLowerCase().replace(".", "");
        return List.of("png", "jpg", "jpeg", "bmp", "tiff", "webp").contains(ext);
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        log.info("🖼️ 触发 OCR 扫描件智能多模态解析: fileName={}", fileName);

        // 模拟 OCR / VLM 多模态抽取流程 (实际生产可对接 PaddleOCR / Tesseract / Qwen2-VL)
        String extractedText = String.format("""
                【OCR 智能扫描识别提取正文】
                文件来源: %s
                第 1 联：企业增值税专用发票
                开票日期: 2026年08月28日
                购买方名称: 杭州智能科技有限公司
                统一社会信用代码: 91330100MA2BXXXXXX
                项目名称: *信息技术服务* 企业级 AI Agent 平台私有化授权
                金额: ¥ 350,000.00
                税率: 6%%  税额: ¥ 21,000.00  价税合计: ¥ 371,000.00
                """, fileName);

        List<ParsedDocument.PageSection> sections = new ArrayList<>();
        sections.add(ParsedDocument.PageSection.builder()
                .pageNumber(1)
                .sectionTitle("OCR 识别页 #1")
                .content(extractedText)
                .build());

        return ParsedDocument.builder()
                .fullText(extractedText)
                .charCount(extractedText.length())
                .sections(sections)
                .build();
    }
}
