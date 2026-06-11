package com.example.pipeflowcheck.service;

import com.example.pipeflowcheck.config.RuleDefinition;
import com.example.pipeflowcheck.enums.ChannelType;
import com.example.pipeflowcheck.enums.NodeType;
import com.example.pipeflowcheck.enums.StartType;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RuleConfigService {

    private static final Path DEFAULT_RULE_PATH = Path.of("config", "rules.yml");

    public RuleDefinition loadDefault() {
        if (Files.exists(DEFAULT_RULE_PATH)) {
            return load(DEFAULT_RULE_PATH);
        }
        return new RuleEngine().defaultRules();
    }

    public RuleDefinition resetDefault() {
        RuleDefinition rules = new RuleEngine().defaultRules();
        save(rules);
        return rules;
    }

    public void save(RuleDefinition rules) {
        try {
            Files.createDirectories(DEFAULT_RULE_PATH.getParent());
            Files.writeString(DEFAULT_RULE_PATH, toYaml(rules));
        } catch (IOException e) {
            throw new IllegalArgumentException("规则配置保存失败：" + DEFAULT_RULE_PATH, e);
        }
    }

    public RuleDefinition load(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return parse(inputStream);
        } catch (IOException e) {
            throw new IllegalArgumentException("规则配置读取失败：" + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private RuleDefinition parse(InputStream inputStream) {
        Map<String, Object> root = new Yaml().load(inputStream);
        if (root == null) {
            throw new IllegalArgumentException("规则配置为空");
        }

        RuleDefinition definition = new RuleDefinition();
        Object startRulesNode = root.get("startRules");
        if (!(startRulesNode instanceof Map<?, ?> startRulesMap)) {
            throw new IllegalArgumentException("规则配置缺少 startRules");
        }

        Map<StartType, RuleDefinition.StartRule> startRules = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : startRulesMap.entrySet()) {
            StartType startType = enumValue(StartType.class, String.valueOf(entry.getKey()));
            Map<String, Object> rawRule = (Map<String, Object>) entry.getValue();

            RuleDefinition.StartRule rule = new RuleDefinition.StartRule();
            rule.setAllowedChannels(enumSet(ChannelType.class, rawRule.get("allowedChannels")));
            rule.setNormalEndTypes(enumSet(NodeType.class, rawRule.get("normalEndTypes")));
            rule.setSpecialEndTypes(enumSet(NodeType.class, firstPresent(rawRule, "pollutedEndTypes", "specialEndTypes")));
            rule.setSpecialWhenChannels(enumSet(ChannelType.class, firstPresent(rawRule, "pollutedWhenChannels", "specialWhenChannels")));
            startRules.put(startType, rule);
        }
        definition.setStartRules(startRules);

        Object terminalTypesNode = root.get("terminalTypes");
        if (terminalTypesNode instanceof List<?>) {
            definition.setTerminalTypes(enumSet(NodeType.class, terminalTypesNode));
        }

        Object errorPolicyNode = root.get("errorPolicy");
        if (errorPolicyNode instanceof Map<?, ?> rawPolicy) {
            RuleDefinition.ErrorPolicy policy = new RuleDefinition.ErrorPolicy();
            policy.setAllDownstreamPathsMustBeValid(booleanValue(rawPolicy.get("allDownstreamPathsMustBeValid"), true));
            policy.setCycleAsError(booleanValue(rawPolicy.get("cycleAsError"), true));
            policy.setDeadEndAsError(booleanValue(rawPolicy.get("deadEndAsError"), true));
            definition.setErrorPolicy(policy);
        }

        return definition;
    }

    private Object firstPresent(Map<String, Object> rawRule, String first, String second) {
        return rawRule.containsKey(first) ? rawRule.get(first) : rawRule.get(second);
    }

    private String toYaml(RuleDefinition rules) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> startRules = new LinkedHashMap<>();
        for (Map.Entry<StartType, RuleDefinition.StartRule> entry : rules.getStartRules().entrySet()) {
            RuleDefinition.StartRule rule = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("allowedChannels", enumNames(rule.getAllowedChannels()));
            item.put("normalEndTypes", enumNames(rule.getNormalEndTypes()));
            item.put("specialEndTypes", enumNames(rule.getSpecialEndTypes()));
            item.put("specialWhenChannels", enumNames(rule.getSpecialWhenChannels()));
            startRules.put(entry.getKey().name(), item);
        }
        root.put("startRules", startRules);
        root.put("terminalTypes", enumNames(rules.getTerminalTypes()));

        Map<String, Object> errorPolicy = new LinkedHashMap<>();
        errorPolicy.put("allDownstreamPathsMustBeValid", rules.getErrorPolicy().isAllDownstreamPathsMustBeValid());
        errorPolicy.put("cycleAsError", rules.getErrorPolicy().isCycleAsError());
        errorPolicy.put("deadEndAsError", rules.getErrorPolicy().isDeadEndAsError());
        root.put("errorPolicy", errorPolicy);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setAllowUnicode(true);
        return new Yaml(options).dump(root);
    }

    private <E extends Enum<E>> List<String> enumNames(Set<E> values) {
        if (values == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (E value : values) {
            names.add(value.name());
        }
        return names;
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private <E extends Enum<E>> Set<E> enumSet(Class<E> enumType, Object value) {
        if (!(value instanceof List<?> values)) {
            return Set.of();
        }
        return values.stream()
                .map(item -> enumValue(enumType, String.valueOf(item)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("规则配置枚举值非法：" + value, e);
        }
    }
}
