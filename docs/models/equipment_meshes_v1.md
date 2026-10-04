# AFL Equipment Meshes V1（装备网格）

状态：**已实现（2026-10-03），生成脚本 `--check` 通过，编译通过，没有实机验证**。

装备类物品（随身带的、戴在身上的）用和 Material Meshes（`material_meshes_v1.md`）同一套 bake：
- 物品栏里是一张 16×16 的 2D 像素图标，由网格按图标视角画出来；
- 手持、掉落、展示框，以及戴在身上（各自的 Curios 渲染器）时，显示 AFL 网格；
- 每件一张 256 的 LabPBR 贴图集（Base Color / `_s` / `_n`），一个骨骼。

用户的决定（2026-10-03）：装备的网格由 Claude 制作，要做 PBR；做一个装备专用的网格生成脚本，以后的装备（背包、生存监测仪等）都往里加。

## 生成脚本

`tools/build-equipment-meshes-v1.mjs`

```text
node tools/build-equipment-meshes-v1.mjs [id ...]               写出源文件、贴图集、geo、AFL 网格、图标、物品模型
node tools/build-equipment-meshes-v1.mjs [id ...] --check       检查所有输出是否最新
node tools/build-equipment-meshes-v1.mjs [id ...] --preview DIR 只把输出写到 DIR，用来离线看
```

- `ITEMS` 里每件装备一个函数，返回 `{PARTS, MATS, icon, anchor?}`，交给 `build-material-meshes-v1.mjs` 的 `bake()`。加新装备就是加一项。
- 输出位置和材料网格相同：`src/main/blockbench/<id>.bbmodel`（可编辑源）、`geo/<id>.geo.json`、`meshes/<id>.aflmesh.json`、`models/item/<id>.json`（`forge:separate_transforms`）、`textures/item/<id>.png`（图标）、`textures/item/<id>_mesh{,_s,_n}.png`（也复制到 `src/main/blockbench/textures/`）。
- 打印的统计里有 `verticalOffset`（填到 `AflStaticMeshItemClient` 的 `bind`）；戴在身上的装备还有 `wornAnchor`，即身体锚点（比如手臂轴线）在烘焙后网格里的位置（单位：像素），填到对应的 Curios 渲染器里。
- 尺寸按图标大小建模（单位：像素），和材料网格一样。

## `bake()` 新增的能力（`build-material-meshes-v1.mjs`）

这些都是可选项，不用时输出和以前逐字节相同（材料网格、电池的 `--check` 都通过）。
- **玻璃**：材质写 `alpha`（0–255），它的 UV 岛在 Base Color 里就用这个透明度；全部由这种材质组成的部件进入网格的半透明层（`render_layer: translucent`，游戏里由 `AflHybridMeshRendering` 画）。玻璃按 AFL 规则：F0 10、光滑度 235，透明度至少 26（光影的 alpha 测试），一般用 70–110 才能在 Sundial 下看得见。
- **图标 `xray`**：列出的材质（玻璃）只在后面没有别的部件时才画，所以图标里能看到玻璃里面的东西。
- **`display`**：按场合覆盖物品的显示变换（细长的工具按原版手持物品的方式拿）。
- **图标 `seamless`**：列出的部件周围不画深色接缝（1 像素的指针会留下一个深色十字）。

## 物品

### 体温计 `clinical_thermometer`

- 一根 8 棱的玻璃管（直径约 1.3 像素，长 12.35 像素），里面有白色搪瓷衬条和红色液柱；一端是装满红色液体的感温泡，另一端玻璃收圆。沿 X 轴放，感温泡在 -X。
- 材质：玻璃（alpha 110）；红色液体 `[168,52,44]`，介电质；白色搪瓷 `[206,203,194]`。
- 图标：视角 `[30, 45, 0]`，感温泡在左下，红色液柱透过玻璃看得到。
- 手持显示：`verticalOffset` 0.46189。拿在手上的变换不用材料网格的“拿方块”尺寸规则，而是照原版木棍、剑（handheld）的变换，Z 轴多转 -135°：原版手持图标的杆从左下指向右上，这个网格沿 X 轴、感温泡在 -X，这样转完是捏着玻璃尾端、感温泡朝外（第三人称 `[0, -90, -80]`，平移 `[0, 4, 0.5]`，缩放 0.85；第一人称 `[0, -90, -110]`，平移 `[1.13, 3.2, 1.13]`，缩放 0.68）。

### 腕式温度计 `wrist_thermometer`

- 一条方形表带，绕着 4×4 像素的手臂截面（和玩家模型的手臂一样），沿手臂方向宽 2 像素；表带 +X 一侧是表盘：深色涂层钢表壳（车床旋出的环，顶部外缘倒角）、内凹的米白色表盘、红色指针、上面一层玻璃。表盘不印字、不画刻度。
- 材质：深色尼龙表带；涂层黑钢表壳（F0 24，倒角高光）；米白搪瓷表盘；红色指针；玻璃（alpha 70）。
- 图标：视角 `[8, -90, 0]`，正对表盘，表带左右伸出。
- 手持显示：`verticalOffset` 0.390625。
- 戴在身上：`wornAnchor` `[-0.575, 0, 0]`，即手臂轴线在烘焙后网格里 x = -0.575 像素、高度居中。`client/ClientEquipmentSlots` 的 WristItemRenderer 把它画在左手腕：手臂姿态 → 平移到手臂中心（细手臂模型 x 方向缩到 0.8）、离肩 8.5 像素 → 翻转成模型坐标（+Y 朝下，+X 仍朝外，表盘在手腕外侧）→ 用 `ItemDisplayContext.HEAD`（这个物品没有 head 变换，保持原始大小）画网格。

## 检查方式

- 生成脚本的 `--check`；
- `compileJava --offline`；
- 离线渲染只是我自己看形状用的，没有放进仓库。

## 需要实机验证

- 两件物品的手持、掉落、展示框外观，以及玻璃在 Sundial 下的效果；
- 腕式温度计戴在左手腕上的位置（第三人称、其他玩家视角），以及穿胸甲时的样子；
- 物品栏图标。
