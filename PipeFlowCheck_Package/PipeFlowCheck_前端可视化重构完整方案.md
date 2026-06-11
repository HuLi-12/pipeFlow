# PipeFlowCheck 前端可视化重构完整方案

## 0. 方案结论

当前 PipeFlowCheck 的核心检测链路已经比较完整，主要问题集中在前端可视化：

- 没有真实 GIS / CAD 坐标；
- 当前展示的是拓扑关系图，不是真实空间管网图；
- 全量拓扑图需要保留；
- 节点不允许重复，必须以 `node_id` 为唯一节点；
- 错误子图、任务子图、路径上下文图都必须从全局唯一节点池中筛选；
- 不能继续只依赖 ECharts Graph + 手写布局来承载所有图分析功能。

最终建议：

```text
前端直接替换为 G6 图工作台；
布局引入 ELK / elkjs；
后端检测链路保留；
后端 graph payload 升级为 nodes + edges + views + tasks + paths + errors；
前端提供全量拓扑图、错误子图、任务子图、路径上下文图四类视图；
所有视图共享同一套 nodes / edges，不重复节点；
ZIP 中同步输出新版 graph.json 和 standalone graph.html。
```

---

## 1. 当前项目现状

当前项目已经具备完整的后端检测流程：

```text
Excel 上传
  ↓
ExcelReadService 解析 Nodes / Edges / Tasks
  ↓
GraphBuildService 构建邻接表
  ↓
PipeCheckService DFS 全路径检测
  ↓
RuleEngine 判断通道与终点合法性
  ↓
ResultSummaryService 汇总任务结果
  ↓
ExcelWriteService 输出 Result / Summary
  ↓
VisualizationService 生成 graph payload / DOT / HTML / SVG / PNG
  ↓
ZipPackagingService 打包输出
```

当前前端 `index.html` 已经具备：

```text
上传 Excel
下载模板
下载结果 Excel
下载结果 ZIP
图谱展示
节点颜色
错误边高亮
节点/边点击详情
任务汇总
路径明细
模板要求折叠展示
```

当前问题不是检测逻辑，而是可视化框架不够适合“无坐标、有方向、共享节点、多子图”的管网拓扑展示。

---

## 2. 当前可视化问题分析

### 2.1 为什么树状布局不合适

管网关系不是严格树结构，而是有向图：

```text
多个入口可能汇入同一个下游节点；
一个节点可能分流到多个下游；
多个任务可能共享节点；
错误路径可能跨越多个分支；
合流井、污水厂、河流等节点容易形成汇聚结构。
```

树状布局的问题：

```text
共享节点位置尴尬；
同层节点容易挤压；
边线交叉严重；
全局没有中心；
错误路径被正常路径淹没；
拓扑越复杂，树状布局越难看。
```

### 2.2 为什么随机布局不适合作为主布局

随机布局虽然能打散节点，但会带来：

```text
每次刷新位置不同；
上下游方向感弱；
用户难以形成空间记忆；
错误链路可能不突出；
图形可解释性不稳定。
```

因此随机布局只适合作为辅助调试布局，不适合作为默认业务视图。

### 2.3 为什么 ECharts Graph 不再适合作为主框架

ECharts Graph 适合轻量关系图展示，但 PipeFlowCheck 现在需要的是图分析工作台：

```text
多视图切换；
子图过滤；
节点搜索；
错误路径聚焦；
任务路径聚焦；
布局切换；
节点/边详情；
节点共享；
大图性能控制；
独立 HTML 导出。
```

这些需求更适合使用专门的图可视化框架。

---

## 3. 前端技术路线

### 3.1 推荐方案

```text
图渲染框架：AntV G6
自动布局：ELK / elkjs
数据接口：后端返回统一 GraphPayload
首页入口：http://localhost:8080/
```

### 3.2 为什么选择 G6

G6 更适合当前项目，因为它是面向关系数据的图可视化框架，支持：

```text
节点 / 边自定义；
布局切换；
交互状态；
节点搜索；
子图筛选；
节点拖拽；
缩放平移；
高亮邻居；
插件扩展；
图分析场景。
```

### 3.3 为什么引入 ELK

ELK layered layout 适合有方向的 node-link 图，可以将节点尽可能沿一个方向分层排列。

PipeFlowCheck 的管网数据本质上是：

```text
入口节点 → 中间井 → 终点节点
```

所以 ELK layered 比手写 BFS 分层更适合：

