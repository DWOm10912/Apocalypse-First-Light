# 热力发电机 3D 模型 V1

状态：**V1 静态 3D 替换已接入原 `apocalypse_firstlight:thermal_generator`**。不新增第二个方块；沿用原 BlockEntity、配方、菜单、GUI、燃料、储能、FE、掉落和本地化。旧六面模型/贴图保留为历史资源，但不再被该方块的当前 blockstate/item 引用。

| 项目 | 当前资产 |
| --- | --- |
| 源文件 | `src/main/blockbench/thermal_generator_3d.bbmodel` |
| 单图集 | `src/main/resources/assets/apocalypse_firstlight/textures/block/thermal_generator_3d.png`，256×256 |
| 静态导出 | `src/main/resources/assets/apocalypse_firstlight/models/block/thermal_generator_3d.json` 是源模型直接导出的校验中间文件；实际 blockstate/item 引用 `thermal_generator_3d_render.json` 分层组合模型 |
| Geo 导出 | `src/main/resources/assets/apocalypse_firstlight/geo/thermal_generator_3d.geo.json`，1.12.0，无动画 |
| 复杂度 | 源文件 362 cubes / 45 groups；正式静态导出 342 cubes / 39 bones，排除隐藏占位与参考体积 |
| 尺寸 | 1×1×1 基准，主体位于 16×16×16 内；排气塔最高 Y17.2，仅高出 0.075 格 |
| 世界渲染 | 原生 baked block model；不新增 BER、动画系统或注册项；lit 两态静态几何相同，旧声音和烟雾逻辑保留 |
| 物品显示 | 原 `models/item/thermal_generator.json` 继承 `thermal_generator_3d_render`；GUI 缩放 0.624（原 0.78 的 80%），旋转及位移不变；手持/掉落沿用源模型显示变换 |
| 碰撞 | 保留原有机身、底脚、顶盖、排气塔、接口的四向组合碰撞；玻璃/机舱为封闭实体，包含 Y17.2 顶盖，未改变已有碰撞盒数值 |
| 选取轮廓 | 独立四向两盒轮廓：机身 `[0,0,0]..[16,15.55,16]` 加排气塔 `[2.6,15.55,10.4]..[6.4,17.2,14]`；不再描出脚座/顶盖/接口的细碎边线；简化区域可被鼠标选中，但不改变实际碰撞 |
| 渲染分层 | Forge 内置 `forge:composite`：340 个机壳/内部零件 cube 用 `minecraft:solid`，仅两块玻璃用 `minecraft:translucent`；物品 pass 顺序为机壳后玻璃，避免整机进入透明排序阶段 |
| 朝向与遮挡 | 放置仍朝向玩家；原四向旋转、镜像逻辑不变，形状同步旋转；关闭完整不透明方块遮挡，避免相邻面按整立方剔除 |
| 挖掘 | 沿用 `requiresCorrectToolForDrops()`、`mineable/pickaxe` 与 `needs_diamond_tool`；未改硬度、掉落表或工具等级 |
| 正面 | NORTH / −Z；上方三色灯，中左固体燃烧室，中右转子，左下液体观察窗，右下保留通风栅 |
| 观察窗 | 净玻璃 5.51×5.91 模型单位，Z1.23～1.27；中央全透明，烟灰细边和克制反光，无原版玻璃纹样 |
| 腔体 | 后壁 Z6.25，玻璃后约 4.98 单位真实深度；独立内壁/炉排，不使用平面火焰贴图 |
| 火焰 | 三组旧几何保留在隐藏的 `source_only_flame_preview` 中，组及 cube 均不导出；不是最终燃料/火焰，也不再成为 runtime 必需结构 |
| 液体观察窗 | 原左下区域 X6.65～14.1 / Y2.15～3.7；玻璃 Z1.06～1.1，后壁 Z4.6，真实内深 3.5；深灰压框、内凹耐热玻璃、两侧液位刻度；本轮槽内无液体 |
| 动态锚点 | 固体燃料、火焰、火星及液体底面、最大液位面、FX 均有独立空骨骼；坐标与显示体积见专项文档 |
| 转子 | 8 个环向段×3 个轴向材质带，共 24 段；8 向对称辐条、轮毂与左右轴段 |
| 转子轴心 | `[4.1,8.125,4.8]`，局部 X 旋转轴；轮缘、轮毂、轴段共心，正面投影对齐护罩开口中心 |
| 护罩开口 | X2.4～5.8、Y4.2～12.05；本次转子校正不移动护罩、横栏或外壳 |
| 排气 | 顶部后侧，X2.6～6.4、Z10.4～14，最高 Y17.2；网格芯与盖板分层 |
| FE 接口 | 背面中心 X8/Y8，面板 X5.5～10.5、Y5.5～10.5、Z15.75～16；原 `machine_back` 中央 10×10 像素直接复用 |
| 左右流体接口 | 面向机器正面时左侧 +X、右侧 −X，中心分别 `[16,8,8]` / `[0,8,8]`；原左右流体贴图中央 14×14 像素无损复用，外围六像素宽渐变至灰钢；仅视觉接口预留 |
| 工业风格 | 保留旧机器的低饱和灰钢、厚框、折线检修缝、通风槽、底盘；大面平滑，无随机噪点；底部复用旧 bottom 图 |
| 概念对应 | 保留已认可的主体、转子、顶盖和主窗；新增指定的左下液体窗与左右接口；无发光或动态燃料效果 |

