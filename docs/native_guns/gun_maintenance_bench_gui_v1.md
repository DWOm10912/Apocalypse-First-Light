# 枪械维护台 GUI V1

状态：以下为已废弃的 V1 历史记录，不代表当前界面。大面板、GUI 维护垫副本和 GUI 枪预览已移除，当前规则见 [俯视交互 V2](gun_maintenance_bench_topdown_interaction_v2.md)。真实槽、存档、取回与单次掉落逻辑保留；V1 的 bind-pose magazine 快照问题已在 V2 修复。

| 项目 | 当前规则 |
|---|---|
| GUI | 320×220；极简标题、原模型维护垫 3D 场景、底部真实 9 格快捷栏；世界约 53% 压暗 |
| 开启 | 任意合法 part 转发主块；空手、普通物品、Native 枪均可，不自动放入手持枪 |
| 真实槽 | 主块 `GunMaintenanceBenchBlockEntity.maintenanceGunSlot`，容量 1，仅正式 AFL NativeGunItem；无 ghost inventory |
| 放入 | 空台点击快捷栏枪，服务器再次验证 Menu、距离、索引、槽空及物品类型，移动完整 ItemStack，原快捷栏清空 |
| 持久化 | 保存完整 ItemStack/NBT、originHotbarSlot、originPlayerUUID；关闭/走远/断线不返还 |
| 取回 | 点击中央枪；原玩家优先原空格，其后当前玩家快捷栏空格→主背包空格→脚下掉落；绝不覆盖物品 |
| 所有权 | 普通世界容器，任何玩家可取；来源 UUID 只影响返回格，不是权限锁 |
| 同步 | 原版 Menu button 请求、真实只读 Slot 同步及 BE 更新包；拒绝普通拖拽、shift-click 和伪造索引 |
| 世界 | 仅主块 BER 绘制真实槽物品，四向旋转；空台跳过枪械绘制；无 ItemEntity、手臂或射击/换弹控制器 |
| 静态资产 | 复用 GeckoLib 模型缓存，缓存独立 bind-pose 骨架及原 cube，不修改共享动画状态；重载按新模型缓存重建 |
| 视图 | `MaintenanceViewProfile` 集中配置 scale、三轴 offset/rotation、中心点与点击范围；P9 scale 0.5，BR51 0.22，长轴斜放 10° |
| 附件 | GUI/BER 同读槽内真实 ItemStack，经 NativeSightRendering 绘制现有红点附件 |
| 拆除 | 任意 part 拆除先清空主槽再掉枪，联动回调不重复；工作台自身沿用原工具/掉落规则 |
| 防复制 | 服务器线程串行验证，已取空的后续请求失败；失效 Menu 拒绝；清槽与返还不由客户端执行 |

本轮不实现配件 UI、修理、耐久、电池、照明、制作、FE 或精密制造台 GUI；以后配件编辑继续以同一真实槽作为真值源。

## 验证

2026-09-08：`build runGameTestServer --offline` 通过，11/11 测试通过（`build/maintenance-tests-final.log`）。覆盖 P9/BR51 完整 NBT 往返、原格/快捷栏/主背包/掉落回退、重复请求、两位 FakePlayer 交错取放、上层拆除单次掉枪，以及既有工作台工具与结构回归。

独立图形客户端实际点击 GUI，确认 P9（带红点）与 BR51 从快捷栏转移、中央点击取回、关闭后桌面保留；已检查两枪 GUI 与桌面截图。红色/散架预览已修复：使用 `NO_OVERLAY`，每个 cube 独立 push/pop 变换矩阵。日志 `build/maintenance-client-fixed.log`，截图 `build/thermal-fluid-client/screenshots/maintenance_{p9,br51,world_p9,world_br51}.png`。

关闭客户端保存后再次启动，确认 P9 与红点仍在台内并能重开 GUI：`build/maintenance-reload.log`。BR51 完整 NBT 已做服务端往返，但未另跑 BR51 磁盘重启；真实双客户端并发、四向桌面视觉和跨区块卸载未单独验收。预览不绘制手臂，背后世界原有第一人称手臂/HUD 不由本界面隐藏。

测试客户端隔离目录使已有 Native Gun Smoke 的相对源模型路径查找失败（`NoSuchFileException`）；不把该检查计为通过，不影响上述专用维护台测试结果。未修改枪械 gameplay 或源资产。
