# 热力发电机 Dynamic Renderer V1

| 状态 | 转子 | 指示灯 | Block Light |
|---|---|---|---:|
| OFF | 定格 | 全灭 | 0 |
| RUNNING | 连续旋转 | 仅绿灯 full-bright | 9 |
| FULL | 定格 | 仅黄灯 full-bright | 0 |
| ERROR | 定格 | 支持红灯 full-bright；正式逻辑不触发 | 0 |

- 服务端沿用实际 FE 转换与容量判断：储能满优先 FULL；本 tick 生成 FE 且仍有空间为 RUNNING，否则 OFF。不推断电网故障，不新增燃料状态机。`RED = reserved for future fault / soot system`。
- 原 `LIT` 属性只在变化时更新，标准 `lightLevel` 为 9/0；不生成假光源或彩色光。保留炉膛声音，旧烟雾占位已由 [Particles V1](thermal_generator_particles_v1.md) 的客户端持续排烟替代；积碳未实现。

## 渲染

- `client/ThermalGeneratorRenderer.java` 为 BER。世界 baked 模型 `thermal_generator_3d_world.json` 保留 302 个不透明 cube；35 个转子 cube、三个灯面与两片原玻璃单独渲染，避免静态/动态重叠。玻璃在 Particles V1 使用不写深度的透明 pass，保留深度测试，让腔内原版粒子可见；几何未变。物品仍使用完整静态 `thermal_generator_3d_render`，手持/背包/掉落不缺零件。
- 转子沿原 X 轴、原 pivot `[4.1,8.125,4.8]` 转动，6°/tick，即正常 TPS 下 20 RPM。服务端累计运行 tick 并持久化；客户端以快照时间和 partial tick 求相位，不按帧累计，停止时保持相位。无 animation JSON。
- 灯罩与灯面始终存在。灭灯面颜色倍率 0.45；亮灯面用 full-bright，灯面纹理提供颜色，三个状态互斥。生成的灯面使用 tint index 接收颜色倍率。
- 固体槽中煤炭/木炭显示三个固定位置的小型真实物品模型，煤块显示一个较完整模型；不是精确数量计数。空槽、空桶及非支持物品不显示。所有偏移固定，燃料不会逐帧随机；不生成实体、不复制物品。源燃料用光后不显示正在消耗的最后一份，遵守“显示槽内内容”的规则。
- 液体直接复用 `FluidRenderHelper.renderTankCuboid` 的 Forge still sprite、动画图集、tint 和流体发光等级。高度为 `minY + (maxY-minY) × amount/capacity`，空槽不绘制，最近同步量连续映射，不离散成空/满档。
- 锚点与显示体积由 `tools/export-thermal-generator-dynamic.mjs` 从原 `src/main/blockbench/thermal_generator_3d.bbmodel` 生成 `ThermalGeneratorRenderGeometry.java`；不改已认可源几何。固体 `[7.65,4.55,2.1]..[13,6.3,5.55]`，液体 `[7.35,2.5,1.45]..[13.4,3.32,4.35]`（16 单位/格）。
- NORTH 为源模型方向；BER 绕方块中心旋转，EAST −90°、SOUTH 180°、WEST 90°，与 baked blockstate 一致。
- 机壳写入不透明深度；BER 动态内容位于玻璃后，玻璃仍走原透明 terrain pass。未把整个机器改成透明，也未移动玻璃/碰撞箱。

## 同步与性能

- 原更新包增加 `VisualState`、`RotorTicks`、`RotorSnapshotTime`、单个 `VisibleFuel`；Tank 继续沿用原快照。状态/槽位变化立即同步，持续液量变化沿用节流，不每帧发包。
- 只读本 BE；最多三个物品模型、一个液体 cuboid；转子/灯使用资源系统烘焙模型，重载资源时仍读取当前缓存，无世界扫描或新增实体。大规模基地 FPS 不作为此版本的已测指标。

## 验证

- `build/thermal-dynamic-tests.log`：构建及 14/14 GameTest 通过，包括真实光照传播/熄灭、OFF/RUNNING/FULL、三种固体燃料、液体、partial tick、相位 NBT、可视槽位同步、四向映射及原 Fluid IO/Jade 数据/固体燃料/工具掉落回归。
- `node tools/sync-thermal-generator-3d.mjs`：源/运行模型、UV、锚点与体积校验通过；源几何未改，新几何相交为 0，保留既有下侧板的 10 对内部相交。
- 隔离图形客户端：`build/thermal-dynamic-client-final.log` 十二种场景完成；夜间 OFF、三种固体燃料、25/50/75% 熔岩、FULL、四向与空槽截图位于 `build/thermal-fluid-client/screenshots/thermal_dynamic_*.png`。已检查煤炭/木炭纹理区别、煤块、绿/黄互斥、红灯不亮、内容在腔内、空槽隐藏及转子前后帧。细窄观察窗在高俯角时会被原压框遮挡，未为扩大视野重做模型。
- 保存退出后用独立新客户端重新进入：`build/thermal-dynamic-reload-seed.log` / `build/thermal-dynamic-reload-verify.log` 均 PASS，FULL、2000 mB、64 煤炭、停转相位 1234 tick 与光照 0 恢复；此测试包含磁盘保存及区块重新加载。
- 同次重进客户端真实 Jade Tooltip 与 JEI 分类/催化剂/五种燃料布局及定标刷新 probe PASS。最终 `build` PASS，发布 jar 不含 dev 包；无大规模 FPS 基准测试。
- 隔离目录仍会触发既有 Native Gun smoke 的相对源码路径报错；不是本轮机器渲染错误，未修改枪械或把该项计为通过。
