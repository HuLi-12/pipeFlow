# PipeFlowCheck - 城市排水管网流向合规性检测系统

## 1. 项目定位
PipeFlowCheck 是一个基于有向图遍历的排水管网流向合规性检测系统。系统通过读取标准 Excel 模板管网图数据，将节点和通道关系构建为有向图，从 Tasks 表指定入口出发遍历所有下游路径，并按固定规则判断路径是否合法。

## 2. 核心原则
- 所有下游路径都必须合法。
- 任意一条路径出现断头、环路、非法通道、非法终点、终点有下游，则该入口判定为错误。
- Excel 输入采用标准模板，必须包含 Nodes、Edges、Tasks 三张表。
- Tasks 表用于明确指定检测入口和入口类型，系统不自动推断入口。

## 3. 核心规则
### 3.1 雨水入口规则
- 允许通道：RAIN、SEWAGE、COMBINED
- 终点判断：
  - 如果只经过 RAIN：合法终点 RIVER、LAKE、RAIN_OUTLET
  - 一旦经过 SEWAGE 或 COMBINED：合法终点必须是 WWTP（污水处理厂）
### 3.2 污水入口规则
- 允许通道：SEWAGE、COMBINED
- 合法终点：WWTP
### 3.3 起点节点类型校验
- RAIN 入口允许起点节点类型：RAIN_INLET、RAIN_WELL、COMBINED_WELL
- SEWAGE 入口允许起点节点类型：SEWAGE_INLET、SEWAGE_WELL、COMBINED_WELL

## 4. Excel 模板格式
### 4.1 Nodes 表
| node_id | node_name | node_type | remark |
|---------|-----------|-----------|--------|
| N001    | 雨水口1   | RAIN_INLET | 北门雨水口 |

### 4.2 Edges 表
| from_node_id | to_node_id | channel_type | remark |
|--------------|------------|--------------|--------|
| N001         | N002       | RAIN         | 雨水通道 |

### 4.3 Tasks 表（必须）
| task_id | start_node_id | start_type | remark |
|---------|---------------|------------|--------|
| T001    | N001          | RAIN       | 检查雨水口1 |

## 5. 输出
- **结果 Excel**：包含路径明细 Sheet（task_id、path、channel_path、readable_path、error_code、error_reason、risk_level）和任务汇总 Sheet。
- **可视化 DOT 文件**：Graphviz 格式，节点按类型着色，错误路径节点和边标红。
- **可视化 SVG 文件**：由 graphviz-java 引擎渲染，可直接预览管网拓扑图。
- **ZIP 打包**：结果 Excel + DOT 源文件 + SVG 图片，一次下载。

## 6. 安全保护
- 单条路径最大深度限制（500 层），防止 StackOverflow
- 单任务最大路径数限制（10000 条），防止无限扩展
- 环路检测
- 起点类型与节点类型不匹配校验

## 7. API 接口
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/pipe-flow/check | 上传 Excel，返回结果 Excel 下载 |
| POST | /api/pipe-flow/check-zip | 上传 Excel，返回 ZIP（Excel + DOT + SVG） |
| POST | /api/pipe-flow/check-json | 上传 Excel，返回 JSON 检测结果 |
| GET  | /api/pipe-flow/template | 下载标准 Excel 模板 |

## 8. 推荐技术栈
Java 17 + Spring Boot 3.x + Apache POI + graphviz-java + DFS 全路径遍历。

## 9. 包内容
- docs/：完整开发方案与优化方案说明
- diagrams/：模块流程图、数据流图、Mermaid 源文件
- config/：规则配置样例
- src/：Java 核心代码
- examples/：Excel 模板说明与测试用例说明
