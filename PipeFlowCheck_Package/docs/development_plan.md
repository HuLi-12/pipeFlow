# PipeFlowCheck 完整开发方案

## 一、项目命名

推荐名称：**PipeFlowCheck - 城市排水管网流向合规性检测系统**。

候选中文名称：
1. 城市排水管网流向合规性检测系统
2. 排水管网通道通畅与去向正确性分析系统
3. 基于有向图遍历的雨污水管网路径校验系统

最终推荐使用 PipeFlowCheck，因为名称短、工程化、便于包名、接口名和部署命名。

---

## 二、项目目标

系统读取 Excel 管网图数据，将节点与通道关系抽象为有向图，从检测入口开始遍历所有下游路径，并根据规则判断每条路径是否合法。

最终输出结果 Excel，内容包括：
- 检测任务编号
- 起始节点
- 起始节点类型
- 完整路径
- 抵达终点
- 状态：通道正常 / 错误
- 错误原因
- 风险等级

---

## 三、核心业务原则

### 1. 所有下游路径必须合法

如果一个节点存在多个下游分支，则系统必须检查所有分支。

只要任意一条分支出现错误，该入口整体判定为错误。

例如：

```text
雨水口A
  ├── 雨水井1 -> 河流       合法
  └── 合流井1 -> 河流       错误
```

最终结果应为：

```text
错误：存在非法下游路径，雨水进入合流/污水系统后未抵达污水处理厂
```

### 2. 当前主要支持雨水和污水

当前基础规则：

| 入口类型 | 允许通道 | 合法终点 |
|---|---|---|
| 雨水 RAIN | RAIN、SEWAGE、COMBINED | 未进入污水/合流时：RIVER、LAKE、RAIN_OUTLET；进入污水/合流后：WWTP |
| 污水 SEWAGE | SEWAGE、COMBINED | WWTP |

### 3. 支持规则扩展

后续可以扩展：
- 生活污水 LIFE_SEWAGE
- 工业废水 INDUSTRIAL_WASTEWATER
- 初期雨水 FIRST_FLUSH_RAIN
- 再生水 RECLAIMED_WATER
- 事故排水 EMERGENCY_DRAINAGE

规则不建议写死在代码中，建议配置化。

---

## 四、推荐输入数据结构

Excel 不必一开始完全固定，但系统内部需要统一成三类对象：Node、Edge、CheckTask。

### 1. Node 节点

字段建议：
- node_id：节点唯一编号
- node_name：节点名称
- node_type：节点类型
- remark：备注

节点类型建议：
- RAIN_INLET：雨水口
- SEWAGE_INLET：污水口
- LIFE_SEWAGE_INLET：生活污水入口
- RAIN_WELL：雨水井
- SEWAGE_WELL：污水井
- COMBINED_WELL：合流井
- RAIN_OUTLET：雨水排口
- RIVER：河流
- LAKE：湖泊
- WWTP：污水处理厂
- NORMAL：普通节点

### 2. Edge 通道

字段建议：
- from_node_id：上游节点
- to_node_id：下游节点
- channel_type：通道类型
- remark：备注

通道类型建议：
- RAIN：雨水通道
- SEWAGE：污水通道
- COMBINED：合流通道
- LIFE_SEWAGE：生活污水通道
- CUSTOM：扩展通道

### 3. CheckTask 检测任务

字段建议：
- task_id：任务编号
- start_node_id：起始节点
- start_type：入口类型
- remark：备注

---

## 五、输出结果 Excel

建议输出字段：

| 字段 | 说明 |
|---|---|
| task_id | 检测任务编号 |
| start_node_id | 起点编号 |
| start_node_name | 起点名称 |
| start_type | 起点类型 |
| status | 通道正常 / 错误 |
| end_node_id | 终点编号 |
| end_node_name | 终点名称 |
| path | 完整路径 |
| error_code | 错误编码 |
| error_reason | 错误原因 |
| risk_level | 风险等级 |

---

## 六、算法方案

### 1. 图结构

将 Edge 转换为邻接表：

```java
Map<String, List<Edge>> graph;
```

其中 key 为 from_node_id，value 为所有下游边。

### 2. DFS 全路径遍历

使用 DFS 而不是只找一条路径，原因是系统要求所有下游路径都必须合法。

DFS 过程中维护：
- currentNodeId：当前节点
- path：当前路径
- visited：当前分支访问过的节点，用于检测环路
- context：规则上下文，例如是否进入污水/合流系统
- errors：错误集合

### 3. 判断流程

