# Field Attachment View V1

状态：代码已实现；V1.0.2 基础 HUD 清理的 `compileJava --offline` 已通过，随后按用户实机反馈追加的“隐藏全部原版状态 HUD”未重新编译或实机验证。视觉、声音、真实鼠标输入、多人及 Shader 验收由用户手动完成。未新增动画、模型、音效或修理系统。

## 操作与边界

默认 Z（Controls 可重绑）进入第一人称即时配件改装；再次按绑定键或 Esc 平滑退出。V 保持 Inspect，R 保持 Reload，B 保持 Fire Mode；旧 SightExchange 快捷安装继续为空操作。必须存活、非旁观、第一人称、主手为允许改装且有受支持槽位的 AFL Native Gun、没有其他 Screen、没有正在检视/换弹/装备/射击等动作。C.A.T. 显示既有拒绝提示。

Field 使用当前真实主手 ItemStack 和现有 renderer。ENTERING/OPEN/EXITING 期间隐藏玩家双臂、跳过第一人称 sway/recoil/ADS、屏蔽 bob；关闭后恢复。透明非暂停 Screen 显示鼠标，阻断普通视角及快捷栏/丢弃/背包输入；背景世界继续运行。死亡、换维度、换枪/槽、旁观、第三人称、政策失效或 Screen 被替换时取消并关闭；Z/Esc 正常退出保留过渡动画。镜像使用玩家实际主手侧，不支持副手枪。

V1.0.2 在同一 ENTERING/OPEN/EXITING 生命周期内按帧抑制 Vanilla CROSSHAIR、HOTBAR 和 ITEM_NAME，并在 NativeGunHud 顶层跳过完整普通持枪 HUD。后续按用户实机反馈，将 PLAYER_HEALTH、ARMOR_LEVEL、FOOD_LEVEL、AIR_LEVEL、MOUNT_HEALTH、JUMP_BAR 和 POTION_ICONS 也加入同一个 Field-only 取消清单。CROSSHAIR 与 HOTBAR 的取消同时覆盖对应位置的 Vanilla attack indicator。EXPERIENCE_BAR（含等级数字）保持项目既有的全局移除行为，本轮不改变其条件或生命周期。Boss bar、聊天、字幕、标题以及伤害/燃烧等世界画面反馈不在取消清单中；FieldAttachmentScreen、复用的 MaintenanceAttachmentHud、热点、候选页和鼠标也不经过上述 NativeGunHud gate。没有设置 `Minecraft.options.hideGui`，没有持久 HUD 隐藏状态；Field 状态变回 CLOSED 后下一帧自然恢复 Field-only 抑制的 HUD，异常取消同理。维护台行为未改。

当前实际槽位仍仅 SIGHT / MUZZLE / MAGAZINE；每枪按既有 compatibility 数据决定是否显示。未增加 grip、stock、laser、flashlight 或 skin。

## 组件

Java 路径均相对于 src/main/java/com/antaurora/apofirstlight/。

