# Native Gun 每发动画重触发与最后一发挂机 V1

P9-01 与 BR51-01 的成功射击仍由服务端 `NativeGunActions.request` 统一验证。只有 `NativeGunAmmo.consumeOne` 成功后才产生动画、弹道和射击通知；客户端不自行猜测弹药或最后一发。

GeckoLib 4.7.4 的同名 trigger 在控制器仍播放时不会自行回到 0 秒。共享射击路径因此在每次成功射击时，使用 GeckoLib 原生 `stopTriggeredAnim` 后立即 `triggerAnim`，强制当前 `shoot` 从机械后座起点重新开始。已结束且达到武器 `fireIntervalTicks` 的旧射击 session 可以被新射击替换；reload、inspect、draw 等动作锁不受影响。

最后一发由扣弹前后严格的 `ammo 1 → 0` 判定。P9 与 BR51 的 shoot 都在第一个服务端 tick 内到达 slide/bolt 后座关键姿态；共享策略在该 tick 后停止普通 shoot，让弹药驱动的现有 `empty_idle`（P9）或 `static_bolt_caught`（BR51）立即接管。不会等待 0.6 秒或 0.8333 秒的完整 shoot，也不会先复进再跳回挂机。

本修复不改变射速、伤害、后坐、声音、ADS、第一人称变换、手臂、Rig、锚点或 Artist 动画资源。自动检查覆盖精确射击间隔的第二发、权威 1→0 判定、stop→trigger 顺序、首 tick 后座关键帧与两把枪的空仓映射；最终机械连续性仍需客户端实机验收。
