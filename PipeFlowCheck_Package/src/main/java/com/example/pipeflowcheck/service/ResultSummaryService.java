package com.example.pipeflowcheck.service;

import com.example.pipeflowcheck.enums.ErrorCode;
import com.example.pipeflowcheck.model.CheckResult;
import com.example.pipeflowcheck.model.TaskSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ResultSummaryService {

    public List<TaskSummary> summarize(List<CheckResult> results) {
        Map<String, List<CheckResult>> byTask = new LinkedHashMap<>();
        for (CheckResult result : results) {
            byTask.computeIfAbsent(result.getTaskId(), key -> new ArrayList<>()).add(result);
        }

        List<TaskSummary> summaries = new ArrayList<>();
        for (List<CheckResult> taskResults : byTask.values()) {
            CheckResult first = taskResults.get(0);
            List<CheckResult> errors = taskResults.stream()
                    .filter(result -> result.getErrorCode() != null)
                    .toList();

            summaries.add(TaskSummary.builder()
                    .taskId(first.getTaskId())
                    .startNodeId(first.getStartNodeId())
                    .startNodeName(first.getStartNodeName())
                    .startType(first.getStartType())
                    .status(errors.isEmpty() ? "通道正常" : "错误")
                    .pathCount(taskResults.size())
                    .errorCode(summaryErrorCode(taskResults, errors))
                    .errorReason(summaryReason(errors))
                    .riskLevel(summaryRisk(taskResults))
                    .build());
        }
        return summaries;
    }

    private ErrorCode summaryErrorCode(List<CheckResult> taskResults, List<CheckResult> errors) {
        if (errors.isEmpty()) {
            return null;
        }
        if (taskResults.size() > 1) {
            return ErrorCode.MULTI_PATH_ERROR;
        }
        return errors.get(0).getErrorCode();
    }

    private String summaryReason(List<CheckResult> errors) {
        if (errors.isEmpty()) {
            return "";
        }
        if (errors.size() == 1) {
            return value(errors.get(0).getErrorReason());
        }
        return "存在 " + errors.size() + " 条错误路径，首个错误：" + value(errors.get(0).getErrorReason());
    }

    private String summaryRisk(List<CheckResult> taskResults) {
        if (taskResults.stream().map(CheckResult::getRiskLevel).filter(Objects::nonNull).anyMatch("高"::equals)) {
            return "高";
        }
        if (taskResults.stream().map(CheckResult::getRiskLevel).filter(Objects::nonNull).anyMatch("中"::equals)) {
            return "中";
        }
        return "无";
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
