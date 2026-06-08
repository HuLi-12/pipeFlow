# Excel 模板建议

当前不强制固定 Excel 格式，但建议至少包含以下三张表。

示例输入：`examples/pipeflowcheck_sample_12_nodes.xlsx`，包含 12 个节点、12 条通道、6 个检测任务，覆盖正常路径、非法通道、非法终点、环路和起点缺失。

示例输出：`examples/pipeflowcheck_sample_12_nodes_result.xlsx`，由当前 Java 服务读取上述输入后生成，包含路径明细 `Result` 和任务汇总 `Summary` 两张表。

## Sheet: Nodes
| node_id | node_name | node_type | remark |
|---|---|---|---|
| N001 | 雨水口A | RAIN_INLET | 小区北门 |
| N002 | 雨水井1 | RAIN_WELL | 中转 |
| N003 | 河流1 | RIVER | 合法雨水终点 |
| N004 | 污水口B | SEWAGE_INLET | 小区污水入口 |
| N005 | 污水处理厂1 | WWTP | 合法污水终点 |

## Sheet: Edges
| from_node_id | to_node_id | channel_type | remark |
|---|---|---|---|
| N001 | N002 | RAIN | 雨水通道 |
| N002 | N003 | RAIN | 最终入河 |
| N004 | N005 | SEWAGE | 污水进厂 |

## Sheet: Tasks
| task_id | start_node_id | start_type | remark |
|---|---|---|---|
| T001 | N001 | RAIN | 检查雨水口A |
| T002 | N004 | SEWAGE | 检查污水口B |

## Result 输出字段
| task_id | start_node_id | start_node_name | status | end_node_id | end_node_name | path | error_reason | risk_level |
|---|---|---|---|---|---|---|---|---|

## Summary 汇总字段
| task_id | start_node_id | start_node_name | start_type | status | path_count | error_code | error_reason | risk_level |
|---|---|---|---|---|---|---|---|---|