```text
自动分层；
减少边交叉；
支持正交边；
支持复杂有向图；
布局参数可配置；
不需要真实坐标。
```

### 3.4 最终前端依赖

建议第一阶段直接通过 CDN 引入，后续再本地化：

```html
<script src="https://cdn.jsdelivr.net/npm/@antv/g6/dist/g6.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/elkjs/lib/elk.bundled.js"></script>
```

后续离线部署时改为：

```text
src/main/resources/static/vendor/g6.min.js
src/main/resources/static/vendor/elk.bundled.js
```

---

## 4. 总体前端架构

### 4.1 页面结构

```text
PipeFlowCheck 首页
├── 顶部上传区
│   ├── Excel 文件选择
│   ├── 开始检测
│   ├── 下载模板
│   ├── 下载结果 Excel
│   └── 下载结果 ZIP
│
├── 检测概览区
│   ├── 任务总数
│   ├── 异常任务数
│   ├── 路径总数
│   ├── 错误路径数
│   ├── 节点数
│   └── 边数
│
├── 图谱工作台
│   ├── 视图切换
│   │   ├── 全量拓扑图
│   │   ├── 错误子图
│   │   ├── 任务子图
│   │   └── 路径上下文图
│   │
│   ├── 图操作区
│   │   ├── 布局选择
│   │   ├── 任务选择
│   │   ├── 路径选择
│   │   ├── 节点搜索
│   │   ├── 仅显示错误
│   │   └── 重置视图
│   │
│   ├── G6 主图区域
│   │
│   └── 右侧详情区
│       ├── 节点详情
│       ├── 边详情
│       ├── 错误原因
│       └── 图例
│
├── 输入模板要求，默认折叠
├── 任务汇总，默认折叠
└── 路径明细，默认折叠
```

### 4.2 默认展示逻辑

```text
上传前：
  展示空图提示 + 模板要求折叠面板

上传并检测后：
  如果存在错误路径：
      默认展示错误子图
  如果不存在错误路径：
      默认展示全量拓扑图
```

### 4.3 视图切换逻辑

```text
全量拓扑图：
  显示全部 nodes 和 edges；
  普通节点正常显示；
  错误节点和错误边高亮；
  默认只显示入口、终点、错误节点标签。

错误子图：
  只显示错误路径涉及的节点和边；
  节点不重复；
  共享节点合并；
  显示全部标签；
  错误原因在右侧列表展示。

任务子图：
  用户选择 taskId；
  显示该任务相关的所有节点和边；
  错误路径红色；
  正常路径淡色；
  显示全部标签。

路径上下文图：
  用户选择某条路径；
  显示该路径节点和边；
  可额外显示路径节点的一跳上下游；
  用于定位具体错误段。
```

---

## 5. GraphPayload 数据结构升级

### 5.1 当前结构问题

当前 graph payload 主要是：

```json
{
  "nodes": [],
  "edges": [],
  "errors": []
}
```

这个结构只能画图，不适合做多视图切换。

### 5.2 新结构设计

建议升级为：

```json
{
  "overview": {
    "taskCount": 12,
    "normalTaskCount": 9,
    "errorTaskCount": 3,
    "pathCount": 28,
    "errorPathCount": 4,
    "nodeCount": 120,
    "edgeCount": 160
  },
  "nodes": [
    {
      "id": "N001",
      "name": "雨水口1",
      "type": "RAIN_INLET",
      "status": "normal",
      "remark": "",
      "isEntry": true,
      "isTerminal": false,
      "isError": false
    }
  ],
  "edges": [
    {
      "id": "E001",
      "from": "N001",
      "to": "N002",
      "type": "RAIN",
      "status": "normal",
      "remark": "",
      "isError": false
    }
  ],
  "tasks": [
    {
      "taskId": "T001",
      "startNodeId": "N001",
      "startNodeName": "雨水口1",
      "startType": "RAIN",
      "status": "错误",
      "pathCount": 3,
      "errorCount": 1,
      "riskLevel": "高"
    }
  ],
  "paths": [
    {
      "pathId": "T001-P001",
      "taskId": "T001",
      "pathNo": 1,
      "status": "错误",
      "nodePath": ["N001", "N004", "N005"],
      "edgePath": ["E003", "E004"],
      "channelPath": ["COMBINED", "COMBINED"],
      "endNodeId": "N005",
      "errorCode": "INVALID_END",
      "errorReason": "雨水进入合流后最终未抵达污水处理厂"
    }
  ],
  "views": {
    "global": {
      "nodeIds": ["N001", "N002", "N003"],
      "edgeIds": ["E001", "E002"]
    },
    "error": {
      "nodeIds": ["N001", "N004", "N005"],
      "edgeIds": ["E003", "E004"]
    },
    "task": {
      "T001": {
        "nodeIds": ["N001", "N002", "N003", "N004", "N005"],
        "edgeIds": ["E001", "E002", "E003", "E004"]
      }
    },
    "path": {
      "T001-P001": {
        "nodeIds": ["N001", "N004", "N005"],
        "edgeIds": ["E003", "E004"]
      }
    }
  },
  "errors": [
    {
      "taskId": "T001",
      "pathId": "T001-P001",
      "errorCode": "INVALID_END",
      "errorReason": "雨水进入合流后最终未抵达污水处理厂",
      "nodePath": ["N001", "N004", "N005"],
      "edgePath": ["E003", "E004"],
      "readablePath": "雨水口1(N001) --COMBINED--> 合流井1(N004) --COMBINED--> 河流2(N005)"
    }
  ]
}
```

