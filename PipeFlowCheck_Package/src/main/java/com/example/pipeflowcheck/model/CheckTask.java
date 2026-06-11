package com.example.pipeflowcheck.model;

import com.example.pipeflowcheck.enums.StartType;
import lombok.Data;

@Data
public class CheckTask {
    private String taskId;
    private String startNodeId;
    private StartType startType;
    private String remark;
}
