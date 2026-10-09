# Storage Rack V1（仓储货架）

状态（2026-10-04）：**已实现**，模型和 PBR 离线预览过，`compileJava --offline` PASS；**没有实机验证**（放置、相邻共用立柱、搜索、货物显示、Survival 挖掘掉落都还没在游戏里看过）。

## 是什么

仓库、后场用的深灰色免螺栓轻型钢货架（参考：SR5 2000×600×2000、Edsal 轻型货架）。一格宽、两格高、五层隔板，只有下面四层放货，最上面一层是顶板。用户 2026-10-04 定下：

- 相邻时自动共用中间的立柱；
- 一个货架 3×4（12 格），五层，只有四层能放东西；
- 钻石镐。

| 注册 ID | 名称 | 方块实体 | 容量 | 说明 |
|---|---|---|---|---|
| `apocalypse_firstlight:storage_rack` | 仓储货架 / Storage Rack | `StorageRackBlockEntity`（下半格） | 12 格，3×4 | 两格高，一格宽，堆叠 1 |

- 代码：`block/StorageRackBlock`、`blockentity/StorageRackBlockEntity`、`client/StorageRackRenderer`；注册在 `AflBlocks` / `AflItems` / `AflBlockEntities` / `AflBlockEntityRenderers`，创造标签页 `furniture`（收银背柜之后）。
- 挖掘：工业钢结构，`minecraft:mineable/pickaxe` + `minecraft:needs_diamond_tool`，`requiresCorrectToolForDrops()`；硬度 / 抗性 4.0 / 6.0，`SoundType.METAL`。掉落表 `loot_tables/blocks/storage_rack.json`：只有下半格掉物品（`half=lower`，`survives_explosion`）；拆上半格时，创造模式或工具不对就连下半格一起移除、不掉落（和收银背柜一样）。容器里的东西拆掉时撒出来。
- 重量：`item_mass` 20 kg（估计），`carry/oversized`。

## 方块状态

`facing`（正面）、`half`（`lower` / `upper`）、`left`、`right`。左边 = `facing.getCounterClockWise()`。某一边的邻居也是仓储货架、朝向相同、`half` 相同时，这一边为 `true`（`updateShape` 里随邻居更新）：

- `false`：画这一边自己的角钢立柱、侧梁和脚垫（`end_left` / `end_right`）；
- `true`：横梁和隔板伸到边界（`joint_left` / `joint_right`）；两格之间的共用立柱只由左边那格的 `joint_right` 画（以边界为中心的角钢加背板条），右边那格的 `joint_left` 不画立柱，所以不会重叠。

使用：从任一半的正面右键打开下半格的容器（`hint.apocalypse_firstlight.storage_rack.search` / `.view`）。碰撞箱：规范方向（朝北）z 5.8..15.8，下半格满高，上半格到 15.2 px。

## 搜索和货物

- 搜索：渐进式搜索（[progressive_container_search_v1.md](../gameplay/progressive_container_search_v1.md)），`AflContainerSearchLayout.GRID_3X4`，设置和收银背柜一样（每格 20 ticks，开放货架快速搜索）；玩家自己放的货架不用搜。世界战利品在第一次服务端 tick 时展开，货物从一开始就显示。
- 货物：共用商品库（[container_goods_v1.md](../gameplay/container_goods_v1.md)），12 处（下面四层 × 3），8 格满（`GOODS_FULL_AT`）。层面 y 1.35 / 8.55 / 15.75 / 22.95，x −4.4 / 0 / 4.4，正面 z −1.4，缩放 1.05 × 1.15 × 1.05（名义格 4 × 4.6 × 8 px）。主题 → 商品：
  - `generic`：纸盒、零件盒、罐头、瓶子；
  - `grocery`：纸盒、饮料盒、罐头、瓶子、零食袋；
  - `pharmacy`：纸盒、药盒、药瓶；
  - `hardware` / `industrial`：零件盒、油漆桶、喷漆罐、纸盒。

## 资产

| 项 | 内容 |
|---|---|
| 生成器 | `tools/build-storage-rack-v1.mjs`（纯 Mesh，`tools/cube-slab-mesh-lib.mjs`；`--check` 校验输出，`--preview DIR` 只写预览） |
| 尺寸（`RACK`，px，下半格坐标，y 跨两格） | 前 −2.2，后 7.8，顶 31.2；层面 y 1.4 / 8.6 / 15.8 / 23.0 / 30.2；角钢 1.3 × 0.22，横梁高 1.1，隔板 0.2，脚垫 0.35；端部内侧 ±6.7，连接处 ±7.35 |
| 材质 | 框架深灰粉末涂层钢 [72,76,81]（f0 24），隔板稍浅 [98,102,107]，黑色塑料脚垫 [40,41,44]；没有画上去的装饰 |
| 可编辑源 | `src/main/blockbench/storage_rack_v1.bbmodel`，贴图 `src/main/blockbench/textures/storage_rack_v1{,_s,_n}.png` |
| 运行时模型 | `blockstates/storage_rack.json`（multipart：`core_<half>` 加两边的 `end_*` / `joint_*`），`models/block/storage_rack/<piece>.obj/.mtl/.json`（上半格的件整体下移 16 px），`models/item/storage_rack.json`（`core` + 两端）；`forge:obj` |
| 贴图 | `textures/block/storage_rack{,_s,_n}.png`（LabPBR，13.75 texel/px，796 个 UV 岛） |
| 统计 | 单个货架 1108 个三角形，共面 0 |
| 物品栏 | rotation `[30,225,0]`，translation `[0.812,-3.104,0]`，scale 0.41（2026-10-08 从 0.5 改小：图标高 18.1 px，超出 16 px 的格子；现在 14.9 px） |

## 测试

- `/dev container_search spawn apocalypse_firstlight:storage_rack fill 8`（没有实机运行过）。
- 作者工具：`AuthoringFixtureRegistry` 的 `storage_rack`（STORAGE_WITH_INVENTORY，两格高，`left` / `right` 由邻居计算）。2026-10-08 起货架也算 `shapeSafe`：放新货架时旁边那个货架的 `left` / `right` 跟着更新，`reconcile_shapes` 能修已有的一排。

## 连接问题（2026-10-08）

用户问"货架的链接逻辑是不是错了"。方块本身的逻辑没错（玩家手放时，邻居收到方块更新，两边都会重算）。错的是建造工具摆的那一排：
- 存档读出来，Fuel Stop A1 仓库那排（`x −85..−89, z 462`，朝南）每个都是 `right=false`，`−86..−89` 是 `left=true`。所以每个接缝处，左边那个画了自己往里缩的端立柱（`end_right`），右边那个按"已连接"不画立柱、隔板伸到边界（`joint_left`），居中的共用立柱没有画。看起来就是接缝偏到一边，隔板中间断开一道缝。
- 原因：建造工具一个一个往西摆，新货架会算自己的两边，但写方块时不发邻居更新，前一个货架的右边就一直没更新。
- 冷柜后面那排（`x −78..−83, z 465`）也是这样摆的，存档里却是对的：后来旁边有一次普通的方块更新，顺着整排重算了一遍。
- 修复见 [作者工具文档](../dev/minecraft_authoring_mcp_v1.md)"Storage rack rows"。仓库那排后来由用户在游戏里自己修好了（没有用 `reconcile_shapes`），2026-10-08 用户 PASS。

## 已知问题 / 以后

- 没有实机验证。
- 一组货架只在同朝向、同高度、正左右相邻时共用立柱；背靠背或转角不合并。
