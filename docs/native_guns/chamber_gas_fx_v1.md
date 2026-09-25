# AFL Chamber Gas FX V1.2

状态：V1.1 实机视频 `QQ20260924-215818-HD.mp4` 显示 Expansion Cloud 和 Residual Smoke 过浓、过大，遮挡第一人称画面。V1.2 仅调整粒子数量、透明度、尺寸、寿命与余烟间隔；沿用 V1.1 已确认的位置、方向、Jet 速度、cue、膛室状态和 emitter 架构。修改后仅静态检查及一次 `compileJava --offline` 验证，画面密度仍待用户实机复测。第三人称、世界弹壳接管及通用多种膛室状态适配未实现。

## 触发与状态

- `NativeChamberGasFx.bind` 在 GeckoLib action controller 采样前安装 custom instruction handler，包含 `NativeCameraBoneConsumer` 较早进行的动画采样。只启用 `animationAsset() == silverwood_12`，不改变 BR51/HR55/P9。
- 两段 reload 的 `timeline["0.583"] = "chamber_eject_fx"` 为独立视觉 cue。`NativeGunAnimations.cues` 仍只处理服务端 sound_effects；eject 声音仍为 0.543 秒。0.583 秒对应空换弹上壳离膛后约一帧、下壳开始离膛；tactical 下壳离膛后约一帧。V1 两膛同帧喷出，不根据 reload clip 推算膛数。
- cue 只在当前本地手持实例的第一人称 reload 中生效。使用 `NativeBreakActionChambers.read` 读取已同步且尚未在 mag-in 结算的膛室状态，再为每个 SPENT 膛创建 emitter。当前状态仍来自已有 ammo count：2=双 LIVE，1=上 LIVE/下 SPENT，0=双 SPENT；不存在独立 EMPTY 状态。FX 不新增/修改 gameplay 状态。
- 双 LIVE 不创建 emitter；上 LIVE/下 SPENT 只下膛；双 SPENT 两膛。inspect 无 cue 且 handler 拒绝非 reload；不改变抛壳动画、弹药数或装填时机。未来新增 EMPTY/其它膛室布局时须由相应状态适配提供 SPENT 列表，不能直接沿用当前二膛映射。
- Gecko 的一次性 keyframe 消费加本地 session 去重，避免 camera pass/hand pass 重复喷发。距离 cue 超过 2 tick 的过期事件不补播；缺失当前姿态时不延迟补喷。切枪、换世界、离开第一人称、打开 GUI 或动作改变时取消 emitter；暂停冻结。已生成的粒子独立留在世界中直到寿命结束。

## 坐标与骨骼

- 新增无几何 `upper_chamber_fx` / `lower_chamber_fx`，都挂在 `ammo_state`，pivot 分别 `[0,8.9,3.7]` / `[0,7.18,3.7]`，位于后膛口平面稍外侧。现有 shell 根节点会飞离膛口，现有 chamber 节点有缩放显隐，故均不用于持续 emitter。
- `NativeAnimatedWeaponRenderer` 的 per-bone layer 读取最终动画姿态并移到 pivot，调用 `NativeGunFx.firstPersonToWorld`。此 helper 与现有第一人称 casing 出生路径共用完全相同的矩阵：`inverse(worldView) * inverse(worldProjection) * sanitize(handProjection, worldProjection) * anchorPose`，再加相机世界位置。
- sanitizer 保留手部 FOV/XY，替换 clip-Z；位置转换同时包含 hip/ADS/reload 的最终姿态。V1 的两投影点相减已经抵消了平移，但其方向仍受 hand/world FOV 非等比缩放影响；V1.1 不再将此结果用于速度。
- 方向单独由 `NativeGunFx.firstPersonDirection` 计算：`inverse(worldView) * anchorPose` 的 3×3 列归一化去除轴长，`transformDirection`（w=0）后再次归一化。当前 anchor 祖先没有非均匀缩放/剪切，保留旋转与坐标朝向，排除平移及投影/FOV。旧 casing 的位置/速度逻辑不改变。
- 局部离膛主轴为 `+Z`，`+X` 与叉积轴构成垂直扰动平面。Jet/Cloud 起喷不再添加世界向上速度；第 3 tick 后才施加轻微浮力。两个膛口同父级、无独立旋转，主轴平行。
- 静态几何审查：FX anchors 与 primer/chamber 的 XY 中心一致，Z=3.7 位于原 chamber 几何末端 Z=3.67 外侧 0.03 模型单位，因此 V1.1 不改 anchor/source/GEO。首个 Jet 粒子在这个中心直接出生，不再加横向随机出生偏移。视频中的侧向烟团不足以证明锚点本身偏心；出生中心是否在实际画面贴合仍须用调试轴线验收。
- 原空壳动画首段 `+Z` 占归一化位移的 0.925（empty upper）、0.886（empty lower）、0.917（tactical lower）。其原有横/上斜抛使完整轨迹偏离法线 22.37°/27.65°/23.50°。Gas 与其离膛主轴同向，并非与斜抛轨迹逐帧重合；为保留 Claude 动画，本轮不改这些关键帧。上述为静态检查，不代表实机视觉验证。
- 余烟每次 spawn 重新读取当前 pose；不保留骨骼引用给粒子、不更新已出生粒子的父变换。世界光照使用 renderer 已有 packed light，在出生时缓存，粒子不再查询光照。

## 粒子与参数

