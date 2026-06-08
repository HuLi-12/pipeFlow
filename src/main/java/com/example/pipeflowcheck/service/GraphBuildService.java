package com.example.pipeflowcheck.service;

import com.example.pipeflowcheck.model.Edge;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GraphBuildService {
    public Map<String, List<Edge>> buildGraph(List<Edge> edges) {
        return edges.stream().collect(Collectors.groupingBy(Edge::getFromNodeId));
    }
}
