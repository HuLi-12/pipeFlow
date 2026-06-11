package com.example.pipeflowcheck.model;

import com.example.pipeflowcheck.enums.ChannelType;
import lombok.Data;

@Data
public class Edge {
    private String fromNodeId;
    private String toNodeId;
    private ChannelType channelType;
    private String remark;
}
