# 现代液晶显示器

状态：模型、贴图与静态装饰方块接入完成；等待游戏内视觉与生存采掘验证。

| 项目 | 内容 |
|---|---|
| 源模型 | `src/main/blockbench/modern_lcd_monitor.bbmodel` |
| 贴图 | `src/main/resources/assets/apocalypse_firstlight/textures/block/modern_lcd_monitor.png` |
| 数量 | 110 cubes，21 groups |
| 尺寸 | 宽16、深4.8、高12.8 units，即1×0.3×0.8 blocks |
| 贴图 | 128×128，屏幕独占 UV [0,0,112,63] |
| 屏幕 | 14.6×8.2125 units，16:9，黑屏、不发光 |
| 组合预览 | `src/main/blockbench/previews/office_desk_monitor_preview.bbmodel` |
| 注册 ID | `apocalypse_firstlight:modern_lcd_monitor` |
| 采掘 | 镐；铁镐或更高等级；错误工具不掉落 |
| VoxelShape | 覆盖底座、支架和屏幕，并随水平朝向旋转 |

屏幕、边框、状态点、背壳、支架、底座分别分组。背壳位于 display_assembly 内，显示总成轴心 (8,6.5,8.1)，便于后续整体调整屏幕倾角；支架轴心 (8,0.6,8.5)，底座底面 Y=0。

初版底座、外框和背壳边缘存在重叠。已通过切除重复体积保留外部形状，校验110个cube无正体积交叠。已查看正面、侧面、背面、顶部、三分之四及办公桌组合截图；UV绑定、16:9比例及保存回读已检查。移动视角是否完全无闪烁仍待用户复核；未进行游戏运行验证。

方块已加入方块注册、物品注册、创造模式方块页、双语名称与战利品表。显示器放在完整支撑方块上时使用正常高度；检测到下方为 13.5 units 高的现代办公桌时，模型与 VoxelShape 自动下沉 2.5 model units，底座落在桌面 Y=13.5，避免悬空。无通电、动画或屏幕 UI；游戏内显示和支撑切换仍待实机验证。

工具：`tools/modern-lcd-monitor.blockbench.js`、`tools/monitor-refine.blockbench.js`、`tools/monitor-remove-overlaps.blockbench.js`、`tools/monitor-delivery.mjs`、`tools/preview-monitor-desk.mjs`、`tools/export-office-props-runtime.mjs`。