### 5.3 核心原则

```text
nodes 是全局唯一节点池；
edges 是全局唯一边池；
views 只保存 nodeIds 和 edgeIds；
前端根据 view 过滤显示；
任何视图都不能复制节点；
同一个 node_id 永远只对应一个节点对象。
```

---

## 6. 后端调整方案

### 6.1 保留不变的模块

以下模块保持不变或只做轻微适配：

```text
ExcelReadService
GraphBuildService
RuleEngine
RuleConfigService
PipeCheckService
ResultSummaryService
ExcelWriteService
TemplateWriteService
ZipPackagingService
ApiExceptionHandler
```

原因：

```text
当前问题集中在可视化组织结构；
检测逻辑和 Excel 输出逻辑已经可用；
不需要重写核心检测算法。
```

### 6.2 重构 VisualizationService

当前 VisualizationService 职责过重，建议拆分：

```text
GraphPayloadService
SubgraphViewService
GraphLayoutService
GraphExportService
StandaloneHtmlService
```

---

## 7. 新增后端服务设计

### 7.1 GraphPayloadService

职责：

```text
将 nodeMap、edges、tasks、results 组装为新版 GraphPayload。
```

主要方法：

```java
public GraphPayload buildPayload(
    Map<String, Node> nodeMap,
    List<Edge> edges,
    List<CheckTask> tasks,
    List<CheckResult> results
)
```

输出内容：

```text
overview
nodes
edges
tasks
paths
views
errors
```

---

### 7.2 SubgraphViewService

职责：

```text
根据 Result 和 Task 构建不同视图的 nodeIds / edgeIds。
```

主要方法：

```java
public GraphViews buildViews(
    List<GraphNode> nodes,
    List<GraphEdge> edges,
    List<GraphTask> tasks,
    List<GraphPath> paths
)
```

需要生成：

```text
global view
error view
task views
path views
```

#### 全量视图

```java
views.global.nodeIds = all node ids
views.global.edgeIds = all edge ids
```

#### 错误视图

```java
错误视图节点 = 所有错误 path 的 nodePath 并集
错误视图边 = 所有错误 path 的 edgePath 并集
```

#### 任务视图

```java
每个 task 的节点 = 该 task 所有 path 的 nodePath 并集
每个 task 的边 = 该 task 所有 path 的 edgePath 并集
```

#### 路径视图

```java
每条 path 的节点 = path.nodePath
每条 path 的边 = path.edgePath
```

---

### 7.3 EdgeIdentityService

当前 Excel 的 Edge 没有显式 edge_id，前端子图需要稳定的 edge_id。

建议后端生成统一 edgeId：

```java
String edgeId = "E" + String.format("%05d", index + 1);
```

同时建立映射：

```text
from + "->" + to + ":" + channelType -> edgeId
```

用于从 CheckResult.path 和 channelPath 反推 edgePath。

注意：

```text
如果同一 from/to 之间存在不同 channelType，可以区分；
如果 from/to/channelType 完全重复，ExcelReadService 已经阻止重复。
```

---

### 7.4 GraphLayoutService

短期不建议后端继续承担主布局。新版方案中：

