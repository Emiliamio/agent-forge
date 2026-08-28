package com.agentforge.service.rag.parser;

import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档解析器工厂
 * 依据文件类型后缀自动路由匹配最佳解析器
 */
@Component
@RequiredArgsConstructor
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;

    /**
     * 获取支持指定文件格式的解析器实例
     *
     * @param fileType 文件扩展名 (如 PDF, DOCX, MD, TXT)
     * @return 对应的文档解析器
     */
    public DocumentParser getParser(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        return parsers.stream()
                .filter(p -> p.supports(fileType.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED,
                        "暂不支持解析 [" + fileType + "] 格式文件，支持格式: PDF, DOCX, MD, TXT"));
    }
}
