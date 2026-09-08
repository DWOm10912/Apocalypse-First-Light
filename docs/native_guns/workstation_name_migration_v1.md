# 工作站命名迁移 V1

| 历史名称 / ID | 正式名称 / ID | 兼容 |
| --- | --- | --- |
| 枪械工作台 / Gun Workbench；`gun_workbench` | 枪械维护台 / Gun Maintenance Bench；`gun_maintenance_bench` | `WorkstationLegacyMappings` 对 Block、Item 的 missing mapping 重映射；facing/part 不变，不注册旧别名 |
| 精密装配台 / Precision Assembly Station；`precision_assembly_station`（未注册资产） | 精密制造台 / Precision Fabrication Station；`precision_fabrication_station` | 源、贴图、Geo、静态模型、工具与文档同步改名，无旧世界注册需迁移 |

两台均宽 2 × 高 2 × 深 1。维护台尺寸以用户最新确认和现有模型为准，本轮不调整造型或占位。

旧世界 remap 路径已接入；实际旧存档加载迁移尚未单独实测，测试前建议保留备份。旧 `/give` ID 不再使用。
