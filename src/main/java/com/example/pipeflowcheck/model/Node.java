package com.example.pipeflowcheck.model;

import com.example.pipeflowcheck.enums.NodeType;
import lombok.Data;

@Data
public class Node {
    private String nodeId;
    private String nodeName;
    private NodeType nodeType;
    private String remark;
}
