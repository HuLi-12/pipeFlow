# PipeFlowCheck — 排水管网流向检测系统

## 概述

PipeFlowCheck 是一个面向城市排水管网的流向合规性检测系统。用户上传包含**节点、边、检测任务**的标准 Excel 模板，系统自动构建管网图，对每个任务执行深度优先遍历（DFS），按规则引擎检测路径合法性，并以**可视化图谱 + Excel 报告 + ZIP 归档**三种形式输出结果。

---

## 技术栈

| 层次 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3 + Java 17 |
| 构建工具 | Maven |
| Excel 处理 | Apache POI 5.2.5 |
| 可视化 | ECharts 5.5.1（前端图谱），Graphviz-java 0.18.1（SVG/PNG 后备）|
| ZIP 打包 | java.util.zip |
| 工具库 | Lombok, Jackson |
| 测试 | JUnit 5, Spring MockMvc |

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                     PipeCheckController                      │
│  /check, /check-json, /check-zip, /check-view-json,         │
│  /check-graph-json, /template                                │
└───────────┬──────────────────────────────┬──────────────────┘
            │                              │
            ▼                              ▼
┌───────────────────────┐    ┌──────────────────────────────┐
│   ExcelReadService     │    │   GraphBuildService          │
│   Nodes / Edges / Tasks│    │   邻接表: fromNode → [Edges] │
│   (POI XSSFWorkbook)   │    │                              │
└───────────┬───────────┘    └──────────────┬───────────────┘
            │                              │
            ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    PipeCheckService                          │
│  对每个 Task 执行 DFS 遍历，RuleEngine 判定路径终点合法性    │
│  CheckContext 记录路径 + 通道 + 状态机                        │
└───────────────────────────┬─────────────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            ▼               ▼               ▼
┌─────────────────┐ ┌────────────┐ ┌──────────────────┐
│ ExcelWriteService│ │ResultSum- │ │VisualizationService│
│ Result + Summary │ │maryService│ │ backend layout    │
│ Sheet            │ │ 汇总 +    │ │ + graph JSON      │
│                  │ │ 错误分类   │ │ + standalone HTML │
└─────────────────┘ └────────────┘ └──────────────────┘
```

---

## 核心数据模型

### 节点 (Node)

| 字段 | 说明 |
|------|------|
| `node_id` | 节点唯一标识（如 N001）|
| `node_name` | 节点名称（如 雨水口-1）|
| `node_type` | 节点类型枚举 |
| `remark` | 备注 |

**节点类型枚举 (`NodeType`)**：

| 类型 | 含义 | 是否终点 |
|------|------|----------|
| `RAIN_INLET` | 雨水入口 | 否 |
| `SEWAGE_INLET` | 污水入口 | 否 |
| `LIFE_SEWAGE_INLET` | 生活污水入口 | 否 |
| `RAIN_WELL` | 雨水检查井 | 否 |
| `SEWAGE_WELL` | 污水检查井 | 否 |
| `COMBINED_WELL` | 合流检查井 | 否 |
| `NORMAL` | 普通节点 | 否 |
| `RAIN_OUTLET` | 雨水排口 | **是** |
| `RIVER` | 河流 | **是** |
| `LAKE` | 湖泊 | **是** |
| `WWTP` | 污水处理厂 | **是** |

### 边 (Edge)

| 字段 | 说明 |
|------|------|
| `from_node_id` | 上游节点 ID |
| `to_node_id` | 下游节点 ID |
| `channel_type` | 通道类型枚举 |
| `remark` | 备注 |

**通道类型枚举 (`ChannelType`)**：`RAIN`(雨水) / `SEWAGE`(污水) / `COMBINED`(合流) / `LIFE_SEWAGE`(生活污水) / `CUSTOM`(自定义)

### 检测任务 (CheckTask)

| 字段 | 说明 |
|------|------|
| `task_id` | 任务唯一标识（如 T001）|
| `start_node_id` | 起点节点 ID |
| `start_type` | 入口类型枚举 |

**入口类型枚举 (`StartType`)**：`RAIN`(雨水) / `SEWAGE`(污水) / `LIFE_SEWAGE`(生活污水) / `CUSTOM`(自定义)

---

## 规则引擎设计

### 规则配置 (`RuleEngine`)

每种 `StartType` 定义一组规则：

```
RAIN:
  allowedChannels: [RAIN, SEWAGE, COMBINED]
  normalEndTypes: [RIVER, LAKE, RAIN_OUTLET]   ← 走雨水通道到达的合法终点
  specialEndTypes: [WWTP]                       ← 进入污水/合流通道后的合法终点
  specialWhenChannels: [SEWAGE, COMBINED]