```text
后端可以不返回 x / y；
前端用 ELK 计算布局；
ZIP 静态图可以继续用 DOT / Graphviz 输出。
```

后端 GraphLayoutService 可保留作为兼容：

```java
public Map<String, Position> computeFallbackLayout(GraphPayload payload)
```

但前端主布局交给 ELK。

---

### 7.5 GraphExportService

职责：

```text
生成 DOT / SVG / PNG / standalone HTML。
```

继续输出：

```text
pipe-network-graph.json
pipe-network-graph.html
pipe-network-graph.dot
pipe-network-full.dot
pipe-network-graph.svg
pipe-network-full.png
```

但 `pipe-network-graph.json` 应升级为新版结构。

---

## 8. 控制器接口调整

### 8.1 保留现有接口

继续保留：

```text
GET  /api/pipe-flow/template
POST /api/pipe-flow/check
POST /api/pipe-flow/check-json
POST /api/pipe-flow/check-view-json
POST /api/pipe-flow/check-graph-json
POST /api/pipe-flow/check-zip
```

### 8.2 `/check-view-json` 返回结构调整

当前返回：

```json
{
  "summary": [],
  "results": [],
  "graph": {}
}
```

建议调整为：

```json
{
  "summary": [],
  "results": [],
  "graph": {
    "overview": {},
    "nodes": [],
    "edges": [],
    "tasks": [],
    "paths": [],
    "views": {},
    "errors": []
  }
}
```

### 8.3 `/check-graph-json` 返回结构调整

直接返回新版 `GraphPayload`：

```json
{
  "overview": {},
  "nodes": [],
  "edges": [],
  "tasks": [],
  "paths": [],
  "views": {},
  "errors": []
}
```

---

## 9. 前端重构方案

### 9.1 文件结构

建议将前端从单个巨大 `index.html` 拆分：

```text
src/main/resources/static/
├── index.html
├── css/
│   └── app.css
├── js/
│   ├── app.js
│   ├── api.js
│   ├── graph-workspace.js
│   ├── graph-layout.js
│   ├── graph-style.js
│   ├── graph-filter.js
│   ├── graph-detail.js
│   └── table-render.js
└── vendor/
    ├── g6.min.js
    └── elk.bundled.js
```

第一阶段可以先 CDN，稳定后再下载到 `vendor/`。

---

### 9.2 前端状态管理

```javascript
const state = {
  payload: null,
  graph: null,
  currentView: 'error',       // global | error | task | path
  currentTaskId: null,
  currentPathId: null,
  layoutMode: 'elk-layered',  // elk-layered | force | compact | radial
  labelMode: 'smart',         // smart | all | none
  selectedNodeId: null,
  selectedEdgeId: null
};
```

---

### 9.3 GraphWorkspace 核心方法

```javascript
async function initGraphWorkspace(containerId)

function setPayload(payload)

function switchView(viewType, options)

function renderCurrentView()

function buildViewData(payload, viewType, options)

async function applyLayout(graphData, layoutMode)

function renderGraph(graphData)

function highlightError()

function focusNode(nodeId)

function searchNode(keyword)

function resetView()
```

---

### 9.4 视图构建逻辑

```javascript
function buildViewData(payload, viewType, options) {
  const view = resolveView(payload.views, viewType, options);
  const nodeSet = new Set(view.nodeIds);
  const edgeSet = new Set(view.edgeIds);

  return {
    nodes: payload.nodes.filter(n => nodeSet.has(n.id)),
    edges: payload.edges.filter(e => edgeSet.has(e.id)),
    errors: payload.errors,
    overview: payload.overview
  };
}
```

---

## 10. G6 渲染设计

### 10.1 节点样式

```javascript
function nodeStyle(node, currentView) {
  return {
    size: node.isError ? 42 : 34,
    style: {
      fill: nodeColor(node.type),
      stroke: node.isError ? '#dc2626' : '#334155',
      lineWidth: node.isError ? 3 : 1
    },
    labelText: shouldShowLabel(node, currentView) ? `${node.name}\n${node.id}` : ''
  };
}
```

### 10.2 边样式

```javascript
function edgeStyle(edge) {
  return {
    style: {
      stroke: edge.isError ? '#dc2626' : channelColor(edge.type),
      lineWidth: edge.isError ? 4 : 1.5,
      lineDash: edge.isError ? [6, 4] : null,
      endArrow: true
    },
    labelText: edge.isError ? edge.type : ''
  };
}
```

