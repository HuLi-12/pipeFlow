# PipeCheckService 单元测试建议

## 1. 正常路径
- RAIN -> RAIN -> RIVER，期望通道正常
- RAIN -> COMBINED -> WWTP，期望通道正常
- SEWAGE -> SEWAGE -> WWTP，期望通道正常

## 2. 错误路径
- SEWAGE -> RAIN -> RIVER，期望 CHANNEL_NOT_ALLOWED
- RAIN -> COMBINED -> RIVER，期望 INVALID_END
- RAIN -> RAIN_WELL，无下游，期望 INVALID_END 或 DEAD_END
- A -> B -> A，期望 CYCLE_FOUND

## 3. 多分支路径
- 一个入口同时流向两个下游，一个合法一个非法，入口总结果应判定为错误。