SEWAGE:
  allowedChannels: [SEWAGE, COMBINED]
  normalEndTypes: [WWTP]
  specialEndTypes: [WWTP]

LIFE_SEWAGE:
  allowedChannels: [LIFE_SEWAGE, SEWAGE, COMBINED]
  normalEndTypes: [WWTP]
  specialEndTypes: [WWTP]
```

**规则逻辑**：
1. **allowedChannels** —— 入口类型允许通过的通道类型（如雨水不能走 LIFE_SEWAGE 通道）
2. **normalEndTypes** —— 正常状态下途经通道类型时，路线的合法终点类型
3. **specialEndTypes** —— 进入特殊通道后的合法终点类型
4. **specialWhenChannels** —— 触发特殊状态的通道类型（如雨水进入污水管后，终点要求变为 WWTP）

### 状态机

```
                    ┌──────────────┐
                    │ Normal State │
                    └──────┬───────┘
                           │
               ┌───────────┴───────────┐
               ▼                       ▼
        channel ∈ normalEndTypes   channel ∈ specialWhenChannels
               │                       │
               ▼                       ▼
        继续遍历 / 到达终点          ┌────────────────┐
                                   │ Special State   │
                                   │ specialEndTypes │
                                   └────────────────┘
```

### 起点节点兼容性校验

检查任务设定的 `start_type` 是否与起点节点的 `node_type` 匹配：

| StartType | 兼容的 NodeType |
|-----------|----------------|
| `RAIN` | RAIN_INLET, RAIN_WELL, COMBINED_WELL |
| `SEWAGE` | SEWAGE_INLET, SEWAGE_WELL, COMBINED_WELL |
| `LIFE_SEWAGE` | LIFE_SEWAGE_INLET, SEWAGE_WELL, COMBINED_WELL |
| `CUSTOM` | 所有类型 |

---

## 核心检测算法

采用 **深度优先搜索（DFS）+ 分支拷贝** 的路径遍历策略。

### 算法流程

```
checkAll(tasks)
  for each task:
    checkSingleTask(task)
      ├─ 校验起点兼容性
      └─ dfs(startNode)
           ├─ 节点存在性检查
           ├─ 环路检测 (visited set)
           ├─ 最大深度保护 (MAX_DEPTH=500)
           ├─ 最大路径数保护 (MAX_PATHS=10000)
           ├─ 终点有下游检测 (TERMINAL_HAS_DOWNSTREAM)
           ├─ 叶子节点判定
           │    ├─ isValidEnd → 合法终点 → success
           │    └─ 非法终点 → DEAD_END / INVALID_END
           └─ 分支遍历
                ├─ channelAllowed 检测
                ├─ 特殊通道状态转移
                └─ 递归 dfs(nextNode)
