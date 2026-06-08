package com.example.pipeflowcheck.controller;

import com.example.pipeflowcheck.model.CheckResponse;
import com.example.pipeflowcheck.model.CheckResult;
import com.example.pipeflowcheck.model.PipeNetworkData;
import com.example.pipeflowcheck.service.ExcelReadService;
import com.example.pipeflowcheck.service.ExcelWriteService;
import com.example.pipeflowcheck.service.GraphBuildService;
import com.example.pipeflowcheck.service.PipeCheckService;
import com.example.pipeflowcheck.service.ResultSummaryService;
import com.example.pipeflowcheck.service.TemplateWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/pipe-flow")
@RequiredArgsConstructor
public class PipeCheckController {

    private final ExcelReadService excelReadService;
    private final GraphBuildService graphBuildService;
    private final PipeCheckService pipeCheckService;
    private final ExcelWriteService excelWriteService;
    private final ResultSummaryService resultSummaryService;
    private final TemplateWriteService templateWriteService;

    @PostMapping("/check")
    public ResponseEntity<byte[]> check(@RequestParam("file") MultipartFile file) throws IOException {
        List<CheckResult> results = detect(file);
        byte[] report = excelWriteService.write(results);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pipe-flow-check-result.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(report);
    }

    @PostMapping("/check-json")
    public CheckResponse checkJson(@RequestParam("file") MultipartFile file) throws IOException {
        List<CheckResult> results = detect(file);
        return new CheckResponse(resultSummaryService.summarize(results), results);
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pipe-flow-check-template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(templateWriteService.write());
    }

    private List<CheckResult> detect(MultipartFile file) throws IOException {
        validateUpload(file);
        PipeNetworkData data = excelReadService.read(file.getInputStream());
        return pipeCheckService.checkAll(
                data.getNodeMap(),
                graphBuildService.buildGraph(data.getEdges()),
                data.getTasks()
        );
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("仅支持 .xlsx 文件");
        }
    }
}
