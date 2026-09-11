# 美式商业垃圾箱

状态：已作为纯静态 2×1 城市装饰方块接入游戏，Registry ID 为 `apocalypse_firstlight:commercial_dumpster`。当前不含 BlockEntity、容器、GUI、动画或功能性状态。

| 项目 | 当前值 |
| --- | --- |
| 可编辑模型 | `src/main/blockbench/commercial_dumpster.bbmodel` |
| 贴图 | `src/main/resources/assets/apocalypse_firstlight/textures/block/commercial_dumpster.png` |
| 运行时方块模型 | `src/main/resources/assets/apocalypse_firstlight/models/block/commercial_dumpster/{master,secondary}.json` |
| 完整物品模型 | `src/main/resources/assets/apocalypse_firstlight/models/item/commercial_dumpster.json` |
| 复杂度 | 260 cubes，31 groups |
| 贴图 | 128 × 128；低饱和绿色钢板、炭黑盖子、底边和举升槽轻微磨损 |
| 箱体尺寸 | 宽约 2、深约 1、高约 1.35 方块；举升槽在左右略有突出 |
| 左盖轴心 | [8, 21, 15]，后缘铰链 |
| 右盖轴心 | [24, 21, 15]，后缘铰链 |
| 占位 | 2×1；`MASTER/SECONDARY` 随水平朝向旋转 |
| 碰撞 | 每个 part 独立近似箱体、斜顶与外侧举升槽；选框使用每半边闭合外包络 |
| 采集 | `minecraft:mineable/pickaxe` + `minecraft:needs_iron_tool`；石镐及以下不正确掉落 |
| 显示 | 第一人称 0.30；第三人称 0.25；GUI 0.40 |

根组 `commercial_dumpster_root` 下分别保留箱体、左右盖、铰链、左右举升槽及小五金。箱体包括四面板、封底、上沿及三条底部滑橇；每块盖子包括主板、10 条筋、边框、前把手及两条内侧加强条。左右盖可独立旋转；已保存姿态为闭盖，组旋转为零，盖子本身的斜度由 cube 旋转表达。

已检查 MCP 三分之四闭盖预览和开盖预览；已校验源模型数量、每面贴图绑定、PNG 尺寸和后缘 pivot。运行时导出在 X=16 将几何切为两格，并裁去人为产生的内部切面；左右最外侧举升槽仍允许略微伸出占位。BlockItem 使用居中的完整 260-cube 几何，不会显示成半台。Blockbench 源与物品模型同步第一/第三人称、GUI、地面和物品框显示变换。

放置由单个 BlockItem 原子写入 `MASTER` 和 `SECONDARY`，第二格受方块可替换性、流体、实体碰撞、交互权限、区块及世界边界检查约束；失败时回滚。破坏任一格会清理整台，只有 master 拥有 loot，secondary 在正确工具采集时转交唯一物品；递归拆除使用 mutation guard，活塞推动被禁用。命令或爆炸拆除不保证一定产生掉落，但不会由两个 part 稳定复制物品。

资源同步校验与完整 Gradle `build` 已通过；Minecraft 实机的四向放置、阻挡放置、两侧采集、碰撞、显示比例及重进世界检查仍待执行。

制作脚本：`tools/commercial-dumpster.blockbench.js`；加强条：`tools/commercial-dumpster-finish.blockbench.js`；安全首次导出：`tools/export-commercial-dumpster.mjs`。运行时与显示同步：`tools/export-commercial-dumpster-runtime.mjs`；回归校验：`tools/verify-commercial-dumpster.mjs`。
