package com.example.pipeflowcheck.config;

import com.example.pipeflowcheck.enums.ChannelType;
import com.example.pipeflowcheck.enums.NodeType;
import com.example.pipeflowcheck.enums.StartType;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
public class RuleDefinition {
    private Map<StartType, StartRule> startRules = new HashMap<>();
    private Set<NodeType> terminalTypes = Set.of(NodeType.RIVER, NodeType.LAKE, NodeType.RAIN_OUTLET, NodeType.WWTP);
    private ErrorPolicy errorPolicy = new ErrorPolicy();

    @Data
    public static class StartRule {
        private Set<ChannelType> allowedChannels;
        private Set<NodeType> normalEndTypes;
        private Set<NodeType> specialEndTypes;
        private Set<ChannelType> specialWhenChannels;
    }

    @Data
    public static class ErrorPolicy {
        private boolean allDownstreamPathsMustBeValid = true;
        private boolean cycleAsError = true;
        private boolean deadEndAsError = true;
    }
}