```

### DFS 上下文 (`CheckContext`)

每次分支遍历时通过 `copy()` 深拷贝上下文：

| 字段 | 说明 |
|------|------|
| `nodePath` | 当前路径经过的节点 ID 列表 |
| `channelPath` | 经过的通道类型列表 |
| `visited` | 已访问节点（环路检测）|
| `enteredSpecialChannel` | 是否进入特殊状态 |
| `pathCounter` | 全局共享计数器（所有分支共用）|

### 错误码体系 (`ErrorCode`)

| 错误码 | 风险等级 | 说明 |
|--------|----------|------|
| `START_NODE_TYPE_MISMATCH` | 高 | 任务入口类型与起点节点类型不兼容 |
| `NODE_NOT_FOUND` | 高 | 路径中出现不存在的节点 |
| `CHANNEL_NOT_ALLOWED` | 高 | 入口类型不允许进入该通道 |
| `INVALID_END` | 高 | 路径终点不是合法的终点类型 |
| `DEAD_END` | 中 | 路径中途中断，无下游节点 |
| `CYCLE_FOUND` | 中 | 检测到环路 |
| `PATH_TOO_DEEP` | 中 | 路径超过最大深度 500 |
| `TERMINAL_HAS_DOWNSTREAM` | 中 | 终点类型节点（排口/河流等）仍有下游边 |
| `TOO_MANY_PATHS` | 中 | 下游分支超过 10000 条 |
| `MULTI_PATH_ERROR` | 根据子错误 | 一个任务产生了多条路径（含正常和错误）|

### 结果汇总逻辑 (`ResultSummaryService`)

1. 按 `task_id` 分组所有 `CheckResult`
2. 若该组无错误 → `status=通道正常`
3. 若该组有错误：
   - 产生多条路径（含正常路径） → `errorCode=MULTI_PATH_ERROR`
   - 仅一条错误路径 → 沿用该路径的 `errorCode`
4. 风险等级取所有路径中的最高级

---

## 可视化实现

### 后端布局算法 (`computeBackendLayout`)

采用 **Sugiyama 分层布局**（全局网格），不依赖外部 Graphviz：

```
1. BFS 分层
   - 计算入度，找出所有根节点（入度为 0）
   - 按树分组，对每棵树进行 BFS 分层
   - 每个节点分配 layer (0, 1, 2, ...)

2. Barycenter 层内排序
   - 对每层节点，按上游节点在前一层的平均位置排序
   - 减少边交叉

3. 固定间距定位
   - H_GAP = 200px（水平间距）
   - V_GAP = 250px（层间间距）
   - 每层内节点居中排列
```

### ECharts 前端渲染

- **layout: 'none'** —— 使用后端计算的固定坐标，禁用 ECharts 自动布局
- **zoom: 1, center: [0, 0]** —— 1:1 显示，不自动缩放压缩
- **roam: true** —— 支持鼠标滚轮缩放和平移
- **节点颜色**：按节点类型区分（黄色=雨水，灰色=污水，紫色=合流等）
- **错误高亮**：错误相关节点红色边框加粗，错误边红色虚线

### 输出格式

| 格式 | 说明 |
|------|------|
| `graph JSON` | 结构化图谱数据（nodes / edges / errors），含 x/y 坐标 |
| `standalone HTML` | 可直接打开的独立图谱页面（嵌入 ECharts）|
| `DOT` | Graphviz DOT 格式源文件（含/不含错误高亮）|
| `SVG / PNG` | 通过 Graphviz-java 渲染的静态图片 |

---

## API 接口

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/pipe-flow/check` | POST | 上传 Excel，返回结果 Excel（Result + Summary Sheet）|
| `/api/pipe-flow/check-json` | POST | 上传 Excel，返回 JSON 格式结果 + 汇总 |
| `/api/pipe-flow/check-view-json` | POST | 上传 Excel，返回结果 + 汇总 + 图谱 JSON（前端渲染用）|
| `/api/pipe-flow/check-graph-json` | POST | 上传 Excel，仅返回图谱 JSON（nodes + edges 含坐标）|
| `/api/pipe-flow/check-zip` | POST | 上传 Excel，返回 ZIP 归档（含 result.xlsx + 图谱文件）|
| `/api/pipe-flow/template` | GET | 下载标准 Excel 模板（含 Nodes/Edges/Tasks/Template Sheet）|

---

## Excel 模板格式

### 必需 Sheet

#### Nodes

| node_id | node_name | node_type | remark |
|---------|-----------|-----------|--------|
| N001 | 雨水口-1 | RAIN_INLET | 雨水入口 1 号 |

#### Edges

