# 枪械维护台俯视交互 V2

| 项目 | 当前行为 |
|---|---|
| 主体 | 真实世界 `apocalypse_firstlight:gun_maintenance_bench`，宽 2 × 高 2 × 深 1；不复制场景或枪到 GUI |
| 进入 | 右键任意合法 part，空手/普通物品/P9/BR51 均可；不自动放枪、不传送玩家 |
| 相机 | `MaintenanceCameraController`；俯角 73°、稳定态 FOV 75°；smoothstep 进入 0.22 秒、退出 0.16 秒，不写玩家位置/头部方向 |
| 坐标 | NORTH 本地维护垫中心 `(1.0375,1.03875,0.45625)`；目标高于垫中心 0.04；镜头比目标高 0.98、向前 `0.98/tan(73°)`；统一绕根格中心旋转支持四向 |
| 叠加层 | 透明 `GunMaintenanceScreen` 仅接管鼠标及底部 9 格真实快捷栏/简短提示；不继承箱子 Container Screen，无大板、维护垫副本或独立枪预览 |
| 输入 | 维护期间阻止普通移动、跳跃、转头、攻击/使用、Native 开火/ADS/换弹、丢弃、滚轮换格；隐藏原 HUD、方块选框与自身手臂/人物 |
| 有效性 | 主 BE、四格完整且朝向一致、同维度、玩家存活、距根格中心不超过 5 格；失效关闭 |
| 退出 | Esc/背包键先取消附件页，再关闭 Context HUD，再退出；失效/死亡/切世界直接退出，恢复相机与输入，不自动返还枪 |
| 真实槽 | 沿用服务器 `GunMaintenanceBenchBlockEntity.maintenanceGunSlot`，容量 1，仅正式 AFL Native 枪；服务器重验 Menu、索引和槽状态后移动真实 ItemStack |
| 持久化 | 完整 ItemStack/NBT、originHotbarSlot、originPlayerUUID；关闭、断线、卸载和存档重启不返还 |
| 取回 | V2.1：原玩家点击原快捷栏位置虚影，非原玩家点击快捷栏右侧拿取按钮；枪身 hover/点击不再承担取回 |
| 回退 | 原玩家原空格优先，再当前玩家快捷栏空格→主背包空格→脚下掉落；任何玩家均可取，绝不覆盖或写别人的背包 |
| 桌面枪 | 仅根 BE 的 BER 读取真实槽；空台无枪；附件由同一真实 ItemStack 读取 |
| 维护姿态 | 独立缓存的原始模型静态骨架；不继承第一/第三人称、物品栏姿态，不播放 idle/shoot/reload/ADS |
| P9 弹匣 | 原模型 magazine 的 22° 骨骼旋转从资源读取，避免动画初始快照未生成时变成 0°；隐藏 reload_magazine/empty_old 临时骨骼，正式弹匣保留，不写原模型或第一人称动画 |
| 方向 | 模型 −Z 枪口映射至台面 +X（维护视角左侧）；模型 +Y 映射至台面后侧，握把/弹匣朝玩家一侧；侧躺，不翻面 |
| 视觉配置 | `MaintenanceViewProfile` 集中控制；P9 scale 0.55、Y 偏移 0.045，BR51 scale 0.29、Y 偏移 0.045；均 Y −90°、Z −90°，枪管平行维护垫长边；原枪数据不变 |
| 护垫 | 删除黑色 mat_dark_part 与白色 mat_small_component；源/完整 Java/分片/可选 Geo 同步，447 cubes；护垫、台钳、零件盒与贴图不变 |
| 拆除/防复制 | 任意 part 拆除先清槽再单次掉枪；工作台仍钻石级镐正确掉落；取放服务器线程串行执行，重复/失效请求拒绝 |

## Return Interaction V2.1

多人专项验证已完成：Dedicated + 两个真实客户端、50 轮服务器同 tick 竞争及库存数量守恒，见 [多人测试 V1](../testing/gun_maintenance_bench_multiplayer_test_v1.md)。下文早期“未测”记录仅指当时验证范围，当前以专项报告为准。

