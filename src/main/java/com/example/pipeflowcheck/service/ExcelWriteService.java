package com.example.pipeflowcheck.service;

import com.example.pipeflowcheck.model.CheckResult;
import com.example.pipeflowcheck.model.TaskSummary;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelWriteService {

    private final ResultSummaryService resultSummaryService;

    private static final String[] RESULT_HEADERS = {
            "task_id",
            "start_node_id",
            "start_node_name",
            "start_type",
            "status",
            "end_node_id",
            "end_node_name",
            "path",
            "error_code",
            "error_reason",
            "risk_level"
    };

    private static final String[] SUMMARY_HEADERS = {
            "task_id",
            "start_node_id",
            "start_node_name",
            "start_type",
            "status",
            "path_count",
            "error_code",
            "error_reason",
            "risk_level"
    };

    public byte[] write(List<CheckResult> results) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Result");
            writeHeader(sheet, RESULT_HEADERS);
            for (int i = 0; i < results.size(); i++) {
                writeResult(sheet.createRow(i + 1), results.get(i));
            }
            autoSize(sheet, RESULT_HEADERS.length);

            Sheet summarySheet = workbook.createSheet("Summary");
            writeHeader(summarySheet, SUMMARY_HEADERS);
            writeSummary(summarySheet, resultSummaryService.summarize(results));
            autoSize(summarySheet, SUMMARY_HEADERS.length);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("结果 Excel 生成失败", e);
        }
    }

    private void writeHeader(Sheet sheet, String[] headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            row.createCell(i).setCellValue(headers[i]);
        }
    }

    private void writeResult(Row row, CheckResult result) {
        row.createCell(0).setCellValue(value(result.getTaskId()));
        row.createCell(1).setCellValue(value(result.getStartNodeId()));
        row.createCell(2).setCellValue(value(result.getStartNodeName()));
        row.createCell(3).setCellValue(value(result.getStartType()));
        row.createCell(4).setCellValue(value(result.getStatus()));
        row.createCell(5).setCellValue(value(result.getEndNodeId()));
        row.createCell(6).setCellValue(value(result.getEndNodeName()));
        row.createCell(7).setCellValue(value(result.getPath()));
        row.createCell(8).setCellValue(result.getErrorCode() == null ? "" : result.getErrorCode().name());
        row.createCell(9).setCellValue(value(result.getErrorReason()));
        row.createCell(10).setCellValue(value(result.getRiskLevel()));
    }

    private void writeSummary(Sheet sheet, List<TaskSummary> summaries) {
        int rowIndex = 1;
        for (TaskSummary summary : summaries) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(value(summary.getTaskId()));
            row.createCell(1).setCellValue(value(summary.getStartNodeId()));
            row.createCell(2).setCellValue(value(summary.getStartNodeName()));
            row.createCell(3).setCellValue(value(summary.getStartType()));
            row.createCell(4).setCellValue(value(summary.getStatus()));
            row.createCell(5).setCellValue(summary.getPathCount());
            row.createCell(6).setCellValue(summary.getErrorCode() == null ? "" : summary.getErrorCode().name());
            row.createCell(7).setCellValue(value(summary.getErrorReason()));
            row.createCell(8).setCellValue(value(summary.getRiskLevel()));
        }
    }

    private void autoSize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