| from_node_id | to_node_id | channel_type | remark |
|-------------|-----------|-------------|--------|
| N001 | N002 | RAIN | 雨水管道 |

#### Tasks

| task_id | start_node_id | start_type | remark |
|---------|--------------|-----------|--------|
| T001 | N001 | RAIN | 雨水 A 完整路径 |

### 校验规则
- 所有 Sheet 必须有表头行
- 节点 `node_id` 不能重复
- 边引用的节点必须在 Nodes 中存在
- 同一条边（from+to+channelType）不能重复

---

## 项目结构

```
PipeFlowCheck_Package/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/pipeflowcheck/
    │   │   ├── PipeFlowCheckApplication.java         # Spring Boot 入口
    │   │   ├── config/
    │   │   │   ├── RuleDefinition.java               # 规则模型
    │   │   │   └── VisualizationConfig.java           # 可视化配置模型
    │   │   ├── controller/
    │   │   │   ├── PipeCheckController.java           # 核心 API 控制器
    │   │   │   ├── ApiExceptionHandler.java           # 全局异常处理
    │   │   │   └── RuleConfigController.java          # 规则配置 API
    │   │   ├── enums/
    │   │   │   ├── NodeType.java                     # 节点类型枚举
    │   │   │   ├── ChannelType.java                  # 通道类型枚举
    │   │   │   ├── StartType.java                    # 入口类型枚举
    │   │   │   └── ErrorCode.java                    # 错误码枚举
    │   │   ├── model/
    │   │   │   ├── Node.java                         # 节点模型
    │   │   │   ├── Edge.java                         # 边模型
    │   │   │   ├── CheckTask.java                    # 检测任务模型
    │   │   │   ├── CheckResult.java                  # 检测结果模型
    │   │   │   ├── CheckContext.java                 # DFS 上下文
    │   │   │   ├── CheckResponse.java                # JSON 响应模型
    │   │   │   ├── PipeNetworkData.java              # 管网数据容器
    │   │   │   └── TaskSummary.java                  # 任务汇总模型
    │   │   └── service/
    │   │       ├── ExcelReadService.java              # Excel 解析
    │   │       ├── ExcelWriteService.java             # 结果 Excel 生成
    │   │       ├── GraphBuildService.java             # 邻接表构建
    │   │       ├── PipeCheckService.java              # 核心检测逻辑
    │   │       ├── RuleEngine.java                    # 规则引擎
    │   │       ├── RuleConfigService.java             # 规则配置加载
    │   │       ├── ResultSummaryService.java          # 结果汇总
    │   │       ├── TemplateWriteService.java          # 模板生成
    │   │       ├── VisualizationService.java          # 可视化布局
    │   │       └── ZipPackagingService.java           # ZIP 打包
    │   └── resources/
    │       └── static/
    │           └── index.html                        # 前端页面
    └── test/java/com/example/pipeflowcheck/
        ├── PipeCheckControllerTest.java              # 控制器测试
        ├── PipeCheckServiceBehaviorTest.java          # 检测逻辑行为测试
        ├── PipeFlowCheckApplicationTest.java          # 应用启动测试
        ├── PipeFlowCheckIntegrationTest.java          # 集成测试
        ├── RuleConfigServiceTest.java                 # 规则配置测试
        ├── SampleWorkbookFactory.java                # 测试 Excel 工厂
        └── StaticPageTest.java                       # 静态页面测试
```

---

## 检测场景示例

### 正常场景
- **雨水入口 → 雨水井 → 雨水井 → 雨水排口** → 正常
- **污水入口 → 污水井 → 污水处理厂** → 正常
- **生活污水入口 → 生活污水管 → 污水井 → 污水处理厂** → 正常

### 错误场景
- **雨水进入污水管 → 环路检测** → CYCLE_FOUND
- **路径中途无下游节点** → DEAD_END
- **排口类型节点仍有下游边** → TERMINAL_HAS_DOWNSTREAM
- **污水入口走雨水管道** → CHANNEL_NOT_ALLOWED
