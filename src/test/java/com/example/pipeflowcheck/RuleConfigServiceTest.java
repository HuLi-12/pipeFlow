package com.example.pipeflowcheck;

import com.example.pipeflowcheck.enums.ChannelType;
import com.example.pipeflowcheck.enums.NodeType;
import com.example.pipeflowcheck.enums.StartType;
import com.example.pipeflowcheck.service.RuleConfigService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleConfigServiceTest {

    @Test
    void loadsRulesFromYamlFile() throws Exception {
        var config = Files.createTempFile("pipeflowcheck-rules", ".yml");
        Files.writeString(config, """
                startRules:
                  RAIN:
                    allowedChannels: [RAIN]
                    normalEndTypes: [RAIN_OUTLET]
                    pollutedEndTypes: [WWTP]
                    pollutedWhenChannels: []
                terminalTypes:
                  - RAIN_OUTLET
                  - WWTP
                errorPolicy:
                  allDownstreamPathsMustBeValid: true
                  cycleAsError: true
                  deadEndAsError: true
                """, StandardCharsets.UTF_8);

        var rules = new RuleConfigService().load(config);

        assertTrue(rules.getStartRules().get(StartType.RAIN).getAllowedChannels().contains(ChannelType.RAIN));
        assertFalse(rules.getStartRules().get(StartType.RAIN).getAllowedChannels().contains(ChannelType.COMBINED));
        assertTrue(rules.getTerminalTypes().contains(NodeType.RAIN_OUTLET));
    }
}
