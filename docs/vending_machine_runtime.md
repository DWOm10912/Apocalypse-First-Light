# 自动售货机 Runtime Integration V1

## 当前行为

- 唯一方块/物品 ID：`apocalypse_firstlight:vending_machine`；“黎明启示录 · 家具与设施” Creative Tab（2026-09-28 起，见 `docs/ui/creative_tabs_v1.md`），中英文名称已注册。
- 属性 `facing=north/east/south/west`、`half=lower/upper`、`broken=false/true`。新取得的机器默认玻璃完整；破损机器物品带 `AflBrokenGlass` 标记，重放时两半均维持破损状态。下半唯一 BlockEntity，上半跟随状态；空间不足不放置。
- 主手持 `apocalypse_firstlight:crowbar` 右键正面完整玻璃：服务端预约该机器并启动独立第一人称 `smash_glass`；第12 tick（0.60秒）重新验证目标后才破碎，两半同步，库存保留。动作共33 tick（1.65秒）。侧面、背面、支付区、机顶不触发。
- 提示为“破坏玻璃”，与 `MaintenanceAttachmentHud` 共用 `AttachmentHintStyle`：150ms 渐入/淡出、灰色无阴影文字、深灰底板、命中点右14/上16像素。世界交互以准星为锚点，已破碎时淡出。
- 主手专用单手 ViewModel 使用 Native 玩家皮肤/袖子渲染，含蓄力、朝屏幕中心前砸、1 tick命中停顿、回位；命中时12个玻璃粒子与轻微镜头反馈。起手不再触发原版挥手。未增加撬棍耐久消耗规则。
- 动作期间同一玩家/同一机器只能有一项预约；命中前每tick检查工具、热栏、距离、视线、目标BE身份及玩家状态。换物品、移开准星、离开距离、目标消失、退出或打开界面会取消；取消停止对应音效，不回滚已经提交的破碎。动作期间左键取消，重复右键被拦截。细节及验证见 [crowbar_first_person_smash_v1.md](crowbar_first_person_smash_v1.md)。
- 音效 `apocalypse_firstlight:vending_machine_break` 来自用户最新 `E:/Download/vending_machine_break.ogg`，未转换，SHA256 `19c346082141e16317e1dfdf2a7b363fdaffee05de3d2d748f886d7c98c1ad9a`。实测1.027483秒，碎裂起音约0.04765秒。起手静音；S2C命中确认一起启动声音、命中姿势和破碎显示。等待确认时姿势停留在命中前，避免声音/视觉先于提交。旧音频及分段方案已弃用。
- **内容（2026-10-01 改，没有实机验证）**：不再逐格拿放真实物品。下半的方块实体改为 9 格可搜索容器（3×3 界面，接入逐格搜索，每格 20 ticks / 1 秒，±15%）；玻璃完好时打不开，砸碎后瞄准正面玻璃区（`frontPoint`）右键打开，提示"搜索 / 查看"（画在准星处）。玻璃完好时漏斗和物品能力都拿不到东西（`canPlaceItem` / `canTakeItem` 要求 `broken`，物品能力返回空），砸碎后按搜索框架的规则。
- 玻璃后面画通用货物（[container_goods_v1.md](gameplay/container_goods_v1.md)）：12 条货道各一种：易拉罐、瓶子、零食袋，按标准格位的 0.85 画；显示几条 = 有东西的格数 × 12 / 6，向上取整（6 格以上全摆满）。世界战利品在第一次服务端 tick 时生成（隔着玻璃一开始就要能看出满不满），只是生成，格子仍要搜。玩家放置的不用搜。
- 没有付费系统、撬门、开门或 worldgen。

## 展示与模型坐标

货道 = 4层×3列（2026-10-01 起只用来摆货物，容器本身是 9 格）。NORTH 局部方块坐标：货道中心 X=(5.63/16,9.23/16,12.83/16)，托盘顶面 Y=(8.0+4.7×层号)/16，托盘前沿 Z=2.75/16（`VendingMachineBlockEntity.laneX / laneY / LANE_FRONT_Z`）。原来的 12 槽实时摆放、旧 8 槽迁移和 `LayoutVersion` 已删除（开发阶段，老存档只读入前 9 格）。

源文件 X/Z 加8、Y不变，UV从128像素转换到16单位，无镜像转换。运行时 `body` 为269 cubes，完整玻璃1 cube，破碎残片22 cubes；共用贴图。BER 依次画不透明主体、货物和透明玻璃，按 `broken` 选玻璃模型。BlockState JSON 是粒子占位，实际状态模型由 BER 选择；不是两个 Registry ID。`entity/vending_machine` 纹理必须显式纳入 `minecraft:blocks` 图集；已在 `assets/minecraft/atlases/blocks.json` 添加单图来源，修复游戏内紫黑缺失贴图。

