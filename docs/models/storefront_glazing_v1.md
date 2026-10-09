# Storefront Glazing V1（店面玻璃幕墙）+ 黑框玻璃双开门

状态（2026-10-06）：**已实现资源和代码，未实机验证。**
- 生成器 `--check` 通过，网格没有共面重叠。
- 离线三维预览（把 A1 的 15 格 × 3 行店面拼起来看）过了一轮，修掉了一处问题（见"渲染"）。
- `compileJava --offline` **没有通过，失败不在本资产**：第一次是 Codex 同时在写的道路代码缺类（`RoadPlan` 引用 `RoadLotPlanner`，`RoadPlanningCommand` 缺 `RoadPlanJson`）；第二次 javac 跑完了全部文件的类型检查，只剩 1 个错误，在 `worldgen/roads/RoadPlanner.java`（缺 `ClaimSets`），本资产改动的文件没有报错。要等道路代码补全、整体编译通过后再算编译验证。
- **2026-10-06 用户实机（截图）**：拼接、端框、窗台、横梃切换、竖梃切换都正常；把竖梃都切成"不画"可以拼出 2 × 3 m 的整块大玻璃（保留这种用法，不限制）。不开光影时框是黑的；开光影时竖框偏白，已调 PBR（见"材质与渲染"），待复看。碰撞、生存挖掘掉落、黑框门的渲染还没看过。

## 为什么做

[AFL Fuel Stop A1](../worldgen/fuel_stop_a1_design_v1.md) 的外墙 P0 资产之一。现代北美便利店整面用店面玻璃，仓库里只有玻璃门，没有能连成一整面的店面玻璃方块。

## 用户 2026-10-06 定下的方向

- 静态资产：烘焙进区块网格，没有方块实体，没有动画。
- 黑色阳极氧化铝框；玻璃是透明中空玻璃，带一点青绿色，看得清店内，不做镜面。
- 分格 1.5 m：竖梃交替落在格边和格中。
- 玻璃门做一个黑框变体，旧的银框门保留，不覆盖。
- 转角件不在本轮做。
- A1 西侧卫生间高窗改成整格，对齐 3–4 m 高窗带（见 A1 设计稿）。

## 现实参考

- 来源：A1 第 1 轮已核验的规划图纸——QuikTrip G3SE（Chandler 2023、Casa Grande 2025）、Wawa Estero（2023）的立面，以及 ORNL 原型"前立面整面玻璃"。
- 北美常见的居中安装铝合金店面系统：框料约 2" × 4½"，中空玻璃，每格约 5 ft，在门顶或雨篷高度加横梃。
- 这是对行业通用做法的概括，不是某个具体产品的尺寸。

## 方块

| 项 | 内容 |
|---|---|
| ID | `apocalypse_firstlight:storefront_glazing`（店面玻璃幕墙 / Storefront Glazing），堆叠 64 |
| 代码 | `block/StorefrontGlazingBlock`；注册在 `AflBlocks` / `AflItems` |
| 创造栏 | 建筑方块，排在玻璃双开门（银框、黑框）后面 |
| 挖掘 | `minecraft:mineable/pickaxe` + `minecraft:needs_diamond_tool`（按 AGENTS 规定，铝制商业结构默认钻石级），`requiresCorrectToolForDrops()`；硬度 / 抗性 3.0 / 6.0；玻璃音效。掉落表掉自身，带 `survives_explosion`。没有实机检查生存模式挖掘掉落 |
| 重量 | `item_mass` 2.0 kg（估计，建材搬运单位，和顶棚套件同一档） |
| 噪音 | 2026-10-06 加进 `noise_glass_blocks`（打碎时感染者听到的是玻璃声），第一版漏了 |
| 光照 | `noOcclusion`；天光往下传；不挡视线、不让人窒息、不导红石；阴影亮度 1.0，相邻方块的面照常画 |
| 碰撞 / 选中 | 只有框料那一层：离外皮 3..5 px 的 2 px 厚薄片，四个朝向各一个形状 |

### 方块状态

| 属性 | 含义 |
|---|---|
| `facing` | 朝外的一面。放置时朝向玩家，也就是从外面对着墙放 |
| `left` / `right` / `up` / `down` | 那一侧是同朝向的店面玻璃（左右以从外面看为准，左 = 朝外方向的顺时针侧）。邻居变化时自动更新 |
| `mullion` | `edge`（左边缘有竖梃）、`center`（格中有竖梃）、`none` |
| `transom` | 和上面一格的玻璃之间画一根横梃 |

