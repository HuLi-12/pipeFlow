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

@Service
@RequiredArgsConstructor
public class PipeCheckService {

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
        return dfs(task.getStartNodeId(), nodeMap, graph, context, rules);
    }

    private List<CheckResult> dfs(String currentNodeId,
                                  Map<String, Node> nodeMap,
                                  Map<String, List<Edge>> graph,
                                  CheckContext context,
                                  RuleDefinition rules) {
        List<CheckResult> results = new ArrayList<>();

        if (!nodeMap.containsKey(currentNodeId)) {
            results.add(error(context, nodeMap, currentNodeId, ErrorCode.NODE_NOT_FOUND, "节点不存在：" + currentNodeId));
            return results;
        }

        if (context.getVisited().contains(currentNodeId)) {
            context.getPath().add(currentNodeId);
            results.add(error(context, nodeMap, currentNodeId, ErrorCode.CYCLE_FOUND, "检测到环路，节点：" + currentNodeId));
            return results;
        }

        context.getVisited().add(currentNodeId);
        context.getPath().add(currentNodeId);

        Node currentNode = nodeMap.get(currentNodeId);
        List<Edge> nextEdges = graph.getOrDefault(currentNodeId, Collections.emptyList());

        if (nextEdges.isEmpty()) {
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

        for (Edge edge : nextEdges) {
            CheckContext branch = context.copy();
            if (!ruleEngine.isChannelAllowed(rules, branch.getTask().getStartType(), edge.getChannelType())) {
                branch.getPath().add(edge.getToNodeId());
                results.add(error(branch, nodeMap, edge.getToNodeId(), ErrorCode.CHANNEL_NOT_ALLOWED,
                        "入口类型 " + branch.getTask().getStartType() + " 不允许进入通道：" + edge.getChannelType()));
                continue;
            }
            if (ruleEngine.shouldEnterSpecialState(rules, branch.getTask().getStartType(), edge.getChannelType())) {
                branch.setEnteredSpecialChannel(true);
            }
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
                .path(String.join("->", context.getPath()))
                .riskLevel("无")
                .build();
    }

    private CheckResult error(CheckContext context, Map<String, Node> nodeMap, String endNodeId, ErrorCode code, String reason) {
        CheckTask task = context.getTask();
        return CheckResult.builder()
                .taskId(task.getTaskId())
                .startNodeId(task.getStartNodeId())
                .startNodeName(nodeName(nodeMap, task.getStartNodeId()))
                .startType(task.getStartType().name())
                .status("错误")
                .endNodeId(endNodeId)
                .endNodeName(nodeName(nodeMap, endNodeId))
                .path(context.getPath().stream().collect(Collectors.joining("->")))
                .errorCode(code)
                .errorReason(reason)
                .riskLevel(code == ErrorCode.DEAD_END || code == ErrorCode.CYCLE_FOUND ? "中" : "高")
                .build();
    }

    private String nodeName(Map<String, Node> nodeMap, String nodeId) {
        Node node = nodeMap.get(nodeId);
        return node == null ? null : node.getNodeName();
    }
}