为让破洞与后墙区别更清晰，两份源模型的 `back_liner` 单独改为图集中较深的中性灰 `bin` 材质 UV；几何与玻璃碎片数量不变。修改同步到 `scripts/build_vending_machine_blockbench.js`，`scripts/tune_vending_machine_liner.js` 对既有源文件执行相同的幂等 UV 调整，`scripts/export_vending_machine_runtime.js` 导出运行时模型。

每半碰撞/选框为 [.18,0,.18]..[15.82,16,15.82] units 的稳定柜体盒，破玻璃不允许角色穿入机体。渲染 AABB 覆盖完整两格高度。上下半任一被挖走会清理另一半。

## 挖掘及掉落

硬度3、爆炸抗性5；`requiresCorrectToolForDrops()`，`minecraft:mineable/pickaxe` + `minecraft:needs_diamond_tool`。钻石/下界合金镐生存挖掘任一半只掉1台机器，破损状态写入掉落物 `AflBrokenGlass=true`，再次放置保留破损；空手、木/石/铁镐不掉机器。创造拆除不掉机器。库存独立掉落一次，包括错误工具或外部移除；支撑移除、爆炸不会掉机器本体。机器物品不封装库存，旧商品仍按原规则掉落。

## 修改文件

- `src/main/java/com/antaurora/apofirstlight/block/VendingMachineBlock.java`
- `src/main/java/com/antaurora/apofirstlight/blockentity/VendingMachineBlockEntity.java`
- `src/main/java/com/antaurora/apofirstlight/client/VendingMachineRenderer.java`
- `src/main/java/com/antaurora/apofirstlight/client/WorldInteractionHint.java`（原 `VendingMachineHint.java`，2026-09-29 改名）
- `src/main/java/com/antaurora/apofirstlight/client/AttachmentHintStyle.java`
- `src/main/java/com/antaurora/apofirstlight/client/MaintenanceAttachmentHud.java`
- `src/main/java/com/antaurora/apofirstlight/client/AflBlockEntityRenderers.java`
- `src/main/java/com/antaurora/apofirstlight/item/VendingMachineBlockItem.java`
- `src/main/java/com/antaurora/apofirstlight/registry/{AflBlocks,AflItems,AflBlockEntities,AflCreativeTabs,AflSounds}.java`
- `src/main/resources/assets/apocalypse_firstlight/blockstates/vending_machine.json`
- `src/main/resources/assets/apocalypse_firstlight/models/block/vending_machine{,_body,_intact_glass,_broken_glass}.json`
- `src/main/resources/assets/apocalypse_firstlight/models/item/vending_machine.json`
- `src/main/resources/assets/apocalypse_firstlight/textures/entity/vending_machine.png`
- `src/main/resources/assets/minecraft/atlases/blocks.json`（显式收录售货机贴图）
- `src/main/resources/assets/apocalypse_firstlight/sounds/vending_machine_break.ogg`、`sounds.json`、`lang/{en_us,zh_cn}.json`
- `src/main/resources/data/apocalypse_firstlight/loot_tables/blocks/vending_machine.json`（空表；玩家拆机掉落集中在方块中）
- `src/main/resources/data/minecraft/tags/blocks/{mineable/pickaxe,needs_diamond_tool}.json`
- `src/dev/java/com/antaurora/apofirstlight/dev/VendingMachineGameTests.java`
- `scripts/build_vending_machine_blockbench.js`、`scripts/tune_vending_machine_liner.js`、`scripts/export_vending_machine_runtime.js`、`scripts/vending-tests.init.gradle`

## 验证

- `gradlew.bat build runGameTestServer --offline -I scripts/vending-tests.init.gradle --console=plain` 已通过；专用 GameTest 1/1 通过。
- 专用 GameTest 综合场景包括四朝向放置、下半BE、锁定库存、正面破碎、上下同步、12格拿取、旧 8 槽 NBT 迁移、破损机器掉落及重放、碰撞保留、生存六种工具掉落及单次库存掉落、创造拆除、上方阻挡放置、支撑移除。（以上是当时的运行结果。2026-10-01 GameTest 改写：12 格拿取和旧 8 槽迁移换成"玻璃完好时内容锁住、砸碎后瞄准正面上下两半都能打开界面"，**改写后没有运行**。）
- 运行时使用项目本地 `GRADLE_USER_HOME=.gradle-user`。
- 图形客户端验证与独立砸击动作的最新结果见 `crowbar_first_person_smash_v1.md`；服务端测试本身不能证明视觉与音效体验。
- 背板改色已检查两份源模型与运行时 body UV；独立砸击动画和延迟命中已由 Crowbar First-Person V1 替换原先的即时破碎流程。
