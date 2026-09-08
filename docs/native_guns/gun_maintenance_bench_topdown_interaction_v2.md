# 枪械维护台俯视交互 V2

| 项目 | 当前行为 |
|---|---|
| 主体 | 真实世界 `apocalypse_firstlight:gun_maintenance_bench`，宽 2 × 高 2 × 深 1；不复制场景或枪到 GUI |
| 进入 | 右键任意合法 part，空手/普通物品/P9/BR51 均可；不自动放枪、不传送玩家 |
| 相机 | `MaintenanceCameraController`；俯角 67.5°、FOV 75°；瞬间切换，不写玩家位置/头部方向 |
| 坐标 | NORTH 本地维护垫中心 `(1.0375,1.03875,0.45625)`；目标高于垫中心 0.04；镜头比目标高 0.82、向前 `0.82/tan(67.5°)`，位于灯罩下面；统一绕根格中心旋转支持四向 |
| 叠加层 | 透明 `GunMaintenanceScreen` 仅接管鼠标及底部 9 格真实快捷栏/简短提示；不继承箱子 Container Screen，无大板、维护垫副本或独立枪预览 |
| 输入 | 维护期间阻止普通移动、跳跃、转头、攻击/使用、Native 开火/ADS/换弹、丢弃、滚轮换格；隐藏原 HUD、方块选框与自身手臂/人物 |
| 有效性 | 主 BE、四格完整且朝向一致、同维度、玩家存活、距根格中心不超过 5 格；失效关闭 |
| 退出 | Esc、背包键、失效/死亡/切世界；恢复原相机类型及正常输入，不自动返还枪 |
| 真实槽 | 沿用服务器 `GunMaintenanceBenchBlockEntity.maintenanceGunSlot`，容量 1，仅正式 AFL Native 枪；服务器重验 Menu、索引和槽状态后移动真实 ItemStack |
| 持久化 | 完整 ItemStack/NBT、originHotbarSlot、originPlayerUUID；关闭、断线、卸载和存档重启不返还 |
| 取回 | V2.1：原玩家点击原快捷栏位置虚影，非原玩家点击快捷栏右侧拿取按钮；枪身 hover/点击不再承担取回 |
| 回退 | 原玩家原空格优先，再当前玩家快捷栏空格→主背包空格→脚下掉落；任何玩家均可取，绝不覆盖或写别人的背包 |
| 桌面枪 | 仅根 BE 的 BER 读取真实槽；空台无枪；附件由同一真实 ItemStack 读取 |
| 维护姿态 | 独立缓存的原始模型静态骨架；不继承第一/第三人称、物品栏姿态，不播放 idle/shoot/reload/ADS |
| P9 弹匣 | 原模型 magazine 的 22° 骨骼旋转从资源读取，避免动画初始快照未生成时变成 0°；隐藏 reload_magazine/empty_old 临时骨骼，正式弹匣保留，不写原模型或第一人称动画 |
| 方向 | 模型 −Z 枪口映射至台面 +X（维护视角左侧）；模型 +Y 映射至台面后侧，握把/弹匣朝玩家一侧；侧躺，不翻面 |
| 视觉配置 | `MaintenanceViewProfile` 集中控制；P9 scale 0.55、Y 偏移 0.045，BR51 scale 0.29、Y 偏移 0.045；均 Y −80°、Z −90°；原枪数据不变 |
| 护垫 | 删除黑色 mat_dark_part 与白色 mat_small_component；源/完整 Java/分片/可选 Geo 同步，447 cubes；护垫、台钳、零件盒与贴图不变 |
| 拆除/防复制 | 任意 part 拆除先清槽再单次掉枪；工作台仍钻石级镐正确掉落；取放服务器线程串行执行，重复/失效请求拒绝 |

## Return Interaction V2.1