- **自动分格**：放下时，左边有同朝向的玻璃就取它的下一档（edge → center → none → edge），只有右边有就取上一档，都没有就是 `edge`。上下有玻璃时跟上下那格对齐，竖梃上下成一条线。所以从左往右一格一格放，就是 1.5 m 分格：一段的第一格是端框，第二格格中一根竖梃，第三格没有，第四格左边缘一根……
- **手动调整**：空手潜行右键。
  - 点在一格的上面四分之一，切换横梃；
  - 点在其它位置，竖梃换下一档。
  - 只在潜行、空手时生效，有准星提示"移动竖梃"或"切换横梃"（潜行键 + 使用键）。
- **框怎么画**（方块状态里的 multipart）：
  - 玻璃一直画；
  - 左边框：`left=false`（端框）或 `mullion=edge`；
  - 右边框：`right=false`；
  - 格中竖梃：`mullion=center`；
  - 上框：`up=false`（顶框）或 `transom=true`（横梃）；
  - 窗台：`down=false`，包括下框、铝披水和滴水边。
- **结构 / WorldEdit**：
  - 旋转只转 `facing`。左右是相对朝向的，`mullion` 和 `transom` 不用改。
  - 镜像会交换左右，但格边竖梃会换到另一边，节奏只是近似。V1 结构不镜像。
  - 写进 NBT 时直接存状态。

## 几何（px，一格，外面在 −Z）

| 部件 | 尺寸 |
|---|---|
| 框料 | 正面 1 px（真实 5 cm），进深 2 px，在外皮之后 3..5 px（z −5..−3）。端框和格边竖梃在格内贴边，格中竖梃在 ±0.5 |
| 上框 / 横梃 | y 15..15.98；窗台下框 y 0.02..1。横框前后各缩进 0.02 px，两头不封面（接下一格的横框，或藏进端框），避免和竖向框料共面 |
| 窗台披水 | 从外皮到框前（z −8..−5.01），高 0.35 px |
| 滴水边 | z −8.25..−8，y −0.45..0.2：只留墙面外那 0.25 px，顶面在披水板中间高度，接着披水板外端（2026-10-09 改，原来 z −8.25..−7.75、顶面 y 0） |
| 玻璃 | z −4.15..−3.85，只画两个大面；边缘总在框里，或接下一格的玻璃 |
| 三角面 | 玻璃 4；左框、右框、格中竖梃各 12；上框 8；窗台 32 |

## 材质与渲染

- 生成器 `tools/build-storefront-glazing-v1.mjs`（纯 Mesh，`tools/cube-slab-mesh-lib.mjs`）。`--check` 校验输出，`--preview DIR` 只写预览。
- 1024 图集，16 texel/px，带 `_s` / `_n`（LabPBR）。
- **框**：黑色阳极氧化 [36,38,41]，光滑度 76（粗糙度约 0.5，缎面），F0 14。是氧化膜，不用纯金属 F0——光影下整片金属板会刺眼。2026-10-06 第一版是光滑度 128（粗糙度 0.25）、F0 26，用户实机开光影时竖框把身后亮的天空和地面反射进来，看着是浅灰偏白（不开光影是黑的），所以调成哑光。调整后还没在光影下复看。
- **玻璃**：[176,204,204]，光滑度 240，F0 10，透明度 46。和冷柜、管道玻璃一样大于光影包半透明测试的 26，比它们（56）更透一点。
- **连续件**：框和玻璃都会连到下一格，所以是纯色，没有边缘高光，相邻方块之间看不出接缝。没有画上去的装饰。
- **模型**：平直光照（立面受天光均匀），blockstate 用 y 旋转转到四个朝向。玻璃模型是 `minecraft:translucent`，框是实心层。
  - 2026-10-06 离线预览发现：斜着看时，玻璃在每个格子交界有一条很淡的竖线。原因是贴图缩小时，玻璃色块边缘混进了框的颜色。
  - 修法：玻璃的所有 UV 都取色块中心，它本来就是一个颜色。
- Sundial 光影下的半透明层：以前出过问题的是方块实体的玻璃，静态玻璃应该不受影响，没有实机验证。
- 2026-10-08 用户实机：店面玻璃映天空正常，旁边黑框门的玻璃不一样。门玻璃已改成停稳时由区块的半透明层画，和店面玻璃走同一个光影程序，见 [玻璃双开门 V2](commercial_glass_double_door_v2.md) 开头的说明。

## 资产文件

