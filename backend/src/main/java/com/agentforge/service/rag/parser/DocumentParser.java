package com.agentforge.service.rag.parser;

import java.io.InputStream;

/**
 * 多源文档解析器接口
 */
public interface DocumentParser {

    /**
     * 判断当前解析器是否支持指定文件类型 (如 PDF, DOCX, MD, TXT)
     */
    boolean supports(String fileType);

    /**
     * 从输入流中解析文档并提取结构化文本与元数据
     *
     * @param inputStream 文件输入流
     * @param fileName    原始文件名
     * @return 解析后的文档对象
     */
    ParsedDocument parse(InputStream inputStream, String fileName);
}