### 10.3 标签策略

```text
全量图：
  只显示入口节点、终点节点、错误节点标签；
  普通中间节点不显示标签，hover 时显示。

错误子图：
  显示全部节点标签和边类型。

任务子图：
  显示全部节点标签和边类型。

路径上下文图：
  显示路径节点标签，周边节点淡化。
```

---

## 11. ELK 布局设计

### 11.1 ELK 输入结构

```javascript
const elkGraph = {
  id: 'root',
  layoutOptions: {
    'elk.algorithm': 'layered',
    'elk.direction': 'RIGHT',
    'elk.spacing.nodeNode': '80',
    'elk.layered.spacing.nodeNodeBetweenLayers': '140',
    'elk.edgeRouting': 'ORTHOGONAL'
  },
  children: nodes.map(n => ({
    id: n.id,
    width: 120,
    height: 48
  })),
  edges: edges.map(e => ({
    id: e.id,
    sources: [e.from],
    targets: [e.to]
  }))
};
```

### 11.2 ELK 输出处理

```javascript
const laidOut = await elk.layout(elkGraph);

const nodePositionMap = {};
laidOut.children.forEach(n => {
  nodePositionMap[n.id] = {
    x: n.x,
    y: n.y
  };
});
```

### 11.3 布局模式

建议提供：

```text
ELK 分层布局：
  默认布局，适合有向管网。

ELK 宽松布局：
  增大 nodeNode 与 layer spacing，适合演示。

Force 自由布局：
  辅助查看复杂网络，不作为默认。

Compact 紧凑布局：
  节点很多时使用。

Radial 径向布局：
  以选中入口或错误起点为中心查看局部结构。
```

---

## 12. 子图视图详细设计

### 12.1 全量拓扑图

显示：

```text
views.global.nodeIds
views.global.edgeIds
```

特点：

```text
所有节点唯一；
所有边唯一；
错误节点红框；
错误边红色虚线；
普通边淡化；
默认隐藏普通节点标签；
支持搜索定位。
```

### 12.2 错误子图

显示：

```text
views.error.nodeIds
views.error.edgeIds
```

特点：

```text
只显示错误相关节点和边；
节点共享，不重复；
显示全部标签；
右侧列出所有错误路径；
点击错误路径可聚焦对应 path view。
```

### 12.3 任务子图

显示：

```text
views.task[taskId].nodeIds
views.task[taskId].edgeIds
```

特点：

```text
选择一个 Task；
显示该 Task 所有路径相关节点和边；
错误路径高亮；
正常路径淡化；
适合解释任务汇总。
```

### 12.4 路径上下文图

显示：

```text
views.path[pathId].nodeIds
views.path[pathId].edgeIds
```

可选增强：

```text
显示路径节点的一跳上下游；
上下文节点透明度降低；
路径本身高亮。
```

---

## 13. 右侧详情区设计

右侧详情区分 4 个 Tab：

```text
节点详情
边详情
错误详情
图例说明
```

### 13.1 节点详情

```text
节点编号
节点名称
节点类型
是否入口
是否终点
是否错误相关
备注
入边数量
出边数量
相关任务
```

### 13.2 边详情

```text
边编号
上游节点
下游节点
通道类型
是否错误边
相关任务
相关路径
备注
```

### 13.3 错误详情

```text
taskId
pathId
errorCode
errorReason
readablePath
风险等级
```

### 13.4 图例说明

```text
雨水节点：黄色
污水节点：灰色
合流节点：紫色
污水处理厂：绿色
河流/湖泊：蓝色
错误节点：红色边框
错误边：红色虚线
```

---

## 14. 表格联动设计

### 14.1 任务汇总联动

任务汇总表每一行增加操作：

```text
查看任务子图
```

点击后：

```javascript
state.currentView = 'task';
state.currentTaskId = row.taskId;
renderCurrentView();
```

### 14.2 路径明细联动

路径明细表每一行增加操作：

```text
查看路径
```

点击后：

```javascript
state.currentView = 'path';
state.currentPathId = row.pathId;
renderCurrentView();
```

### 14.3 错误详情联动

错误列表每一项增加：

```text
定位错误路径
```

点击后：

```javascript
state.currentView = 'path';
state.currentPathId = error.pathId;
renderCurrentView();
```

---

## 15. ZIP 输出调整

当前 ZIP 输出可以保留，但建议升级内容：

