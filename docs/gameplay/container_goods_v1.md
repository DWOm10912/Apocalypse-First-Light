# Container Goods V1（容器货物显示）

状态（2026-10-01）：**已实现，没有实机验证**。`compileJava --offline` PASS（main 源集包含 `src/dev`）。已接入：冷冻冰柜（[chest_freezer_v2.md](../models/chest_freezer_v2.md)）、工业储物柜（[industrial_locker_v2.md](../models/industrial_locker_v2.md)）。

## 是什么

可搜索的容器（[progressive_container_search_v1.md](progressive_container_search_v1.md)）里面画一些通用的货物模型，让柜子看起来有东西。这些货物不是真实物品，看不出具体装的是什么：纸箱、袋子、衣服、安全帽之类。用户 2026-10-01 定下的规则：
- **有货才显示**，空了就不显示；
- **每个容器随机**：先填哪几处、每处摆什么，按容器的位置随机，同一个容器在所有客户端、重进存档后都一样；
- **东西多就摆得多**：显示几处跟着容器里有几格东西走，没搜过的格子也算。所以能看出满不满，看不出装的是什么。这和搜索系统"不泄露内容"的原则有出入，是用户要求的；
- **货物跟着地方走**（储物柜）：学校和工厂的柜子摆的东西不同，由战利品表决定主题，见下文。

## 共用规则

- 显示几处 = 有东西的格数 × 2 / 3，向上取整，不超过这个容器的位置数（`AflContainerGoods.shown`）。1 格 → 1，3 格 → 2，6 格 → 4，9 格 → 6，12 格 → 8。
- 拿走东西时，最后填上的那处先消失。
- 客户端只收到"显示几处"和主题名，物品本身不同步。
- 战利品要先生成才知道有几格，所以这些容器会提前生成战利品（只是生成，格子仍要搜才能看到）：冷冻冰柜在第一次服务端 tick，储物柜在第一次开门时。
- 货物模型的骨骼都标 `neverRender`，物品图标里不出现；在世界里由方块实体的 `meshPartVisible` 决定显示哪些。

## 主题（goods themes）

- 数据：`data/<命名空间>/goods_themes/<主题>.json`，内容 `{"loot_tables": [...]}`，写完整的战利品表 ID；以 `/` 结尾的表示这个文件夹下的所有表。匹配最长的那条生效。主题名就是文件名（不看命名空间）。
- 没有匹配、或者没有战利品表（玩家放置的）：`generic`（通用）。
- 容器生成战利品时记下主题，存进存档，之后不再变；重载数据不会改已经记下的主题。
- 代码：`containersearch/AflGoodsThemes`（服务端数据，`/reload` 时重新读取）。
- 现在的两个主题文件（项目里还没有这些战利品表，等建筑战利品表做出来时按这个文件夹命名就会生效）：

| 主题 | 匹配的战利品表 |
|---|---|
| `industrial` | `apocalypse_firstlight:chests/industrial/`、`apocalypse_firstlight:chests/factory/`、`apocalypse_firstlight:chests/warehouse/` 下的所有表 |
| `school` | `apocalypse_firstlight:chests/school/` 下的所有表 |

- 每种容器自己决定每个主题下每种位置能摆什么；不认识的主题按 `generic` 处理。冷冻冰柜目前不分主题。

## 道具模型

共用模块 `tools/afl-goods-props.mjs`：用盒子、圆柱、旋转体在局部坐标里拼出道具，再旋转、摆到位置上。不印字、不画图案；纸箱的黄胶带、反光背心的反光条是凸起的几何条，不是画上去的。材质 `PROP_MATS`（纯色，最亮约 190，避免光影下发白）。

| 道具 | 构成 |
|---|---|
| 纸箱 `carton` | 牛皮纸色纸箱，黄胶带沿顶部接缝并包过两端；可叠 2 层，越往上越小、略微转角 |
| 帆布旅行袋 `holdall` | 大倒角的软袋，两条深色带子绕一圈 |
| 帆布包 `tote`（挂钩） | 挂在挂钩上，两根带子越过挂钩 |
| 工具箱 `toolbox` | 红色箱体、深一点的盖子、两根立柱上的黑色提手 |
| 劳保靴 `boots` | 一双：橡胶底、圆角鞋面、靴筒 |
| 安全帽 `hard_hat` | 旋转体帽壳加帽檐（黄 / 白 / 橙）；挂在挂钩上时帽顶朝外 |
| 保温杯 + 饭盒 `thermos` | 带深色盖的圆柱保温杯，旁边一个带提手的饭盒 |
| 工作夹克 `jacket` | 衣架挂在挂衣杆上：V 领、袖子垂在两侧 |
| 反光背心 `vest` | 衣架挂着：橙色，前面两条反光条 |
| 劳保手套 `gloves` | 一双，袖口挂在挂钩上 |
| 书包 `backpack` | 圆角包身、前袋（另一种颜色）、提手；挂在挂钩上时提环越过挂钩 |
| 课本 `books` | 3–4 本平放叠起，封面加书脊，书页块略缩进 |
| 运动鞋 `sneakers` | 一双：白色鞋底、彩色鞋面、后跟 |
| 运动包 `sports_bag` | 躺着的圆筒包，两端深色，顶上一根提带 |
| 篮球 `basketball` | 旋转体球加两圈凸起的接缝 |

冷冻冰柜的冷冻货（纸盒、软袋、冰淇淋桶）写在它自己的生成器里（`tools/build-chest-freezer-v2.mjs` 的 `GOODS`）。

## 测试

开发指令（开发环境，OP 2 级，看着一个方块执行）：

```
/dev container_search spawn <方块> [theme <主题>] [loot <战利品表> | fill <格数>]
```

- `theme <主题>`：直接指定货物主题，自动补全已加载的主题（`generic`、`industrial`、`school`）。不写的话按战利品表决定；用 `fill` 时没有真正的战利品表，所以是 `generic`。只对有主题的容器有效（目前是储物柜），对冰柜会提示"忽略"。
- `fill <格数>`：随机放这么多格测试食物，用来看"东西越多摆得越满"。
- 例：`/dev container_search spawn apocalypse_firstlight:industrial_locker theme school fill 12` 刷一个学校主题、装 12 格、需要搜索的储物柜（12 格 → 全部 7 处都摆）。
- 详见搜索文档第 21 节。

## 代码

| 文件 | 内容 |
|---|---|
| `containersearch/AflContainerGoods.java` | 显示几处的规则、数格子、`Themed` 接口（开发指令直接设主题用） |
| `containersearch/AflGoodsThemes.java` | 主题数据的读取和匹配 |
| `data/apocalypse_firstlight/goods_themes/*.json` | `industrial`、`school` |
| `tools/afl-goods-props.mjs` | 道具模型 |
| `blockentity/IndustrialLockerBlockEntity.java`、`blockentity/ChestFreezerBlockEntity.java` | 各自的位置、主题表、显示 |
