package com.example.pipeflowcheck.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class PipeNetworkData {
    private Map<String, Node> nodeMap;
    private List<Edge> edges;
    private List<CheckTask> tasks;
}