- weapon/client/FieldAttachmentViewState：CLOSED/ENTERING/OPEN/EXITING、独立 NativeAdsProgress、进入时 level/player/slot/GeoItem ID/stack snapshot、取消原因；selected slot/pending 由同一 Screen 的共享 HUD 持有并经 state accessor 暴露。
- client/FieldAttachmentScreen：透明非暂停 Screen；普通鼠标与键盘事件；调用同一 MaintenanceAttachmentHud，无第二套候选 UI。
- weapon/client/FieldAttachmentTransform：在 ConfiguredGunFirstPerson 与 P901FirstPerson 的 renderStatic 前作用于同一父 PoseStack。以既有 ADS calibration 的 HIP 逆矩阵求展示校正，但使用独立 profile 和 progress；P9 composition 仍处在原位置。
- weapon/client/FieldAttachmentViewProfile：独立资源 profile，进入时读取；不复用 MaintenanceViewProfile。
- weapon/client/FieldAttachmentHotspots：在 NativeAnimatedWeaponRenderer/P901Renderer 原有递归绘制中，仅匹配三个受支持槽位的 anchor；应用该帧骨骼 pose、pivot 与真实第一人称 projection，转换为 GUI-scaled 点。每帧清空，尺寸实时读取；无第二次模型树遍历或 mesh picking。
- weapon/AttachmentHotspotDefinition：slot、preferred/fallback anchor、局部偏移；维护台的 interactionPoint 和 Field capture 共用。
- client/AttachmentHudHost：gun/revision/projection/submit/action adapter。MaintenanceAttachmentHud 的既有维护台构造器继续提供 bench adapter；Field 提供 main-hand adapter。AttachmentCandidatePage、按钮、Context HUD、候选分页、音效和 pending UI 共用。
- weapon/AttachmentModificationPolicy：共享 AFL Native Gun 与 C.A.T. 禁止规则。
- weapon/AttachmentInteractionCore：共享安装/拆卸/替换、源物品精确匹配、消耗、旧附件归还、弹匣容量与溢出弹药返还、库存快照/失败掉落回滚。
- weapon/FieldAttachmentActionRequest / FieldAttachmentOperation：主手交易请求、单玩家 pending、序号防重放、51-tick deadline、每 tick 与提交前重验及显式取消。

## 主手服务端事务

协议 **29**，在既有包之后追加 FieldAttachmentPacket、FieldAttachmentCancel、FieldAttachmentResult。请求含 token、selected hotbar slot、GeoItem ID、完整 expected gun、target slot、source inventory slot 和完整 expected source。sourceSlot=-1 表示拆卸；非负表示安装/替换（是否存在旧附件由服务端真实枪栈决定）。不传 camera、mouse、Screen 或 progress。

服务端验证：存活/非旁观/在线、同一 inventory menu、非冲刺/使用物品、slot 0..8 且当前仍选中、GeoItem ID 和完整枪栈匹配、无枪械动作/有效开火/维护台 pending、共享 policy、槽位及附件兼容、源物品匹配且不是枪自身槽。Begin 记录原枪栈对象及 dimension；每 tick 和截止时再次检查，换成同型甚至同 NBT 的另一栈也因对象变化而取消。

安装/拆卸/替换均使用 MaintenanceAttachmentOperation.DURATION_TICKS = 51（既有 2.480 秒声音）。开始与截止前不扣物品；仅服务端原子提交。主手发布时保留 ItemStack 对象，仅写入 updated copy 的 NBT，然后同步 inventory；避免 NativeGunActions 把对象替换误认为重新装备。附件 renderer 自然读取同步后的状态，不新增 reset/cache clear/re-render packet。

结果阶段沿用 0=拒绝/取消、1=提交、2=服务端已开始。Screen 只接受当前 token 的结果；退出立刻发送该 token 的 cancel。取消只能阻止尚未提交的操作，已提交交易不会凭客户端关闭而回滚。重复 token 不重启/重放；换维度、失去枪/源物品、菜单变化、退出登录与服务器停止清理 pending。Field pending 期间服务端枪械动作和开火入口也被阻断。

## 维护台与声音

保留 GunMaintenanceMenu、bench position/revision/distance/完整结构验证、原位渲染、相机、MaintenanceViewProfile、归属/取回槽元数据和操作延迟。MaintenanceAttachmentTransaction 仅委托共享核心交换库存；目标仍由 bench adapter 发布并同步。此代码复用不等于本轮完成维护台实机回归。

音效沿用 UI_BUTTON_CLICK 与 AflSounds.ATTACHMENT_OPERATION；操作声仅在 phase=2 后本地播放，失败/退出停止，不新增声音文件。Field 仅提供附件入口；维护台的其他职责未迁移，本轮未实现新修理功能。

## 手工校准

资源目录：src/main/resources/assets/apocalypse_firstlight/field_attachment/。

### V1.0.1 独立展示姿态修正