- 原玩家的 originHotbarSlot 只显示真实存枪 icon 的约 42% 可见度虚影，不叠加箭头、角标或其他像素图案（用槽背景合成淡化，兼容不同物品渲染类型）；仅客户端绘制，不写 Inventory、不创建 Fake Slot，不参与使用/拖拽。Hover 仅在此槽显示本地化“取回枪械”。
- 原槽被其他物品占用时，维护叠加层仍显示虚影，不让两个图标互相遮挡；保留真实库存，不覆盖或删除，点击此位置仍按既有空位/背包/掉落规则安全返还。
- 非原玩家不映射他人的来源槽，只显示 Hotbar 右侧 20×20 小按钮，Hover“拿取枪械”。台内取空后所有入口消失。
- `RETURN_GUN=9` 额外验证来源 UUID；`TAKE_GUN=10` 允许其他玩家及原玩家走安全取回。原版 containerId 请求绑定当前服务器 Menu 的根位置/维度，再校验玩家、距离、四格有效性和真实槽；不接受客户端随意指定另一台坐标。
- 来源元数据沿用 BE 更新包完整同步；初次 Menu 创建早于区块包时，客户端从随后到达的真实 BE 读取 metadata。取放仍只有服务器修改真实物品；重复请求无第二份物品。
- 同步等待期不显示取回入口：仅槽物品先到时不把缺失 UUID 判为非原玩家；完整 BE 快照到达后才判断归属。已确认的旧数据若确实没有 UUID，保留右侧拿取按钮用于安全回收；这与单人/多人模式无关。相同物品的后到槽包不撤销已确认归属。
- 归属等待修正：构建及 18/18 GameTest 通过（`build/maintenance-owner-sync-tests.log`），覆盖槽先到、本人/他人快照、相同槽后到、旧数据缺失 UUID 与清空；本次未进行图形客户端或真实双客户端复测。
- 枪身没有 Return Tooltip、Return Click 或整枪返回区域；通用 picking 不映射 RETURN。P9 的两个独立附件热点现已接入，维修仍未实现。

## 附件交互与后续扩展

### Stored Gun Localized Name Label

维护槽非空时，`GunMaintenanceScreen` 读取同步真实 ItemStack 的 `getHoverName()`，支持当前语言和自定义重命名；无名称映射表。灰白原生字体锚定维护垫上方木台面中央：NORTH 本地点 `(1.0375,1.03125,0.84)` 经四向旋转、共享维护相机投影转换为 GUI 坐标。名称不再跟随底部 origin placeholder，也不因附件页上移。清空立即隐藏。最长 220 GUI px 且限于屏幕边距，超长原字号省略；无大背景板。

日常持枪名称另由 `src/main/java/com/antaurora/apofirstlight/weapon/client/NativeGunHud.java` 显示在右侧白色枪械剪影上方，与剪影间隔 6 GUI px，水平居中并限制屏幕边距；剪影和弹药数字位置不变。读取主手真实 ItemStack 的 `getHoverName()`，最大 140 GUI px，超长省略。原版换物品时的短暂名称提示保持不变。该处剪影不是维护台底部的取回虚影。

位置修正构建日志：`build/gun-name-placement-build.log`；本轮未启动图形客户端，木台面投影位置、不同 GUI 比例及语言的最终视觉效果待实机验收。

摆放规则：默认“平行、居中、规整”，移除旧 10° 斜放。`MaintenanceGunRendering` 从同一静态 bind-pose bounds 缓存枪体纵向（模型 Z）边界中点，使基础枪体枪口/枪尾留边均衡；附件不参与重心重算，装卸附件不引起枪体跳位。保留各枪 scale、横向/高度中心和 offset 微调，`centerZ` 仅在模型/边界未就绪时回退使用。热点与绘制共用 transform，未另设屏幕补偿。该次调整不改相机、模型、VoxelShape 或事务；构建验证单独记录于 `build/maintenance-parallel-build.log`，未重新启动图形客户端验收。

当前桌面共享枪械渲染读取真实枪的 SIGHT + MUZZLE + MAGAZINE，可同时显示红点、消音器与 P9 24发或 BR51 35发扩容弹匣。P9 支持三类热点，BR51 同样支持 SIGHT、MUZZLE、MAGAZINE，两者共用热点→Context HUD→背包候选→服务器安装/更换/拆卸；弹匣热点从 magazine_slot 读取定位，P9 跟随 magazine、BR51 跟随 mag_standard。玩家装拆仅限维护台，V 快捷装拆已移除。见 [附件交互 V1](attachments/gun_maintenance_attachment_interaction_v1.md)。

UI/SFX 打磨后，按钮具有底板/边框/悬停/按下反馈与原版点击声。服务器批准后播放统一附件操作音（2.480 秒），等待 51 tick 再验证提交；期间正式枪和附件库存不变。Esc 退出会取消，右键及重复提交在等待期间禁用。详见 [UI/SFX V1](attachments/gun_maintenance_attachment_ui_sfx_polish_v1.md)。

`MaintenanceActionState` 记录等待服务器的安装/拆卸状态，不播放动画；`MaintenanceActionRequest` 为未来动作插入点。优先查询独立 maintenance 骨骼锚点，缺失时复用安装锚点，二者概念保持独立。修理、手臂动画、电池与照明未实现。

