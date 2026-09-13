# Crowbar First-Person / Vending Glass Smash V1

## 审查与取舍

- `CrowbarItem` 是普通单目标近战 Item：6伤害、13 tick蓄力、480耐久，无自定义右键/使用持续时间；此次保留该物品和攻击逻辑。
- 售货机使用单一注册ID `vending_machine`，`broken`属性选择BER的两套玻璃模型，下半BE保存4×3展示槽。此次保留模型、掉落破损标记、库存迁移及拿放逻辑。
- `MaintenanceAttachmentHud` 和 `VendingMachineHint` 已共用 `AttachmentHintStyle`：150ms渐入淡出、灰字、深灰底、无文字阴影。继续复用；动作中提示淡出。
- Native第一人称入口是`RenderHandEvent`，枪械Geo骨骼定位点经`NativeHandBinding`交给`NativePlayerArmRenderer`绘制玩家皮肤和袖子。此次复用这两项接触坐标/渲染能力，抽取模板中的右臂Classic/Slim预览结构。
- `NativeCameraBoneConsumer` 当前是BR51限定的枪械动画消费者；撬棍使用自身动作时钟的短促命中镜头反馈。没有接入枪械弹药、ADS、射击控制器或枪械recoil状态。

## 资源与时间轴

`crowbar_first_person.bbmodel` 是独立可编辑源：`crowbar_fp_root → smash_motion → crowbar_model / right_hand_anchor`。右手参考臂只供编辑，不导出；运行时读取玩家Classic/Slim皮肤和袖子。原始`crowbar.bbmodel`未覆盖。左右惯用手由同一Rig镜像呈现。

`tools/build-crowbar-viewmodel.mjs` 从原始撬棍和`templates/afl_first_person_player_arm_rig.bbmodel`派生独立源、geo、animation；工具17 cubes，共用现有`textures/item/crowbar.png`。脚本是重新生成工具，运行会重建派生源，手工编辑该派生源前需留意。

使用用户最新替换的 `E:/Download/vending_machine_break.ogg`，原样复制：1.027483秒，碎裂起音约0.04765秒、峰值约0.05152秒。旧2.447687秒版本和派生起手/命中音频已弃用。起手静音，服务端确认命中才播放一次完整新音效，不变速。

默认右手；复用Native玩家皮肤与袖子，撬棍通过`renderFullSize`独立抵消展示缩小，三轴恢复1:1。取代此前0.775/0.78/0.775的加粗方案。缩放在皮肤绑定前围绕握持接触点处理，不移动接触点，不缩放撬棍。枪械默认缩放未改变。源中Classic/Slim参考臂同步为4×12×4 / 3×12×4；工具几何、弯钩朝向、动画时序不变。本次仅编译及静态检查，未启动客户端，1:1外观尚待用户实测。

握持朝向微调：仅`crowbar_model`绕握柄Y轴旋转-110°（Gecko绑定旋转编码+110°），弯钩朝前并略偏右外侧，待机呈斜侧面。手臂定位、粗细、动画轨道及33/12 tick交互时间均不变。`tools/adjust-crowbar-grip.mjs`对现有源与geo进行定点更新，生成脚本亦同步该朝向。此次仅编译/静态检查，按用户要求未启动客户端；此前截图不能代表此次朝向的实机验收。

| 时段 | 表现 |
| --- | --- |
| 0–10 tick | 静音后拉、抬高蓄力 |
| 10–12 tick | 快速向屏幕中心前方砸下 |
| 12 tick / 0.60s | 服务端提交broken、12个玻璃粒子，客户端确认后播放碎裂声 |
| 12–13 tick | 极短命中停顿；音频主峰在开始后约1 tick |
| 13–26 tick | 反弹回位，保留碎裂尾音 |
| 26–33 tick | 单手待机收尾；33 tick / 1.65秒结束 |

服务端和客户端共读打包的`animations/crowbar_first_person.animation.json`：`afl_timing`定义33/12 tick。`CrowbarSmashTimeline`采样同一`smash_glass`线性关键帧；GeoObjectRenderer消费采样骨骼。未确认时最多前进到11 tick，收到IMPACT才进入12 tick，并一起播放碎裂声、应用服务端已提交的破碎显示；随后按本地确认时钟回位。网络延迟可能延长蓄力等待，不提前碎裂。编辑时间轴后需重新构建并重启，不支持资源包改变服务端动作期限。