```text
pipe-flow-check-result.xlsx
pipe-network-graph.json
pipe-network-graph.html
pipe-network-graph.dot
pipe-network-full.dot
pipe-network-graph.svg
pipe-network-full.png
```

新增或调整：

```text
pipe-network-graph.json
  使用新版 GraphPayload 结构

pipe-network-graph.html
  使用新版 G6 + ELK 工作台
  支持全量 / 错误 / 任务 / 路径视图切换

pipe-network-error-view.html
  可选，默认打开错误子图

pipe-network-global-view.html
  可选，默认打开全量图
```

---

## 16. 独立 HTML 方案

`generateStandaloneHtml()` 需要同步替换为新版 G6 HTML。

独立 HTML 不依赖后端 API，直接嵌入：

```javascript
const payload = __GRAPH_PAYLOAD__;
```

然后走同一套：

```javascript
initGraphWorkspace()
setPayload(payload)
renderCurrentView()
```

注意：

```text
独立 HTML 中 G6 和 ELK 可以继续使用 CDN；
如果需要完全离线，需要把 G6 和 ELK 源码嵌入 HTML 或作为 ZIP 内 vendor 文件一起输出。
```

---

## 17. 实施步骤

### 阶段一：数据结构升级

目标：

```text
后端返回新版 GraphPayload。
```

任务：

```text
1. 新增 GraphPayload / GraphNode / GraphEdge / GraphTask / GraphPath / GraphViews / GraphError 模型；
2. 新增 EdgeIdentityService；
3. 新增 GraphPayloadService；
4. 新增 SubgraphViewService；
5. 修改 VisualizationService.buildGraphPayload；
6. 修改 /check-view-json 和 /check-graph-json 返回结构；
7. 保持老字段兼容：nodes / edges / errors 不变，但增加 overview / tasks / paths / views。
```

验收：

```text
上传 Excel 后，接口返回 graph.overview、graph.views、graph.paths；
错误路径中 nodePath 和 edgePath 完整；
views.error 不是空；
views.task 能按 taskId 找到子图。
```

---

### 阶段二：前端替换为 G6 工作台

目标：

```text
替换 ECharts Graph 为 G6。
```

任务：

```text
1. 拆分 index.html；
2. 引入 G6；
3. 创建 GraphWorkspace；
4. 实现全量图；
5. 实现错误子图；
6. 实现任务子图；
7. 实现路径上下文图；
8. 实现节点/边点击详情；
9. 实现搜索节点；
10. 保留模板要求、任务汇总、路径明细默认折叠。
```

验收：

```text
访问 http://localhost:8080/ 默认进入新版页面；
上传 Excel 后显示检测概览；
有错误时默认进入错误子图；
全量图可以切换；
任务汇总点击后进入任务子图；
路径明细点击后进入路径图；
所有视图中 node_id 不重复。
```

---

### 阶段三：接入 ELK 布局

目标：

```text
用 ELK 替代手写分层布局。
```

任务：

```text
1. 引入 elkjs；
2. 实现 graph-layout.js；
3. 将 G6 节点数据转为 ELK 输入；
4. 获取 x/y 后回填 G6；
5. 支持 ELK 标准、宽松、紧凑布局；
6. 保留 force 布局作为辅助。
```

验收：

```text
全量图不再明显聚成一团；
错误子图显示清晰；
任务子图显示清晰；
切换布局不会丢失节点状态；
错误边仍然高亮。
```

---

### 阶段四：ZIP 与独立 HTML 升级

目标：

```text
下载 ZIP 后也能看到新版交互图。
```

任务：

```text
1. 修改 generateStandaloneHtml；
2. ZIP 中输出新版 pipe-network-graph.html；
3. graph.html 支持视图切换；
4. graph.html 默认错误子图；
5. graph.html 使用内嵌 GraphPayload；
6. 可选输出 vendor/g6.min.js 和 vendor/elk.bundled.js。
```

验收：

```text
调用 /check-zip；
解压 ZIP；
打开 pipe-network-graph.html；
能看到与在线页面一致的图工作台。
```

---

### 阶段五：测试与文档

目标：

```text
保证功能稳定可交付。
```

测试用例：

```text
1. 无错误样例：默认全量图；
2. 有错误样例：默认错误子图；
3. 多任务共享节点：节点不重复；
4. 多错误共享终点：错误子图中终点只出现一次；
5. 任务汇总点击联动：进入正确任务子图；
6. 路径明细点击联动：进入正确路径图；
7. ZIP 独立 HTML 可打开；
8. GraphPayload 中 views.global / views.error / views.task / views.path 完整。
```

