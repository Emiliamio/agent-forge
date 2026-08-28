package com.agentforge.service.rag.parser.excel;

import cn.hutool.core.util.StrUtil;
import com.agentforge.service.rag.parser.DocumentParser;
import com.agentforge.service.rag.parser.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工业级 Excel/CSV 韧性清洗解析器 (Resilient Excel Parser)
 * 具备以下企业级数据修复能力：
 * 1. 自动拆解合并单元格 (Un-merge) 并级联向下/向右继承上下文表头
 * 2. 自动捕获与清洗 #DIV/0!、#REF!、#N/A 等公式损坏错误
 * 3. 过滤连续空行，生成紧凑结构化 Markdown 表格
 */
@Slf4j
@Component
public class ResilientExcelParser implements DocumentParser {

    @Override
    public boolean supports(String fileExtension) {
        if (StrUtil.isBlank(fileExtension)) return false;
        String ext = fileExtension.toLowerCase().replace(".", "");
        return List.of("xlsx", "xls", "csv").contains(ext);
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        log.info("📊 触发 Excel 韧性装甲解析与合并单元格修复: fileName={}", fileName);

        StringBuilder fullText = new StringBuilder();
        List<ParsedDocument.PageSection> sections = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int sheetCount = workbook.getNumberOfSheets();

            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();

                // 1. 构建合并单元格映射字典: (row, col) -> 合并区域主格内容
                Map<String, String> mergedCellMap = buildMergedCellMap(sheet, evaluator);

                // 2. 逐行提取并生成 Markdown 表格
                StringBuilder sheetMd = new StringBuilder();
                sheetMd.append("### 工作表: ").append(sheetName).append("\n\n");

                int maxCol = 0;
                List<List<String>> rowDataList = new ArrayList<>();

                for (Row row : sheet) {
                    if (row == null) continue;
                    List<String> rowCells = new ArrayList<>();
                    boolean isRowEmpty = true;

                    for (int c = 0; c < Math.max(row.getLastCellNum(), maxCol); c++) {
                        String key = row.getRowNum() + "_" + c;
                        String cellValue;

                        if (mergedCellMap.containsKey(key)) {
                            cellValue = mergedCellMap.get(key);
                        } else {
                            Cell cell = row.getCell(c);
                            cellValue = getCellStringValue(cell, evaluator);
                        }

                        if (StrUtil.isNotBlank(cellValue)) {
                            isRowEmpty = false;
                        }
                        rowCells.add(cellValue.trim());
                    }

                    // 过滤纯空行
                    if (!isRowEmpty) {
                        maxCol = Math.max(maxCol, rowCells.size());
                        rowDataList.add(rowCells);
                    }
                }

                // 3. 转换为标准 Markdown 表格
                if (!rowDataList.isEmpty()) {
                    // 表头
                    List<String> headers = rowDataList.get(0);
                    sheetMd.append("| ").append(String.join(" | ", headers)).append(" |\n");
                    sheetMd.append("|").append(" --- |".repeat(headers.size())).append("\n");

                    // 数据行
                    for (int i = 1; i < rowDataList.size(); i++) {
                        List<String> row = rowDataList.get(i);
                        // 补齐列数
                        while (row.size() < headers.size()) {
                            row.add("");
                        }
                        sheetMd.append("| ").append(String.join(" | ", row.subList(0, headers.size()))).append(" |\n");
                    }
                    sheetMd.append("\n");
                }

                String sheetText = sheetMd.toString();
                fullText.append(sheetText);
                sections.add(ParsedDocument.PageSection.builder()
                        .pageNumber(s + 1)
                        .sectionTitle("工作表: " + sheetName)
                        .content(sheetText)
                        .build());
            }

        } catch (Exception e) {
            log.error("Excel 韧性解析失败: fileName={}, error={}", fileName, e.getMessage(), e);
            throw new RuntimeException("Excel 数据清洗解析异常: " + e.getMessage(), e);
        }

        return ParsedDocument.builder()
                .fullText(fullText.toString())
                .charCount(fullText.length())
                .sections(sections)
                .build();
    }

    private Map<String, String> buildMergedCellMap(Sheet sheet, FormulaEvaluator evaluator) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            Row firstRow = sheet.getRow(region.getFirstRow());
            if (firstRow == null) continue;
            Cell firstCell = firstRow.getCell(region.getFirstColumn());
            String masterVal = getCellStringValue(firstCell, evaluator);

            // 将主格内容填充到整个合并区域所有单元格
            for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                    map.put(r + "_" + c, masterVal);
                }
            }
        }
        return map;
    }

    private String getCellStringValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return new SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
                    }
                    double num = cell.getNumericCellValue();
                    if (num == (long) num) {
                        return String.valueOf((long) num);
                    }
                    return String.valueOf(num);
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        return evaluator.evaluate(cell).formatAsString().replace("\"", "");
                    } catch (Exception e) {
                        // 遇到公式错误 (#DIV/0!, #REF!)，优雅降级为错误标记，防止崩溃
                        return "[公式待刷新]";
                    }
                case BLANK:
                case ERROR:
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }
}
