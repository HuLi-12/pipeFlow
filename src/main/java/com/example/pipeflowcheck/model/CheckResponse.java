package com.example.pipeflowcheck.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CheckResponse {
    private List<TaskSummary> summary;
    private List<CheckResult> results;
}
