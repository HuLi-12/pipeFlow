package com.example.pipeflowcheck;

import com.example.pipeflowcheck.enums.ErrorCode;
import com.example.pipeflowcheck.enums.NodeType;
import com.example.pipeflowcheck.enums.StartType;
import com.example.pipeflowcheck.model.CheckTask;
import com.example.pipeflowcheck.model.Node;
import com.example.pipeflowcheck.service.PipeCheckService;
import com.example.pipeflowcheck.service.RuleConfigService;
import com.example.pipeflowcheck.service.RuleEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipeCheckServiceBehaviorTest {

    @Test
    void reportsDeadEndWhenIntermediateNodeHasNoDownstreamEdges() {
        Node inlet = node("N001", "Rain inlet", NodeType.RAIN_INLET);
        Node well = node("N002", "Dead rain well", NodeType.RAIN_WELL);
        CheckTask task = new CheckTask();
        task.setTaskId("T001");
        task.setStartNodeId("N002");
        task.setStartType(StartType.RAIN);

        PipeCheckService service = new PipeCheckService(new RuleEngine(), new RuleConfigService());

        var results = service.checkAll(Map.of(inlet.getNodeId(), inlet, well.getNodeId(), well), Map.of(), List.of(task));

        assertEquals(ErrorCode.DEAD_END, results.get(0).getErrorCode());
    }

    private Node node(String id, String name, NodeType type) {
        Node node = new Node();
        node.setNodeId(id);
        node.setNodeName(name);
        node.setNodeType(type);
        return node;
    }
}