1. 检查当前节点是否存在
2. 检查是否出现环路
3. 将当前节点加入路径
4. 查询当前节点所有下游边
5. 如果没有下游边，则判断当前节点是否为合法终点
6. 如果存在下游边，则逐条检查通道是否合法
7. 对每个下游节点递归 DFS
8. 汇总所有路径结果
9. 只要存在错误路径，则任务失败

---

## 七、错误类型设计

| 错误编码 | 错误说明 | 风险等级 |
|---|---|---|
| NODE_NOT_FOUND | 节点不存在 | 高 |
| CHANNEL_NOT_ALLOWED | 入口进入不允许的通道 | 高 |
| INVALID_END | 抵达非法终点 | 高 |
| DEAD_END | 路径中断，未找到合法终点 | 中 |
| CYCLE_FOUND | 检测到环路 | 中 |
| EXCEL_FORMAT_ERROR | Excel 格式错误 | 高 |
| MISSING_REQUIRED_FIELD | 必填字段缺失 | 高 |
| MULTI_PATH_ERROR | 多下游路径中存在非法路径 | 高 |

---

## 八、规则配置设计

规则配置可以使用 YAML/JSON/数据库。核心结构如下：

```yaml
startRules:
  RAIN:
    allowedChannels: [RAIN, SEWAGE, COMBINED]
    normalEndTypes: [RIVER, LAKE, RAIN_OUTLET]
    pollutedEndTypes: [WWTP]
    pollutedWhenChannels: [SEWAGE, COMBINED]
  SEWAGE:
    allowedChannels: [SEWAGE, COMBINED]
    normalEndTypes: [WWTP]
```

含义：
- allowedChannels：该入口允许经过的通道类型
- normalEndTypes：普通情况下允许抵达的终点
- pollutedWhenChannels：一旦经过这些通道，就进入特殊状态
- pollutedEndTypes：进入特殊状态后必须抵达的终点

对雨水而言：
- 如果一直走 RAIN 通道，可以抵达河流、湖泊、雨水排口
- 一旦进入 SEWAGE 或 COMBINED，则必须抵达 WWTP

---

## 九、模块划分

### 1. Controller 层

负责文件上传、结果下载。

主要接口：
- POST /api/pipe-flow/check

### 2. ExcelReadService

负责读取 Excel，转换为内部统一模型。

### 3. GraphBuildService

负责构建有向图邻接表。

### 4. RuleConfigService

负责加载和管理规则配置。

### 5. PipeCheckService

负责执行核心 DFS 检测。

### 6. ExcelWriteService

负责生成结果 Excel。

### 7. ExceptionHandler

负责统一异常处理。

---

## 十、推荐开发步骤

### 阶段 1：基础模型与模板

1. 定义 Node、Edge、CheckTask、CheckResult
2. 定义 NodeType、ChannelType、StartType、ErrorCode
3. 定义 Excel 输入模板
4. 定义结果 Excel 输出模板

### 阶段 2：Excel 解析

1. 读取 Nodes Sheet
2. 读取 Edges Sheet
3. 读取 Tasks Sheet
4. 校验必填字段
5. 构造统一数据对象

### 阶段 3：构建图

1. 将 Edge 按 from_node_id 分组
2. 检查边引用的节点是否存在
3. 检查孤立节点、重复边、空边

### 阶段 4：核心校验

1. 实现 DFS 全路径搜索
2. 实现环路检测
3. 实现死路检测
4. 实现通道规则校验
5. 实现终点规则校验
6. 实现多分支汇总逻辑

### 阶段 5：输出报告

1. 输出每个任务的总体结果
2. 输出所有路径明细
3. 输出错误原因
4. 错误行标红或标注风险等级

### 阶段 6：接口封装

1. 上传 Excel
2. 返回结果 Excel
3. 支持规则文件上传或默认规则
4. 日志记录

---

## 十一、测试用例

### 正常用例

1. 雨水 -> 雨水通道 -> 河流
2. 雨水 -> 合流通道 -> 污水处理厂
3. 污水 -> 污水通道 -> 污水处理厂
4. 污水 -> 合流通道 -> 污水处理厂

### 错误用例

1. 污水 -> 雨水通道 -> 河流
2. 雨水 -> 合流通道 -> 河流
3. 任意入口 -> 普通井口断头
4. 井口 A -> 井口 B -> 井口 A 环路
5. 多分支中一条正常、一条错误
6. 节点不存在
7. Excel 字段缺失

---

## 十二、最终交付形式

建议最终系统形态：

```text
用户上传 Excel
      ↓
后端解析 Excel
      ↓
构建有向图
      ↓
读取规则配置
      ↓
DFS 遍历所有下游路径
      ↓
判断所有路径是否合规
      ↓
生成结果 Excel
      ↓
用户下载检测报告
```