动画预留：`generator_rotor`、`status_green`、`status_yellow`、`status_red`。旧 `flame_core` / `flame_mid` / `flame_outer` 仅保留在源文件；后续火焰定位使用 `solid_flame_fx_anchor`。护罩与转子分组独立；本轮不提供动画 JSON。

构建初稿：`tools/build-thermal-generator-3d.bb.js`，仅用于指定空项目。转子校正：`tools/align-thermal-rotor.bb.js`，仅操作转子分组，保留外部几何；后续编辑以源文件为准。

本轮局部建模脚本：`tools/add-thermal-fuel-windows.bb.js`。初稿构建脚本已顺序调用转子校正与该脚本；本轮直接编辑当前源模型，没有重建用户已经调整过的机壳。

从 Blockbench 分别导出 project / java_block / bedrock 后，运行 `node tools/sync-thermal-generator-3d.mjs`，检查源/Java/Geo 的几何、旋转、UV、层级、接口像素、玻璃透明度和转子共心及对称性，再抽取 PNG、规范化导出。

已做 Blockbench 多角度检查及资源数值校验；静态替换已接入，不等于已完成游戏内透明排序、美术表现或 Jade/JEI 实机验收。无新方块，无挖掘规则变更。动态燃料、液体和转子动画仍未实现。

显示体积、锚点、source-only 排除及流体接口像素均纳入导出校验。当前会话修改前已有两块侧底板延伸到 Y1.1，与底座/角柱形成 10 对内部相交；保留该既有几何，新增结构相交为 0，不宣称全模型零相交。详细记录见 [动态燃料视觉预留](thermal_generator_dynamic_fuel_visuals_v1.md)。

## 静态替换验证（2026-09-08）

- `gradlew.bat build --offline` 通过；日志 `build/thermal-generator-static-build.log`。
- `gradlew.bat -I src/dev/thermal-generator-model-gametest.init.gradle runGameTestServer --offline`：**3/3 通过**，日志 `build/thermal-generator-static-gametest.log`。测试在独立 build 目录运行，不修改玩家存档。
- 覆盖四向真实物品放置、lit 切换保留同一 BlockEntity、原菜单类型、状态 NBT 往返、选取/碰撞分别旋转、两盒选取与细分碰撞独立、顶部空区/排气塔射线；生存八类工具的机器掉落及燃料槽内容掉落；四种燃料精确能量、16 FE/t 输出及满储能暂停。
- `node tools/sync-thermal-generator-3d.mjs` 验证八个 blockstate 变体、原 item parent、新图标缩放及源/runtime 资源同步。
- 尚未进行图形客户端内放置、透明排序、手持/掉落视觉和 Jade/JEI 实机验收；没有把 headless 测试当作视觉通过。

本轮选取与透明层修正不改变 `.bbmodel` 几何或贴图。分层文件由 `tools/sync-thermal-generator-3d.mjs` 从实际导出的 cube 自动划分，重新导出后不会退回整机透明渲染。透明玻璃仍可能显示其后的轮廓，金属机壳应参与不透明深度遮挡；最终视觉以客户端为准。

选取/分层修正后 `build runGameTestServer` 通过，3/3 GameTests 通过；日志 `build/thermal-generator-outline-tests.log`。已核对 340 个不透明 cube / 2 个玻璃 cube、机壳优先的物品渲染顺序；尚未完成本版客户端模型烘焙和实机透线检查。
