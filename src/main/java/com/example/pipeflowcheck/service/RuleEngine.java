package com.example.pipeflowcheck.service;

import com.example.pipeflowcheck.config.RuleDefinition;
import com.example.pipeflowcheck.enums.ChannelType;
import com.example.pipeflowcheck.enums.NodeType;
import com.example.pipeflowcheck.enums.StartType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class RuleEngine {

    /**
     * 当前给出内置规则。生产环境建议从 rules.yml 或数据库加载。
     */
    public RuleDefinition defaultRules() {
        RuleDefinition rules = new RuleDefinition();

        RuleDefinition.StartRule rain = new RuleDefinition.StartRule();
        rain.setAllowedChannels(Set.of(ChannelType.RAIN, ChannelType.SEWAGE, ChannelType.COMBINED));
        rain.setNormalEndTypes(Set.of(NodeType.RIVER, NodeType.LAKE, NodeType.RAIN_OUTLET));
        rain.setSpecialEndTypes(Set.of(NodeType.WWTP));
        rain.setSpecialWhenChannels(Set.of(ChannelType.SEWAGE, ChannelType.COMBINED));

        RuleDefinition.StartRule sewage = new RuleDefinition.StartRule();
        sewage.setAllowedChannels(Set.of(ChannelType.SEWAGE, ChannelType.COMBINED));
        sewage.setNormalEndTypes(Set.of(NodeType.WWTP));
        sewage.setSpecialEndTypes(Set.of(NodeType.WWTP));
        sewage.setSpecialWhenChannels(Set.of(ChannelType.SEWAGE, ChannelType.COMBINED));

        RuleDefinition.StartRule lifeSewage = new RuleDefinition.StartRule();
        lifeSewage.setAllowedChannels(Set.of(ChannelType.LIFE_SEWAGE, ChannelType.SEWAGE, ChannelType.COMBINED));
        lifeSewage.setNormalEndTypes(Set.of(NodeType.WWTP));
        lifeSewage.setSpecialEndTypes(Set.of(NodeType.WWTP));
        lifeSewage.setSpecialWhenChannels(Set.of(ChannelType.LIFE_SEWAGE, ChannelType.SEWAGE, ChannelType.COMBINED));

        rules.setStartRules(Map.of(
                StartType.RAIN, rain,
                StartType.SEWAGE, sewage,
                StartType.LIFE_SEWAGE, lifeSewage
        ));
        return rules;
    }

    public boolean isChannelAllowed(RuleDefinition rules, StartType startType, ChannelType channelType) {
        RuleDefinition.StartRule rule = rules.getStartRules().get(startType);
        return rule != null && rule.getAllowedChannels().contains(channelType);
    }

    public boolean shouldEnterSpecialState(RuleDefinition rules, StartType startType, ChannelType channelType) {
        RuleDefinition.StartRule rule = rules.getStartRules().get(startType);
        return rule != null && rule.getSpecialWhenChannels().contains(channelType);
    }

    public boolean isValidEnd(RuleDefinition rules, StartType startType, boolean specialState, NodeType endType) {
        RuleDefinition.StartRule rule = rules.getStartRules().get(startType);
        if (rule == null) {
            return false;
        }
        return specialState
                ? rule.getSpecialEndTypes().contains(endType)
                : rule.getNormalEndTypes().contains(endType);
    }
}
