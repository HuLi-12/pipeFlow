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

    /** Collect only the task start nodes for error highlighting. */
    public Set<String> collectErrorNodeIds(List<CheckResult> results) {
        return results.stream()
                .filter(r -> r.getErrorCode() != null)
                .map(CheckResult::getStartNodeId)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());
    }

    /** Edges are kept visible but are not highlighted as errors. */
    public Set<String> collectErrorEdgeKeys(List<CheckResult> results) {
        return Set.of();
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



    /**
     * Build front-end friendly graph JSON payload from network data and check results.
     * The payload shape is: {nodes:[], edges:[], errors:[]}.
     * Node x/y positions are computed via Graphviz for an overlap-free layout.
     */
    public Map<String, Object> buildGraphPayload(Map<String, Node> nodeMap, List<Edge> edges, List<CheckResult> results) {
        Set<String> errorNodeIds = collectErrorNodeIds(results);
        Set<String> errorEdgeKeys = collectErrorEdgeKeys(results);

        List<Map<String, Object>> nodePayload = nodeMap.values().stream()
                .map(node -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", node.getNodeId());
                    item.put("name", node.getNodeName());
                    item.put("type", node.getNodeType().name());
                    item.put("status", errorNodeIds.contains(node.getNodeId()) ? "error" : "normal");
                    item.put("remark", node.getRemark() == null ? "" : node.getRemark());
                    return item;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> edgePayload = edges.stream()
                .map(edge -> {
                    String edgeKey = edge.getFromNodeId() + "->" + edge.getToNodeId();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", edgeKey);
                    item.put("from", edge.getFromNodeId());
                    item.put("to", edge.getToNodeId());
                    item.put("type", edge.getChannelType().name());
                    item.put("status", errorEdgeKeys.contains(edgeKey) ? "error" : "normal");
                    item.put("remark", edge.getRemark() == null ? "" : edge.getRemark());
                    return item;
                })
                .collect(Collectors.toList());

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

        // Compute backend layout positions (overlap-free, fixed spacing) and add x/y to nodes
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
            // If Graphviz uses math coords (y-up), flip y
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

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodes", nodePayload);
        payload.put("edges", edgePayload);
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
     * Generate a standalone HTML graph page with embedded JSON data.
     * The page can be opened directly from the ZIP without requesting the backend.
     */
    public String generateStandaloneHtml(String graphJson) {
        String safeJson = graphJson == null || graphJson.isBlank() ? "{\"nodes\":[],\"edges\":[],\"errors\":[]}" : graphJson;
        return """
                <!DOCTYPE html>
                <html lang=\"zh-CN\">
                <head>
                  <meta charset=\"UTF-8\" />
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
                  <title>PipeFlowCheck 管网图</title>
                  <script src=\"https://cdn.jsdelivr.net/npm/echarts@5.5.1/dist/echarts.min.js\"></script>
                  <style>
                    * { box-sizing: border-box; }
                    body { margin:0; font-family: \"Microsoft YaHei\", Arial, sans-serif; background:#eef2f7; color:#111827; }
                    .app { height:100vh; display:grid; grid-template-columns:minmax(0,1fr) 360px; gap:14px; padding:14px; }
                    .card { background:#fff; border-radius:14px; box-shadow:0 10px 30px rgba(15,23,42,.08); overflow:hidden; }
                    .header { height:58px; border-bottom:1px solid #e5e7eb; display:flex; align-items:center; justify-content:space-between; padding:0 18px; }
                    .header h1 { margin:0; font-size:18px; }
                    .hint { font-size:12px; color:#6b7280; }
                    #graph { width:100%; height:calc(100vh - 86px); }
                    .side { padding:16px; overflow-y:auto; }
                    .side h2 { margin:0 0 10px; font-size:16px; }
                    .section { margin-top:16px; padding-top:14px; border-top:1px solid #e5e7eb; }
                    .btn-row { display:flex; gap:8px; margin-bottom:12px; }
                    button { border:1px solid #cbd5e1; background:#fff; border-radius:8px; padding:7px 10px; cursor:pointer; font-size:13px; }
                    button.active { background:#eff6ff; color:#1d4ed8; border-color:#93c5fd; }
                    button:hover { background:#f8fafc; }
                    .legend { display:grid; grid-template-columns:1fr 1fr; gap:8px; font-size:13px; }
                    .legend-item { display:flex; align-items:center; gap:6px; }
                    .dot { width:13px; height:13px; border-radius:999px; border:1px solid #475569; flex:0 0 auto; }
                    .kv { font-size:13px; margin:8px 0; line-height:1.6; word-break:break-all; }
                    .kv strong { color:#111827; }
                    .error-box { background:#fff1f2; border:1px solid #fecdd3; border-radius:10px; padding:10px; margin-bottom:10px; font-size:13px; line-height:1.7; }
                    .status-ok { color:#15803d; font-weight:700; }
                    .status-error { color:#dc2626; font-weight:700; }
                    @media (max-width:1000px) { .app { grid-template-columns:1fr; height:auto; } #graph { height:650px; } }
                  </style>
                </head>
                <body>
                  <div class=\"app\">
                    <div class=\"card\"><div class=\"header\"><h1>PipeFlowCheck 管网图</h1><div class=\"hint\">黄色=雨水，灰色=污水，红色虚线=错误链路</div></div><div id=\"graph\"></div></div>
                    <div class=\"card side\"><h2>操作</h2><div class=\"btn-row\"><button class=\"active\" data-graph-layer=\"all\" onclick=\"setGraphLayer('all')\">全部节点</button><button data-graph-layer=\"errorNodes\" onclick=\"setGraphLayer('errorNodes')\">错误节点</button><button data-graph-layer=\"errorPath\" onclick=\"setGraphLayer('errorPath')\">错误节点 + 路径</button></div><div class=\"section\"><h2>详情</h2><div id=\"detail\">点击节点或边查看详情。</div></div><div class=\"section\"><h2>错误链路</h2><div id=\"errorList\"></div></div><div class=\"section\"><h2>节点图例</h2><div class=\"legend\" id=\"legend\"></div></div></div>
                  </div>
                <script>
                const graphData = __GRAPH_DATA__;
                const nodeColorMap={RAIN_INLET:'#FACC15',RAIN_WELL:'#FEF08A',SEWAGE_INLET:'#6B7280',SEWAGE_WELL:'#9CA3AF',COMBINED_WELL:'#A855F7',WWTP:'#22C55E',RIVER:'#38BDF8',LAKE:'#7DD3FC',RAIN_OUTLET:'#0EA5E9',LIFE_SEWAGE_INLET:'#92400E',NORMAL:'#E5E7EB'};
                const nodeTypeNameMap={RAIN_INLET:'雨水口',RAIN_WELL:'雨水井',SEWAGE_INLET:'污水口',SEWAGE_WELL:'污水井',COMBINED_WELL:'合流井',WWTP:'污水处理厂',RIVER:'河流',LAKE:'湖泊',RAIN_OUTLET:'雨水排口',LIFE_SEWAGE_INLET:'生活污水口',NORMAL:'普通节点'};
                function channelColor(type){ if(type==='RAIN')return '#0EA5E9'; if(type==='SEWAGE')return '#64748B'; if(type==='COMBINED')return '#8B5CF6'; if(type==='LIFE_SEWAGE')return '#92400E'; return '#94A3B8'; }
                const chart=echarts.init(document.getElementById('graph'));
                /* forest layout: one tree per root, columns side by side */
                function computeLayout(nodes,edges){
                  if(!nodes||!nodes.length)return{positions:{},nodeLayer:{},treeIds:{}};
                  if(nodes.length===1)return{positions:{[nodes[0].id]:{x:0,y:0}},nodeLayer:{[nodes[0].id]:0},treeIds:{[nodes[0].id]:nodes[0].id}};
                  const out={};nodes.forEach(n=>{out[n.id]=[];});edges.forEach(e=>{if(out[e.from])out[e.from].push(e.to);});
                  const deg={};nodes.forEach(n=>{deg[n.id]=0;});edges.forEach(e=>{if(deg[e.to]!==undefined)deg[e.to]++;});
                  const roots=nodes.filter(n=>deg[n.id]===0).map(n=>n.id);if(roots.length===0&&nodes.length>0)roots.push(nodes[0].id);
                  const tr={};roots.forEach(r=>tr[r]=r);
                  roots.forEach(root=>{const q=[root];while(q.length){const c=q.shift();(out[c]||[]).forEach(n=>{if(!tr[n]){tr[n]=root;q.push(n);}});}});
                  nodes.forEach(n=>{if(!tr[n.id])tr[n.id]=n.id;});
                  const tm={};Object.entries(tr).forEach(([id,r])=>{(tm[r]||(tm[r]=[])).push(id);});
                  const V=350,H=300,tGap=450,pos={},nL={};let tw=0;
                  Object.entries(tm).forEach(([rid,ids]) => {
                    const te=edges.filter(e=>ids.includes(e.from)&&ids.includes(e.to));
                    const tl={},q=[rid];tl[rid]=0;const vs=new Set(q);let mL=0;
                    while(q.length){const c=q.shift(),nl=tl[c]+1;(out[c]||[]).forEach(n=>{if(ids.includes(n)&&!vs.has(n)){vs.add(n);tl[n]=nl;mL=Math.max(mL,nl);q.push(n);}});}
                    ids.forEach(id=>{if(tl[id]===undefined)tl[id]=++mL;});
                    const bL={};ids.forEach(id=>{(bL[tl[id]]||(bL[tl[id]]=[])).push(id);});const ks=Object.keys(bL).sort((a,b)=>a-b);
                    const od={};if(ks.length>0)od[ks[0]]=bL[ks[0]];
                    for(let li=1;li<ks.length;li++){const ck=ks[li],pk=ks[li-1],pr=od[pk];const br={};bL[ck].forEach(id=>{const p=te.filter(e=>e.to===id).map(e=>e.from),pp=p.filter(x=>pr.includes(x));const s=pp.reduce((a,p)=>{const i=pr.indexOf(p);return a+(i>=0?i:0);},0);br[id]=pp.length>0?s/pp.length:-1;});od[ck]=[...bL[ck]].sort((a,b)=>{const ba=br[a]<0?9999:br[a],bb=br[b]<0?9999:br[b];return ba-bb;});}
                    let tW=0;
                    ks.forEach(l=>{const lid=od[l]||bL[l],lw=(lid.length-1)*H;lid.forEach((id,i)=>{pos[id]={x:tw+i*H,y:parseInt(l)*V};});tW=Math.max(tW,lw);});
                    tw+=Math.max(tW,H)+tGap;ids.forEach(id=>{nL[id]=tl[id];});
                  });
                  const off=-tw/2;if(off)Object.keys(pos).forEach(id=>{pos[id].x+=off;});
                  return{positions:pos,nodeLayer:nL,treeIds:tr};
                }
                let graphLayerMode='all';
                function buildOption(mode='all'){
                  const errorNodeIds=new Set((graphData.errors||[]).map(e=>e.startNodeId).filter(Boolean));
                  const errorEdgeIds=new Set();
                  const pathNodeIds=new Set((graphData.errors||[]).flatMap(e=>e.nodePath||[]));
                  const pathEdgeIds=new Set((graphData.errors||[]).flatMap(e=>e.edgePath||[]));
                  const focusErrorOnly=mode!=='all';
                  const showPath=mode==='errorPath';
                  const hasPos=graphData.nodes&&graphData.nodes.length>0&&graphData.nodes[0].x!==undefined;
                  let nlayer={},tids={},lay={positions:{},nodeLayer:{},treeIds:{}};
                  if(!hasPos){lay=computeLayout(graphData.nodes||[],graphData.edges||[]);nlayer=lay.nodeLayer||{};tids=lay.treeIds||{};}
                  const nodes=(graphData.nodes||[]).map(n=>{const isError=errorNodeIds.has(n.id);const isPathNode=showPath&&pathNodeIds.has(n.id);const faded=focusErrorOnly&&!(isError||isPathNode);let x=0,y=0;if(hasPos){x=n.x;y=n.y;}else{const p=lay.positions?lay.positions[n.id]:null;if(p){x=p.x;y=p.y;}}return{id:n.id,name:n.name+'\\n'+n.id,value:n,x:x,y:y,symbolSize:isError?44:32,itemStyle:{color:nodeColorMap[n.type]||nodeColorMap.NORMAL,borderColor:isError?'#DC2626':isPathNode?'#F59E0B':'#334155',borderWidth:isError?4:isPathNode?3:1.5,opacity:faded?0.22:1},label:{show:true,color:'#111827',fontSize:10,opacity:faded?0.25:1}};});
                  const links=(graphData.edges||[]).map(e=>{const isError=errorEdgeIds.has(e.id);const isPathEdge=showPath&&pathEdgeIds.has(e.id);const faded=focusErrorOnly&&!isPathEdge;const xt=tids[e.from]&&tids[e.to]&&tids[e.from]!==tids[e.to];let cv=0.15;if(!hasPos){const sl=nlayer[e.from],tl=nlayer[e.to];const dist=tl!==undefined&&sl!==undefined?Math.abs(tl-sl):1;if(dist>=3)cv=0.8;else if(dist>=2)cv=0.5;const srcPos=lay.positions?lay.positions[e.from]:null;if(srcPos&&srcPos.x>0)cv=-cv;}return{source:e.from,target:e.to,value:e,lineStyle:{color:isError?'#DC2626':isPathEdge?'#F59E0B':channelColor(e.type),width:isError?5:isPathEdge?4:xt?1.5:2,type:isError?'dashed':xt?'dotted':'solid',curveness:cv,opacity:faded?0.16:xt?0.4:0.95},label:{show:showPath&&isPathEdge,formatter:e.type,color:isError?'#DC2626':isPathEdge?'#B45309':'#475569',fontSize:11,opacity:faded?0.2:1}};});
                  return {animationDuration:500,tooltip:{trigger:'item',formatter:function(params){if(params.dataType==='node'){const n=params.data.value;return '<b>'+n.name+' ('+n.id+')</b><br/>类型：'+(nodeTypeNameMap[n.type]||n.type)+'<br/>状态：'+(n.status==='error'?'错误相关节点':'正常')+'<br/>备注：'+(n.remark||'');}if(params.dataType==='edge'){const e=params.data.value;return '<b>'+e.from+' → '+e.to+'</b><br/>通道：'+e.type+'<br/>状态：'+(e.status==='error'?'错误链路':'正常')+'<br/>备注：'+(e.remark||'');}return'';}},series:[{type:'graph',layout:'none',roam:true,zoom:1,center:[0,0],draggable:true,edgeSymbol:['none','arrow'],edgeSymbolSize:[0,13],emphasis:{focus:'adjacency',lineStyle:{width:6}},data:nodes,links:links,label:{position:'inside'},edgeLabel:{show:showPath},lineStyle:{opacity:.95}}]};
                }
                function renderGraph(mode=graphLayerMode){ chart.setOption(buildOption(mode),true); chart.off('click'); chart.on('click',function(params){ if(params.dataType==='node')showNodeDetail(params.data.value); if(params.dataType==='edge')showEdgeDetail(params.data.value); }); }
                function setGraphLayer(mode){graphLayerMode=mode;document.querySelectorAll('[data-graph-layer]').forEach(b=>b.classList.toggle('active',b.dataset.graphLayer===mode));renderGraph(mode);}
                function focusErrorPath(){ setGraphLayer('errorPath'); }
                function resetGraph(){ setGraphLayer('all'); }
                function showNodeDetail(n){ document.getElementById('detail').innerHTML='<div class=\"kv\"><strong>节点编号：</strong>'+n.id+'</div><div class=\"kv\"><strong>节点名称：</strong>'+n.name+'</div><div class=\"kv\"><strong>节点类型：</strong>'+(nodeTypeNameMap[n.type]||n.type)+'</div><div class=\"kv\"><strong>状态：</strong>'+(n.status==='error'?'<span class=\"status-error\">错误相关节点</span>':'<span class=\"status-ok\">正常</span>')+'</div><div class=\"kv\"><strong>备注：</strong>'+(n.remark||'')+'</div>'; }
                function showEdgeDetail(e){ document.getElementById('detail').innerHTML='<div class=\"kv\"><strong>边编号：</strong>'+e.id+'</div><div class=\"kv\"><strong>上游节点：</strong>'+e.from+'</div><div class=\"kv\"><strong>下游节点：</strong>'+e.to+'</div><div class=\"kv\"><strong>通道类型：</strong>'+e.type+'</div><div class=\"kv\"><strong>状态：</strong>'+(e.status==='error'?'<span class=\"status-error\">错误链路</span>':'<span class=\"status-ok\">正常</span>')+'</div><div class=\"kv\"><strong>备注：</strong>'+(e.remark||'')+'</div>'; }
                function renderLegend(){ const legend=document.getElementById('legend'); legend.innerHTML=Object.entries(nodeTypeNameMap).filter(([t])=>nodeColorMap[t]).map(([type,name])=>'<div class=\"legend-item\"><span class=\"dot\" style=\"background:'+nodeColorMap[type]+'\"></span>'+name+'</div>').join(''); }
                function renderErrors(){ const box=document.getElementById('errorList'); if(!graphData.errors||graphData.errors.length===0){box.innerHTML='<div class=\"kv\">暂无错误链路。</div>';return;} box.innerHTML=graphData.errors.map(err=>'<div class=\"error-box\"><strong>'+err.taskId+'｜'+err.errorCode+'</strong><br/>'+err.errorReason+'<br/><strong>错误路径：</strong>'+err.readablePath+'</div>').join(''); }
                window.addEventListener('resize',()=>chart.resize()); renderLegend(); renderErrors(); renderGraph();
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
