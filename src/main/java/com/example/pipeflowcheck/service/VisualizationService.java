package com.example.pipeflowcheck.service;

import com.example.pipeflowcheck.enums.NodeType;
import com.example.pipeflowcheck.model.CheckResult;
import com.example.pipeflowcheck.model.Edge;
import com.example.pipeflowcheck.model.Node;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizException;
import guru.nidi.graphviz.parse.Parser;
import org.springframework.stereotype.Service;

import java.util.Arrays;
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
     * @param errorEdgeKeys set of "from->to" edge keys that are on error paths (to mark red)
     * @return DOT format string
     */
    public String generateDot(Map<String, Node> nodeMap, List<Edge> edges,
                              Set<String> errorNodeIds, Set<String> errorEdgeKeys) {
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
            String edgeKey = edge.getFromNodeId() + "->" + edge.getToNodeId();
            sb.append("  \"").append(edge.getFromNodeId())
                    .append("\" -> \"").append(edge.getToNodeId())
                    .append("\" [label=\"").append(edge.getChannelType());
            if (errorEdgeKeys.contains(edgeKey)) {
                sb.append("\", color=\"red\", penwidth=3");
            }
            sb.append("\"];\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Overload without error info (for template/full-graph usage).
     */
    public String generateDot(Map<String, Node> nodeMap, List<Edge> edges) {
        return generateDot(nodeMap, edges, Set.of(), Set.of());
    }

    /**
     * Collect ALL node IDs from error paths (not just start/end), for red-highlighting.
     */
    public Set<String> collectErrorNodeIds(List<CheckResult> results) {
        return results.stream()
                .filter(r -> r.getErrorCode() != null)
                .flatMap(r -> Arrays.stream(r.getPath().split("->")))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Collect edge keys ("from->to") from error paths for red-highlighting edges.
     */
    public Set<String> collectErrorEdgeKeys(List<CheckResult> results) {
        return results.stream()
                .filter(r -> r.getErrorCode() != null)
                .flatMap(r -> {
                    String[] nodes = r.getPath().split("->");
                    if (nodes.length < 2) return java.util.stream.Stream.empty();
                    return java.util.stream.IntStream.range(0, nodes.length - 1)
                            .mapToObj(i -> nodes[i] + "->" + nodes[i + 1]);
                })
                .collect(Collectors.toSet());
    }

    /**
     * Render DOT source to SVG string using graphviz-java.
     * Falls back to empty string if rendering fails.
     */
    public String renderSvg(String dotSource) {
        try {
            return Graphviz.fromString(dotSource).render(Format.SVG).toString();
        } catch (GraphvizException e) {
            System.err.println("SVG 渲染失败，仅输出 DOT 文件: " + e.getMessage());
            return "";
        }
    }
}
