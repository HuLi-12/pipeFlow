package com.example.pipeflowcheck.controller;

import com.example.pipeflowcheck.config.RuleDefinition;
import com.example.pipeflowcheck.enums.ChannelType;
import com.example.pipeflowcheck.enums.NodeType;
import com.example.pipeflowcheck.enums.StartType;
import com.example.pipeflowcheck.service.RuleConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleConfigController {

    private final RuleConfigService ruleConfigService;

    @GetMapping
    public Map<String, Object> currentRules() {
        return payload(ruleConfigService.loadDefault());
    }

    @PutMapping
    public Map<String, Object> saveRules(@RequestBody RuleDefinition rules) {
        ruleConfigService.save(rules);
        return payload(ruleConfigService.loadDefault());
    }

    @PostMapping("/reset")
    public Map<String, Object> resetRules() {
        return payload(ruleConfigService.resetDefault());
    }

    private Map<String, Object> payload(RuleDefinition rules) {
        return Map.of(
                "rules", rules,
                "startTypes", StartType.values(),
                "channelTypes", ChannelType.values(),
                "nodeTypes", NodeType.values()
        );
    }
}
