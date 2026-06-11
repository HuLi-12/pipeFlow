# PipeFlowCheck 方案封装说明

## 1. 项目名称
PipeFlowCheck - 城市排水管网流向合规性检测系统

## 2. 核心输入输出
- 输入：Excel 管网图数据，包括节点、通道、检测入口。
- 输出：结果 Excel，包含路径、终点、状态、错误原因、风险等级。

## 3. 核心算法
采用 DFS 全路径遍历。有向图中的一个入口可能存在多条下游路径，系统必须遍历全部路径。只有所有下游路径都合法时，入口才判定为通道正常。

## 4. 当前支持规则
- 雨水：可走雨水、污水、合流通道；若进入污水/合流通道，终点必须是污水处理厂；否则雨水通道可进入河流、湖泊、雨水排口。
- 污水：只能走污水、合流通道，终点必须是污水处理厂。

## 5. 扩展规则
可以通过 rules.yml 增加生活污水、工业废水、初期雨水等类型。新增类型只需要扩展入口类型、允许通道、合法终点和特殊状态触发通道。

## 6. 主要模块
- ExcelReadService：解析 Excel
- DataValidateService：校验数据格式
- GraphBuildService：构建有向图
- RuleConfigService：加载规则配置
- RuleEngine：执行规则判断
- PipeCheckService：DFS 全路径检测
- ExcelWriteService：输出检测报告

## 7. 推荐接口
POST /api/pipe-flow/check

请求：上传 Excel 文件。
响应：下载结果 Excel 文件。

## 8. 交付内容
本包包含完整开发方案文档、流程图、数据流图、规则配置样例、Excel 模板、Java 代码骨架和测试用例说明。