未来事务：服务器验证 → 动作状态 → 临时视觉附件/双手动作 → Animation Complete → 服务器再次验证 → Commit Attachment。动画完成前不得写正式枪附件；左手稳定枪，右手操作配件或工具。

## 验证

### Camera / Layout / Transition Polish V1

用户已确认删除圈出的整套蓝色虎钳（覆盖原提示词中“保留虎钳”的冲突条款）与桌面右侧零件盒。源模型、完整 Java 模型、四格分片及 Geo 同步；392 cubes，维护垫横向 2 倍、纵向 1.4 倍，中心、高度、原材质 UV 不变，保留木质边缘。工具墙、右侧壁架和下层储物保留。P9/BR51 的逐枪 scale 与 anchor 未改。

用户反馈后的 VoxelShape 补漏：删除旧虎钳碰撞/选取体，维护垫按扩大后的薄层更新；零件盒原本没有独立 AABB。四格裁切及四向旋转保持原逻辑，精密制造台不变。

相机在原版相机 pose 与维护 pose 之间按单调实时时钟、逐帧 smoothstep 插值位置/最短 yaw/pitch，并平滑衔接原版 FOV 与维护 FOV。没有创建实体、修改玩家方向或 FOV 设置；原第一/第三人称设置保留。进入到位才绘制/启用维护 UI；退出隐藏 UI、继续屏蔽输入，完成后关闭 Screen。正常退出立即发送原版关闭菜单包以取消待提交操作，镜头结束不返还枪。失效/卸载沿用立即清理，不强行播放退出动画。

快捷栏放枪、原槽取回及非原玩家拿取增加原版 `UI_BUTTON_CLICK`，pitch 1、volume 0.35；候选/Context/取消沿用既有点击声。无新增点击音频，51 tick 附件操作和共享音频未改。

本轮 `build runClient` 成功（`build/maintenance-camera-layout-client.log`）；隔离客户端 P9/BR51 实际取放、重开、镜头终点、退出状态通过，截图 `maintenance_v2_p9.png` / `maintenance_v2_br51.png` 已检查，布局无遮挡。资源验证通过：重叠/同向共面 0，贴图绑定正确。用户确认当前版本可结束；追加四向测试按要求中止，不计成功或构建失败。本轮未完成四向第一/第三人称恢复、全部中断场景、附件全流程、音效计数及真实多人回归；下文旧轮次证据不代替本轮验证。详见 [本轮记录](gun_maintenance_camera_layout_transition_polish_v1.md)。

2026-09-08：`build runGameTestServer -I src/dev/maintenance-gametest.init.gradle --offline` 通过，**18/18 GameTest**：原工作台 10 项（含四向与钻石/低级镐）、维护交易 1 项、Native 射击/干击/普通与空仓换弹/取消/创造备弹/ADS 共 7 项。日志 `build/maintenance-v2-tests-final.log`。

V2 阶段图形客户端四向通过：空手/普通物品/P9/BR51 开启、当时的两枪点击取放、实际相机坐标与 67.5°、普通移动锁定、Esc 恢复原第一/第三人称类型及头部方向；日志 `build/maintenance-v2-facing.log`。其中整枪点击入口已由 V2.1 替换。截图 `build/thermal-fluid-client/screenshots/maintenance_v2_{north,east,south,west}_{p9,br51,outside}.png`。

V2.1 服务端回归 18/18 通过（`build/maintenance-v21-tests.log`），新增来源 UUID 冒用拒绝、A/B 两种先后取回次序、双方入口清空以及既有回退覆盖。图形客户端取回流程通过（`build/maintenance-v21-client-final.log`）：原槽虚影、tooltip、枪身点击无动作、原槽占用回退、非原玩家拿取与关闭重开持久性；非原玩家来源由 FakePlayer 设置，真实双客户端同步不计为已测。随后移除了虚影角标；此纯绘制调整单独构建验证，不视为新增实机验证。

真实保存退出并再次启动：四台原存枪保留，包含 P9+红点与 BR51；已检查清空装饰后画面，日志 `build/maintenance-v2-disk.log`。关闭选框后的最终构建/四向重进再次通过，日志 `build/maintenance-v2-final-client.log`，截图同目录 `maintenance_v2_{north,east,south,west}_disk.png`。`node tools/validate-gun-maintenance-bench.mjs` 和分片导出通过，447 cubes，重叠/同面叠面为 0。

边界：P9/BR51 第一人称动画文件及渲染器未改；本轮战斗回归为服务器逻辑与 ADS 数据测试，不冒称重新人工逐帧验收全部换弹动画。真实双客户端并发未测，保留两位 FakePlayer 交错取放覆盖；维度切换/死亡中断有代码校验，未独立做图形场景。隔离客户端目录仍会使已有 Native Gun Smoke 的相对源文件路径检查失败，不把该检查计为通过。
