# Modern Office Desk — 建模资产

状态：**V2（2026-10-03）已接入**。Pure Mesh + LabPBR，静态 OBJ 方块模型；选中轮廓和碰撞同时改了。生成脚本 `--check` 通过，`compileJava --offline` 通过；**没有实机验证**（外观、光影、轮廓、碰撞、四个朝向都待验）。

V1 是 145 个 cube 的 Blockbench 方块模型（128 色块贴图），2026-10-03 被 V2 替换。方块逻辑、注册 ID、方块状态（`facing`、`part`）、掉落、挖掘规则都没变。

| 项目 | 当前值 |
|---|---|
| 注册 ID | `apocalypse_firstlight:modern_office_desk` |
| 生成脚本 | `tools/build-office-props-v2.mjs`（和椅子、显示器、键盘、鼠标共用，见下） |
| 可编辑源 | `src/main/blockbench/modern_office_desk_v2.bbmodel`（Free Model 网格），贴图副本 `src/main/blockbench/textures/modern_office_desk_v2{,_s,_n}.png` |
| 运行时模型 | `models/block/modern_office_desk/center.{json,obj,mtl}`；`left.json` / `right.json` 只有粒子贴图，不画东西 |
| 贴图 | `textures/block/modern_office_desk{,_s,_n}.png`，512 LabPBR 贴图集，约 6 texel/px |
| 尺寸 | 48 × 13.5 × 16 px（3 × 0.84375 × 1 格），中心格坐标 x -24..24、z -8..8；走线孔盖高出桌面 0.06 px |
| 三角面 | 752 |
| 方块结构 | 三格宽；`left / center / right` 联动放置与拆除，中心格负责掉落；`left` 在 facing 的逆时针一侧 |
| 采掘 | 镐；铁镐或更高等级（`mineable/pickaxe` + `needs_iron_tool`，`requiresCorrectToolForDrops`），错误工具不掉落。未改 |

## 外观

- 桌板：48 × 16 px，厚 1.05 px（y 12.45–13.5），浅暖灰三聚氰胺 `[178,177,170]`，缎面，边缘倒圆。
- 钢架：炭灰涂层钢 `[54,56,60]`（F0 22）。
  - 两端各一个框：两根 1.7 px 方管腿（x ±22.1，z ±6.1），上横杆、近地横脚；
  - 前后两根长横梁；
  - 腿下有黑色脚垫。
- 背面：一整块挡板（下沿折边）；一条 U 形走线槽，用两根吊带挂在桌板下。
- 桌面靠后有两个黑色圆形走线孔盖（x ±14）。
- 没有贴字、Logo 或磨损。

## 渲染

- 静态道具走烘焙模型（见 `docs/rendering/animated_block_mesh_runtime_v1.md` 的 STATIC PROP 规则），和 Retail Shelf V3 一样用 Forge OBJ：`forge:obj`、`flip_v`、`shade_quads`、`automatic_culling: false`、`ambientocclusion: false`。没有方块实体，不会每帧重画。
- 整张桌子画在中心格的模型里（OBJ 跨 x -1.5..1.5 格），所以桌面是一整块，没有三格接缝。
- OBJ 的法线：相邻面夹角小于 36° 的地方做了平滑，开光影时曲面不显棱面。原版着色仍按面方向。
- 已知限制：
  - 左右两格模型是空的，挖它们时看不到裂纹；
  - 中心格所在的区块分段被剔除时，伸到相邻分段的桌面也会跟着不画（只在区块分段边上才会出现）。

## 选中轮廓与碰撞（2026-10-03）

旧形状由很多盒子拼成，轮廓是台阶状的。V2 改成：
- **选中轮廓**（`getShape`，也用于准星拾取）：每格一个整盒 `0,0,0 → 16,13.5,16`。
- **碰撞**（`getCollisionShape`）：
  - 三格都有桌板 `y 12.45–13.5`；
  - 左格另加两根桌腿 `x 1.05–2.75`，`z 1.05–2.75` 和 `13.25–14.95`；
  - 右格另加两根桌腿 `x 13.25–14.95`，z 同左格；
  - 随朝向旋转（`HorizontalShapeUtils`）。

代码在 `block/ModernOfficeDeskBlock.java`。生成脚本里的 `SELECTION` 记了同样的数值，网格超出时构建失败。

## 物品

`models/item/modern_office_desk.json` 的父模型是 `block/modern_office_desk/center`。各场合的变换照旧，只有 GUI 的平移按新网格重新居中：rotation `[30,135,0]`、translation `[0,0.176,0]`、scale 0.28。

## 生成脚本

```text
node tools/build-office-props-v2.mjs [desk chair monitor keyboard mouse]           写出源文件、OBJ / MTL / 方块和物品模型、贴图
node tools/build-office-props-v2.mjs --check                                       检查所有输出是否最新
node tools/build-office-props-v2.mjs --preview DIR                                 只把 OBJ 和贴图写到 DIR（离线查看）
```

## 旧工具（已过时）

- V1 的 Blockbench cube 源 `src/main/blockbench/modern_office_desk.bbmodel` 保留作参考。几个旧预览脚本（`build-office-workstation-preview.mjs`、`build-office-desk-input-preview.mjs`、`preview-monitor-desk.mjs` 等）还在读它；它已经不是运行时的来源。
- 运行时导出脚本 `tools/export-office-props-runtime.mjs` 已删除。
- **不要再运行** `tools/export-modern-office-desk.mjs`：它会把 V1 色块贴图写回 `textures/block/modern_office_desk.png`，盖掉 V2 贴图集。
- `tools/modern-office-desk*.blockbench.js`、`tools/raise-modern-office-desk.mjs` 只对 V1 源有意义。

## 需要实机验证

- 外观：浅灰桌板在 Sundial 下是否偏白，钢架和背板的观感；
- 四个朝向下三格是否对齐；
- 新轮廓、碰撞（站上桌面、走到桌边）；
- 显示器、键盘、鼠标、工位放在桌上时下沉 2.5 px 后是否贴合桌面；
- 生存挖掘和掉落。
