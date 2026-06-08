package com.example.pipeflowcheck;

import com.example.pipeflowcheck.enums.ErrorCode;
import com.example.pipeflowcheck.model.CheckResult;
import com.example.pipeflowcheck.model.PipeNetworkData;
import com.example.pipeflowcheck.service.ExcelReadService;
import com.example.pipeflowcheck.service.ExcelWriteService;
import com.example.pipeflowcheck.service.GraphBuildService;
import com.example.pipeflowcheck.service.PipeCheckService;
import com.example.pipeflowcheck.service.ResultSummaryService;
import com.example.pipeflowcheck.service.RuleConfigService;
import com.example.pipeflowcheck.service.RuleEngine;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipeFlowCheckIntegrationTest {

    @Test
    void readsExcelChecksAllPathsAndWritesResultWorkbook() throws Exception {
        ExcelReadService excelReadService = new ExcelReadService();
        GraphBuildService graphBuildService = new GraphBuildService();
        PipeCheckService pipeCheckService = new PipeCheckService(new RuleEngine(), new RuleConfigService());
        ExcelWriteService excelWriteService = new ExcelWriteService(new ResultSummaryService());

        PipeNetworkData data = excelReadService.read(new ByteArrayInputStream(SampleWorkbookFactory.sampleWorkbook()));

        assertEquals(12, data.getNodeMap().size());
        assertEquals(12, data.getEdges().size());
        assertEquals(5, data.getTasks().size());

        List<CheckResult> results = pipeCheckService.checkAll(
                data.getNodeMap(),
                graphBuildService.buildGraph(data.getEdges()),
                data.getTasks()
        );

        Map<String, List<CheckResult>> byTask = results.stream()
                .collect(Collectors.groupingBy(CheckResult::getTaskId));

        assertEquals("Rain inlet A", byTask.get("T001").get(0).getStartNodeName());
        // T001: 1 success (N001->N002->N003 RIVER) + 1 TERMINAL_HAS_DOWNSTREAM (N001->N002->N011→N003)
        assertTrue(byTask.get("T001").stream().anyMatch(result -> "通道正常".equals(result.getStatus())));
        assertTrue(byTask.get("T001").stream().anyMatch(result -> result.getErrorCode() == ErrorCode.TERMINAL_HAS_DOWNSTREAM));
        assertTrue(byTask.get("T002").stream().allMatch(result -> "通道正常".equals(result.getStatus())));
        assertTrue(byTask.get("T003").stream().anyMatch(result -> result.getErrorCode() == ErrorCode.CHANNEL_NOT_ALLOWED));
        assertTrue(byTask.get("T004").stream().anyMatch(result -> result.getErrorCode() == ErrorCode.INVALID_END));
        assertTrue(byTask.get("T005").stream().anyMatch(result -> result.getErrorCode() == ErrorCode.CYCLE_FOUND));

        byte[] resultWorkbook = excelWriteService.write(results);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(resultWorkbook))) {
            Sheet resultSheet = workbook.getSheet("Result");
            assertEquals("task_id", resultSheet.getRow(0).getCell(0).getStringCellValue());
            assertTrue(hasErrorReason(resultSheet, "T003", "CHANNEL_NOT_ALLOWED"));

            Sheet summarySheet = workbook.getSheet("Summary");
            assertEquals("task_id", summarySheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("MULTI_PATH_ERROR", findSummaryErrorCode(summarySheet, "T003"));
        }
    }

    @Test
    void rejectsMissingRequiredExcelValue() throws Exception {
        ExcelReadService excelReadService = new ExcelReadService();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> excelReadService.read(new ByteArrayInputStream(SampleWorkbookFactory.workbookWithMissingNodeId())));

        assertTrue(exception.getMessage().contains("node_id"));
    }

    @Test
    void rejectsSampleWorkbookWithMissingStartNode() {
        ExcelReadService excelReadService = new ExcelReadService();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            try (var inputStream = Files.newInputStream(Path.of("examples", "pipeflowcheck_sample_12_nodes.xlsx"))) {
                excelReadService.read(inputStream);
            }
        });
        assertTrue(exception.getMessage().contains("不存在"));
    }

    @Test
    void rejectsDuplicateNodeId() {
        ExcelReadService excelReadService = new ExcelReadService();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> excelReadService.read(new ByteArrayInputStream(SampleWorkbookFactory.workbookWithDuplicateNodeId())));

        assertTrue(exception.getMessage().contains("节点重复"));
    }

    @Test
    void rejectsEdgesThatReferenceMissingNodes() {
        ExcelReadService excelReadService = new ExcelReadService();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> excelReadService.read(new ByteArrayInputStream(SampleWorkbookFactory.workbookWithMissingEdgeTarget())));

        assertTrue(exception.getMessage().contains("边引用节点不存在"));
    }

    private boolean hasErrorReason(Sheet resultSheet, String taskId, String errorCode) {
        for (int rowIndex = 1; rowIndex <= resultSheet.getLastRowNum(); rowIndex++) {
            if (taskId.equals(resultSheet.getRow(rowIndex).getCell(0).getStringCellValue())
                    && errorCode.equals(resultSheet.getRow(rowIndex).getCell(10).getStringCellValue())) {
                return !resultSheet.getRow(rowIndex).getCell(11).getStringCellValue().isBlank();
            }
        }
        return false;
    }

    private String findSummaryErrorCode(Sheet summarySheet, String taskId) {
        for (int rowIndex = 1; rowIndex <= summarySheet.getLastRowNum(); rowIndex++) {
            if (taskId.equals(summarySheet.getRow(rowIndex).getCell(0).getStringCellValue())) {
                return summarySheet.getRow(rowIndex).getCell(6).getStringCellValue();
            }
        }
        return "";
    }
}
