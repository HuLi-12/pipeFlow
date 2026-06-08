package com.example.pipeflowcheck.model;

import com.example.pipeflowcheck.enums.ErrorCode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskSummary {
    private String taskId;
    private String startNodeId;
    private String startNodeName;
    private String startType;
    private String status;
    private int pathCount;
    private ErrorCode errorCode;
    private String errorReason;
    private String riskLevel;
}
