# PipeFlowCheck 前端管网图可视化实现说明

## 1. 本次补齐的功能

本次在现有 Excel 检测、结果 Excel、DOT/SVG/PNG 输出基础上，补齐了前端交互式管网图展示能力。

新增能力：

1. 新增 `/api/pipe-flow/check-view-json` 接口，一次返回：
   - `summary`：任务汇总；
   - `results`：路径明细；
   - `graph`：前端可直接渲染的管网图 JSON。
2. 新增 `/api/pipe-flow/check-graph-json` 接口，只返回管网图 JSON。
3. `/api/pipe-flow/check-zip` 输出 ZIP 中新增：
   - `pipe-network-graph.json`：前端图数据；
   - `pipe-network-graph.html`：可直接打开的交互式管网图页面；
   - 保留原有 `xlsx / dot / svg / png` 输出。
4. 首页 `static/index.html` 已增加可视化区域：
   - 上传 Excel 后直接展示管网图；
   - 节点按类型着色；
   - 错误节点红色边框；
   - 错误链路红色、加粗、虚线；
   - 点击节点/边查看详情；
   - 支持“定位错误链路”。

---

## 2. 前端图 JSON 结构

后端返回的 `graph` 结构如下：

```json
{
  "nodes": [
    {
      "id": "N001",
      "name": "雨水口A",
      "type": "RAIN_INLET",
      "status": "error",
      "remark": "正常雨水入口"
    }
  ],
  "edges": [
    {
      "id": "N001->N002",
      "from": "N001",
      "to": "N002",
      "type": "RAIN",
      "status": "error",
      "remark": "雨水进入雨水井"
    }
  ],
  "errors": [
    {
      "taskId": "T001",
      "startNodeId": "N001",
      "errorCode": "INVALID_END",
      "errorReason": "雨水进入合流通道后，终点必须是污水处理厂。",
      "nodePath": ["N001", "N004", "N005"],
      "edgePath": ["N001->N004", "N004->N005"],
      "readablePath": "雨水口A(N001) --COMBINED--> 合流井(N004) --COMBINED--> 河流(N005)"
    }
  ]
}
```

---

## 3. 节点颜色规则

| 类型 | 含义 | 颜色 |
|---|---|---|
| `RAIN_INLET` | 雨水口 | 黄色 |
| `RAIN_WELL` | 雨水井 | 浅黄色 |
| `SEWAGE_INLET` | 污水口 | 灰色 |
| `SEWAGE_WELL` | 污水井 | 浅灰色 |
| `COMBINED_WELL` | 合流井 | 紫色 |
| `WWTP` | 污水处理厂 | 绿色 |
| `RIVER` | 河流 | 蓝色 |
| `LAKE` | 湖泊 | 浅蓝色 |
| `RAIN_OUTLET` | 雨水排口 | 蓝色 |
| `LIFE_SEWAGE_INLET` | 生活污水口 | 棕色 |
| `NORMAL` | 普通节点 | 浅灰色 |

---

## 4. 错误链路展示规则

错误链路由后端根据 `CheckResult.path` 自动解析得到。

前端展示规则：

1. `nodes[].status = "error"`：节点显示红色边框；
2. `edges[].status = "error"`：边显示红色、加粗、虚线；
3. `errors[].nodePath`：用于“定位错误链路”时突出错误节点；
4. `errors[].edgePath`：用于“定位错误链路”时突出错误边。

---

## 5. 新增/修改的核心文件

```text
src/main/java/com/example/pipeflowcheck/controller/PipeCheckController.java
src/main/java/com/example/pipeflowcheck/service/VisualizationService.java
src/main/resources/static/index.html
docs/frontend_graph_visualization_plan.md
```

---

## 6. 验证结果

已完成验证：

1. 应用可启动；
2. 首页可访问；
3. `/api/pipe-flow/check-view-json` 可返回 `summary / results / graph`；
4. 示例 Excel 返回：12 个节点、12 条边、5 条错误路径；
5. `/api/pipe-flow/check-zip` 输出 ZIP 包含：

```text
pipe-flow-check-result.xlsx
pipe-network-graph.json
pipe-network-graph.html
pipe-network-graph.dot
pipe-network-full.dot
pipe-network-graph.svg
pipe-network-full.png
```

6. 前端脚本已通过 Node.js 语法检查。

---

## 7. 后续可选优化

1. 前端图布局可从 force 改为 dagre 分层布局；
2. 节点颜色可从后端配置读取，而不是前端写死；
3. 支持按 Task 单独展示子图；
4. 支持点击 Summary 中某个 Task 后，仅高亮该 Task 对应路径；
5. 支持导出当前前端图为 PNG。