- 原玩家的 originHotbarSlot 只显示真实存枪 icon 的约 42% 可见度虚影，不叠加箭头、角标或其他像素图案（用槽背景合成淡化，兼容不同物品渲染类型）；仅客户端绘制，不写 Inventory、不创建 Fake Slot，不参与使用/拖拽。Hover 仅在此槽显示本地化“取回枪械”。
- 原槽被其他物品占用时，维护叠加层仍显示虚影，不让两个图标互相遮挡；保留真实库存，不覆盖或删除，点击此位置仍按既有空位/背包/掉落规则安全返还。
- 非原玩家不映射他人的来源槽，只显示 Hotbar 右侧 20×20 小按钮，Hover“拿取枪械”。台内取空后所有入口消失。
- `RETURN_GUN=9` 额外验证来源 UUID；`TAKE_GUN=10` 允许其他玩家及原玩家走安全取回。原版 containerId 请求绑定当前服务器 Menu 的根位置/维度，再校验玩家、距离、四格有效性和真实槽；不接受客户端随意指定另一台坐标。
- 来源元数据沿用 BE 更新包完整同步；初次 Menu 创建早于区块包时，客户端从随后到达的真实 BE 读取 metadata。取放仍只有服务器修改真实物品；重复请求无第二份物品。
- 同步等待期不显示取回入口：仅槽物品先到时不把缺失 UUID 判为非原玩家；完整 BE 快照到达后才判断归属。已确认的旧数据若确实没有 UUID，保留右侧拿取按钮用于安全回收；这与单人/多人模式无关。相同物品的后到槽包不撤销已确认归属。
- 归属等待修正：构建及 18/18 GameTest 通过（`build/maintenance-owner-sync-tests.log`），覆盖槽先到、本人/他人快照、相同槽后到、旧数据缺失 UUID 与清空；本次未进行图形客户端或真实双客户端复测。
- 枪身没有 Return Tooltip、Return Click 或整枪返回区域；通用 picking 仅保留作未来基础设施，不在 UI 中映射 RETURN。相机、BER、姿态、scale 与持久规则不变；配件/维修交互未实现。

## 后续扩展（未实现）

`MaintenanceActionState` 仅运行 IDLE，其他状态为预留；`MaintenanceInteractionAnchor` 和 profile 的可选空 anchor 集合用于未来双手工作位置，与附件最终安装 `sight_anchor` 分开。未实现配件按钮、安装、修理、手臂动画、电池或照明。

未来事务：服务器验证 → 动作状态 → 临时视觉附件/双手动作 → Animation Complete → 服务器再次验证 → Commit Attachment。动画完成前不得写正式枪附件；左手稳定枪，右手操作配件或工具。

## 验证

2026-09-08：`build runGameTestServer -I src/dev/maintenance-gametest.init.gradle --offline` 通过，**18/18 GameTest**：原工作台 10 项（含四向与钻石/低级镐）、维护交易 1 项、Native 射击/干击/普通与空仓换弹/取消/创造备弹/ADS 共 7 项。日志 `build/maintenance-v2-tests-final.log`。

V2 阶段图形客户端四向通过：空手/普通物品/P9/BR51 开启、当时的两枪点击取放、实际相机坐标与 67.5°、普通移动锁定、Esc 恢复原第一/第三人称类型及头部方向；日志 `build/maintenance-v2-facing.log`。其中整枪点击入口已由 V2.1 替换。截图 `build/thermal-fluid-client/screenshots/maintenance_v2_{north,east,south,west}_{p9,br51,outside}.png`。

V2.1 服务端回归 18/18 通过（`build/maintenance-v21-tests.log`），新增来源 UUID 冒用拒绝、A/B 两种先后取回次序、双方入口清空以及既有回退覆盖。图形客户端取回流程通过（`build/maintenance-v21-client-final.log`）：原槽虚影、tooltip、枪身点击无动作、原槽占用回退、非原玩家拿取与关闭重开持久性；非原玩家来源由 FakePlayer 设置，真实双客户端同步不计为已测。随后移除了虚影角标；此纯绘制调整单独构建验证，不视为新增实机验证。

真实保存退出并再次启动：四台原存枪保留，包含 P9+红点与 BR51；已检查清空装饰后画面，日志 `build/maintenance-v2-disk.log`。关闭选框后的最终构建/四向重进再次通过，日志 `build/maintenance-v2-final-client.log`，截图同目录 `maintenance_v2_{north,east,south,west}_disk.png`。`node tools/validate-gun-maintenance-bench.mjs` 和分片导出通过，447 cubes，重叠/同面叠面为 0。

边界：P9/BR51 第一人称动画文件及渲染器未改；本轮战斗回归为服务器逻辑与 ADS 数据测试，不冒称重新人工逐帧验收全部换弹动画。真实双客户端并发未测，保留两位 FakePlayer 交错取放覆盖；维度切换/死亡中断有代码校验，未独立做图形场景。隔离客户端目录仍会使已有 Native Gun Smoke 的相对源文件路径检查失败，不把该检查计为通过。
