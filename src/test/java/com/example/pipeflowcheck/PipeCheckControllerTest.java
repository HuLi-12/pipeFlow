package com.example.pipeflowcheck;

import com.example.pipeflowcheck.controller.ApiExceptionHandler;
import com.example.pipeflowcheck.controller.PipeCheckController;
import com.example.pipeflowcheck.service.ExcelReadService;
import com.example.pipeflowcheck.service.ExcelWriteService;
import com.example.pipeflowcheck.service.GraphBuildService;
import com.example.pipeflowcheck.service.PipeCheckService;
import com.example.pipeflowcheck.service.ResultSummaryService;
import com.example.pipeflowcheck.service.RuleConfigService;
import com.example.pipeflowcheck.service.RuleEngine;
import com.example.pipeflowcheck.service.TemplateWriteService;
import com.example.pipeflowcheck.service.VisualizationService;
import com.example.pipeflowcheck.service.ZipPackagingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PipeCheckControllerTest {

    @Test
    void returnsResultWorkbookForUploadedExcel() throws Exception {
        PipeCheckController controller = controller();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                SampleWorkbookFactory.sampleWorkbook()
        );

        ResponseEntity<byte[]> response = controller.check(file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getBody()))) {
            assertNotNull(workbook.getSheet("Result"));
        }
    }

    @Test
    void returnsJsonResultForPageRendering() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                SampleWorkbookFactory.sampleWorkbook()
        );

        MvcResult result = mockMvc.perform(multipart("/api/pipe-flow/check-json").file(file))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("application/json", result.getResponse().getContentType());
        JsonNode root = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
        assertEquals(8, root.get("results").size());
        assertEquals(5, root.get("summary").size());
        assertEquals("MULTI_PATH_ERROR", root.get("summary").get(2).get("errorCode").asText());
    }

    @Test
    void returnsJsonResultForMultiScenarioWorkbook() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "multi-scenario.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                SampleWorkbookFactory.multiScenarioWorkbook()
        );

        MvcResult result = mockMvc.perform(multipart("/api/pipe-flow/check-json").file(file))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
        assertEquals(15, root.get("results").size());
        assertEquals(12, root.get("summary").size());
    }

    @Test
    void returnsStandardTemplateWorkbook() throws Exception {
        ResponseEntity<byte[]> response = controller().template();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getBody()))) {
            assertNotNull(workbook.getSheet("Nodes"));
            assertNotNull(workbook.getSheet("Edges"));
            assertNotNull(workbook.getSheet("Tasks"));
            assertNotNull(workbook.getSheet("Template"));
            assertEquals("node_id", workbook.getSheet("Nodes").getRow(0).getCell(0).getStringCellValue());
            assertEquals("from_node_id", workbook.getSheet("Edges").getRow(0).getCell(0).getStringCellValue());
            assertEquals("task_id", workbook.getSheet("Tasks").getRow(0).getCell(0).getStringCellValue());
        }
    }

    @Test
    void returnsBadRequestForInvalidExcel() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                SampleWorkbookFactory.workbookWithMissingNodeId()
        );

        mockMvc.perform(multipart("/api/pipe-flow/check").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestForEmptyUpload() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        mockMvc.perform(multipart("/api/pipe-flow/check").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestForNonXlsxUpload() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", "not,xlsx".getBytes());

        mockMvc.perform(multipart("/api/pipe-flow/check").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonXlsxUploadBeforeParsing() {
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", "not,xlsx".getBytes());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> controller().check(file));

        assertEquals("仅支持 .xlsx 文件", exception.getMessage());
    }

    private PipeCheckController controller() {
        return new PipeCheckController(
                new ExcelReadService(),
                new GraphBuildService(),
                new PipeCheckService(new RuleEngine(), new RuleConfigService()),
                new ExcelWriteService(new ResultSummaryService()),
                new ResultSummaryService(),
                new TemplateWriteService(),
                new VisualizationService(),
                new ZipPackagingService()
        );
    }
}
