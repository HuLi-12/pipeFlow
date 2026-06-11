package com.example.pipeflowcheck.config;

import com.example.pipeflowcheck.enums.NodeType;
import lombok.Data;

import java.util.Map;

@Data
public class VisualizationConfig {
    private Map<NodeType, String> nodeColors;
    private String errorNodeColor = "#FF6B6B";
    private String errorEdgeColor = "#FF0000";
    private int errorEdgePenWidth = 3;
    private String normalEdgeColor = "#888888";
    private String terminalShape = "box";
    private String normalShape = "ellipse";
    private String fontName = "Microsoft YaHei";
    private String graphDirection = "LR";
}