| 项 | 路径 |
|---|---|
| 可编辑源 | `src/main/blockbench/storefront_glazing_v1.bbmodel`，贴图 `src/main/blockbench/textures/storefront_glazing_v1{,_s,_n}.png` |
| 运行时模型 | `models/block/storefront_glazing/{glass,frame_left,frame_right,mullion_center,rail_top,sill,item}.obj/.mtl/.json`（`forge:obj`） |
| 方块状态 | `blockstates/storefront_glazing.json`（multipart） |
| 物品模型 | `models/item/storefront_glazing.json`（一格带两边端框、顶框和窗台） |
| 贴图 | `textures/block/storefront_glazing{,_s,_n}.png` |

## 黑框玻璃双开门

- **ID**：`apocalypse_firstlight:commercial_glass_double_door_black`（玻璃双开门（黑框） / Commercial Glass Double Door (Black)），堆叠 1。
- **代码**：和银框门用同一个类 `CommercialGlassDoubleDoorBlock`，共用同一个方块实体类型（`AflBlockEntities.COMMERCIAL_GLASS_DOUBLE_DOOR` 现在两个方块都认）、同一套 GeckoLib 模型、动画和音效。
- **渲染**：`CommercialGlassDoubleDoorModel` 按方块选贴图。物品渲染器 `CommercialGlassDoubleDoorItemRenderer` 现在按物品对应的方块建一个方块实体来画。
- **掉落**：门的三处掉落（拆下格、支撑消失、战利品表）原来写死掉银框门。现在 `CommercialGlassDoubleDoorBlock` 掉自己的物品，所以黑框门掉黑框门，银框门不变。
- **贴图**：`textures/entity/commercial_glass_double_door_black.png`，由生成器从银框贴图重新上色得到，可编辑副本在 `src/main/blockbench/textures/commercial_glass_swing_door_black.png`。（2026-10-07 已过时：门重置为 V2，两种颜色都用新网格和自己的 PBR 贴图，见 [commercial_glass_double_door_v2.md](commercial_glass_double_door_v2.md)。这两张重新上色的贴图已删除，本生成器不再写门的贴图。）
  - 银框贴图是八条 16 px 的色带：六条不透明的铝和密封条色调，加上半透明的玻璃。
  - 不透明色带全部换成黑色阳极氧化，保留原来面与面之间的明暗关系。
  - 把手、合页和框共用这些色带，所以也是黑色（黑框店面常配哑光黑五金）。
  - 玻璃不变。
- **挖掘、重量、搬运**：和银框门一样——镐、铁级（门的明确例外，见 [commercial_glass_double_door_v1.md](../commercial_glass_double_door_v1.md)）、40 kg、`carry/oversized`。
- **其他资源**：方块状态、只含粒子的方块模型、物品模型（`builtin/entity`）、战利品表（只从左下格掉），贴图在 `assets/minecraft/atlases/blocks.json` 里加进方块图集（粒子用）。

## 已知问题 / 以后

- **2026-10-09 修：落地玻璃的窗台边闪。** 用户录屏：入口门两侧落到地面的那几列玻璃，窗台外沿和门口地面交界的那条线一截一截地闪（看起来像玻璃和门框侧板之间的问题）。
  - 原因：滴水边的顶面在 y 0，和下面地面方块的顶面在同一个平面、朝向也一样（都朝上），两个面抢着显示。墙上的玻璃（下面是勒脚、离地 1 m）不碰地面，所以只有入口那几列有。
  - 第一次只拿玻璃和侧板比面，没有拿玻璃和地面比，所以没找到；看录屏放大后才发现在窗台外沿。
  - 改法：滴水边只留墙面外那 0.25 px（z −8.25..−8），顶面抬到 y 0.2（披水板 0..0.35 的中间），接在披水板外端。生成器自己的共面检查和新工具 `tools/audit-boundary-faces.mjs` 都过了（工具对滴水边内侧那条 0.2 px 高的面还报 3 px²，那条面被披水板整个挡住，看不到）。
  - 只改了 `storefront_glazing/sill` 和物品模型，不用编译，F3+T 重载资源可见。没有进游戏看。

- 编译要等 Codex 的道路代码补全后再跑一次；全部未实机验证。
- 转角件（两段玻璃在外角相接的转角竖梃）不在本轮做。A1 的转角玻璃两头都碰到砖柱，用不上。
- 中弹碎玻璃、世界生成里的破损版本，以后可以加 `broken` 状态。
- 每段玻璃左右两端都是端框，一段的长度不必是 3 的倍数；右端最后一格不管在哪一档都会画端框。
