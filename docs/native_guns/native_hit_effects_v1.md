# Native Gun 命中粒子 V1

## 数据、命中与同步

可选 `presentation.hit_effect`：缺省、null、空字符串均为 NONE。当前通用 preset 为 `dizzy_stars`，C.A.T 仅通过 cat.json 选择；其他枪 JSON 不变。未知 preset 沿用 gun data 校验拒绝/last-good 机制。

NativeGunShot 完全未修改。NativeGunActions 在既有 execute 返回后读取 `Hit.entity()!=null`，选择 preset 或空字符串，复用实际 `Hit.point()`。MISS、BLOCK HIT 的 entity 为 null，不触发。判据是服务端确认实体几何命中，不是 hurt 返回值：无敌帧等拒绝伤害时仍可有视觉反馈，不额外施加伤害/眩晕状态。

既有 NativeShotS2CPacket / NativeShotFxS2CPacket 末尾增加最大64字符 preset ID。射手走 PLAYER，旁观者沿用 TRACKING_ENTITY（射手）广播范围，不新增包类型/频道。客户端每收到确认包播放一次，不依赖手持物或枪口快照，不重新 raycast。普通枪 preset 为空立即返回。协议 **28**，客户端与服务端需同时更新。

## 素材和参数

原文件名、原字节复制至 `src/main/resources/assets/apocalypse_firstlight/textures/particle/`：

| 文件 | 粒子 ID（apocalypse_firstlight:） | 每实例权重 |
| --- | --- | --- |
| cat_yellow_star.png | hit_yellow_star | 50% |
| cat_bule_star.png | hit_blue_star | 30% |
| cat_dizzy.png | hit_dizzy | 20% |

每张128×16横向8帧。particles/hit_*.json 各引用一个原strip，没有 .mcmeta 全局循环动画。共用 StripHitParticle.Provider / StripHitParticle，以透明粒子图集绘制世界空间 billboard。

U 区间 `[frame*2,(frame+1)*2]` 使用 sprite 0..16坐标，V 为完整高度；`frame=min(7,floor(age*8/lifetime))`。各实例从0单向到7，独立计时不循环；寿命8～12 tick，避免6 tick跳过素材帧。Minecraft 无动画元数据的 EMPTY frame size 保留整张128×16图。

- 每次实体命中随机3～6个，独立选型，不固定组合；不限制旋涡只能1个。
- quadSize 0.10～0.18（Vanilla半边长，完整宽约0.20～0.36格）。
- 出生偏移半径0.05～0.12格，均匀球面方向。
- X/Z初速各[-0.0175,0.0175)，Y初速[0.012,0.035)格/tick。
- 初始旋转0～2π，角速度[-0.06,0.06)弧度/tick，摩擦0.96，重力0，无物理碰撞。
- 前半程alpha=1，后半程 `clamp(2*(lifetime-age)/lifetime,0,1)`，结束销毁。
- 无永久实体/光源/额外shader，遵守Vanilla粒子设置和距离裁剪。600 RPM、最长0.6秒寿命下单射手存活量级约36个上限；没有额外节流吞命中。

## 文件和扩展

NativeHitEffect 定义通用preset和实体命中gating；NativeGunPresentation、NativeGunDefinition、NativeGunData 解析可选字段；NativeGunActions/AflNetwork 传递视觉附加值。客户端 NativeHitEffects 分发preset并生成粒子，StripHitParticle/HitParticleAnimation 共用动画逻辑，AflParticles/AflParticleProviders 注册三个类型。

后续 blood 只需新增通用 NativeHitEffect preset、NativeHitEffects生成策略和资源/provider（同类strip可复用粒子类），再由JSON选择；不需要修改NativeGunShot或添加weapon-ID判断。本轮没有blood，也没有多preset数组。

## 验证范围

compileJava/processResources；独立NativeHitEffectTest覆盖默认、实体/非实体gating、权重、全部8帧、非循环、fade、图片尺寸。静态检查cat.json除hit_effect外语义不变、NativeGunShot无差异、原图SHA256一致。未进行图形/联机验收。

用户测试：C.A.T分别射击实体/墙/空气，检查仅实体出粒子；AUTO持续命中观察数量/淡出；普通枪对照；旁观者确认命中位置的动画。联机双方需要协议28版本。

无CAT weapon-ID特判；未修改hitscan/damage/headshot/range/spread、后坐、噪声、耳鸣、弹药、ADS、动画、FireMode、维护台、idle sound或ARGB trail。未runClient/clean/commit/push。
