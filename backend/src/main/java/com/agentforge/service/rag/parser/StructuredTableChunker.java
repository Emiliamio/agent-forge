package com.agentforge.service.rag.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化表格级联表头解析与切片注入器
 * 解决传统切片导致跨页表格“表头与数据行脱节”的行业硬伤
 */
public class StructuredTableChunker {

    /**
     * 将二维表格转换为携带完整表头上下文的切片
     *
     * @param headers 表头列表 (如 ["部门", "员工", "季度", "销售额(万)"])
     * @param rows    数据行列表
     * @return 注入表头上下文的增强切片列表
     */
    public static List<String> chunkTableWithHierarchy(List<String> headers, List<List<String>> rows) {
        List<String> resultChunks = new ArrayList<>();
        if (headers == null || headers.isEmpty() || rows == null || rows.isEmpty()) {
            return resultChunks;
        }

        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            StringBuilder sb = new StringBuilder();
            sb.append("【表格数据行 #").append(i + 1).append("】");

            for (int col = 0; col < headers.size() && col < row.size(); col++) {
                String headerName = headers.get(col);
                String cellValue = row.get(col);
                sb.append(String.format(" [%s: %s]", headerName, cellValue));
            }
            resultChunks.add(sb.toString());
        }
        return resultChunks;
    }
}