## 状态与网络

右键仍走方块原版请求包，服务端额外以玩家真实视线、Forge方块触及距离（上限6格）、正面玻璃区域验证。允许主手；副手不触发本专用动作。

服务端`CrowbarSmashAction`为玩家和机器BE预约一次动作，每tick检查：存活、非旁观/睡眠、连接、菜单、热栏、原工具对象、同维度、同BE；命中前还要求仍瞄准原玻璃且未破碎。目标被换成同坐标新机器也会取消。死亡/退出/条件丢失释放预约；结束不留下持久化“忙碌”标记。

新增有方向限制的`CrowbarSmashPacket`（S2C START/IMPACT/CANCEL/END）和`Cancel`（C2S动作UUID）；C2S不能提交破碎，不能取消别人的动作。`AflNetwork`协议从24升为25，双方需同版。广播给跟踪玩家及本人，单次动作无逐帧/逐tick广播。

START仅启动动作；IMPACT给本人及跟踪观察者播放一次`vending_machine_break`。取消停止对应声音；已提交的破碎不回滚。恢复期间禁止该玩家提前拿放展示物。独立多人延迟环境仍待验证。

## 变更文件

- `src/main/java/com/antaurora/apofirstlight/interaction/CrowbarSmashAction.java`
- `src/main/java/com/antaurora/apofirstlight/interaction/CrowbarSmashTimeline.java`
- `src/main/java/com/antaurora/apofirstlight/client/CrowbarFirstPerson.java`
- `src/main/java/com/antaurora/apofirstlight/client/CrowbarSmashClient.java`
- `src/main/java/com/antaurora/apofirstlight/client/VendingMachineHint.java`
- `src/main/java/com/antaurora/apofirstlight/block/VendingMachineBlock.java`
- `src/main/java/com/antaurora/apofirstlight/network/AflNetwork.java`
- `src/main/java/com/antaurora/apofirstlight/network/CrowbarSmashPacket.java`
- `src/main/java/com/antaurora/apofirstlight/weapon/client/NativePlayerArmRenderer.java`（可选截面缩放，枪械默认不变）
- `src/main/resources/assets/apocalypse_firstlight/sounds/vending_machine_break.ogg`（最新用户原文件）
- `src/main/blockbench/crowbar_first_person.bbmodel`
- `src/main/resources/assets/apocalypse_firstlight/geo/crowbar_first_person.geo.json`
- `src/main/resources/assets/apocalypse_firstlight/animations/crowbar_first_person.animation.json`
- `tools/build-crowbar-viewmodel.mjs`
- `src/dev/java/com/antaurora/apofirstlight/dev/VendingMachineGameTests.java`
- `src/dev/java/com/antaurora/apofirstlight/dev/CrowbarSmashClientProbe.java`
- `src/dev/crowbar-smash-client.init.gradle`

## 验证

- `build runGameTestServer --offline -I scripts/vending-tests.init.gradle`：通过，综合GameTest 1/1。包含四朝向延迟命中、重复请求、取消、同机竞争，以及原12槽/旧8槽迁移/破损掉落重放/生存工具掉落规则回归。
- 图形客户端探针：最新1.027483秒音频/33 tick时间轴通过；帧1/8/10/11为完整，帧12开始破碎，恢复完成PASS。已检查右手待机和中心砸击截图。一次探针因窗口鼠标视角偏离而超时，固定测试视线后重跑通过；该固定仅限开发探针。独立测试目录为`build/crowbar-smash-client`，使用复制的测试世界，未操作用户正式存档。
- 截图：`build/crowbar-smash-client/screenshots/crowbar_smash_idle_hint.png`、`crowbar_smash_t12.png`。测试客户端为英文语言，提示显示Break glass；中文资源仍为“破坏玻璃”。
- 独立多人延迟/第三方着色器、实际扬声器听感与主观打击感尚未验证。
