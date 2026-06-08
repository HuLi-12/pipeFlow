package com.example.pipeflowcheck.service;

import com.example.pipeflowcheck.config.RuleDefinition;
import com.example.pipeflowcheck.enums.ErrorCode;
import com.example.pipeflowcheck.model.CheckContext;
import com.example.pipeflowcheck.model.CheckResult;
import com.example.pipeflowcheck.model.CheckTask;
import com.example.pipeflowcheck.model.Edge;
import com.example.pipeflowcheck.model.Node;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class PipeCheckService {

    /** 单条路径最大深度，防止 StackOverflow */
    static final int MAX_DEPTH = 500;
    /** 单 Task 最大路径数，防止无限扩展 */
    static final int MAX_PATHS = 10000;

    private final RuleEngine ruleEngine;
    private final RuleConfigService ruleConfigService;

    public List<CheckResult> checkAll(Map<String, Node> nodeMap,
                                      Map<String, List<Edge>> graph,
                                      List<CheckTask> tasks) {
        RuleDefinition rules = ruleConfigService.loadDefault();
        List<CheckResult> results = new ArrayList<>();
        for (CheckTask task : tasks) {
            results.addAll(checkSingleTask(nodeMap, graph, task, rules));
        }
        return results;
    }

    private List<CheckResult> checkSingleTask(Map<String, Node> nodeMap,
                                              Map<String, List<Edge>> graph,
                                              CheckTask task,
                                              RuleDefinition rules) {
        CheckContext context = new CheckContext();
        context.setTask(task);
        context.setPathCounter(new int[]{0});
        return dfs(task.getStartNodeId(), nodeMap, graph, context, rules);
    }

    private List<CheckResult> dfs(String currentNodeId,
                                  Map<String, Node> nodeMap,
                                  Map<String, List<Edge>> graph,
                                  CheckContext context,
                                  RuleDefinition rules) {
        List<CheckResult> results = new ArrayList<>();

        // --- node existence ---
        if (!nodeMap.containsKey(currentNodeId)) {
            results.add(error(context, nodeMap, currentNodeId, ErrorCode.NODE_NOT_FOUND,
                    "节点不存在：" + currentNodeId));
            return results;
        }

        // --- cycle detection ---
        if (context.getVisited().contains(currentNodeId)) {
            context.getNodePath().add(currentNodeId);
            results.add(error(context, nodeMap, currentNodeId, ErrorCode.CYCLE_FOUND,
                    "检测到环路，节点：" + currentNodeId));
            return results;
        }

        context.getVisited().add(currentNodeId);
        context.getNodePath().add(currentNodeId);

        // --- max depth protection (check after adding, so depth includes current node) ---
        if (context.getNodePath().size() >= MAX_DEPTH) {
            results.add(error(context, nodeMap, currentNodeId, ErrorCode.PATH_TOO_DEEP,
                    "路径超过最大深度 " + MAX_DEPTH + "，节点：" + currentNodeId));
            return results;
        }

        // --- max path count protection ---
        if (context.getPathCounter()[0] >= MAX_PATHS) {
            results.add(error(context, nodeMap, currentNodeId, ErrorCode.TOO_MANY_PATHS,
                    "下游路径数量超过最大限制 " + MAX_PATHS + "，节点：" + currentNodeId));
            return results;
        }

        Node currentNode = nodeMap.get(currentNodeId);
        List<Edge> nextEdges = graph.getOrDefault(currentNodeId, Collections.emptyList());

        // --- TERMINAL_HAS_DOWNSTREAM: terminal-type node still has outgoing edges ---
        if (!nextEdges.isEmpty() && rules.getTerminalTypes().contains(currentNode.getNodeType())) {
            results.add(error(context, nodeMap, currentNodeId, ErrorCode.TERMINAL_HAS_DOWNSTREAM,
                    "终点类型节点 " + currentNode.getNodeName() + "(" + currentNode.getNodeType() + ") 存在下游边"));
            return results;
        }

        // --- leaf / terminal node ---
        if (nextEdges.isEmpty()) {
            context.getPathCounter()[0]++;
            boolean validEnd = ruleEngine.isValidEnd(
                    rules,
                    context.getTask().getStartType(),
                    context.isEnteredSpecialChannel(),
                    currentNode.getNodeType()
            );
            if (validEnd) {
                results.add(success(context, nodeMap, currentNode));
            } else {
                ErrorCode errorCode = rules.getTerminalTypes().contains(currentNode.getNodeType())
                        ? ErrorCode.INVALID_END
                        : ErrorCode.DEAD_END;
                String reason = errorCode == ErrorCode.DEAD_END
                        ? "路径中断，当前节点无下游：" + currentNode.getNodeName()
                        : "未抵达合法终点，当前终点：" + currentNode.getNodeName();
                results.add(error(context, nodeMap, currentNodeId, errorCode, reason));
            }
            return results;
        }

        // --- branch traversal ---
        for (Edge edge : nextEdges) {
            if (context.getPathCounter()[0] >= MAX_PATHS) {
                break;
            }
            CheckContext branch = context.copy();

            if (!ruleEngine.isChannelAllowed(rules, branch.getTask().getStartType(), edge.getChannelType())) {
                branch.getNodePath().add(edge.getToNodeId());
                context.getPathCounter()[0]++;
                results.add(error(branch, nodeMap, edge.getToNodeId(), ErrorCode.CHANNEL_NOT_ALLOWED,
                        "入口类型 " + branch.getTask().getStartType() + " 不允许进入通道：" + edge.getChannelType()));
                continue;
            }

            if (ruleEngine.shouldEnterSpecialState(rules, branch.getTask().getStartType(), edge.getChannelType())) {
                branch.setEnteredSpecialChannel(true);
            }

            // record the channel traversed
            branch.getChannelPath().add(edge.getChannelType().name());

            results.addAll(dfs(edge.getToNodeId(), nodeMap, graph, branch, rules));
        }
        return results;
    }

    private CheckResult success(CheckContext context, Map<String, Node> nodeMap, Node endNode) {
        CheckTask task = context.getTask();
        return CheckResult.builder()
                .taskId(task.getTaskId())
                .startNodeId(task.getStartNodeId())
                .startNodeName(nodeName(nodeMap, task.getStartNodeId()))
                .startType(task.getStartType().name())
                .status("通道正常")
                .endNodeId(endNode.getNodeId())
                .endNodeName(endNode.getNodeName())
                .path(String.join("->", context.getNodePath()))
                .channelPath(context.getChannelPath().isEmpty() ? "" : String.join("->", context.getChannelPath()))
                .readablePath(buildReadablePath(context, nodeMap))
                .riskLevel("无")
                .build();
    }

    private CheckResult error(CheckContext context, Map<String, Node> nodeMap, String endNodeId,
                              ErrorCode code, String reason) {
        CheckTask task = context.getTask();
        return CheckResult.builder()
                .taskId(task.getTaskId())
                .startNodeId(task.getStartNodeId())
                .startNodeName(nodeName(nodeMap, task.getStartNodeId()))
                .startType(task.getStartType().name())
                .status("错误")
                .endNodeId(endNodeId)
                .endNodeName(nodeName(nodeMap, endNodeId))
                .path(context.getNodePath().stream().collect(Collectors.joining("->")))
                .channelPath(context.getChannelPath().isEmpty() ? "" : String.join("->", context.getChannelPath()))
                .readablePath(buildReadablePath(context, nodeMap))
                .errorCode(code)
                .errorReason(reason)
                .riskLevel(riskLevel(code))
                .build();
    }

    private String buildReadablePath(CheckContext context, Map<String, Node> nodeMap) {
        List<String> nodePath = context.getNodePath();
        List<String> channelPath = context.getChannelPath();
        if (nodePath.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(formatNode(nodeMap, nodePath.get(0)));
        int channelCount = Math.min(channelPath.size(), nodePath.size() - 1);
        for (int i = 0; i < channelCount; i++) {
            sb.append(" --").append(channelPath.get(i)).append("--> ");
            sb.append(formatNode(nodeMap, nodePath.get(i + 1)));
        }
        // if nodePath has more nodes than channels (e.g. last node didn't traverse an edge)
        for (int i = channelCount + 1; i < nodePath.size(); i++) {
            sb.append(" -> ");
            sb.append(formatNode(nodeMap, nodePath.get(i)));
        }
        return sb.toString();
    }

    private String formatNode(Map<String, Node> nodeMap, String nodeId) {
        Node node = nodeMap.get(nodeId);
        if (node == null) {
            return nodeId;
        }
        return node.getNodeName() + "(" + nodeId + ")";
    }

    private String riskLevel(ErrorCode code) {
        if (code == ErrorCode.DEAD_END || code == ErrorCode.CYCLE_FOUND
                || code == ErrorCode.PATH_TOO_DEEP || code == ErrorCode.TERMINAL_HAS_DOWNSTREAM
                || code == ErrorCode.TOO_MANY_PATHS) {
            return "中";
        }
        return "高";
    }

    private String nodeName(Map<String, Node> nodeMap, String nodeId) {
        Node node = nodeMap.get(nodeId);
        return node == null ? null : node.getNodeName();
    }
}
