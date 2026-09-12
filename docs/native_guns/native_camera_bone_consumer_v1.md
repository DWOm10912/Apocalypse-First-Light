# Native Camera Bone Consumer V1

状态：客户端 V1 已接入。用户实机反馈除尚未实装的检视交互外，其他效果没问题。
检视保留消费白名单，但不记为实机通过；完整异常生命周期矩阵尚未逐项确认。

## 范围与入口

- `src/main/java/com/antaurora/apofirstlight/weapon/client/NativeCameraBoneConsumer.java`
  使用 Forge `ViewportEvent.ComputeCameraAngles`，只修改该事件的 pitch/yaw/roll。
- `NativeAnimatedWeaponRenderer.java` 的 GeoModel 在 `handleAnimations` 前共享本帧时间。
- 仅 `ConfiguredNativeGunItem` 的 `profile.id() == br51_01` 启用；不改配置数据。
- 白名单：`inspect`、`reload_tactical`、`reload_empty`、`draw`、`put_away`。
  `shoot`、idle 及其他动作不消费；NativeGunRecoil 和 Weapon Sway 保持原实现。
- 只读取根级 `camera` 的 rotation，相对 initial snapshot；不读取 position/scale/pivot。
  P9 源/geo/animation/equip 未因该功能修改，P9 未启用消费。

## 同帧求值

Camera 事件早于手持绘制。事件中使用同一个 renderer 的 GeoModel、GeoItem instance ID、
AnimatableManager、ITEMSTACK、第一人称 perspective 和 Gecko 原生 TICK 调用 `handleAnimations`。
随后从 AnimationProcessor 的索引取得最终已 easing/interpolation 的 camera rotation。
不解析动画 JSON、不重采样、不运行第二套动画时钟。

GeckoLib 4.7.4 的 GeoItem 时钟是实时时间；本帧首次取样缓存为 preparedTick，
后续相同模型/instance 的第一人称手持求值复用它，利用 Gecko 的同 tick 去重。
RenderTick START 丢弃该帧引用；缓存不保存 Camera Offset，不跨帧复用旋转。
模型缺失和动画资源未加载直接不应用；bone 使用索引查找，没有每帧全树扫描。

## 坐标与安全边界

当前映射：最终 bone radians `X/Y/Z` 分别乘 `-180/pi`，
作为事件的 `pitch/yaw/roll` 度数增量，强度 1。支持 roll。
依据：Gecko loader 对 runtime JSON X/Y 再取反，与源导出的 X/Y 取反相抵；
视觉 view rotation 采用逆方向。用户认可本轮非检视动作效果；尚无逐轴隔离、
逐帧 Blockbench 对照证据，不将整体观感认可扩展为全部轴向测试通过。

每次事件从引擎新建 Camera 姿态开始，不累积 offset。
action STOPPED、无 trigger、不在白名单、缺 bone/model、非 BR51 均直接零消费。
无玩家/世界、死亡、旁观、睡眠、望远镜、界面/overlay、失焦、非第一人称、
Camera entity 非本地玩家均不应用。换枪每帧重新读取主手，旧枪没有可继承的旋转缓存。
不调用 player rotation setter，不改变玩家 look vector、服务端方向、弹道或 ADS。

## 开发验证

`-Dafl.nativeCameraDebug=true`：默认关闭；开启后最多每秒输出一次已应用动作的
bone 状态、动作、原始弧度、映射角度、scale 和 applied 状态。
`src/dev/native-camera-client.init.gradle` 仅为本地 runClient 打开该诊断。

最终同帧时间共享版本通过 `compileJava`、`processResources` 和完整 `build --offline -PaflWithoutTacz`。
开发客户端已启动并进入测试世界；实际日志确认 draw、reload_tactical、reload_empty
取得非零 camera rotation 并应用到 Camera 事件。此证据不代表轴向/观感已经验收。
用户反馈：检视交互尚未实装，其他效果没问题。本轮不新增检视按键/交互；
已有 `P901Actions.operation` 的 inspect 处理和资产轨道不等于正式检视交互已交付。
待逐项确认：异常中断/换枪回零、第三人称切换、非 BR51/非枪、死亡/重生、
界面返回、ADS 前后、结束后射击。不将源码检查、构建通过或笼统观感认可记作整个矩阵 PASS。
