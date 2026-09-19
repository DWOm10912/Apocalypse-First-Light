# AFL HUD Layout Editor V1（仅开发环境）

## 状态与范围

已实现纯客户端通用编辑器。JSON 源文件是唯一持久化真值源，不创建 config 副本、不修改 JAR、没有服务端权限/同步包。当前两个正式 HUD Layout 已完整接入，现有源 JSON 的布局数值未改动。

| layout_id | 源 JSON（位于 src/main/resources/assets/apocalypse_firstlight/gui/layout/） | 可编辑元素 |
| --- | --- | --- |
| native_gun | native_gun_hud.json | global、silhouette、divider、weapon_name、ammo、fire_mode |
| geiger_counter | geiger_hud.json | global、symbol、text、rows.radiation、rows.dose、rows.zone |

当前没有额外注册的只读 HUD。下列现有显示属于 `NOT YET LAYOUT-JSON DRIVEN`：命中反馈、RadiationHeartOverlay、ExplosionTinnitusOverlay、RadiationStaticTextures、VendingMachineHint、GunMaintenanceScreen / MaintenanceAttachmentHud。NativeGunCrosshair 的基础动态十字已使用 `gui/layout/native_crosshair.json` 视觉参数，但固定屏幕中心，不注册本编辑器、不提供拖动/保存；见 `../native_guns/native_dynamic_crosshair_v1.md`。上述心形覆盖、全屏效果和上下文界面不强行改造成布局，也不以猜测坐标注册红框。machine_layout/*.json 是机器容器界面，不是 HUD，不纳入本轮。

## 命令与操作

通过 Forge RegisterClientCommandsEvent 注册，与现有服务端 `/afl` 子命令共存，不发送新的网络请求：

```text
/afl hudlayout list
/afl hudlayout edit native_gun
/afl hudlayout edit geiger_counter
/afl hudlayout reload native_gun
/afl hudlayout reload geiger_counter
/afl hudlayout reload all
```

edit 打开透明 Screen；未持枪时 Native Gun 使用只读 P9 物品样本预览，不给玩家物品、不写 stack。持有 Native Gun 时使用实际物品名称/剪影/弹药/模式。盖革预览可不持有仪器，读取现有 ClientGeigerData，不改变读数或音频。编辑器之外的显隐条件不变。

- 青色框为当前 HUD 元素；白框为当前选中元素。点击元素左键拖动，点击容器空白处选中整体。
- “选择下一个元素”用于选取窄分割线、父级 text/global 等被子元素覆盖的框。
- 滚轮每格修改主缩放字段 0.05，Shift+滚轮 0.01。Native Gun ammo 同比例修改已有 current_scale/reserve_scale/separator_scale；没有伪造额外 scale 字段。divider 和盖革各 rows 没有缩放字段，滚轮无操作。
- 所有坐标使用 GUI 缩放坐标。子元素位移除以整体 scale；盖革 global 的偏移方向与其原 schema 相反，因此拖动时适配负号。盖革整数偏移按整次手势累积后取整，不丢失每次小幅移动。
- Native Gun 保留现有左右分栏/屏幕边界约束，元素拖动不解除原渲染器的裁剪和适配。
- 红框/低透明度填充仅显示其他已注册 HUD 的占用区域及 ID，不拦截碰撞，也不允许选择或修改其他 HUD。即使其他仪器当前未手持，也显示其布局占位。

## 保存、取消、重置、重载

保存只写选中 descriptor 的源 JSON，保留未知字段，然后直接 apply 对应 loader 快照并退出；不触发整个客户端资源重载。
取消放弃 draft 并退出，不写盘。预览从未覆盖正常 loader 快照，所以取消无需回写临时配置。
ESC 有未保存修改时显示“保存并退出 / 放弃并退出 / 返回编辑”；再次 ESC 返回编辑。
重置合并当前 loader 的内置默认字段，仅预览，不移除未知字段，直到保存才写盘。注意内置 fallback 不等于作者后来微调过的源 JSON；重置不是恢复进入编辑器前的数值，后者使用取消或重新加载。
界面“重新加载”丢弃当前 draft，重新读取当前源 JSON 并预览；命令 reload 则读取并应用对应正常 HUD 快照。reload all 先成功读取全部，再应用，不写任何文件。

非法 JSON 在编辑器入口/重载时给出错误而不崩溃；失败重载保留 draft。保存失败保留 draft 和界面。外部修改源文件时保存拒绝覆盖，需重新加载；错误详情显示在界面。

## 开发目录与写入安全

必须 `FMLEnvironment.production == false`，并从 JVM 当前目录或 Minecraft gameDirectory 向上最多检查5层，找到同时具备 build.gradle、settings.gradle、gradlew 及可写 HUD 源目录的项目根。不硬编码绝对路径、不扫描磁盘、不创建源目录。

写入目标必须是 descriptor 注册的 `[a-z0-9_]+.json` 已有文件；源目录和文件真实路径必须与项目内预期路径相符，拒绝逃逸/符号链接目标。保存前再次检查路径/权限与磁盘字节快照。只在同目录创建当前布局的临时文件，然后 ATOMIC_MOVE + REPLACE_EXISTING；不支持原子替换时直接失败，不降级为可能损坏文件的普通覆盖。临时文件最后清理。

编辑 A 时 store/session 只持有 A 的保存目标；B 的 adapter 只参与 current/occupied 计算，不读取 B 的源文件进行保存，也不 apply B。session draft、selection、drag 状态均为界面实例字段。

发布/JAR 环境或没有可靠可写源目录时，命令明确提示“HUD Layout 编辑器仅支持开发环境”，不创建 config/资源包副本。

在确认源目录的开发环境下，两套 loader 启动及 F3+T 都直接读取源 JSON，避免保存后又被过期 build/resources 副本覆盖；不需要 processResources 才能读取源布局。没有源目录或发布环境仍沿用资源管理器/资源包读取，缺失/损坏内容按各自原有 fallback 处理。此开发分支仅用于这两个正式 HUD layout，不作用于其他资源。

## 架构与边界

代码位于 `src/main/java/com/antaurora/apofirstlight/client/hudlayout/`：

- HudLayoutRegistry / HudLayoutDescriptor / HudEditableElement / HudBounds：注册与统一接口。
- HudLayoutSession：单布局 draft、选择、拖动/缩放、重置/保存/取消。
- HudLayoutSourceStore / HudLayoutDevResources：安全源路径、原子写入、开发环境读取。
- ClientHudLayout / HudLayouts / NativeGunHudLayoutAdapter / GeigerHudLayoutAdapter：客户端注册与原 schema 适配。
- HudLayoutEditorScreen / HudLayoutCommands：透明界面、命令、仅编辑期间抑制当前 HUD 的重复绘制。

NativeGunHud 增加共用 preview/measurement 入口；名字/弹药/模式 bounds 复用实际 font.width、缩小/省略/基线计算，剪影与分割线采用原布局函数，转为当前 frame.scale 的屏幕坐标、裁剪到 frame 后联合。global 选框使用完整容器。
GeigerHudOverlay 提取只读 preview/lines 入口，原读数和绘制公式不变。bounds 联合实际128×48缩放面板、符号、实时本地化三行文字。其文字字号原本独立于 hudScale，adapter 保持该行为，不擅自统一 schema。

新 HUD 只需实现 ClientHudLayout 并注册到 HudLayouts，即可获得同一编辑会话/界面。通用 Screen/Session/Commands 没有 native_gun 或 geiger_counter 的 ID 特判。

## 验证与人工测试

compileJava / processResources 编译通过。HudLayoutEditorTest 独立40项、NativeGunHudLayoutTest 50项检查通过。前者使用合成 descriptor 验证注册、会话隔离、按比例坐标、保存/取消/reset/reload、未知字段保留、其他 JSON 字节不变、生产环境/路径拒绝、外部冲突、非法 JSON、本地化键；后者验证 Native Gun 纯几何和配置解析。它们不验证 Minecraft Screen 输入分发、视觉及资源重载生命周期。

人工：开发客户端运行 edit，拖动/滚轮/Shift、父子选取、ESC三个选项；保存后看 Git diff 只出现当前源JSON；切到另一布局检查红框不可编辑；手动改源JSON后 reload；测试 GUI Scale 2/3/4、中英文、不同枪和∞；发布版检查拒绝提示。特别检查红框覆盖、文本对齐和拖动手感。

未运行 runClient/clean，未 commit/push。未修改枪械战斗、FireMode、ADS、Ammo、Recoil、Noise、盖革测量/音频功能、网络协议或服务端存档。
