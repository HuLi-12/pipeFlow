package com.example.pipeflowcheck.service;

import com.example.pipeflowcheck.config.VisualizationConfig;
import com.example.pipeflowcheck.enums.NodeType;
import com.example.pipeflowcheck.model.CheckResult;
import com.example.pipeflowcheck.model.Edge;
import com.example.pipeflowcheck.model.Node;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VisualizationService {

    private static final Path CONFIG_PATH = Path.of("config", "rules.yml");

    private VisualizationConfig config;

    @PostConstruct
    public void init() {
        this.config = loadConfig();
    }

    /** Load visualization config from rules.yml with hardcoded fallbacks. */
    private VisualizationConfig loadConfig() {
        VisualizationConfig cfg = new VisualizationConfig();
        if (!Files.exists(CONFIG_PATH)) {
            return cfg; // use defaults
        }
        try (InputStream is = Files.newInputStream(CONFIG_PATH)) {
            Map<String, Object> root = new Yaml().load(is);
            if (root == null) return cfg;

            Object vizNode = root.get("visualization");
            if (!(vizNode instanceof Map<?, ?> raw)) return cfg;

            // nodeColors
            Object colorsNode = raw.get("nodeColors");
            if (colorsNode instanceof Map<?, ?> rawColors) {
                Map<NodeType, String> colors = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : rawColors.entrySet()) {
                    try {
                        NodeType nt = NodeType.valueOf(String.valueOf(e.getKey()));
                        colors.put(nt, String.valueOf(e.getValue()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (!colors.isEmpty()) cfg.setNodeColors(colors);
            }

            copyStr(raw, "errorNodeColor", cfg::setErrorNodeColor);
            copyStr(raw, "errorEdgeColor", cfg::setErrorEdgeColor);
            copyInt(raw, "errorEdgePenWidth", cfg::setErrorEdgePenWidth);
            copyStr(raw, "normalEdgeColor", cfg::setNormalEdgeColor);
            copyStr(raw, "terminalShape", cfg::setTerminalShape);
            copyStr(raw, "normalShape", cfg::setNormalShape);
            copyStr(raw, "fontName", cfg::setFontName);
            copyStr(raw, "graphDirection", cfg::setGraphDirection);

            return cfg;
        } catch (IOException e) {
            return cfg;
        }
    }

    private void copyStr(Map<?, ?> src, String key, java.util.function.Consumer<String> setter) {
        Object v = src.get(key);
        if (v != null) setter.accept(String.valueOf(v));
    }

    private void copyInt(Map<?, ?> src, String key, java.util.function.Consumer<Integer> setter) {
        Object v = src.get(key);
        if (v instanceof Number n) setter.accept(n.intValue());
    }

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
        VisualizationConfig c = config;
        StringBuilder sb = new StringBuilder();
        sb.append("digraph PipeFlow {\n");
        sb.append("  rankdir=").append(c.getGraphDirection()).append(";\n");
        sb.append("  node [style=filled, fontname=\"").append(c.getFontName()).append("\"];\n");
        sb.append("  edge [fontname=\"").append(c.getFontName()).append("\", fontsize=10];\n");
        sb.append("  label=\"城市排水管网流向图\";\n");
        sb.append("  labelloc=t;\n");
        sb.append("  fontsize=16;\n");
        sb.append("  fontname=\"").append(c.getFontName()).append("\";\n\n");

        // terminal types for shape lookup
        Set<NodeType> terminalTypes = Set.of(
                NodeType.RIVER, NodeType.LAKE, NodeType.RAIN_OUTLET, NodeType.WWTP
        );

        // node definitions
        for (Node node : nodeMap.values()) {
            String color = c.getNodeColors() != null
                    ? c.getNodeColors().getOrDefault(node.getNodeType(), "#CCCCCC")
                    : nodeColorDefault(node.getNodeType());
            String shape = terminalTypes.contains(node.getNodeType())
                    ? c.getTerminalShape() : c.getNormalShape();
            String fontColor = node.getNodeType() == NodeType.SEWAGE_INLET
                    || node.getNodeType() == NodeType.SEWAGE_WELL ? "white" : "black";
            String fillcolor = errorNodeIds.contains(node.getNodeId()) ? c.getErrorNodeColor() : color;

            // label: nodeName\nnodeId [NODE_TYPE]
            sb.append("  \"").append(node.getNodeId())
                    .append("\" [label=\"").append(node.getNodeName()).append("\\n")
                    .append(node.getNodeId()).append(" [").append(node.getNodeType()).append("]")
                    .append("\", shape=").append(shape)
                    .append(", fillcolor=\"").append(fillcolor)
                    .append("\", fontcolor=\"").append(fontColor)
                    .append("\"];\n");
        }

        sb.append("\n");

        // edge definitions
        for (Edge edge : edges) {
            String edgeKey = edge.getFromNodeId() + "->" + edge.getToNodeId();
            boolean isError = errorEdgeKeys.contains(edgeKey);
            sb.append("  \"").append(edge.getFromNodeId())
                    .append("\" -> \"").append(edge.getToNodeId())
                    .append("\" [label=\"").append(edge.getChannelType());
            if (isError) {
                sb.append("\", color=\"").append(c.getErrorEdgeColor())
                        .append("\", penwidth=").append(c.getErrorEdgePenWidth())
                        .append("];\n");
            } else {
                sb.append("\"];\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    /** Overload without error info (for full-network graph). */
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

    /** Render DOT source to SVG string. Falls back to empty string. */
    public String renderSvg(String dotSource) {
        try {
            return Graphviz.fromString(dotSource).render(Format.SVG).toString();
        } catch (GraphvizException e) {
            System.err.println("SVG 渲染失败: " + e.getMessage());
            return "";
        }
    }

    /** Render DOT source to PNG bytes. Falls back to empty array. */
    public byte[] renderPng(String dotSource) {
        try {
            var baos = new java.io.ByteArrayOutputStream();
            Graphviz.fromString(dotSource).render(Format.PNG).toOutputStream(baos);
            return baos.toByteArray();
        } catch (GraphvizException | IOException e) {
            System.err.println("PNG 渲染失败: " + e.getMessage());
            return new byte[0];
        }
    }

    /** Default color lookup when no config is loaded. */
    private static String nodeColorDefault(NodeType type) {
        switch (type) {
            case RAIN_INLET: return "#4A90D9";
            case RAIN_WELL: return "#6DB3F2";
            case RAIN_OUTLET: return "#2E75B6";
            case SEWAGE_INLET: return "#333333";
            case SEWAGE_WELL: return "#555555";
            case LIFE_SEWAGE_INLET: return "#8B4513";
            case COMBINED_WELL: return "#808080";
            case WWTP: return "#2E8B57";
            case RIVER: return "#87CEEB";
            case LAKE: return "#87CEEB";
            case NORMAL: return "#FFFFFF";
            default: return "#CCCCCC";
        }
    }
}
