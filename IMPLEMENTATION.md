# PipeFlowCheck 实现细节文档

---

## 目录

1. [Excel 解析（ExcelReadService）](#1-excel-解析excelreadservice)
2. [图构建（GraphBuildService）](#2-图构建graphbuildservice)
3. [规则引擎（RuleEngine + RuleConfigService）](#3-规则引擎ruleengine--ruleconfigservice)
4. [核心检测算法（PipeCheckService）](#4-核心检测算法pipecheckservice)
5. [结果汇总（ResultSummaryService）](#5-结果汇总resultsummaryservice)
6. [Excel 结果生成（ExcelWriteService）](#6-excel-结果生成excelwriteservice)
7. [可视化布局（VisualizationService）](#7-可视化布局visualizationservice)
8. [ZIP 打包（ZipPackagingService）](#8-zip-打包zippackagingservice)
9. [模板生成（TemplateWriteService）](#9-模板生成templatewriteservice)
10. [控制器 API（PipeCheckController）](#10-控制器-apipipecheckcontroller)
11. [前端可视化（index.html）](#11-前端可视化indexhtml)
12. [独立 HTML 图谱（generateStandaloneHtml）](#12-独立-html-图谱generatestandalonehtml)
13. [枚举体系](#13-枚举体系)
14. [配置体系](#14-配置体系)
15. [异常处理](#15-异常处理)
16. [构建和运行](#16-构建和运行)

---

## 1. Excel 解析（ExcelReadService）

**文件**：`src/main/java/com/example/pipeflowcheck/service/ExcelReadService.java`

### 职责

将用户上传的 `.xlsx` 文件解析为 `PipeNetworkData`（包含节点、边、任务）。

### 核心实现

#### 入口方法 `read(InputStream)`

```java
public PipeNetworkData read(InputStream inputStream) {
    try (Workbook workbook = new XSSFWorkbook(inputStream)) {
        Map<String, Node> nodeMap = readNodes(requiredSheet(workbook, "Nodes"));
        List<Edge> edges = readEdges(requiredSheet(workbook, "Edges"));
        List<CheckTask> tasks = readTasks(requiredSheet(workbook, "Tasks"));
        validateEdgeReferences(nodeMap, edges);
        return new PipeNetworkData(nodeMap, edges, tasks);
    } catch (IOException e) {
        throw new IllegalArgumentException("Excel 读取失败", e);
    }
}
```

关键点：
- 使用 `XSSFWorkbook` 解析（仅支持 `.xlsx` 格式）
- 要求 **3 张 Sheet 必须存在**：Nodes、Edges、Tasks
- 通过 `requiredSheet()` 检查 Sheet 存在性，否则抛 `IllegalArgumentException`
- 解析完成后通过 `validateEdgeReferences()` 校验边的节点引用

#### 表头解析 `headers(Sheet)`

```java
private Map<String, Integer> headers(Sheet sheet) {
    Row headerRow = sheet.getRow(0);
    // ...
    Map<String, Integer> headers = new HashMap<>();
    for (Cell cell : headerRow) {
        String name = formatter.formatCellValue(cell).trim();
        if (!name.isEmpty()) {
            headers.put(name, cell.getColumnIndex());
        }
    }
    return headers;
}
```

将第一行的每个单元格值作为 key，列索引作为 value，构建列名→列号的映射。

#### 行的三种读取模式

| 方法 | 说明 | 行为 |
|------|------|------|
| `requiredValue()` | 必填字段 | 值为空时抛异常 |
| `optionalValue()` | 可选字段 | 返回空字符串 |
| `enumValue()` | 枚举字段 | 自动解析枚举，非法时抛异常 |

#### 节点解析 `readNodes(Sheet)`

```java
private Map<String, Node> readNodes(Sheet sheet) {
    // 强制要求 headers: node_id, node_name, node_type
    requireHeaders(sheet, headers, "node_id", "node_name", "node_type");
    
    Map<String, Node> nodes = new LinkedHashMap<>();
    for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
        Row row = sheet.getRow(rowIndex);
        if (isBlank(row)) continue;  // 跳过空行
        
        Node node = new Node();
        node.setNodeId(requiredValue(...));
        node.setNodeName(requiredValue(...));
        node.setNodeType(enumValue(NodeType.class, requiredValue(...), ...));
        node.setRemark(optionalValue(...));
        
        if (nodes.containsKey(node.getNodeId())) {
            throw new IllegalArgumentException("节点重复：" + ...);
        }
        nodes.put(node.getNodeId(), node);
    }
    return nodes;
}
```

特点：
- 使用 `LinkedHashMap` 保持插入顺序
- 检测重复 `node_id` 并报错
- `remark` 列为可选

#### 边解析 `readEdges(Sheet)`

```java
private List<Edge> readEdges(Sheet sheet) {
    // 强制要求 headers: from_node_id, to_node_id, channel_type
    requireHeaders(sheet, headers, "from_node_id", "to_node_id", "channel_type");
    
    List<Edge> edges = new ArrayList<>();
    for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
        // 解析每一行，构建 Edge 对象
        edge.setFromNodeId(requiredValue(...));
        edge.setToNodeId(requiredValue(...));
        edge.setChannelType(enumValue(ChannelType.class, ...));
        edge.setRemark(optionalValue(...));
        edges.add(edge);
    }
    return edges;
}
```

#### 任务解析 `readTasks(Sheet)`

```java
private List<CheckTask> readTasks(Sheet sheet) {
    // 强制要求 headers: task_id, start_node_id, start_type
    requireHeaders(sheet, headers, "task_id", "start_node_id", "start_type");
    
    List<CheckTask> tasks = new ArrayList<>();
    // 解析每一行
    task.setTaskId(requiredValue(...));
    task.setStartNodeId(requiredValue(...));
    task.setStartType(enumValue(StartType.class, ...));
    task.setRemark(optionalValue(...));
    tasks.add(task);
}
```

#### 边引用校验 `validateEdgeReferences`

```java
private void validateEdgeReferences(Map<String, Node> nodeMap, List<Edge> edges) {
    Set<String> edgeKeys = new HashSet<>();
    for (Edge edge : edges) {
        if (!nodeMap.containsKey(edge.getFromNodeId()))
            throw new IllegalArgumentException("边引用节点不存在：" + edge.getFromNodeId());
        if (!nodeMap.containsKey(edge.getToNodeId()))
            throw new IllegalArgumentException("边引用节点不存在：" + edge.getToNodeId());
        
        String edgeKey = edge.getFromNodeId() + "->" + edge.getToNodeId() + ":" + edge.getChannelType();
        if (!edgeKeys.add(edgeKey))
            throw new IllegalArgumentException("边重复：" + edgeKey);
    }
}
```

校验内容：
1. `from_node_id` 必须存在于 Nodes 中
2. `to_node_id` 必须存在于 Nodes 中
3. `from+to+channelType` 组合不能重复

#### 工具方法

- `isBlank(Row)`：判断整行是否全为空
- `formatter`：`DataFormatter` 实例，将 POI Cell 格式化为字符串，避免数字/日期格式问题

---

## 2. 图构建（GraphBuildService）

**文件**：`src/main/java/com/example/pipeflowcheck/service/GraphBuildService.java`

### 职责

将边的列表转换为**邻接表**形式，以便 DFS 快速查找每个节点的下游边。

### 核心实现

```java
public Map<String, List<Edge>> buildGraph(List<Edge> edges) {
    return edges.stream().collect(Collectors.groupingBy(Edge::getFromNodeId));
}
```

全部代码仅一个方法一行。

### 数据结构

构建后的结构：
```
{
  "N001": [Edge(from=N001, to=N061, channelType=RAIN), ...],
  "N002": [Edge(from=N002, to=N063, channelType=RAIN), ...],
  ...
}
```

即 `Map<fromNodeId, List<Edge>>`，每个节点作为 key，其所有出边作为 value。

特点：
- 允许空节点（无出边的节点在 map 中不存在 key，DFS 时通过 `getOrDefault(id, emptyList)` 处理）
- 使用 Java Stream API 的 `groupingBy` 实现

---

## 3. 规则引擎（RuleEngine + RuleConfigService）

### 3.1 规则模型（RuleDefinition）

**文件**：`src/main/java/com/example/pipeflowcheck/config/RuleDefinition.java`

#### 顶层结构

```java
public class RuleDefinition {
    private Map<StartType, StartRule> startRules = new HashMap<>();
    private Set<NodeType> terminalTypes = Set.of(NodeType.RIVER, NodeType.LAKE, NodeType.WWTP);
    private ErrorPolicy errorPolicy = new ErrorPolicy();
}
```

#### 起点规则 StartRule

```java
public static class StartRule {
    private Set<ChannelType> allowedChannels;      // 该入口类型允许通过的通道类型
    private Set<NodeType> normalEndTypes;          // 正常状态下的合法终点类型
    private Set<NodeType> specialEndTypes;         // 进入特殊通道后的合法终点类型
    private Set<ChannelType> specialWhenChannels;  // 触发特殊状态的通道类型
}
```

#### 错误策略 ErrorPolicy

```java
public static class ErrorPolicy {
    private boolean allDownstreamPathsMustBeValid = true;  // 所有下游路径必须合法
    private boolean cycleAsError = true;                    // 环路视为错误
    private boolean deadEndAsError = true;                  // 断头路视为错误
}
```

#### 默认终端类型

```java
private Set<NodeType> terminalTypes = Set.of(NodeType.RIVER, NodeType.LAKE, NodeType.WWTP);
```

`RAIN_OUTLET` 也在代码中被视为终端类型（在 VisualizationService.generateDot 和 PipeCheckService 中使用），但未在默认终端类型中列出。这是因为 RAIN_OUTLET 也是终点类型，但在规则中被作为 normalEndType 的一部分处理。

### 3.2 规则引擎（RuleEngine）

**文件**：`src/main/java/com/example/pipeflowcheck/service/RuleEngine.java`

#### 默认规则配置 `defaultRules()`

```java
public RuleDefinition defaultRules() {
    RuleDefinition rules = new RuleDefinition();

    // ===== 雨水 (RAIN) =====
    RuleDefinition.StartRule rain = new RuleDefinition.StartRule();
    rain.setAllowedChannels(Set.of(ChannelType.RAIN, ChannelType.SEWAGE, ChannelType.COMBINED));
    rain.setNormalEndTypes(Set.of(NodeType.RIVER, NodeType.LAKE, NodeType.RAIN_OUTLET));
    rain.setSpecialEndTypes(Set.of(NodeType.WWTP));
    rain.setSpecialWhenChannels(Set.of(ChannelType.SEWAGE, ChannelType.COMBINED));
    // 雨水走雨水通道 → 合法终点：河流、湖泊、雨水排口
    // 雨水进入污水/合流通道 → 触发特殊状态 → 合法终点变为：污水处理厂

    // ===== 污水 (SEWAGE) =====
    RuleDefinition.StartRule sewage = new RuleDefinition.StartRule();
    sewage.setAllowedChannels(Set.of(ChannelType.SEWAGE, ChannelType.COMBINED));
    sewage.setNormalEndTypes(Set.of(NodeType.WWTP));
    sewage.setSpecialEndTypes(Set.of(NodeType.WWTP));
    sewage.setSpecialWhenChannels(Set.of(ChannelType.SEWAGE, ChannelType.COMBINED));
    // 无论是否触发特殊状态，合法终点都是污水处理厂

    // ===== 生活污水 (LIFE_SEWAGE) =====
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
```

规则汇总表：

| StartType | allowedChannels | normalEndTypes | specialEndTypes | specialWhenChannels |
|-----------|----------------|----------------|-----------------|---------------------|
| RAIN | RAIN, SEWAGE, COMBINED | RIVER, LAKE, RAIN_OUTLET | WWTP | SEWAGE, COMBINED |
| SEWAGE | SEWAGE, COMBINED | WWTP | WWTP | SEWAGE, COMBINED |
| LIFE_SEWAGE | LIFE_SEWAGE, SEWAGE, COMBINED | WWTP | WWTP | LIFE_SEWAGE, SEWAGE, COMBINED |

#### 规则判定方法

```java
// 检查通道是否允许
public boolean isChannelAllowed(RuleDefinition rules, StartType startType, ChannelType channelType) {
    RuleDefinition.StartRule rule = rules.getStartRules().get(startType);
    return rule != null && rule.getAllowedChannels().contains(channelType);
}

// 检查是否应进入特殊状态
public boolean shouldEnterSpecialState(RuleDefinition rules, StartType startType, ChannelType channelType) {
    RuleDefinition.StartRule rule = rules.getStartRules().get(startType);
    return rule != null && rule.getSpecialWhenChannels().contains(channelType);
}

// 判断终点是否合法
public boolean isValidEnd(RuleDefinition rules, StartType startType, boolean specialState, NodeType endType) {
    RuleDefinition.StartRule rule = rules.getStartRules().get(startType);
    if (rule == null) return false;
    return specialState
            ? rule.getSpecialEndTypes().contains(endType)
            : rule.getNormalEndTypes().contains(endType);
}
```

### 3.3 规则配置持久化（RuleConfigService）

**文件**：`src/main/java/com/example/pipeflowcheck/service/RuleConfigService.java`

#### 规则加载优先级

```java
public RuleDefinition loadDefault() {
    if (Files.exists(DEFAULT_RULE_PATH)) {  // config/rules.yml
        return load(DEFAULT_RULE_PATH);
    }
    return new RuleEngine().defaultRules();  // 回退到硬编码默认规则
}
```

#### YAML 解析

使用 SnakeYAML 解析 `config/rules.yml` 文件：

```yaml
startRules:
  RAIN:
    allowedChannels: [RAIN, SEWAGE, COMBINED]
    normalEndTypes: [RIVER, LAKE, RAIN_OUTLET]
    specialEndTypes: [WWTP]
    specialWhenChannels: [SEWAGE, COMBINED]
  SEWAGE:
    allowedChannels: [SEWAGE, COMBINED]
    normalEndTypes: [WWTP]
    specialEndTypes: [WWTP]
    specialWhenChannels: [SEWAGE, COMBINED]
  LIFE_SEWAGE:
    allowedChannels: [LIFE_SEWAGE, SEWAGE, COMBINED]
    normalEndTypes: [WWTP]
    specialEndTypes: [WWTP]
    specialWhenChannels: [LIFE_SEWAGE, SEWAGE, COMBINED]

terminalTypes: [RIVER, LAKE, RAIN_OUTLET, WWTP]

errorPolicy:
  allDownstreamPathsMustBeValid: true
  cycleAsError: true
  deadEndAsError: true
```

#### YAML 生成（保存）

`toYaml()` 方法将 `RuleDefinition` 对象序列化为 YAML 格式并写入文件。

#### 兼容性处理

`parse()` 方法做了一组兼容处理：

```java
// 兼容旧版字段名 pollutedEndTypes → specialEndTypes
rule.setSpecialEndTypes(enumSet(NodeType.class, firstPresent(rawRule, "pollutedEndTypes", "specialEndTypes")));
rule.setSpecialWhenChannels(enumSet(ChannelType.class, firstPresent(rawRule, "pollutedWhenChannels", "specialWhenChannels")));
```

---

## 4. 核心检测算法（PipeCheckService）

**文件**：`src/main/java/com/example/pipeflowcheck/service/PipeCheckService.java`

### 4.1 整体流程

```java
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
```

对每个 Task 独立执行检测，结果合并返回。

### 4.2 单任务检测 `checkSingleTask`

```java
private List<CheckResult> checkSingleTask(Map<String, Node> nodeMap,
                                          Map<String, List<Edge>> graph,
                                          CheckTask task, RuleDefinition rules) {
    CheckContext context = new CheckContext();
    context.setTask(task);
    context.setPathCounter(new int[]{0});  // 共享计数器，所有分支共用

    // 起点节点兼容性校验
    Node startNode = nodeMap.get(task.getStartNodeId());
    if (startNode != null && !isStartNodeCompatible(task.getStartType(), startNode.getNodeType())) {
        return List.of(error(context, nodeMap, startNode.getNodeId(), 
            ErrorCode.START_NODE_TYPE_MISMATCH, ...));
    }
    return dfs(task.getStartNodeId(), nodeMap, graph, context, rules);
}
```

#### 起点兼容性检查

```java
private boolean isStartNodeCompatible(StartType startType, NodeType nodeType) {
    Map<StartType, Set<NodeType>> allowed = Map.of(
        StartType.RAIN, Set.of(NodeType.RAIN_INLET, NodeType.RAIN_WELL, NodeType.COMBINED_WELL),
        StartType.SEWAGE, Set.of(NodeType.SEWAGE_INLET, NodeType.SEWAGE_WELL, NodeType.COMBINED_WELL),
        StartType.LIFE_SEWAGE, Set.of(NodeType.LIFE_SEWAGE_INLET, NodeType.SEWAGE_WELL, NodeType.COMBINED_WELL),
        StartType.CUSTOM, Set.of(NodeType.values())
    );
    return allowed.getOrDefault(startType, Set.of()).contains(nodeType);
}
```

### 4.3 DFS 深度优先搜索

```java
private List<CheckResult> dfs(String currentNodeId, ...) {
    // 每个 DFS 调用返回一个或多个 CheckResult
    // 分支时 copy() 上下文，多个分支并行探索
    
    步骤顺序：
    1. 节点存在性检查
    2. 环路检测 (visited set)
    3. 最大深度保护 (MAX_DEPTH=500)
    4. 最大路径数保护 (MAX_PATHS=10000)
    5. 终点有下游检测 (TERMINAL_HAS_DOWNSTREAM)
    6. 叶子节点 → 终点合法性判定
    7. 分支遍历 → channelAllowed → 状态转移 → 递归
}
```

#### 检查顺序和优先级

1. **节点存在性检查**
   ```java
   if (!nodeMap.containsKey(currentNodeId)) {
       return error(..., ErrorCode.NODE_NOT_FOUND, "节点不存在：" + currentNodeId);
   }
   ```
   出现在路径中的节点必须存在于节点表中。

2. **环路检测**
   ```java
   if (context.getVisited().contains(currentNodeId)) {
       // 发现环路，记录为 CYCLE_FOUND
       return error(..., ErrorCode.CYCLE_FOUND, "检测到环路，节点：" + currentNodeId);
   }
   ```
   使用 `HashSet<String> visited` 追踪当前路径已访问节点。

3. **最大深度保护**
   ```java
   if (context.getNodePath().size() >= MAX_DEPTH) {
       // 路径超过 500 节点 → PATH_TOO_DEEP
   }
   ```
   防止超大图导致 StackOverflow。

4. **最大路径数保护**
   ```java
   if (context.getPathCounter()[0] >= MAX_PATHS) {
       // 下游分支超过 10000 → TOO_MANY_PATHS
   }
   ```
   `pathCounter` 是 `int[]` 引用类型，通过 `copy()` 在所有分支间**共享**。

5. **终点有下游检测**
   ```java
   if (!nextEdges.isEmpty() && rules.getTerminalTypes().contains(currentNode.getNodeType())) {
       // 如 RAIN_OUTLET 类型节点仍有出边 → TERMINAL_HAS_DOWNSTREAM
   }
   ```
   排口、河流、湖泊、污水处理厂类型不应有下游边。

6. **叶子节点处理**
   ```java
   if (nextEdges.isEmpty()) {
       context.getPathCounter()[0]++;
       boolean validEnd = ruleEngine.isValidEnd(rules, startType, specialState, endType);
       if (validEnd)   return success(...);
       else            return error(..., DEAD_END 或 INVALID_END);
   }
   ```
   - 无下游节点的叶子节点，用 RuleEngine 判定终点是否合法
   - 合法 → `status=通道正常`
   - 不合法 → 若叶子节点是终点类型则 `INVALID_END`，否则 `DEAD_END`

7. **分支遍历**
   ```java
   for (Edge edge : nextEdges) {
       if (context.getPathCounter()[0] >= MAX_PATHS) break;
       
       CheckContext branch = context.copy();  // 关键：深拷贝上下文
       
       // 通道类型允许性检查
       if (!ruleEngine.isChannelAllowed(rules, startType, edge.getChannelType())) {
           // CHANNEL_NOT_ALLOWED
           continue;
       }
       
       // 特殊状态转移
       if (ruleEngine.shouldEnterSpecialState(rules, startType, edge.getChannelType())) {
           branch.setEnteredSpecialChannel(true);
       }
       
       // 记录通道
       branch.getChannelPath().add(edge.getChannelType().name());
       
       // 递归到下个节点
       results.addAll(dfs(edge.getToNodeId(), nodeMap, graph, branch, rules));
   }
   ```

### 4.4 上下文拷贝（CheckContext）

**文件**：`src/main/java/com/example/pipeflowcheck/model/CheckContext.java`

```java
public CheckContext copy() {
    CheckContext copy = new CheckContext();
    copy.setTask(this.task);                                // 同引用（只读）
    copy.setNodePath(new ArrayList<>(this.nodePath));       // 深拷贝路径
    copy.setChannelPath(new ArrayList<>(this.channelPath)); // 深拷贝通道记录
    copy.setVisited(new HashSet<>(this.visited));           // 深拷贝 visited
    copy.setEnteredSpecialChannel(this.enteredSpecialChannel); // 拷贝状态
    copy.setPathCounter(this.pathCounter);                  // 共享引用（计数器）
    return copy;
}
```

字段说明：

| 字段 | 类型 | 拷贝方式 | 说明 |
|------|------|----------|------|
| `task` | CheckTask | 浅拷贝（只读） | 当前任务的配置信息 |
| `nodePath` | List\<String\> | 深拷贝 | 当前路径经过的节点 ID 列表 |
| `channelPath` | List\<String\> | 深拷贝 | 经过的通道类型名称列表 |
| `visited` | Set\<String\> | 深拷贝 | 已访问节点集合，用于环路检测 |
| `enteredSpecialChannel` | boolean | 拷贝 | 是否进入特殊状态 |
| `pathCounter` | int[] | **共享引用** | 全局最大路径数计数器 |

`pathCounter` 使用引用共享而非拷贝，确保所有分支共享同一计数，当总路径数达到 `MAX_PATHS=10000` 时全局停止。

### 4.5 结果构造

#### 成功结果 `success()`

```java
private CheckResult success(CheckContext context, Map<String, Node> nodeMap, Node endNode) {
    return CheckResult.builder()
        .taskId(task.getTaskId())
        .startNodeId(task.getStartNodeId())
        .startNodeName(nodeName(nodeMap, task.getStartNodeId()))
        .startType(task.getStartType().name())
        .status("通道正常")
        .endNodeId(endNode.getNodeId())
        .endNodeName(endNode.getNodeName())
        .path(String.join("->", context.getNodePath()))        // "N001->N002->N003"
        .channelPath(channelPath.isEmpty() ? "" : String.join("->", context.getChannelPath()))  // "RAIN->SEWAGE"
        .readablePath(buildReadablePath(context, nodeMap))     // "雨水口1(N001) --RAIN--> 雨水井1(N002)"
        .riskLevel("无")
        .build();
}
```

#### 错误结果 `error()`

```java
private CheckResult error(CheckContext context, Map<String, Node> nodeMap, String endNodeId,
                          ErrorCode code, String reason) {
    return CheckResult.builder()
        .taskId(task.getTaskId())
        .startNodeId(task.getStartNodeId())
        .status("错误")
        .endNodeId(endNodeId)
        .path(context.getNodePath().stream().collect(Collectors.joining("->")))
        .errorCode(code)
        .errorReason(reason)
        .riskLevel(riskLevel(code))
        .build();
}
```

### 4.6 可读路径生成 `buildReadablePath`

```java
private String buildReadablePath(CheckContext context, Map<String, Node> nodeMap) {
    StringBuilder sb = new StringBuilder();
    sb.append(formatNode(nodeMap, nodePath.get(0)));  // 起点
    // 交错拼接：节点名 --通道--> 节点名 --通道--> ...
    for (int i = 0; i < channelCount; i++) {
        sb.append(" --").append(channelPath.get(i)).append("--> ");
        sb.append(formatNode(nodeMap, nodePath.get(i + 1)));
    }
    return sb.toString();
}
```

例：`雨水口1(N001) --RAIN--> 雨水井1(N061) --RAIN--> 雨水排口1(N181)`

### 4.7 风险等级计算

```java
private String riskLevel(ErrorCode code) {
    if (code == ErrorCode.DEAD_END || code == ErrorCode.CYCLE_FOUND
            || code == ErrorCode.PATH_TOO_DEEP || code == ErrorCode.TERMINAL_HAS_DOWNSTREAM
            || code == ErrorCode.TOO_MANY_PATHS) {
        return "中";
    }
    return "高";  // NODE_NOT_FOUND, CHANNEL_NOT_ALLOWED, INVALID_END 等
}
```

- **高风险**：节点不存在、通道不允许、非法终点等结构性问题
- **中风险**：断头路、环路、路径过深等可修复问题

---

## 5. 结果汇总（ResultSummaryService）

**文件**：`src/main/java/com/example/pipeflowcheck/service/ResultSummaryService.java`

### 职责

将一棵树的多个 `CheckResult`（可能含多个分支路径）汇总为一个 `TaskSummary`。

### 核心算法

```java
public List<TaskSummary> summarize(List<CheckResult> results) {
    // 1. 按 taskId 分组
    Map<String, List<CheckResult>> byTask = new LinkedHashMap<>();
    for (CheckResult result : results) {
        byTask.computeIfAbsent(result.getTaskId(), key -> new ArrayList<>()).add(result);
    }

    List<TaskSummary> summaries = new ArrayList<>();
    for (List<CheckResult> taskResults : byTask.values()) {
        CheckResult first = taskResults.get(0);
        List<CheckResult> errors = taskResults.stream()
                .filter(result -> result.getErrorCode() != null)
                .toList();

        summaries.add(TaskSummary.builder()
                .taskId(first.getTaskId())
                .startNodeId(first.getStartNodeId())
                .startNodeName(first.getStartNodeName())
                .startType(first.getStartType())
                .status(errors.isEmpty() ? "通道正常" : "错误")
                .pathCount(taskResults.size())
                .errorCode(summaryErrorCode(taskResults, errors))
                .errorReason(summaryReason(errors))
                .riskLevel(summaryRisk(taskResults))
                .build());
    }
    return summaries;
}
```

### 错误码聚合逻辑

```java
private ErrorCode summaryErrorCode(List<CheckResult> taskResults, List<CheckResult> errors) {
    if (errors.isEmpty()) return null;                          // 无错误
    if (taskResults.size() > 1) return ErrorCode.MULTI_PATH_ERROR;  // 多路径含错误
    return errors.get(0).getErrorCode();                        // 单一错误
}
```

- 若所有路径均无错误 → `status=通道正常`
- 若有多条路径且至少一条错误 → `errorCode=MULTI_PATH_ERROR`（多路径错误）
- 若仅一条路径且出错 → 沿用该路径的原始 `errorCode`

### 风险等级聚合

```java
private String summaryRisk(List<CheckResult> taskResults) {
    if (taskResults.stream().anyMatch(r -> "高".equals(r.getRiskLevel()))) return "高";
    if (taskResults.stream().anyMatch(r -> "中".equals(r.getRiskLevel()))) return "中";
    return "无";
}
```

取所有路径中的最高风险等级。

---

## 6. Excel 结果生成（ExcelWriteService）

**文件**：`src/main/java/com/example/pipeflowcheck/service/ExcelWriteService.java`

### 职责

将 `List<CheckResult>` 写入 Excel 工作簿，包含两个 Sheet：Result 和 Summary。

### 输出结构

#### Result Sheet 列定义

```java
private static final String[] RESULT_HEADERS = {
    "task_id", "start_node_id", "start_node_name", "start_type",
    "status", "end_node_id", "end_node_name",
    "path", "channel_path", "readable_path",
    "error_code", "error_reason", "risk_level"
};
```

#### Summary Sheet 列定义

```java
private static final String[] SUMMARY_HEADERS = {
    "task_id", "start_node_id", "start_node_name", "start_type",
    "status", "path_count",
    "error_code", "error_reason", "risk_level"
};
```

### 生成流程

```java
public byte[] write(List<CheckResult> results) {
    try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = ...) {
        // Sheet 1: Result（每条路径一行）
        Sheet sheet = workbook.createSheet("Result");
        writeHeader(sheet, RESULT_HEADERS);
        for (int i = 0; i < results.size(); i++) {
            writeResult(sheet.createRow(i + 1), results.get(i));
        }
        
        // Sheet 2: Summary（每个 Task 汇总一行）
        Sheet summarySheet = workbook.createSheet("Summary");
        writeSummary(summarySheet, resultSummaryService.summarize(results));
        
        workbook.write(outputStream);
        return outputStream.toByteArray();
    }
}
```

---

## 7. 可视化布局（VisualizationService）

**文件**：`src/main/java/com/example/pipeflowcheck/service/VisualizationService.java`

### 7.1 布局算法 `computeBackendLayout`

采用 Sugiyama 风格的分层布局算法，不依赖外部 Graphviz。

#### 步骤 1：构建出边邻接表

```java
Map<String, List<String>> outgoing = new LinkedHashMap<>();
nodeMap.keySet().forEach(id -> outgoing.put(id, new ArrayList<>()));
edges.forEach(e -> {
    List<String> list = outgoing.get(e.getFromNodeId());
    if (list != null) list.add(e.getToNodeId());
});
```

#### 步骤 2：找根节点（入度为 0）

```java
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
```

#### 步骤 3：按树分组（BFS）

```java
Map<String, String> treeRoot = new LinkedHashMap<>();
roots.forEach(r -> treeRoot.put(r, r));
roots.forEach(root -> {
    ArrayDeque<String> q = new ArrayDeque<>();
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
nodeMap.keySet().forEach(id -> treeRoot.putIfAbsent(id, id));  // 未联通节点自成一棵树
```

#### 步骤 4：BFS 分层

```
原理：根节点 layer=0，子节点 layer=父节点 layer+1

对每棵树：
  1. BFS 遍历，逐层分配 layer
  2. 未遍历到的节点（可能因环路或过滤）分配到 ++maxL
```

```java
Map<String, Integer> nodeLayer = new LinkedHashMap<>();
for (String rootId : roots) {
    List<String> ids = treeMap.get(rootId);
    if (ids == null) continue;
    
    ArrayDeque<String> q = new ArrayDeque<>();
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
    // 未遍历节点分配到 maxL+1 及之后
    for (String id : ids) {
        if (!nodeLayer.containsKey(id)) {
            nodeLayer.put(id, ++maxL);
        }
    }
}
```

#### 步骤 5：按层分组

```java
Map<Integer, List<String>> byLayer = new TreeMap<>();
nodeLayer.forEach((id, l) -> byLayer.computeIfAbsent(l, k -> new ArrayList<>()).add(id));
```

#### 步骤 6：Barycenter 层内排序

```
原理：对每层的节点，按上游节点在上一层的平均位置排序，减少边交叉

算法：
  第一层：保持原顺序
  第 k 层：
    对每个节点，找其上游节点（边指向该节点的节点）
    筛选出上游节点中位于第 k-1 层的节点
    计算这些上游节点在第 k-1 层中索引的平均值（barycenter）
    若无上游节点在上一层 → barycenter = -1（排在最后）
    按 barycenter 升序排序
```

```java
Map<Integer, List<String>> ordered = new LinkedHashMap<>();
// 第一层保持原顺序
byLayer.entrySet().stream().findFirst()
    .ifPresent(e -> ordered.put(e.getKey(), e.getValue()));

List<Integer> layerKeys = new ArrayList<>(byLayer.keySet());
for (int li = 1; li < layerKeys.size(); li++) {
    int ck = layerKeys.get(li);          // 当前层 key
    int pk = layerKeys.get(li - 1);      // 上一层 key
    List<String> prevList = ordered.get(pk);
    
    Map<String, Double> bar = new LinkedHashMap<>();
    for (String id : byLayer.get(ck)) {
        // 找当前节点的所有上游节点
        List<String> preds = edges.stream()
                .filter(e -> e.getToNodeId().equals(id))
                .map(Edge::getFromNodeId)
                .collect(Collectors.toList());
        // 筛选出在上一层的节点
        List<String> inPrev = preds.stream()
                .filter(prevList::contains)
                .collect(Collectors.toList());
        // 计算 barycenter
        double sum = 0;
        for (String p : inPrev) {
            int idx = prevList.indexOf(p);
            if (idx >= 0) sum += idx;
        }
        bar.put(id, inPrev.size() > 0 ? sum / inPrev.size() : -1);
    }
    
    // 排序：barycenter >= 0 的排在前面，< 0 的排在后面
    ordered.put(ck, byLayer.get(ck).stream()
            .sorted((a, b) -> {
                double ba = bar.getOrDefault(a, -1.0);
                double bb = bar.getOrDefault(b, -1.0);
                return Double.compare(ba < 0 ? 9999 : ba, bb < 0 ? 9999 : bb);
            })
            .collect(Collectors.toList()));
}
```

#### 步骤 7：固定间距定位

```java
int H_GAP = 200;  // 水平间距
int V_GAP = 250;  // 层间距

int minLayer = byLayer.keySet().stream().findFirst().orElse(0);
for (Map.Entry<Integer, List<String>> entry : ordered.entrySet()) {
    int l = entry.getKey();
    List<String> layerIds = entry.getValue();
    for (int i = 0; i < layerIds.size(); i++) {
        positions.put(layerIds.get(i), new double[]{
            i * (double) H_GAP - (layerIds.size() - 1) * (double) H_GAP / 2.0,  // 层内居中
            (l - minLayer) * (double) V_GAP                                     // 垂直位置
        });
    }
}
```

坐标公式：
- **x** = `i * H_GAP - (layerSize - 1) * H_GAP / 2` → 每层节点居中对齐
- **y** = `(layer - minLayer) * V_GAP` → 按层数递增

### 7.2 构建图谱 Payload `buildGraphPayload`

```java
public Map<String, Object> buildGraphPayload(Map<String, Node> nodeMap, 
                                              List<Edge> edges, 
                                              List<CheckResult> results) {
    // 1. 构建节点列表
    List<Map<String, Object>> nodePayload = ...;  // id, name, type, status, remark
    
    // 2. 构建边列表
    List<Map<String, Object>> edgePayload = ...;  // id, from, to, type, status
    
    // 3. 构建错误列表
    List<Map<String, Object>> errorPayload = ...;  // taskId, errorCode, nodePath, edgePath
    
    // 4. 计算布局坐标并写入节点
    Map<String, double[]> positions = computeBackendLayout(nodeMap, edges);
    if (!positions.isEmpty()) {
        // 居中处理：计算所有节点的中心点，平移到原点
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;
        boolean flipY = (minY + maxY) < 0;  // Graphviz Y 轴翻转检测
        
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
    
    return Map.of("nodes", nodePayload, "edges", edgePayload, "errors", errorPayload);
}
```

### 7.3 DOT 生成 `generateDot`

为 Graphviz 生成 DOT 格式的图描述（仅用于 SVG/PNG 静态导出）。

#### 节点间距动态计算

```java
double ns = Math.max(0.3, Math.round((0.3 + nodeCount * 0.004) * 10.0) / 10.0);
double rs = Math.max(0.5, Math.round((0.5 + nodeCount * 0.006) * 10.0) / 10.0);
```

- `nodesep` = `0.3 + N * 0.004`（节点数越多，间距越大）
- `ranksep` = `0.5 + N * 0.006`（节点数越多，层间距越大）

#### 节点样式

```java
String color = config.getNodeColors() != null
    ? config.getNodeColors().getOrDefault(node.getNodeType(), "#CCCCCC")
    : nodeColorDefault(node.getNodeType());
String shape = terminalTypes.contains(node.getNodeType())
    ? config.getTerminalShape()  // "box"（终点类型用矩形）
    : config.getNormalShape();   // "ellipse"（普通节点用椭圆）
```

#### 边样式

错误边使用红色加粗：
```java
if (isError) {
    sb.append("\", color=\"#FF0000\", penwidth=3];\n");
}
```

### 7.4 SVG 位置解析 `parseSvgNodePositions`

当 Graphviz 可用时（已安装），从 Graphviz 输出的 SVG 中解析节点位置：

```java
DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
Document doc = builder.parse(new ByteArrayInputStream(svgContent.getBytes(...)));

NodeList groups = doc.getDocumentElement().getElementsByTagName("g");
for (int i = 0; i < groups.getLength(); i++) {
    Element g = (Element) groups.item(i);
    if (!"node".equals(g.getAttribute("class"))) continue;
    
    // 解析 <title> 获取节点 ID
    String nodeId = titles.item(0).getTextContent().trim();
    
    // 椭圆节点：取 cx, cy
    Element ell = (Element) ellipses.item(0);
    double ex = Double.parseDouble(ell.getAttribute("cx"));
    double ey = Double.parseDouble(ell.getAttribute("cy"));
    
    // 多边形节点：计算多边形中心
    Element poly = (Element) polygons.item(0);
    positions.put(nodeId, polygonCenter(poly.getAttribute("points")));
}
```

### 7.5 错误路径提取

```java
public Set<String> collectErrorNodeIds(List<CheckResult> results) {
    return results.stream()
            .filter(r -> r.getErrorCode() != null)
            .map(CheckResult::getStartNodeId)
            .collect(Collectors.toSet());
}
```

当前实现仅标记错误任务的**起点节点**为错误状态，不再标记路径中所有节点。

---

## 8. ZIP 打包（ZipPackagingService）

**文件**：`src/main/java/com/example/pipeflowcheck/service/ZipPackagingService.java`

### 职责

将多个文件打包为 ZIP 归档。

### 核心实现

```java
public byte[] packageZip(Map<String, byte[]> entries) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ZipOutputStream zos = new ZipOutputStream(baos)) {

        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            ZipEntry zipEntry = new ZipEntry(entry.getKey());
            zipEntry.setSize(entry.getValue().length);
            zos.putNextEntry(zipEntry);
            zos.write(entry.getValue());
            zos.closeEntry();
        }
        zos.finish();
        return baos.toByteArray();
    } catch (IOException e) {
        throw new IllegalStateException("ZIP 打包失败", e);
    }
}
```

### 打包内容（在 Controller 中组装）

| 文件名 | 来源 | 说明 |
|--------|------|------|
| `pipe-flow-check-result.xlsx` | ExcelWriteService | 检测结果 Excel |
| `pipe-network-graph.json` | buildGraphPayload | 图谱 JSON（含坐标） |
| `pipe-network-graph.html` | generateStandaloneHtml | 独立图谱页面 |
| `pipe-network-graph.dot` | generateDot(含错误) | DOT 源文件（错误高亮） |
| `pipe-network-full.dot` | generateDot(无错误) | DOT 源文件（全量） |
| `pipe-network-graph.svg` | renderSvg | SVG 静态图（可选） |
| `pipe-network-full.png` | renderPng | PNG 全量图（可选） |

---

## 9. 模板生成（TemplateWriteService）

**文件**：`src/main/java/com/example/pipeflowcheck/service/TemplateWriteService.java`

### 职责

生成标准 Excel 模板文件，供用户下载填写。

### 输出结构

4 张 Sheet：

| Sheet | 内容 |
|-------|------|
| Nodes | 表头：node_id, node_name, node_type, remark |
| Edges | 表头：from_node_id, to_node_id, channel_type, remark |
| Tasks | 表头：task_id, start_node_id, start_type, remark |
| Template | 填写说明和允许值参考 |

#### Template Sheet 内容

| sheet | required_columns | optional_columns | allowed_values |
|-------|-----------------|------------------|----------------|
| Nodes | node_id,node_name,node_type | remark | node_type: RAIN_INLET,SEWAGE_INLET,... |
| Edges | from_node_id,to_node_id,channel_type | remark | channel_type: RAIN,SEWAGE,COMBINED,... |
| Tasks | task_id,start_node_id,start_type | remark | start_type: RAIN,SEWAGE,LIFE_SEWAGE,... |

---

## 10. 控制器 API（PipeCheckController）

**文件**：`src/main/java/com/example/pipeflowcheck/controller/PipeCheckController.java`

### 依赖注入

```java
private final ExcelReadService excelReadService;
private final GraphBuildService graphBuildService;
private final PipeCheckService pipeCheckService;
private final ExcelWriteService excelWriteService;
private final ResultSummaryService resultSummaryService;
private final TemplateWriteService templateWriteService;
private final VisualizationService visualizationService;
private final ZipPackagingService zipPackagingService;
private final ObjectMapper objectMapper;
```

### 上传校验 `validateUpload`

```java
private void validateUpload(MultipartFile file) {
    if (file == null || file.isEmpty()) {
        throw new IllegalArgumentException("上传文件不能为空");
    }
    String filename = file.getOriginalFilename();
    if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
        throw new IllegalArgumentException("仅支持 .xlsx 文件");
    }
}
```

### 解析 + 检测流水线 `parseUpload + detect`

```java
private PipeNetworkData parseUpload(MultipartFile file) throws IOException {
    return excelReadService.read(file.getInputStream());
}

private List<CheckResult> detect(MultipartFile file) throws IOException {
    validateUpload(file);
    PipeNetworkData data = parseUpload(file);
    return pipeCheckService.checkAll(
        data.getNodeMap(),
        graphBuildService.buildGraph(data.getEdges()),
        data.getTasks()
    );
}
```

### API 端点实现

#### `POST /api/pipe-flow/check`

返回结果 Excel 文件下载。

```java
@PostMapping("/check")
public ResponseEntity<byte[]> check(@RequestParam("file") MultipartFile file) throws IOException {
    List<CheckResult> results = detect(file);
    byte[] report = excelWriteService.write(results);
    
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pipe-flow-check-result.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(report);
}
```

#### `POST /api/pipe-flow/check-json`

返回 JSON 格式结果（含汇总和明细），供前端直接渲染。

```java
@PostMapping("/check-json")
public CheckResponse checkJson(@RequestParam("file") MultipartFile file) throws IOException {
    List<CheckResult> results = detect(file);
    return new CheckResponse(resultSummaryService.summarize(results), results);
}
```

#### `POST /api/pipe-flow/check-view-json`

扩展版 JSON 结果，额外包含图谱数据。

```java
@PostMapping("/check-view-json")
public Map<String, Object> checkViewJson(@RequestParam("file") MultipartFile file) throws IOException {
    // 验证 → 解析 → 建图 → 检测 → 汇总 → 可视化
    validateUpload(file);
    PipeNetworkData data = parseUpload(file);
    Map<String, List<Edge>> graph = graphBuildService.buildGraph(data.getEdges());
    List<CheckResult> results = pipeCheckService.checkAll(data.getNodeMap(), graph, data.getTasks());
    
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("summary", resultSummaryService.summarize(results));
    payload.put("results", results);
    payload.put("graph", visualizationService.buildGraphPayload(data.getNodeMap(), data.getEdges(), results));
    return payload;
}
```

#### `POST /api/pipe-flow/check-graph-json`

仅返回图谱 JSON。

```java
@PostMapping("/check-graph-json")
public Map<String, Object> checkGraphJson(@RequestParam("file") MultipartFile file) throws IOException {
    validateUpload(file);
    PipeNetworkData data = parseUpload(file);
    Map<String, List<Edge>> graph = graphBuildService.buildGraph(data.getEdges());
    List<CheckResult> results = pipeCheckService.checkAll(data.getNodeMap(), graph, data.getTasks());
    return visualizationService.buildGraphPayload(data.getNodeMap(), data.getEdges(), results);
}
```

#### `POST /api/pipe-flow/check-zip`

返回 ZIP 归档（含 Excel + 图谱文件）。

```java
@PostMapping("/check-zip")
public ResponseEntity<byte[]> checkZip(@RequestParam("file") MultipartFile file) throws IOException {
    validateUpload(file);
    PipeNetworkData data = parseUpload(file);
    List<CheckResult> results = pipeCheckService.checkAll(
            data.getNodeMap(), graphBuildService.buildGraph(data.getEdges()), data.getTasks());
    byte[] report = excelWriteService.write(results);
    
    // 可视化文件
    String dotSource = visualizationService.generateDot(data.getNodeMap(), data.getEdges(), errorNodeIds, errorEdgeKeys);
    Map<String, Object> graphPayload = visualizationService.buildGraphPayload(data.getNodeMap(), data.getEdges(), results);
    String graphJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(graphPayload);
    
    // 组装 ZIP 条目
    var zipEntries = new LinkedHashMap<String, byte[]>();
    zipEntries.put("pipe-flow-check-result.xlsx", report);
    zipEntries.put("pipe-network-graph.json", graphJson.getBytes(UTF_8));
    zipEntries.put("pipe-network-graph.html", visualizationService.generateStandaloneHtml(graphJson).getBytes(UTF_8));
    zipEntries.put("pipe-network-graph.dot", dotSource.getBytes(UTF_8));
    zipEntries.put("pipe-network-full.dot", fullDotSource.getBytes(UTF_8));
    // SVG 和 PNG（可能为空，当 Graphviz 不可用时）
    String svgSource = visualizationService.renderSvg(dotSource);
    if (!svgSource.isEmpty()) zipEntries.put("pipe-network-graph.svg", svgSource.getBytes(UTF_8));
    byte[] pngBytes = visualizationService.renderPng(fullDotSource);
    if (pngBytes.length > 0) zipEntries.put("pipe-network-full.png", pngBytes);
    
    byte[] zip = zipPackagingService.packageZip(zipEntries);
    return ResponseEntity.ok()
            .header(CONTENT_DISPOSITION, "attachment; filename=pipe-flow-check-result.zip")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(zip);
}
```

#### `GET /api/pipe-flow/template`

下载标准模板。

```java
@GetMapping("/template")
public ResponseEntity<byte[]> template() {
    return ResponseEntity.ok()
            .header(CONTENT_DISPOSITION, "attachment; filename=pipe-flow-check-template.xlsx")
            .contentType(...)
            .body(templateWriteService.write());
}
```

---

## 11. 前端可视化（index.html）

**文件**：`src/main/resources/static/index.html`

### 页面架构

```
Header: PipeFlowCheck
├─ 导航 Tabs: Excel 检测 | 可视化分析
├─ 上传表单: file input + 按钮 + 模板下载 + 结果下载
├─ 状态提示区
├─ 模板要求（可折叠）
└─ 可视化分析区
   ├─ 分层按钮: 概览图 / 全量节点 / 错误节点 / 错误节点+路径
   ├─ 概览页控制: 入口类型筛选 + 分页
   ├─ ECharts 图谱
   └─ 侧边栏: 详情 + 错误链路 + 节点图例
```

### 核心功能

#### 文件上传与检测

```javascript
document.getElementById('uploadForm').onsubmit = async function(e) {
    e.preventDefault();
    const formData = new FormData(this);
    
    // 1. 先上传检测并获取 JSON 结果
    const jsonResp = await fetch('/api/pipe-flow/check-view-json', {
        method: 'POST', body: formData
    });
    const jsonData = await jsonResp.json();
    graphData = jsonData.graph;
    // ...更新汇总表格...
    
    // 2. 更新可视化图谱
    renderGraph();
}
```

#### 前端布局回退 `computeLayout`

当后端未提供坐标时（`hasPos = false`），前端自行计算分层布局：

```javascript
function computeLayout(nodes, edges) {
    // 与后端 computeBackendLayout 相同的算法：
    // 1. 构建出边邻接表
    // 2. 找根节点（入度为 0）
    // 3. BFS 按树分组
    // 4. 每棵树独立 BFS 分层 + barycenter 排序 + 列式布局
    // 5. V=350, H=300, treeGap=450
    // 6. 居中偏移
}
```

#### 图谱构建 `buildOption`

```javascript
function buildOption(mode) {
    const hasPos = graphData.nodes && graphData.nodes.length > 0 && graphData.nodes[0].x !== undefined;
    
    const nodes = graphData.nodes.map(n => {
        let x = 0, y = 0;
        if (hasPos) { x = n.x; y = n.y; }
        // 优先使用后端坐标，否则前端计算
        return {
            id: n.id, name: n.name + '\n' + n.id,
            x: x, y: y,
            symbolSize: isError ? 44 : 32,
            itemStyle: { color: nodeColorMap[n.type], ... },
            label: { show: true, ... }
        };
    });
    
    // ECharts 配置
    return {
        series: [{
            type: 'graph',
            layout: 'none',              // 使用固定坐标
            roam: true,                  // 支持缩放平移
            zoom: 1,                     // 1:1 缩放，不自动适配
            center: [0, 0],              // 居中
            draggable: true,
            data: nodes,
            links: links,
            ...
        }]
    };
}
```

关键 ECharts 参数：

| 参数 | 值 | 说明 |
|------|----|------|
| `layout` | `'none'` | 禁用自动布局，使用后端计算的固定坐标 |
| `roam` | `true` | 支持鼠标滚轮缩放和平移 |
| `zoom` | `1` | 1 坐标单位 = 1 像素，不自动缩放 |
| `center` | `[0, 0]` | 原点居中 |
| `draggable` | `true` | 节点可拖拽 |

#### 图谱模式切换

```javascript
function buildOption(mode) {
    const focusErrorOnly = mode !== 'all';
    const showPath = mode === 'errorPath';
    
    // 根据模式决定节点/边的显示状态
    // 'all'（全量）：所有节点正常显示
    // 'errorNodes'（错误节点）：仅显示错误相关节点，其他淡化
    // 'errorPath'（错误路径）：显示错误路径节点和边，其他淡化
}
```

#### 边曲率计算

```javascript
let cv = 0.15;  // 默认曲率
if (!hasPos) {
    // 后端坐标模式：使用固定小曲率
    // 前端布局模式：根据层级差动态调整曲率
    const dist = Math.abs(tl - sl);  // 层级差
    if (dist >= 3) cv = 0.8;
    else if (dist >= 2) cv = 0.5;
    // 根据左右方向翻转曲率
}
```

#### 跨树边检测

```javascript
const xt = tids[e.from] && tids[e.to] && tids[e.from] !== tids[e.to];
// 跨树边使用虚线、低透明度
lineStyle: {
    type: xt ? 'dotted' : 'solid',
    opacity: xt ? 0.4 : 0.95,
    curveness: cv
}
```

### 颜色定义

```javascript
const nodeColorMap = {
    RAIN_INLET: '#FACC15',        // 黄色
    RAIN_WELL: '#FEF08A',         // 淡黄色
    SEWAGE_INLET: '#6B7280',      // 深灰色
    SEWAGE_WELL: '#9CA3AF',       // 浅灰色
    COMBINED_WELL: '#A855F7',     // 紫色
    WWTP: '#22C55E',              // 绿色
    RIVER: '#38BDF8',             // 蓝色
    LAKE: '#7DD3FC',              // 淡蓝色
    RAIN_OUTLET: '#0EA5E9',       // 天蓝色
    LIFE_SEWAGE_INLET: '#92400E', // 棕色
    NORMAL: '#E5E7EB'             // 灰色
};
```

边颜色按通道类型：
```javascript
function channelColor(type) {
    if (type === 'RAIN') return '#0EA5E9';           // 蓝色
    if (type === 'SEWAGE') return '#64748B';          // 灰色
    if (type === 'COMBINED') return '#8B5CF6';        // 紫色
    if (type === 'LIFE_SEWAGE') return '#92400E';     // 棕色
    return '#94A3B8';                                  // 默认
}
```

### 交互事件

```javascript
chart.on('click', function(params) {
    if (params.dataType === 'node') showNodeDetail(params.data.value);
    if (params.dataType === 'edge') showEdgeDetail(params.data.value);
});

function showNodeDetail(n) {
    // 显示节点编号、名称、类型、状态、备注
}
function showEdgeDetail(e) {
    // 显示边编号、上下游节点、通道类型、状态
}
```

---

## 12. 独立 HTML 图谱（generateStandaloneHtml）

**文件**：`VisualizationService.java` 中的 `generateStandaloneHtml()` 方法

### 职责

生成一个完全自包含的 HTML 页面，嵌入所有图谱数据，可在浏览器直接打开，无需依赖后端 API。

### 实现方式

```java
public String generateStandaloneHtml(String graphJson) {
    String safeJson = graphJson == null || graphJson.isBlank() 
        ? "{\"nodes\":[],\"edges\":[],\"errors\":[]}" 
        : graphJson;
    
    return """
        <!DOCTYPE html>
        <html>
        ...
        <script>
        const graphData = __GRAPH_DATA__;
        // 与 index.html 相同的布局、渲染、交互逻辑
        ...
        </script>
        ...
        """.replace("__GRAPH_DATA__", safeJson);
}
```

通过 `String.replace("__GRAPH_DATA__", safeJson)` 将图谱数据嵌入 HTML。

### 与 index.html 的区别

| 特性 | index.html | generateStandaloneHtml |
|------|-----------|----------------------|
| 依赖后端 | 是（上传 → API → 渲染） | 否（数据内嵌） |
| 上传功能 | 有 | 无 |
| 检测功能 | 有 | 无 |
| 可视化 | 有 | 有（完全相同） |
| 数据来源 | API 响应 | HTML 内嵌 JSON |

---

## 13. 枚举体系

### NodeType（节点类型）

```java
public enum NodeType {
    RAIN_INLET,          // 雨水入口
    SEWAGE_INLET,        // 污水入口
    LIFE_SEWAGE_INLET,   // 生活污水入口
    RAIN_WELL,           // 雨水检查井
    SEWAGE_WELL,         // 污水检查井
    COMBINED_WELL,       // 合流检查井
    RAIN_OUTLET,         // 雨水排口（终点）
    RIVER,               // 河流（终点）
    LAKE,                // 湖泊（终点）
    WWTP,                // 污水处理厂（终点）
    NORMAL               // 普通节点
}
```

### ChannelType（通道类型）

```java
public enum ChannelType {
    RAIN,           // 雨水通道
    SEWAGE,         // 污水通道
    COMBINED,       // 合流通道
    LIFE_SEWAGE,    // 生活污水通道
    CUSTOM          // 自定义通道
}
```

### StartType（任务入口类型）

```java
public enum StartType {
    RAIN,           // 雨水入口
    SEWAGE,         // 污水入口
    LIFE_SEWAGE,    // 生活污水入口
    CUSTOM          // 自定义入口
}
```

### ErrorCode（错误码）

| 枚举值 | 触发条件 | 风险等级 | 说明 |
|--------|----------|----------|------|
| `NODE_NOT_FOUND` | DFS 时节点不在 nodeMap 中 | 高 | 边引用了不存在的节点 |
| `CHANNEL_NOT_ALLOWED` | 通道类型不在 StartRule.allowedChannels 中 | 高 | 如污水入口走了雨水通道 |
| `INVALID_END` | 叶子节点是终点类型但不符合合法终点条件 | 高 | 如雨水通道终点是 WWTP 但未进入特殊状态 |
| `DEAD_END` | 叶子节点不是终点类型且无下游 | 中 | 路径中途中断 |
| `CYCLE_FOUND` | visited 集合中已存在当前节点 | 中 | 检测到环路 |
| `PATH_TOO_DEEP` | nodePath 超过 MAX_DEPTH(500) | 中 | 路径过深或存在过长链路 |
| `TERMINAL_HAS_DOWNSTREAM` | 终点类型节点仍有出边 | 中 | 如排口后面还有节点 |
| `TOO_MANY_PATHS` | pathCounter 超过 MAX_PATHS(10000) | 中 | 下游分支过多 |
| `START_NODE_TYPE_MISMATCH` | task.startType 与 startNode.nodeType 不兼容 | 高 | 如上池任务的 startType 是 SEWAGE 但起点是 RAIN_INLET |
| `EXCEL_FORMAT_ERROR` | Excel 格式不满足解析要求 | 高 | - |
| `MISSING_REQUIRED_FIELD` | 必填字段缺失 | 高 | - |
| `MULTI_PATH_ERROR` | 总结时 task 产生多条路径且含错误 | 取决于子错误 | ResultSummaryService 汇总用 |

---

## 14. 配置体系

### 可视化配置（VisualizationConfig）

**文件**：`src/main/java/com/example/pipeflowcheck/config/VisualizationConfig.java`

通过 `config/rules.yml` 文件的 `visualization` 节点配置，被 `VisualizationService.loadConfig()` 加载。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nodeColors` | null（使用硬编码颜色） | 各节点类型的颜色映射 |
| `errorNodeColor` | `#FF6B6B` | 错误节点填充色 |
| `errorEdgeColor` | `#FF0000` | 错误边颜色 |
| `errorEdgePenWidth` | 3 | 错误边宽度 |
| `normalEdgeColor` | `#888888` | 普通边颜色 |
| `terminalShape` | `box` | 终点类型形状 |
| `normalShape` | `ellipse` | 普通节点形状 |
| `fontName` | `Microsoft YaHei` | 字体名称 |
| `graphDirection` | `LR` | 图方向（LR=从左到右） |

### 规则配置（RuleDefinition）

通过 `config/rules.yml` 文件的 `startRules` 节点配置，被 `RuleConfigService` 加载。

示例配置结构：
```yaml
startRules:
  RAIN:
    allowedChannels: [RAIN, SEWAGE, COMBINED]
    normalEndTypes: [RIVER, LAKE, RAIN_OUTLET]
    specialEndTypes: [WWTP]
    specialWhenChannels: [SEWAGE, COMBINED]
terminalTypes: [RIVER, LAKE, RAIN_OUTLET, WWTP]
errorPolicy:
  allDownstreamPathsMustBeValid: true
  cycleAsError: true
  deadEndAsError: true
```

---

## 15. 异常处理

**文件**：`src/main/java/com/example/pipeflowcheck/controller/ApiExceptionHandler.java`

### 全局异常处理

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "error", "BAD_REQUEST",
                    "message", exception.getMessage()
                ));
    }
}
```

对所有控制器中抛出的 `IllegalArgumentException` 统一处理，返回 HTTP 400 和 JSON 错误信息。

### 异常来源

| 抛出位置 | 异常类型 | 触发条件 |
|----------|----------|----------|
| `validateUpload()` | IllegalArgumentException | 空文件、非 .xlsx |
| `ExcelReadService.requiredSheet()` | IllegalArgumentException | Sheet 不存在 |
| `ExcelReadService.requiredValue()` | IllegalArgumentException | 必填字段为空 |
| `ExcelReadService.validateEdgeReferences()` | IllegalArgumentException | 节点引用不存在 |
| `ExcelReadService.enumValue()` | IllegalArgumentException | 枚举值非法 |
| `ExcelReadService.readNodes()` | IllegalArgumentException | 节点重复 |

---

## 16. 构建和运行

### 技术依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.3.0 | Web 框架 |
| Apache POI | 5.2.5 | Excel 读写 |
| Lombok | 1.18.32 | 数据模型简化 |
| graphviz-java | 0.18.1 | DOT → SVG/PNG 渲染 |
| SnakeYAML | 随 Spring Boot | YAML 解析 |
| Jackson | 随 Spring Boot | JSON 序列化 |
| JUnit 5 | 5.10.2 | 测试 |
| Spring Test | 6.1.8 | MockMvc 集成测试 |

### 构建命令

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 打包
mvn package

# 运行
mvn spring-boot:run
# 或
java -jar target/pipe-flow-check-0.1.0.jar
```

### 运行环境要求

- Java 17+
- Maven 3.8+
- （可选）Graphviz 原生库 — 用于 SVG/PNG 导出，不安装不影响核心功能
- 浏览器 —— 前端页面使用 ECharts 5.5.1（CDN 加载）