Field 终点使用 `FieldTarget × HIP⁻¹`，因此在 progress=1 时会抵消普通 first-person display、HIP translation/rotation/scale，以及 P9 的 composition。V1 的偏移并非 HIP 终点仍然残留，而是 `FieldTarget` 错把 ADS aim point 当作视觉中心；模型主体中心与 aim point 的 Y/Z 差距使三把枪向左下偏，长枪最明显。原 `yaw=65` 也有意保留了透视角，无法形成正侧视。

V1.0.1 profile 新增模型空间 `center: [x,y,z]`。`x/y/z=0` 现在表示该 `center` 经 Field rotation/scale 后落在屏幕中心射线上；它不再隐式表示 ADS 瞄准轴居中。`pitch/yaw/roll` 是独立 Field target 的绝对展示旋转，三把枪使用 `yaw=90` 正侧视；`scale` 是相对于枪定义 first-person uniform scale 的展示倍率。进入/退出仍对完整 HIP→Field correction 的 translation、rotation 和实际 matrix scale 做 7/6 tick 平滑插值。关闭 Field 时普通 HIP/ADS/recoil/sway 代码路径不变。

视觉中心来自当前 runtime geo 可见主体的模型空间包围中心初值，写入每枪 JSON，未硬编码进 Java：P9 `[-0.00185, 2.63681, -1.82842]`，BR51 `[-0.10793, 8.28125, -0.625]`，HR55 `[-0.21692, 5.85926, -1.61992]`。这些是一次性静态几何基准，仍需用户实机判断视觉质量中心与最终构图；后续仅调 JSON。

热点捕获未改变：它仍在现有 P9/configured renderer 骨骼遍历中读取最终 PoseStack，因此新的中心、旋转、尺度和过渡会自然反映到 screen-space hotspot，不使用旧 transform 或单独像素补偿。附件业务、HUD、网络协议、服务端交易、C.A.T. policy 与维护台均未修改。

| Profile | x/y/z | center（模型单位） | pitch/yaw/roll（度） | scale multiplier | enter/exit ticks |
|---|---|---|---|---|---|
| p9_01.json | 0 / 0 / -0.9 | -0.00185 / 2.63681 / -1.82842 | 0 / 90 / 0 | 0.8 | 7 / 6 |
| br51_01.json | 0 / 0 / -1.35 | -0.10793 / 8.28125 / -0.625 | 0 / 90 / 0 | 0.8 | 7 / 6 |
| hr55.json | 0 / 0 / -1.35 | -0.21692 / 5.85926 / -1.61992 | 0 / 90 / 0 | 0.8 | 7 / 6 |
| DEFAULT（缺省资源） | 0 / 0 / -1.15 | 0 / 0 / 0 | 0 / 90 / 0 | 0.8 | 7 / 6 |

目标中心以 profile 的 `center` 为参考；参数不是实机校准结果。JSON 在每次进入时读取，资源包替换可在重新加载资源后生效。C.A.T. 被 policy 拒绝，故没有可用 C.A.T. 展示 profile。

热点沿用 MaintenanceHotspots.Point 的 20 GUI-pixel 半径；Context HUD 与候选条布局沿用维护台。需要用户检查：逐枪展示距离/旋转/尺度、真实 anchor 对齐、左手镜像、附件安装后刷新、GUI scale/超宽屏、输入取消、音量、多人、Oculus/Embeddium/Shader。没有通过自定义 world renderer 或 Shader 特判规避标准第一人称链。

## Verification

V1.0.2 基础 HUD 清理的唯一一次实际 `compileJava --offline` 通过（BUILD SUCCESSFUL，37 秒；项目现有 100 条弃用/unchecked warnings）。随后追加的原版状态 HUD 抑制按用户“自行测试”的要求未重新编译。未运行 processResources/build/check/GameTest/runClient，未启动游戏/MCP；本文件不宣称本次追加改动已经编译或运行时视觉通过。