文档更新：

```text
README.md
docs/frontend_graph_workspace_plan.md
docs/graph_payload_schema.md
docs/visualization_usage.md
```

---

## 18. 文件改动清单

### 18.1 后端新增

```text
src/main/java/com/example/pipeflowcheck/visual/model/GraphPayload.java
src/main/java/com/example/pipeflowcheck/visual/model/GraphNode.java
src/main/java/com/example/pipeflowcheck/visual/model/GraphEdge.java
src/main/java/com/example/pipeflowcheck/visual/model/GraphTask.java
src/main/java/com/example/pipeflowcheck/visual/model/GraphPath.java
src/main/java/com/example/pipeflowcheck/visual/model/GraphViews.java
src/main/java/com/example/pipeflowcheck/visual/model/GraphError.java

src/main/java/com/example/pipeflowcheck/service/EdgeIdentityService.java
src/main/java/com/example/pipeflowcheck/service/GraphPayloadService.java
src/main/java/com/example/pipeflowcheck/service/SubgraphViewService.java
src/main/java/com/example/pipeflowcheck/service/GraphExportService.java
```

### 18.2 后端修改

```text
VisualizationService.java
PipeCheckController.java
ZipPackagingService 调用处
generateStandaloneHtml 相关逻辑
```

### 18.3 前端新增

```text
src/main/resources/static/css/app.css
src/main/resources/static/js/app.js
src/main/resources/static/js/api.js
src/main/resources/static/js/graph-workspace.js
src/main/resources/static/js/graph-layout.js
src/main/resources/static/js/graph-style.js
src/main/resources/static/js/graph-filter.js
src/main/resources/static/js/graph-detail.js
src/main/resources/static/js/table-render.js
```

### 18.4 前端替换

```text
src/main/resources/static/index.html
```

### 18.5 文档新增

```text
docs/frontend_graph_workspace_plan.md
docs/graph_payload_schema.md
docs/visualization_usage.md
```

---

## 19. 风险与规避

### 风险一：G6 与 ELK 引入后页面复杂度上升

规避：

```text
先 CDN 引入；
前端模块化拆分；
保持后端接口稳定；
分阶段替换，不一次性重写所有导出功能。
```

### 风险二：大图依然可能复杂

规避：

```text
默认错误子图；
全量图默认隐藏普通标签；
提供搜索；
提供任务子图；
提供路径上下文图；
提供布局切换。
```

### 风险三：独立 HTML 离线依赖 CDN

规避：

```text
第一阶段允许 CDN；
第二阶段将 G6 和 ELK 放入 ZIP vendor 目录；
第三阶段可将依赖内嵌到 HTML。
```

### 风险四：edgePath 反推错误

规避：

```text
后端统一生成 edgeId；
ExcelReadService 已阻止重复 from/to/channelType；
GraphPayloadService 统一维护 edgeKey -> edgeId 映射。
```

---

## 20. 最终验收标准

完成后应满足：

```text
1. http://localhost:8080/ 默认进入新版图工作台；
2. 上传 Excel 后能显示检测概览；
3. 全量拓扑图存在；
4. 错误子图存在；
5. 任务子图存在；
6. 路径上下文图存在；
7. 所有视图共享节点，不重复 node_id；
8. 错误路径节点和边完整；
9. 任务汇总、路径明细、模板要求默认折叠；
10. 点击任务汇总可切换任务子图；
11. 点击路径明细可切换路径上下文图；
12. 节点颜色、边颜色、错误高亮符合规则；
13. ZIP 中的独立 HTML 也能展示新版图工作台；
14. README 和 docs 文档同步更新。
```

---

## 21. 推荐最终版本定位

重构完成后的项目定位应调整为：

```text
PipeFlowCheck 是一个基于标准 Excel 模板的排水管网流向合规性检测与图谱诊断系统。
```

核心能力：

```text
标准模板输入；
任务入口固定；
DFS 全路径检测；
雨水/污水/合流规则判断；
Result + Summary 输出；
全量拓扑图；
错误子图；
任务子图；
路径上下文图；
节点共享；
错误链路诊断；
ZIP 归档交付。
```

最终前端不再是简单的“画一张图”，而是：

```text
管网拓扑诊断工作台。
```
