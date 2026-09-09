# Native Bullet Glass Penetration V1

服务器共享 `NativeGunShot.trace` 使用连续最近命中解析；P9、BR51 和未来 Native Gun 共用，不按枪型特判。实体优先级由实际交点距离决定；等距仍以方块阻挡为准。实体命中后结束，不穿透实体。

- `BulletBlockInteraction` 定义 `STOP` / `BREAK_AND_PASS`；仅 `#apocalypse_firstlight:bullet_breakable_glass` 可击碎并继续。
- 数据：`src/main/resources/data/apocalypse_firstlight/tags/blocks/bullet_breakable_glass.json`，35 项：普通玻璃/板、16 色玻璃/板、遮光玻璃。未发现遮光玻璃已有防弹定位，因此纳入。通过标准 datapack tag reload 扩展，不按透明度或渲染类型识别。
- 成功破坏后沿原方向步进 epsilon **0.01 blocks**；固定原最大射程终点，剩余射程为当前点到终点的距离，步进不赠送射程。
- 一发最多击碎 **16** 块；之后实体/空气仍正常解析，第 17 块玻璃视为阻挡。无普通玩家警告。
- 服务器 `destroyBlock(pos,false,player)` 不掉落玻璃，沿用原版破碎事件的声音、粒子与状态同步，不额外重复发送 break FX。
- 检查玩家建造权限、旁观者、`level.mayInteract`（包括出生点保护）、世界边界及可取消的 Forge `BlockEvent.BreakEvent`。本模组禁止持枪挖掘的监听器只豁免当前服务器玻璃事件对象；其他监听器仍可取消。取消、状态变化或破坏失败均停止，不让未碎玻璃被穿过。不使用只针对生物破坏的 mobGriefing 开关控制玩家枪击；第三方保护模组实测待验收。
- 射线以已加载区块视图执行，未加载方块视为阻挡边界，不主动加载区块。
- 无玻璃额外减伤、射程损耗、散布、偏折或后坐力变更；原距离伤害衰减与爆头逻辑保留。服务器实际扣弹、伤害、shotId 和网络包不变。
- 既有射击同步收到最终 `Hit.point`，连续 tracer 从客户端冻结枪口指向最终停止点，不截在已击碎的玻璃处。未新增 projectile entity。
- 木头、金属、实体穿透、跳弹和材料能量模型均未实现；后续仅扩展块交互策略，不把 V2 描述为当前功能。

## 验证

自动测试源：`src/dev/java/com/antaurora/apofirstlight/dev/BulletGlassGameTests.java`。
最终验证：`build/glass-final-verification.log`，`build runGameTestServer -I src/dev/glass-gametest.init.gradle --offline` 成功，隔离命名空间内 **1/1 必需 GameTest 通过**（单用例包含多项断言）。覆盖 35 种标签方块、独立板正面/连接板斜角、16 层及第 17 层阻挡、玻璃后实体/墙/空气终点、实体在玻璃前、射程不重置、P9/BR51 同发击碎两层并伤害且无额外减伤、无掉落、持枪事件豁免、事件取消、冒险限制、含水板保留水并继续、未加载区块边界不加载。

早期全命名空间运行 `build/glass-tests.log` 为 68 项中 2 项失败：玻璃伤害夹具用孤立板柱被正常散布绕过（实际已伤害僵尸），另有既有 `nativenoiseflatdistances` 失败，未改动噪声系统且不宣称全套回归通过。隔离夹具随后修正为连接窗并清理失败遗留实体；以上最终隔离结果取代早期玻璃结果。数据包热重载未另作运行时测试，标签查询无结果缓存，沿用 Forge 标签重载机制。

P9/BR51 回归范围为服务器共享射线与实际伤害，不包括所有附件组合、开火输入和图形效果；抑制器/瞄具没有玻璃专用分支。Tracer final endpoint 的 PASS 仅指返回端点与现有同步调用静态核对，不代表客户端截图验收。
图形客户端、多人同步及第三方 claim 模组尚未实测；不得把服务器测试当作多人/视觉验收。
