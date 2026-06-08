package com.example.pipeflowcheck.service;

import com.example.pipeflowcheck.enums.NodeType;
import com.example.pipeflowcheck.model.CheckResult;
import com.example.pipeflowcheck.model.Edge;
import com.example.pipeflowcheck.model.Node;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VisualizationService {

    private static final Map<NodeType, String> NODE_COLORS = Map.ofEntries(
            Map.entry(NodeType.RAIN_INLET, "#4A90D9"),
            Map.entry(NodeType.RAIN_WELL, "#6DB3F2"),
            Map.entry(NodeType.RAIN_OUTLET, "#2E75B6"),
            Map.entry(NodeType.SEWAGE_INLET, "#333333"),
            Map.entry(NodeType.SEWAGE_WELL, "#555555"),
            Map.entry(NodeType.LIFE_SEWAGE_INLET, "#8B4513"),
            Map.entry(NodeType.COMBINED_WELL, "#808080"),
            Map.entry(NodeType.WWTP, "#2E8B57"),
            Map.entry(NodeType.RIVER, "#87CEEB"),
            Map.entry(NodeType.LAKE, "#87CEEB"),
            Map.entry(NodeType.NORMAL, "#FFFFFF")
    );

    private static final Set<NodeType> TERMINAL_SHAPES = Set.of(
            NodeType.RIVER, NodeType.LAKE, NodeType.RAIN_OUTLET, NodeType.WWTP
    );

    /**
     * Generate DOT graph source for the pipe network.
     *
     * @param nodeMap       node map
     * @param edges         edge list
     * @param errorNodeIds  set of node IDs that appear in error paths (to mark red)
     * @return DOT format string
     */
    public String generateDot(Map<String, Node> nodeMap, List<Edge> edges, Set<String> errorNodeIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph PipeFlow {\n");
        sb.append("  rankdir=LR;\n");
        sb.append("  node [style=filled, fontname=\"Microsoft YaHei\"];\n");
        sb.append("  edge [fontname=\"Microsoft YaHei\", fontsize=10];\n");
        sb.append("  label=\"城市排水管网流向图\";\n");
        sb.append("  labelloc=t;\n");
        sb.append("  fontsize=16;\n");
        sb.append("  fontname=\"Microsoft YaHei\";\n\n");

        // node definitions
        for (Node node : nodeMap.values()) {
            String color = NODE_COLORS.getOrDefault(node.getNodeType(), "#CCCCCC");
            String shape = TERMINAL_SHAPES.contains(node.getNodeType()) ? "box" : "ellipse";
            String fontColor = node.getNodeType() == NodeType.SEWAGE_INLET
                    || node.getNodeType() == NodeType.SEWAGE_WELL ? "white" : "black";
            String fillcolor = errorNodeIds.contains(node.getNodeId()) ? "#FF6B6B" : color;

            sb.append("  \"").append(node.getNodeId())
                    .append("\" [label=\"").append(node.getNodeName()).append("\\n").append(node.getNodeId())
                    .append("\", shape=").append(shape)
                    .append(", fillcolor=\"").append(fillcolor)
                    .append("\", fontcolor=\"").append(fontColor)
                    .append("\"];\n");
        }

        sb.append("\n");

        // edge definitions
        for (Edge edge : edges) {
            sb.append("  \"").append(edge.getFromNodeId())
                    .append("\" -> \"").append(edge.getToNodeId())
                    .append("\" [label=\"").append(edge.getChannelType())
                    .append("\"];\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Overload without error info (for template/full-graph usage).
     */
    public String generateDot(Map<String, Node> nodeMap, List<Edge> edges) {
        return generateDot(nodeMap, edges, Set.of());
    }

    /**
     * Collect node IDs that appear in error results for visualization highlighting.
     */
    public Set<String> collectErrorNodeIds(List<CheckResult> results) {
        return results.stream()
                .filter(r -> r.getErrorCode() != null)
                .flatMap(r -> List.of(r.getStartNodeId(), r.getEndNodeId()).stream())
                .collect(Collectors.toSet());
    }
}