注册 `apocalypse_firstlight:chamber_gas`：`AflParticles` / `AflParticleProviders` / `ChamberGasParticle.Provider`。资源描述 `assets/apocalypse_firstlight/particles/chamber_gas.json`；单图 `textures/particle/chamber_smoke_base.png`，64×64 RGBA，逐字节复制用户的 `chamber_smoke_base.png`（alpha 0–210）。

使用 `PARTICLE_SHEET_TRANSLUCENT`：正常 alpha blend、depth test、非 additive、非 fullbright。Jet RGB 0.94–0.99、Cloud 0.86–0.91、Residual 0.80–0.95；保留环境光，夜间不会强制发光。无碰撞、无 shader/VBO、无多帧 sprite。参数集中在 `NativeChamberGasFx` 的小型 Layer 常量与 `ChamberGasParticle` 阶段衰减中，无新增配置加载系统。

| 层 | 每膛数量 | 速度 block/tick | 寿命 tick | 初始半宽 block | alpha | drag（速度保留） | 最大尺寸倍率 |
|---|---:|---:|---:|---:|---:|---:|---:|
| A Jet Core | 3 | 0.21 ±5% | 4–7 | 0.034 ±15% | 0.55 ±5% | tick 1: 0.65 / tick 2: 0.48 / tick 3+: 0.72 | 2.8 |
| B Expansion Cloud | 3 | 0.14 ±5% | 6–10 | 0.045 ±15% | 0.22 ±5% | 前两 tick 0.52，随后 0.82 | 1.45 |
| C residual | 每次 1 | 轴向 0.00225 + 上浮 0.009 | 10–18 | 0.0245 ±15% | 0.138 × emitter 衰减 ±15% | 0.92 | 2.2 |

A/B 起喷方向为 0.95 轴向 + 局部垂直平面随机（单位圆盘内 A 最大 0.035，B 最大 0.085），归一化后乘速度。偏轴角上限约 2.11° / 5.11°，没有屏幕空间方向常量。A 初始轴向错位 `[0,0.025,0.05]` block，B 为 `[0,0.012,0.024]`；首粒子严格在中心，不把整团起点挪离膛口。每 tick 上浮加速度 A/B/C 为 0.002/0.0025/0.0015；A/B 从第 3 tick 起加入，C 仍逐 tick 上浮。C 横向随机 ±0.003 block/tick。

尺寸仍在前半寿命按 ease-out 膨胀。A 前 25% 寿命、B 前 15% 寿命维持初始 alpha，随后按 `1-smoothstep((t-hold)/(1-hold))` 衰减；C 保留 `(1-age/lifetime)^2`。随机初始 roll、每 tick 增加 0.018 rad。V1.2 Jet alpha 从 0.70 降至 0.55；Expansion alpha 从 0.38 降至 0.22（降 42.1%），最大尺寸从 `0.045×2.6=0.117` 降至 `0.045×1.45=0.06525` 格（降 44.2%），寿命从 8–14 缩至 6–10 tick。Residual alpha 从 0.23 降至 0.138（0.60×）、初始尺寸从 0.035 降至 0.0245（0.70×），持续 20 tick（1 秒），间隔从 2–5 拉至约 3–6 tick，alpha 仍乘 `1-emitterAge/20`；低 FPS 不追补积压 spawn。两膛瞬时总量从 18 降至 12；按当前 20 tick 日程，余烟每膛约 4 个，整次约 20 个。

## 开发轴线调试

默认关闭。在开发环境 JVM 参数中启用 `-Dafl.chamberGasAxisDebug=true`，重启开发客户端后，上膛显示青色轴线、下膛显示橙色轴线，长度 0.18 block。关闭时移除参数或设为 false。`FMLEnvironment.production` 为 true 时无条件禁用，即使设置该参数也不绘制。

从每帧当前 anchor 的中心出发，使用与粒子完全相同的世界方向，再反变换回当前手部 pass 绘制 `RenderType.lines()`，不使用上一帧缓存。这样能对照真实出生点及速度方向；debug 不生成粒子、不读写 chamber state。仅辅助检查，不能把轴线画出来等同于已完成实机验收。

## 资源合同与验收

源 `src/main/blockbench/silverwood_12_hybrid_claude_reload_presentation_v2.bbmodel` 与正式 `geo/silverwood_12.geo.json` 同步两空骨骼；源 effects 的 timeline script 与正式 `animations/silverwood_12.animation.json` 同步 cue。既有 bone transform、所有原关键帧/音效、Mesh/UV/贴图、AFLMESH binding、hand/muzzle anchors 均保留。ADS eye_relief=0.4、recoil、reload/chamber gameplay 和 Hybrid 核心不变。

V1.2 静态验收：source/GEO/animation/Mesh/纹理/注册/武器数据不变；原 LIVE/SPENT 选择、cue、session、方向与投影计算保持。只核对上述 Layer 参数和余烟间隔，运行一次 `.\gradlew.bat compileJava --offline`；本轮编译不代替用户实机验收。

用户实机验收：一发 SPENT 只下膛喷、两发 SPENT 双膛喷、inspect 不喷；开膛时烟从正确位置快速喷出再减速膨胀；枪移动时旧烟不跟随；余烟减少并自然停止；核对 hip/ADS 退出后的换弹构图、切枪/暂停、明暗场景和资源重载。V1 不保证单张柔软贴图达到参考视频多层湍流/PBR 的全部细节。
