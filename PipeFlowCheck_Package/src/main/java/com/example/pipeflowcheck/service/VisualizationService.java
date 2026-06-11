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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
        int nodeCount = nodeMap.size();
        double ns = Math.max(0.3, Math.round((0.3 + nodeCount * 0.004) * 10.0) / 10.0);
        double rs = Math.max(0.5, Math.round((0.5 + nodeCount * 0.006) * 10.0) / 10.0);
        sb.append("  nodesep=").append(ns).append(";\n");
        sb.append("  ranksep=").append(rs).append(";\n");
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

    /** Collect ALL node IDs from error paths (full path, not just start/end). */
    public Set<String> collectErrorNodeIds(List<CheckResult> results) {
        return results.stream()
                .filter(r -> r.getErrorCode() != null)
                .flatMap(r -> Arrays.stream(r.getPath().split("->")))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());
    }

    /** Collect edge keys ("from->to") from error paths for red-highlighting edges. */
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



    // ===== Terminal node types (used for isTerminal flag) =====
    private static final Set<NodeType> TERMINAL_TYPES = Set.of(
            NodeType.RIVER, NodeType.LAKE, NodeType.RAIN_OUTLET, NodeType.WWTP
    );

    // ===== Entry node types (used for isEntry flag) =====
    private static final Set<NodeType> ENTRY_TYPES = Set.of(
            NodeType.RAIN_INLET, NodeType.SEWAGE_INLET, NodeType.LIFE_SEWAGE_INLET
    );

    /**
     * Build front-end friendly graph JSON payload from network data and check results.
     *
     * New payload shape:
     * {
     *   nodes:   [{id, name, type, status, width, height, isEntry, isTerminal, isError, x, y}],
     *   edges:   [{id:"E00001", from, to, type, status, isError, remark}],
     *   paths:   [{pathId, taskId, status, nodePath, edgePath, channelPath, errorCode, errorReason, readablePath}],
     *   views:   {global:{nodeIds,edgeIds}, error:{nodeIds,edgeIds}, task:{T001:{nodeIds,edgeIds},...}, path:{pathId:{nodeIds,edgeIds},...}},
     *   errors:  [...] (backward-compatible legacy field)
     * }
     */
    public Map<String, Object> buildGraphPayload(Map<String, Node> nodeMap, List<Edge> edges, List<CheckResult> results) {
        Set<String> errorNodeIds = collectErrorNodeIds(results);
        Set<String> errorEdgeKeys = collectErrorEdgeKeys(results);

        // ---- Build edgeIdMap: "from->to:channelType" → "E00001" ----
        Map<String, String> edgeIdMap = new LinkedHashMap<>();
        Map<String, String> edgeKeyById = new LinkedHashMap<>(); // edgeId → "from->to" (legacy key)
        int edgeSeq = 0;
        for (Edge edge : edges) {
            String lookupKey = edge.getFromNodeId() + "->" + edge.getToNodeId() + ":" + edge.getChannelType().name();
            String edgeId = String.format("E%05d", ++edgeSeq);
            edgeIdMap.put(lookupKey, edgeId);
            edgeKeyById.put(edgeId, edge.getFromNodeId() + "->" + edge.getToNodeId());
        }

        // ---- Build node payload with dimensions and flags ----
        List<Map<String, Object>> nodePayload = nodeMap.values().stream()
                .map(node -> {
                    boolean isError = errorNodeIds.contains(node.getNodeId());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", node.getNodeId());
                    item.put("name", node.getNodeName());
                    item.put("type", node.getNodeType().name());
                    item.put("status", isError ? "error" : "normal");
                    item.put("isEntry", ENTRY_TYPES.contains(node.getNodeType()));
                    item.put("isTerminal", TERMINAL_TYPES.contains(node.getNodeType()));
                    item.put("isError", isError);
                    item.put("remark", node.getRemark() == null ? "" : node.getRemark());
                    // Node dimensions for layout engine
                    int nameLen = Math.max(
                            node.getNodeName() != null ? node.getNodeName().length() : 4,
                            node.getNodeId() != null ? node.getNodeId().length() : 4
                    );
                    int width = Math.max(120, Math.min(220, nameLen * 12 + 40));
                    int height = isError ? 62 : 52;
                    item.put("width", width);
                    item.put("height", height);
                    return item;
                })
                .collect(Collectors.toList());

        // ---- Build edge payload with global edge ID ----
        List<Map<String, Object>> edgePayload = edges.stream()
                .map(edge -> {
                    String lookupKey = edge.getFromNodeId() + "->" + edge.getToNodeId() + ":" + edge.getChannelType().name();
                    String edgeId = edgeIdMap.get(lookupKey);
                    String legacyKey = edge.getFromNodeId() + "->" + edge.getToNodeId();
                    boolean isError = errorEdgeKeys.contains(legacyKey);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", edgeId);
                    item.put("from", edge.getFromNodeId());
                    item.put("to", edge.getToNodeId());
                    item.put("type", edge.getChannelType().name());
                    item.put("status", isError ? "error" : "normal");
                    item.put("isError", isError);
                    item.put("remark", edge.getRemark() == null ? "" : edge.getRemark());
                    return item;
                })
                .collect(Collectors.toList());

        // ---- Build paths array from CheckResult ----
        List<Map<String, Object>> pathPayload = new ArrayList<>();
        for (CheckResult r : results) {
            List<String> nodePath = splitPath(r.getPath());
            List<String> edgePathList = buildEdgePath(r.getPath());
            // Map legacy edge keys ("from->to") to global edge IDs
            List<String> edgeIdPath = new ArrayList<>();
            for (String legacyKey : edgePathList) {
                String[] parts = legacyKey.split("->");
                if (parts.length == 2) {
                    // Look up by partial key (channel type unknown here, try both)
                    // We store edge ID per result edge using the edgeKeyById reverse map
                    // Since channel is unknown in path, we search edgeIdMap by prefix
                    String prefix = legacyKey + ":";
                    edgeIdMap.entrySet().stream()
                            .filter(e -> e.getKey().startsWith(prefix))
                            .findFirst()
                            .ifPresent(e -> edgeIdPath.add(e.getValue()));
                }
            }

            Map<String, Object> pathItem = new LinkedHashMap<>();
            pathItem.put("pathId", r.getPathId() != null ? r.getPathId() : r.getTaskId() + "-P001");
            pathItem.put("taskId", r.getTaskId());
            pathItem.put("status", r.getStatus());
            pathItem.put("nodePath", nodePath);
            pathItem.put("edgePath", edgeIdPath);
            pathItem.put("channelPath", splitPath(r.getChannelPath()));
            pathItem.put("errorCode", r.getErrorCode() != null ? r.getErrorCode().name() : null);
            pathItem.put("errorReason", r.getErrorReason() != null ? r.getErrorReason() : "");
            pathItem.put("readablePath", r.getReadablePath() != null ? r.getReadablePath() : "");
            pathPayload.add(pathItem);
        }

        // ---- Build views ----
        Set<String> allNodeIds = nodeMap.keySet();
        Set<String> allEdgeIds = edgePayload.stream()
                .map(e -> (String) e.get("id"))
                .collect(Collectors.toSet());

        // Global view: all nodes and edges
        Map<String, Object> globalView = new LinkedHashMap<>();
        globalView.put("nodeIds", new ArrayList<>(allNodeIds));
        globalView.put("edgeIds", new ArrayList<>(allEdgeIds));

        // Error view: nodes and edges from error paths
        Set<String> errorViewNodes = new LinkedHashSet<>();
        Set<String> errorViewEdges = new LinkedHashSet<>();
        for (Map<String, Object> p : pathPayload) {
            if (p.get("errorCode") != null) {
                errorViewNodes.addAll((List<String>) p.get("nodePath"));
                errorViewEdges.addAll((List<String>) p.get("edgePath"));
            }
        }
        Map<String, Object> errorView = new LinkedHashMap<>();
        errorView.put("nodeIds", new ArrayList<>(errorViewNodes));
        errorView.put("edgeIds", new ArrayList<>(errorViewEdges));

        // Task view: group paths by taskId
        Map<String, Map<String, Object>> taskView = new LinkedHashMap<>();
        Map<String, Set<String>> taskNodes = new LinkedHashMap<>();
        Map<String, Set<String>> taskEdges = new LinkedHashMap<>();
        for (Map<String, Object> p : pathPayload) {
            String tid = (String) p.get("taskId");
            taskNodes.computeIfAbsent(tid, k -> new LinkedHashSet<>()).addAll((List<String>) p.get("nodePath"));
            taskEdges.computeIfAbsent(tid, k -> new LinkedHashSet<>()).addAll((List<String>) p.get("edgePath"));
        }
        for (String tid : taskNodes.keySet()) {
            Map<String, Object> tv = new LinkedHashMap<>();
            tv.put("nodeIds", new ArrayList<>(taskNodes.get(tid)));
            tv.put("edgeIds", new ArrayList<>(taskEdges.get(tid)));
            taskView.put(tid, tv);
        }

        // Path view: each path gets its own view
        Map<String, Map<String, Object>> pathView = new LinkedHashMap<>();
        for (Map<String, Object> p : pathPayload) {
            Map<String, Object> pv = new LinkedHashMap<>();
            pv.put("nodeIds", p.get("nodePath"));
            pv.put("edgeIds", p.get("edgePath"));
            pathView.put((String) p.get("pathId"), pv);
        }

        Map<String, Object> views = new LinkedHashMap<>();
        views.put("global", globalView);
        views.put("error", errorView);
        views.put("task", taskView);
        views.put("path", pathView);

        // ---- Compute backend layout positions ----
        Map<String, double[]> positions = computeBackendLayout(nodeMap, edges);
        if (!positions.isEmpty()) {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (double[] pos : positions.values()) {
                if (pos[0] < minX) minX = pos[0];
                if (pos[0] > maxX) maxX = pos[0];
                if (pos[1] < minY) minY = pos[1];
                if (pos[1] > maxY) maxY = pos[1];
            }
            double cx = (minX + maxX) / 2;
            double cy = (minY + maxY) / 2;
            boolean flipY = (minY + maxY) < 0;

            for (Map<String, Object> item : nodePayload) {
                double[] pos = positions.get(item.get("id"));
                if (pos != null) {
                    double x = pos[0] - cx;
                    double y = pos[1] - cy;
                    if (flipY) y = -y;
                    item.put("x", (int) Math.round(x));
                    item.put("y", (int) Math.round(y));
                }
            }
        }

        // ---- Legacy error payload (backward compatible) ----
        List<Map<String, Object>> errorPayload = results.stream()
                .filter(r -> r.getErrorCode() != null)
                .map(r -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("taskId", r.getTaskId());
                    item.put("startNodeId", r.getStartNodeId());
                    item.put("errorCode", r.getErrorCode().name());
                    item.put("errorReason", r.getErrorReason());
                    item.put("nodePath", splitPath(r.getPath()));
                    item.put("edgePath", buildEdgePath(r.getPath()));
                    item.put("readablePath", r.getReadablePath());
                    return item;
                })
                .collect(Collectors.toList());

        // ---- Assemble final payload ----
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodes", nodePayload);
        payload.put("edges", edgePayload);
        payload.put("paths", pathPayload);
        payload.put("views", views);
        payload.put("errors", errorPayload);
        return payload;
    }

    /** Render DOT through Graphviz, parse SVG to extract node positions. */
    private Map<String, double[]> computeGraphvizPositions(Map<String, Node> nodeMap, List<Edge> edges) {
        try {
            String dot = generateDot(nodeMap, edges);
            String svg = renderSvg(dot);
            if (svg.isEmpty()) return Map.of();
            return parseSvgNodePositions(svg);
        } catch (Exception e) {
            System.err.println("Graphviz layout failed: " + e.getMessage());
            return Map.of();
        }
    }

    /**
     * Compute overlap-free layout positions using BFS layering + fixed spacing.
     * This is the primary layout method — no external Graphviz dependency.
     */
    private Map<String, double[]> computeBackendLayout(Map<String, Node> nodeMap, List<Edge> edges) {
        // Build outgoing adjacency
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        nodeMap.keySet().forEach(id -> outgoing.put(id, new ArrayList<>()));
        edges.forEach(e -> {
            List<String> list = outgoing.get(e.getFromNodeId());
            if (list != null) list.add(e.getToNodeId());
        });

        // Find roots (no incoming edges)
        Map<String, Integer> inDeg = new LinkedHashMap<>();
        nodeMap.keySet().forEach(id -> inDeg.put(id, 0));
        edges.forEach(e -> {
            if (inDeg.containsKey(e.getToNodeId()))
                inDeg.put(e.getToNodeId(), inDeg.get(e.getToNodeId()) + 1);
        });
        List<String> roots = inDeg.entrySet().stream()
                .filter(e -> e.getValue() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (roots.isEmpty() && !nodeMap.isEmpty())
            roots.add(nodeMap.keySet().iterator().next());

        // Assign nodes to first root via BFS
        Map<String, String> treeRoot = new LinkedHashMap<>();
        roots.forEach(r -> treeRoot.put(r, r));
        roots.forEach(root -> {
            java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>();
            q.add(root);
            while (!q.isEmpty()) {
                String cur = q.poll();
                for (String next : outgoing.getOrDefault(cur, List.of())) {
                    if (!treeRoot.containsKey(next)) {
                        treeRoot.put(next, root);
                        q.add(next);
                    }
                }
            }
        });
        nodeMap.keySet().forEach(id -> treeRoot.putIfAbsent(id, id));

        // Group by tree
        Map<String, List<String>> treeMap = new LinkedHashMap<>();
        treeRoot.forEach((id, r) -> treeMap.computeIfAbsent(r, k -> new ArrayList<>()).add(id));

        // BFS layering per tree
        Map<String, Integer> nodeLayer = new LinkedHashMap<>();
        int globalMaxLayer = 0;
        for (String rootId : roots) {
            List<String> ids = treeMap.get(rootId);
            if (ids == null) continue;
            java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>();
            q.add(rootId);
            nodeLayer.put(rootId, 0);
            int maxL = 0;
            while (!q.isEmpty()) {
                String cur = q.poll();
                int nl = nodeLayer.get(cur) + 1;
                for (String next : outgoing.getOrDefault(cur, List.of())) {
                    if (ids.contains(next) && !nodeLayer.containsKey(next)) {
                        nodeLayer.put(next, nl);
                        maxL = Math.max(maxL, nl);
                        q.add(next);
                    }
                }
            }
            for (String id : ids) {
                if (!nodeLayer.containsKey(id)) {
                    nodeLayer.put(id, ++maxL);
                }
            }
            globalMaxLayer = Math.max(globalMaxLayer, maxL);
        }

        // Group by layer
        Map<Integer, List<String>> byLayer = new TreeMap<>();
        nodeLayer.forEach((id, l) -> byLayer.computeIfAbsent(l, k -> new ArrayList<>()).add(id));

        // Barycenter ordering within layers
        Map<Integer, List<String>> ordered = new LinkedHashMap<>();
        byLayer.entrySet().stream().findFirst().ifPresent(e -> ordered.put(e.getKey(), e.getValue()));
        List<Integer> layerKeys = new ArrayList<>(byLayer.keySet());
        for (int li = 1; li < layerKeys.size(); li++) {
            int ck = layerKeys.get(li);
            int pk = layerKeys.get(li - 1);
            List<String> prevList = ordered.get(pk);
            if (prevList == null) { ordered.put(ck, byLayer.get(ck)); continue; }
            Map<String, Double> bar = new LinkedHashMap<>();
            for (String id : byLayer.get(ck)) {
                List<String> preds = edges.stream()
                        .filter(e -> e.getToNodeId().equals(id))
                        .map(Edge::getFromNodeId)
                        .collect(Collectors.toList());
                List<String> inPrev = preds.stream().filter(prevList::contains).collect(Collectors.toList());
                double sum = 0;
                for (String p : inPrev) {
                    int idx = prevList.indexOf(p);
                    if (idx >= 0) sum += idx;
                }
                bar.put(id, inPrev.size() > 0 ? sum / inPrev.size() : -1);
            }
            ordered.put(ck, byLayer.get(ck).stream()
                    .sorted((a, b) -> {
                        double ba = bar.getOrDefault(a, -1.0);
                        double bb = bar.getOrDefault(b, -1.0);
                        return Double.compare(ba < 0 ? 9999 : ba, bb < 0 ? 9999 : bb);
                    })
                    .collect(Collectors.toList()));
        }

        // Assign positions with fixed spacing (global Sugiyama-style layout)
        int H_GAP = 200;
        int V_GAP = 250;
        Map<String, double[]> positions = new LinkedHashMap<>();

        // Use barycenter-ordered layers and assign global grid positions
        int minLayer = byLayer.keySet().stream().findFirst().orElse(0);
        for (Map.Entry<Integer, List<String>> entry : ordered.entrySet()) {
            int l = entry.getKey();
            List<String> layerIds = entry.getValue();
            for (int i = 0; i < layerIds.size(); i++) {
                positions.put(layerIds.get(i), new double[]{
                        i * (double) H_GAP - (layerIds.size() - 1) * (double) H_GAP / 2.0,
                        (l - minLayer) * (double) V_GAP
                });
            }
        }

        // Compute total extent for centering
        double maxX = 0, maxY = 0;
        for (double[] p : positions.values()) {
            if (Math.abs(p[0]) > maxX) maxX = Math.abs(p[0]);
            if (Math.abs(p[1]) > maxY) maxY = Math.abs(p[1]);
        }

        return positions;
    }

    /** Parse Graphviz SVG output to extract node center positions. */
    private Map<String, double[]> parseSvgNodePositions(String svgContent) {
        Map<String, double[]> positions = new LinkedHashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(svgContent.getBytes(StandardCharsets.UTF_8)));

            NodeList groups = doc.getDocumentElement().getElementsByTagName("g");
            for (int i = 0; i < groups.getLength(); i++) {
                Element g = (Element) groups.item(i);
                if (!"node".equals(g.getAttribute("class"))) continue;

                NodeList titles = g.getElementsByTagName("title");
                if (titles.getLength() == 0) continue;
                String nodeId = titles.item(0).getTextContent().trim();
                if (nodeId.isEmpty()) continue;

                // Ellipse-shaped nodes (most common)
                NodeList ellipses = g.getElementsByTagName("ellipse");
                if (ellipses.getLength() > 0) {
                    Element ell = (Element) ellipses.item(0);
                    double ex = Double.parseDouble(ell.getAttribute("cx"));
                    double ey = Double.parseDouble(ell.getAttribute("cy"));
                    positions.put(nodeId, new double[]{ex, ey});
                    continue;
                }

                // Polygon-shaped nodes (terminals in box shape)
                NodeList polygons = g.getElementsByTagName("polygon");
                if (polygons.getLength() > 0) {
                    Element poly = (Element) polygons.item(0);
                    positions.put(nodeId, polygonCenter(poly.getAttribute("points")));
                }
            }
        } catch (Exception e) {
            System.err.println("SVG node position parse failed: " + e.getMessage());
        }
        return positions;
    }

    /** Compute centroid from SVG polygon points string "x1,y1 x2,y2 ...". */
    private double[] polygonCenter(String pointsAttr) {
        String[] pairs = pointsAttr.trim().split("\\s+");
        double sumX = 0, sumY = 0;
        for (String pair : pairs) {
            String[] xy = pair.split(",");
            sumX += Double.parseDouble(xy[0]);
            sumY += Double.parseDouble(xy[1]);
        }
        return new double[]{sumX / pairs.length, sumY / pairs.length};
    }

    private List<String> splitPath(String path) {
        if (path == null || path.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(path.split("->"))
                .filter(id -> !id.isBlank())
                .collect(Collectors.toList());
    }

    private List<String> buildEdgePath(String path) {
        List<String> nodePath = splitPath(path);
        if (nodePath.size() < 2) {
            return Collections.emptyList();
        }
        return java.util.stream.IntStream.range(0, nodePath.size() - 1)
                .mapToObj(i -> nodePath.get(i) + "->" + nodePath.get(i + 1))
                .collect(Collectors.toList());
    }

    /**
     * Generate a standalone HTML graph page with embedded JSON data (G6 + ELK).
     */
    public String generateStandaloneHtml(String graphJson) {
        String safeJson = graphJson == null || graphJson.isBlank() ? "{\"nodes\":[],\"edges\":[],\"errors\":[]}" : graphJson;
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>PipeFlowCheck 管网图</title>
                  <script src="https://unpkg.com/@antv/g6@4.8.24/dist/g6.min.js"></script>
                  <script src="https://unpkg.com/elkjs@0.8.2/lib/elk.bundled.min.js"></script>
                  <style>
                    *{box-sizing:border-box}body{margin:0;font-family:Arial,"Microsoft YaHei",sans-serif;background:#eef2f7;color:#111827}
                    .app{height:100vh;display:grid;grid-template-columns:minmax(0,1fr) 360px;gap:14px;padding:14px}
                    .card{background:#fff;border-radius:14px;box-shadow:0 10px 30px rgba(15,23,42,.08);overflow:hidden}
                    .header{height:54px;border-bottom:1px solid #e5e7eb;display:flex;align-items:center;padding:0 18px}
                    .header h1{margin:0;font-size:17px}.header .hint{font-size:12px;color:#6b7280;margin-left:auto}
                    #graph-container{width:100%;height:calc(100vh - 68px);background:#fff}
                    .side{padding:14px;overflow-y:auto}
                    .side h2{margin:0 0 10px;font-size:15px}
                    .section{margin-top:14px;padding-top:12px;border-top:1px solid #e5e7eb}
                    .btn-row{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:10px}
                    button{border:1px solid #cbd5e1;background:#fff;border-radius:8px;padding:6px 10px;cursor:pointer;font-size:12px}
                    button.active{background:#eff6ff;color:#1d4ed8;border-color:#93c5fd}
                    button:hover{background:#f8fafc}
                    .legend{display:grid;grid-template-columns:1fr 1fr;gap:6px;font-size:12px}
                    .legend-item{display:flex;align-items:center;gap:6px}
                    .dot{width:12px;height:12px;border-radius:999px;border:1px solid #475569;flex:0 0 auto}
                    .kv{font-size:12px;margin:6px 0;line-height:1.6;word-break:break-all}
                    .kv strong{color:#111827}
                    .error-box{background:#fff1f2;border:1px solid #fecdd3;border-radius:8px;padding:8px;margin-bottom:8px;font-size:12px;line-height:1.6;cursor:pointer}
                    .error-box:hover{background:#ffe4e6}
                    .status-ok{color:#15803d;font-weight:700}
                    .status-error{color:#dc2626;font-weight:700}
                    @media(max-width:1000px){.app{grid-template-columns:1fr;height:auto}#graph-container{height:550px}}
                  </style>
                </head>
                <body>
                  <div class="app">
                    <div class="card">
                      <div class="header"><h1>PipeFlowCheck 管网图</h1><div class="hint">拖动/滚轮缩放</div></div>
                      <div id="graph-container"></div>
                    </div>
                    <div class="card side">
                      <h2>操作</h2>
                      <div class="btn-row">
                        <button class="active" data-view="global" onclick="switchView('global')">全量拓扑</button>
                        <button data-view="error" onclick="switchView('error')">错误子图</button>
                      </div>
                      <div class="section"><h2>详情</h2><div id="detail">点击节点或边查看详情。</div></div>
                      <div class="section"><h2>错误链路</h2><div id="errorList"></div></div>
                      <div class="section"><h2>节点图例</h2><div class="legend" id="legend"></div></div>
                    </div>
                  </div>
                <script>
                var graphData = __GRAPH_DATA__;
                var NODE_COLORS={RAIN_INLET:'#FACC15',RAIN_WELL:'#FEF08A',SEWAGE_INLET:'#6B7280',SEWAGE_WELL:'#9CA3AF',COMBINED_WELL:'#A855F7',WWTP:'#22C55E',RIVER:'#38BDF8',LAKE:'#7DD3FC',RAIN_OUTLET:'#0EA5E9',LIFE_SEWAGE_INLET:'#92400E',NORMAL:'#E5E7EB'};
                var NODE_TYPE_NAMES={RAIN_INLET:'雨水口',RAIN_WELL:'雨水井',SEWAGE_INLET:'污水口',SEWAGE_WELL:'污水井',COMBINED_WELL:'合流井',WWTP:'污水处理厂',RIVER:'河流',LAKE:'湖泊',RAIN_OUTLET:'雨水排口',LIFE_SEWAGE_INLET:'生活污水口',NORMAL:'普通节点'};
                function channelColor(t){return t==='RAIN'?'#0EA5E9':t==='SEWAGE'?'#64748B':t==='COMBINED'?'#8B5CF6':t==='LIFE_SEWAGE'?'#92400E':'#94A3B8'}
                var currentView='global',g6Graph=null,elkInstance=null,positionsCache=null;

	                function elkOpts(nc){var sn=100,lg=250,cg=200;if(nc<=30){sn=60;lg=180;cg=120}else if(nc<=100){sn=80;lg=220;cg=160}else{sn=120;lg=300;cg=240}return{'elk.algorithm':'layered','elk.direction':'DOWN','elk.edgeRouting':'POLYLINE','elk.spacing.nodeNode':String(sn),'elk.spacing.edgeNode':'40','elk.spacing.edgeEdge':'20','elk.spacing.componentComponent':String(cg),'elk.layered.spacing.nodeNodeBetweenLayers':String(lg),'elk.layered.nodePlacement.strategy':'NETWORK_SIMPLEX','elk.layered.crossingMinimization.strategy':'LAYER_SWEEP','elk.layered.mergeEdges':'true','elk.partitioning.activate':'false'}}

	                function nodeR(n){return(n.isEntry||n.isTerminal)?22:20}

	                async function runElk(nodes,edges){
	                  if(!elkInstance){try{elkInstance=new ELK()}catch(e){console.warn('ELK fail',e);return null}}
	                  if(!nodes||!nodes.length)return null;
	                  var ch=nodes.map(function(n){var r=nodeR(n);return{id:n.id,width:r*2+4,height:r*2+4}});
	                  var ek=edges.map(function(e){return{id:e.id,sources:[e.from],targets:[e.to]}});
	                  try{
	                    var r=await elkInstance.layout({id:'root',layoutOptions:elkOpts(nodes.length),children:ch,edges:ek});
	                    if(!r||!r.children)return null;
	                    var pos={},mx=1/0,my=1/0,MX=-1/0,MY=-1/0;
	                    r.children.forEach(function(c){if(c.x!==void 0){pos[c.id]={x:c.x,y:c.y};if(c.x<mx)mx=c.x;if(c.x>MX)MX=c.x;if(c.y<my)my=c.y;if(c.y>MY)MY=c.y}});
	                    var spanX=MX-mx,spanY=MY-my,maxSpan=Math.max(spanX,spanY,1);
	                    var scale=Math.min(3000,maxSpan)/maxSpan,cx=(mx+MX)/2,cy=(my+MY)/2;
	                    Object.keys(pos).forEach(function(id){pos[id].x=(pos[id].x-cx)*scale;pos[id].y=(pos[id].y-cy)*scale});
	                    return pos;
	                  }catch(e){console.warn('ELK layout fail',e);return null}
	                }

	                function getViewData(){
	                  if(!graphData||!graphData.nodes)return{nodes:[],edges:[],errors:[]};
	                  if(currentView==='global'){return graphData}
	                  if(currentView==='error'){
	                    var ids=new Set;
	                    (graphData.errors||[]).forEach(function(e){(e.nodePath||[]).forEach(function(n){ids.add(n)})});
	                    var eids=new Set;
	                    (graphData.errors||[]).forEach(function(e){(e.edgePath||[]).forEach(function(ed){eids.add(ed)})});
	                    graphData.edges.forEach(function(e){if(ids.has(e.from)&&!ids.has(e.to)){ids.add(e.to);eids.add(e.id)}if(ids.has(e.to)&&!ids.has(e.from)){ids.add(e.from);eids.add(e.id)}});
	                    return{nodes:graphData.nodes.filter(function(n){return ids.has(n.id)}),edges:graphData.edges.filter(function(e){return eids.has(e.id)}),errors:graphData.errors||[]}
	                  }
	                  return{nodes:[],edges:[],errors:[]}
	                }

	                async function renderGraph(){
	                  var cont=document.getElementById('graph-container');
	                  var vd=getViewData();
	                  if(!vd||!vd.nodes||!vd.nodes.length){
	                    if(g6Graph){g6Graph.destroy();g6Graph=null}
	                    cont.innerHTML='<div style="padding:40px;color:#6b7280;text-align:center">暂无数据</div>';
	                    return
	                  }
	                  positionsCache=await runElk(vd.nodes,vd.edges);

	                  var errN=new Set;
	                  (graphData.errors||[]).forEach(function(e){(e.nodePath||[]).forEach(function(n){errN.add(n)})});
	                  var errE=new Set;
	                  (graphData.errors||[]).forEach(function(e){(e.edgePath||[]).forEach(function(ed){errE.add(ed)})});

	                  var showLabel=currentView!=='global';
	                  var gn=vd.nodes.map(function(n){
	                    var pos=positionsCache?positionsCache[n.id]:{x:n.x||0,y:n.y||0};
	                    var isErr=n.isError||errN.has(n.id);
	                    var r=nodeR(n);
	                    var lbl=showLabel||isErr||n.isEntry||n.isTerminal?n.name+'\\n'+n.id:'';
	                    return{id:n.id,x:pos.x,y:pos.y,size:r*2,label:lbl,
	                      style:{fill:NODE_COLORS[n.type]||'#E5E7EB',stroke:isErr?'#DC2626':'#475569',lineWidth:isErr?5:2.5,shadowBlur:isErr?12:0,shadowColor:isErr?'rgba(220,38,38,0.45)':'transparent'},
	                      labelCfg:{style:{fill:'#1f2937',fontSize:10,fontWeight:isErr?700:400},position:'bottom',offset:6},
	                      anchorPoints:[[0.5,0],[0.5,1],[0,0.5],[1,0.5]],
	                      _data:n}
	                  });

	                  var ge=vd.edges.map(function(e){
	                    var isErr=e.isError||errE.has(e.id);
	                    var lc=isErr?'#DC2626':channelColor(e.type);
	                    return{id:e.id,source:e.from,target:e.to,label:e.type,
	                      style:{stroke:lc,lineWidth:isErr?5:3,lineDash:isErr?[8,5]:void 0,endArrow:{path:'M 0,0 L 14,6 L 14,-6 Z',fill:lc,d:14},radius:6},
	                      labelCfg:{style:{fill:lc,fontSize:9,fontWeight:isErr?700:400},autoRotate:true},
	                      _data:e}
	                  });

	                  if(g6Graph){g6Graph.destroy();g6Graph=null}
	                  cont.innerHTML='';
	                  var w=cont.clientWidth||800,h=cont.clientHeight||620;
	                  g6Graph=new G6.Graph({
	                    container:'graph-container',width:w,height:h,
	                    modes:{default:['drag-canvas','zoom-canvas','click-select']},
	                    defaultNode:{type:'circle',size:40},
	                    defaultEdge:{type:'polyline',style:{stroke:'#94A3B8',lineWidth:3,endArrow:{path:'M 0,0 L 14,6 L 14,-6 Z',d:14}},labelCfg:{autoRotate:true}},
	                    layout:{type:'none'},animate:false,fitView:false
	                  });
	                  g6Graph.data({nodes:gn,edges:ge});
	                  g6Graph.render();
	                  g6Graph.fitView(40);
	                  if(g6Graph.getZoom){var z=g6Graph.getZoom();if(vd.nodes.length>50&&z<0.35)g6Graph.zoomTo(0.35)}
	                  g6Graph.on('node:click',function(evt){var m=evt.item.getModel();showNodeDetail(m._data||m)});
	                  g6Graph.on('edge:click',function(evt){var m=evt.item.getModel();showEdgeDetail(m._data||m)});
	                }

                async function switchView(v){
                  currentView=v;
                  document.querySelectorAll('[data-view]').forEach(function(b){b.classList.toggle('active',b.dataset.view===v)});
                  await renderGraph()
                }

                function showNodeDetail(n){
                  document.getElementById('detail').innerHTML=
                    '<div class="kv"><strong>节点编号：</strong>'+esc(n.id||'')+'</div>'+
                    '<div class="kv"><strong>节点名称：</strong>'+esc(n.name||'')+'</div>'+
                    '<div class="kv"><strong>节点类型：</strong>'+esc(NODE_TYPE_NAMES[n.type]||n.type||'')+'</div>'+
                    '<div class="kv"><strong>状态：</strong>'+(n.isError?'<span class="status-error">错误相关节点</span>':'<span class="status-ok">正常</span>')+'</div>'+
                    '<div class="kv"><strong>备注：</strong>'+esc(n.remark||'')+'</div>'
                }
                function showEdgeDetail(e){
                  document.getElementById('detail').innerHTML=
                    '<div class="kv"><strong>边编号：</strong>'+esc(e.id||'')+'</div>'+
                    '<div class="kv"><strong>上游节点：</strong>'+esc(e.from||e.source||'')+'</div>'+
                    '<div class="kv"><strong>下游节点：</strong>'+esc(e.to||e.target||'')+'</div>'+
                    '<div class="kv"><strong>通道类型：</strong>'+esc(e.type||'')+'</div>'+
                    '<div class="kv"><strong>状态：</strong>'+(e.isError?'<span class="status-error">错误链路</span>':'<span class="status-ok">正常</span>')+'</div>'
                }
                function esc(v){return String(v).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;')}

                function renderLegend(){
                  var el=document.getElementById('legend');
                  el.innerHTML=Object.keys(NODE_TYPE_NAMES).filter(function(t){return NODE_COLORS[t]}).map(function(type){return'<div class="legend-item"><span class="dot" style="background:'+NODE_COLORS[type]+'"></span>'+NODE_TYPE_NAMES[type]+'</div>'}).join('')
                }
                function renderErrors(){
                  var box=document.getElementById('errorList');
                  if(!graphData.errors||!graphData.errors.length){box.innerHTML='<div class="kv">暂无错误链路。</div>';return}
                  box.innerHTML=graphData.errors.map(function(err){return'<div class="error-box"><strong>'+esc(err.taskId)+' | '+esc(err.errorCode)+'</strong><br/>'+esc(err.errorReason)+'<br/><strong>路径：</strong>'+esc(err.readablePath||'')+'</div>'}).join('')
                }

                window.addEventListener('resize',function(){
                  if(g6Graph&&!g6Graph.get('destroyed')){var c=document.getElementById('graph-container');if(c.clientWidth>0&&c.clientHeight>0)g6Graph.changeSize(c.clientWidth,c.clientHeight)}
                });
                renderLegend();renderErrors();renderGraph();
                </script>
                </body>
                </html>
                """.replace("__GRAPH_DATA__", safeJson);
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
