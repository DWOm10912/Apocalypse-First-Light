# 白杨木整套删除记录（2026-10-07）

状态：**已删除**。
- `compileJava --offline` 通过。
- 没有进游戏验证：旧存档读档、创造模式页面都没看过。

## 删掉了什么

2026-08-18 一次加的 11 个方块和物品：
- `poplar_log`、`stripped_poplar_log`
- `poplar_wood`、`stripped_poplar_wood`
- `poplar_planks`、`poplar_stairs`、`poplar_slab`
- `poplar_door`、`poplar_trapdoor`
- `poplar_leaves`、`poplar_sapling`

当时没有写任何文档说明为什么加。

## 为什么删（用户 2026-10-07 同意）

- **世界里不会自然生成**：只有树的 configured feature `poplar_tree`，没有放进任何生物群系。树苗只能从创造模式拿。
- **生存模式做不出来**：
  - 没有合成配方，原木合不出木板；
  - 木板不在 `minecraft:planks` 里，合不出木棍和工作台。
- **没有建筑用到**：所有结构文件里都没有白杨木方块。
- **真正用到的只有三处**：
  - A1 便利店 5 扇室内门（临时占位）；
  - 建造工具把 `poplar_door` 登记成可以安全放置的家具；
  - 一个 GameTest 用它举例。
- 和 AFL 的现代末日、哥伦比亚联邦的定位没有关系。原版橡木、白桦、云杉已经够用。
- 以后地形如果想要岛上的特色树，按需要重新设计一种，不沿用这套。

## 改动

| 位置 | 改动 |
|---|---|
| `registry/AflBlocks.java`、`AflItems.java` | 删掉 11 个方块和 11 个物品的注册，以及没用了的 import |
| `registry/AflCreativeTabs.java` | 自然页去掉 11 个物品 |
| `registry/AflBlockSetTypes.java` | 删掉 `AFL_POPLAR` |
| `ApocalypseFirstLight.java` | 删掉只给白杨木设可燃的那段 `setFlammable` |
| `client/AflBlockRenderTypes.java` | 删掉 4 条渲染层设置 |
| `world/PoplarTreeGrower.java`、`block/StrippableRotatedPillarBlock.java` | 删除。剥皮原木这个类只有白杨木用 |
| 资源 | 删掉方块状态 11 个、方块模型 24 个、物品模型 11 个、贴图 11 张、掉落表 11 个、`worldgen/configured_feature/poplar_tree.json`；删掉中英文名各 11 条，`item_mass/afl_content_v1.json` 里 11 条质量 |
| `data/minecraft/tags/` | 删掉 10 个只装白杨木的标签文件：方块的 `leaves`、`logs`、`logs_that_burn`、`mineable/axe`（同日为商业木门重新建了这个文件）、`mineable/hoe`、`saplings`，物品的 `leaves`、`logs`、`logs_that_burn`、`saplings` |
| 建造工具（`src/dev/.../bridge/`） | `AuthoringFixtureRegistry` 去掉 `poplar_door`；`FixtureBridgeGameTests` 里"多格要用 place_multiblock"的例子换成 `steel_door`；同日钢门有了方块实体，又换成 `office_multifunction_printer`（GameTest 没有运行） |
| `tools/afl_minecraft_mcp/fuel_stop_a1_store.mjs` | A1 的 5 扇室内门先改成 `steel_door` 占位；同日改成商业木门和钢门（`INTERIOR_DOORS`，见 [Steel-frame Doors V1](../models/steel_frame_doors_v1.md)） |

## 对旧存档的影响

- 存档里已有的白杨木方块和物品，读档后会消失。Forge 读档时可能提示有缺失的注册项。这条没有实测。
- 开发存档「新的世界 (1)」里，A1 便利店的 5 扇室内门会变成空门洞。钢框门同日做好了，用 A1 建造脚本的 `doors` 模式补上。见 [A1 施工记录](fuel_stop_a1_store_build_v1.md)。

## 检查

- `node tools/check-item-mass.mjs`：没有白杨木相关的错误，比如引用了不存在的物品。它仍然报 4 个路面方块缺显式质量，是以前就有的问题，和这次删除无关：`road_asphalt_surface`、`road_sidewalk_surface`、`road_utility_surface`、`road_curb`。
- 除历史审查记录外，仓库里已经没有白杨木的引用。
