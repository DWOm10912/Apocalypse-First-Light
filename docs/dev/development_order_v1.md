# 开发顺序 V1：接手的工作和先后（2026-10-08）

2026-10-08 起 AFL 由 Claude 一个人开发，Codex 去做 ANANKE。用户当天定了顺序：**先把加油站做完；高速只记在文档里，以后再开，因为地形和群系还没做。**

## 现在做：Fuel Stop A1（加油站）

在做的两条线：

- **Building Power**：
  - 已做：配电盘、电表箱，墙上插座和插线板；冷柜、冰柜、售货机、饮水机改插头取电；吸顶灯重做（方形面板灯、线形灯）和应急灯（2026-10-08，[Building Lights V1](../models/building_lights_v1.md)，未实机验证）。
  - 待定：地堡开局有没有电（用户说以后再定，现在新世界的地堡是黑的）。
  - A1 实测：建造脚本 `power` 模式补配电盘和电表箱，后场 5 盏换成线形灯、装 2 盏应急灯；摆插座，接能量单元。
  - 见 [building_power_v1.md](../models/building_power_v1.md)、[power_outlets_v1.md](../models/power_outlets_v1.md)。
- **A1 便利店做成正式 NBT 前要换的资产**：
  - 马桶、挂墙洗手池、卫生间隔间门和隔断（吸顶灯 2026-10-08 已重做）；
  - 吊顶板 + 嵌入式 2×4 灯盘（吸顶灯概念图 B，和室内装修一起做）；
  - 室内装修，咖啡吧、热食柜；
  - 招牌字、屋顶空调机组等。
  - 换好以后：`/afl_author configure`、导出、写 StructureDefinition 元数据。
  - 见 [A1 施工记录](../worldgen/fuel_stop_a1_store_build_v1.md) 的"已知不足"。
- 加油区（加油岛、顶棚、地下油罐那一片）用户没说开始就不动。

## 以后再开（从 Codex 接过来的）

| 工作 | 现状 | 依赖 / 什么时候开 |
|---|---|---|
| **Terrain V2 + 群系**（哥伦比亚联邦地形） | Phase 0 已实现，等用户验证；Phase 1–8 只有计划。见 [迁移计划](../worldgen/terrain_v2_migration_plan.md)、[研究报告](../worldgen/columbian_federation_terrain_v2_research.md) | 加油站之后，高速和道路之前。群系的方案也要在这里定 |
| **高速**（Highway V2 + 外观方案） | 工程层：V2-0 契约、M1-A 测试场、M1-B 路线预览，都只在开发环境，没有接自然生成。外观：[Interstate Design V1](../worldgen/interstate_design_v1.md) 的方向 2026-10-08 已批准，七项决定已记下（km、英文标志、自有盾牌、R4 默认 / S6 郊区 / U6F 大城市、路基和边坡重新设计） | 地形和群系做完以后。第一项是 R4 实体样板：研究、尺寸、预览，通过后才改正式代码 |
| **北美城市道路 V1-B** | 有代码，用户实机 FAIL，没有自然入口 | 和高速一起，接城市路网时重做 |
| 其他 worldgen（乡村已退役、城市建筑模板等） | 见 `docs/worldgen/` 各文档 | 跟着地形、道路的进度 |

## 规则

- 以后再开的工作，开工前先读它的文档，Codex 写的保留站得住的部分，不推倒重来。
- 顺序变了就更新这份文档。
