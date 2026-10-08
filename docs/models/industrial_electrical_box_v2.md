# Industrial Electrical Box（配电箱）删除记录（2026-10-07）

状态：**已删除**。
- `compileJava --offline` 通过；`node tools/check-item-mass.mjs` 只报以前就有的 4 个路面方块缺质量。
- 没有进游戏验证：旧存档读档、地堡生成、配电盘开关门音效都没看过。

## 它原来是什么

- `apocalypse_firstlight:industrial_electrical_box`，2026-08-17 加入，2026-09-30 做成 V2。
- 挂墙的小铁箱，先拧开锁再开门，里面 9 格，按 3×3 逐格搜刮。
- **不属于电力系统**，只是一个容器。
- 只有出生地堡 `bunker.nbt` 里放了一个，而且没写战利品表，打开是空的。
- A1 便利店建造时临时放了两个，当配电盘和进户点的占位。
- V2 的完整说明（模型、PBR、形状、交互）在删除前的提交里，例如 `e437fb9b` 的这个文件。

## 为什么删（用户 2026-10-07 同意）

- Building Power V1 做了真正的配电盘 `distribution_panel` 和电表箱 `service_meter_box`（见 [building_power_v1.md](building_power_v1.md)）。两个名字相近、样子相近的箱子，一个有电一个没电，容易混。
- 作为容器它用处很小：只出现在地堡里一次，没有战利品。
- 路灯控制箱属于以后的电网层，那时按需要另做，不沿用它。

## 改动

| 位置 | 改动 |
|---|---|
| Java | 删除 `block/IndustrialElectricalBoxBlock`、`blockentity/IndustrialElectricalBoxBlockEntity`；`AflBlocks`、`AflItems`、`AflBlockEntities`、`AflBlockEntityRenderers`、`AflCreativeTabs`（家具页）、`AflStaticMeshItemClient` 去掉登记；`world/IndustrialMaterialExplosionDrops` 去掉"炸掉配电箱掉 0–3 个钢废料"的两段 |
| 建造工具 | `AuthoringFixtureRegistry` 去掉它的登记 |
| 资源 | 删除 blockstate、方块模型、物品模型、geo、Mesh、profile、三张贴图、掉落表、`mesh_shapes/industrial_electrical_box.json`；Blockbench 源 `src/main/blockbench/industrial_electrical_box.bbmodel` 和三张贴图副本；生成器 `tools/build-industrial-electrical-box-v2.mjs` |
| 数据 | `mineable/pickaxe`、`needs_diamond_tool`、`carry/bulky`、`item_mass/afl_content_v1.json`（12 kg）里去掉它 |
| 文字 | 中英文的方块名、6 条准星提示删除；3 条字幕改成配电盘的 |
| 地堡 `bunker.nbt` | 原来的箱子在 (1, 3, 16)，朝东，挂西墙，离地 2 m 多。换成配电盘 `distribution_panel[facing=east,open=false]`，降一格到 (1, 2, 16)，面板离地约 1.15–1.85 m，伸手够得着。模板里没写方块实体数据，生成时配电盘用默认状态：总闸断开、没有电 |
| A1 建造脚本 `tools/afl_minecraft_mcp/fuel_stop_a1_store.mjs` | 设备间那个改成配电盘，后墙外那个改成电表箱；加了 `power` 模式（`powerSteps()`），只补放这两个 |
| `tools/afl_minecraft_mcp/convenience_store_v2_candidate.mjs` | 店里西墙那个改成配电盘，北墙外那个改成电表箱 |

**保留下来的**：
- **音效**：开门、关门、门锁三段音效挪到 `sounds/distribution_panel/{open,close,latch}.ogg`，事件改名为 `distribution_panel_open` / `_close` / `_latch`。
  - 配电盘的门也是 10 tick，开门的轻碰、关门的撞框都落在动画最后一帧，可以直接用。
  - 配电盘开门时先响门锁，再响开门；关门只有关门声。音量 0.8，音高 0.98–1.02；门锁音量 0.6，音高 1.04–1.06。
  - 生成器改名为 `tools/build-distribution-panel-sounds-v1.mjs`，源素材和参数不变。
  - 其他 7 个音效生成器拿这段开门声做响度基准，路径一起改成 `distribution_panel/open.ogg`：冷柜门、冷柜压缩机、收银机、充电站、燃油火、油枪喷洒、抽水泵。
- **3×3 搜索界面**（`AflContainerSearchLayout.GRID_3X3`）和 Mesh Shape Runtime：当初为配电箱做的，现在收银机、收银柜台、垃圾桶、货架、售货机都在用，不动。

## 对旧存档的影响

- 存档里已有的配电箱方块和物品，读档后会消失。Forge 可能提示有缺失的注册项。没有做旧方块映射，和删白杨木的做法一样。这一条没有实测。
- 已经生成的地堡里那个配电箱会变成空的，墙上留空。新生成的地堡才有配电盘。
- 开发存档里 A1 便利店的两个配电箱会消失：进游戏后运行 A1 建造脚本的 `power` 模式，补上配电盘和电表箱。
