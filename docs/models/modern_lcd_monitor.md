# 现代液晶显示器

状态：**V2（2026-10-03）已接入**。Pure Mesh + LabPBR，静态 OBJ 方块模型；选中轮廓和碰撞同时改了。生成脚本 `--check` 通过，`compileJava --offline` 通过；**没有实机验证**。

V1 是 110 个 cube 的 Blockbench 模型，2026-10-03 被 V2 替换。以下都没变：
- 注册 ID、方块状态（`facing`、`lowered`）、掉落、挖掘规则；
- 放在办公桌上自动下沉 2.5 px 的逻辑。

| 项目 | 内容 |
|---|---|
| 注册 ID | `apocalypse_firstlight:modern_lcd_monitor` |
| 生成脚本 | `tools/build-office-props-v2.mjs monitor`（命令见 [modern_office_desk.md](modern_office_desk.md)） |
| 可编辑源 | `src/main/blockbench/modern_lcd_monitor_v2.bbmodel`，贴图副本 `src/main/blockbench/textures/modern_lcd_monitor_v2{,_s,_n}.png` |
| 运行时模型 | `models/block/modern_lcd_monitor.{json,obj,mtl}`，桌上下沉版 `modern_lcd_monitor_lowered.{json,obj,mtl}`（y -2.5 px） |
| 贴图 | `textures/block/modern_lcd_monitor{,_s,_n}.png`，512 LabPBR，约 12 texel/px；电脑工位也用这张 |
| 尺寸 | 宽 16、高 12.75、底座深 4.8 px；屏幕面朝 -Z（facing=north） |
| 屏幕 | 15.2 × 8.55 px（16:9），深色玻璃 `[12,14,17]`（光滑度 214），黑屏，不发光 |
| 三角面 | 620 |
| 采掘 | 镐；铁镐或更高等级（`mineable/pickaxe` + `needs_iron_tool`，`requiresCorrectToolForDrops`）。未改 |

## 外观

- 窄边框：上、左、右各 0.4 px，下边框 0.75 px。
- 背壳：圆滑收窄的背壳，用放样做出圆角。
- 支架：转接块、扁立柱、圆角底座。
- 材质：缎面黑塑料 `[33,34,37]`，石墨色支架 `[44,45,49]`。不印字，没有指示灯（没通电）。
- 法线平滑同办公桌。

## 选中轮廓与碰撞（2026-10-03）

旧形状是底座、立柱、屏幕三个盒子，轮廓是台阶状的。V2 的轮廓和碰撞都是一个盒子（facing=north，随朝向旋转）：
- 正常：`0,0,5.6 → 16,12.75,10.4`；
- 桌上：`0,-2.5,5.6 → 16,10.25,10.4`。

代码在 `block/ModernLcdMonitorBlock.java`。

## 物品

父模型是 `block/modern_lcd_monitor`。GUI 平移重新居中：rotation `[25,135,0]`、translation `[0.445,0.847,0]`、scale 0.72。其他场合照旧。

## 旧工具（已过时）

- V1 cube 源 `src/main/blockbench/modern_lcd_monitor.bbmodel` 和预览 `previews/office_desk_monitor_preview.bbmodel` 保留作参考。
- **不要再运行** `tools/monitor-delivery.mjs`：它会把 V1 贴图写回 `textures/block/modern_lcd_monitor.png`。
- `modern-lcd-monitor.blockbench.js`、`monitor-refine.blockbench.js`、`monitor-remove-overlaps.blockbench.js`、`preview-monitor-desk.mjs` 只对 V1 源有意义。
- 运行时导出脚本 `tools/export-office-props-runtime.mjs` 已删除。

## 需要实机验证

- 黑屏玻璃在 Sundial 下的反光；
- 放在桌上和地上的高度；
- 新的轮廓和碰撞；
- 四个朝向。
