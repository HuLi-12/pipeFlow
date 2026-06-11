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
    /** node IDs joined by "->", e.g. "N001->N002->N003" */
    private String path;
    /** channel types joined by "->", e.g. "RAIN->SEWAGE" */
    private String channelPath;
    /** human-readable path, e.g. "雨水口1(N001) --RAIN--> 雨水井1(N002)" */
    private String readablePath;
    private ErrorCode errorCode;
    private String errorReason;
    private String riskLevel;
}
