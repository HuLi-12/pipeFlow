package com.example.pipeflowcheck.model;

import com.example.pipeflowcheck.enums.ErrorCode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckResult {
    private String taskId;
    private String startNodeId;
    private String startNodeName;
    private String startType;
    private String status;
    private String endNodeId;
    private String endNodeName;
    private String path;
    private ErrorCode errorCode;
    private String errorReason;
    private String riskLevel;
}
