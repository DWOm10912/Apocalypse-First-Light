# 枪械维护台多人测试 V1

日期：2026-09-08。Forge 47.4.22 / Minecraft 1.20.1 / Java 17.0.19，Windows，本地开发构建。仅新增 dev 测试及文档；未改变玩法、UI、相机、枪姿态或资源。

## 执行环境

- 已审计 `build.gradle`：真实任务为 `runServer`、`runClient`、`runGameTestServer`。已有 `src/dev/java`、GameTest 和 FakePlayer；dev 类从发布 jar 排除。
- Java：`C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`；`GRADLE_USER_HOME` 使用项目 `.gradle-user`。
- 逻辑测试：`gradlew.bat build runGameTestServer -I src/dev/maintenance-gametest.init.gradle --offline`。
- Dedicated：`gradlew.bat runServer -I src/dev/maintenance-multiplayer.init.gradle --offline`。
- 两端分别执行：`gradlew.bat runClient -I src/dev/maintenance-multiplayer.init.gradle -PmpRole=A --offline`，B 替换为 `-PmpRole=B`。先等服务端启动、A 启动进入后再启动 B，避免共享 ForgeGradle 准备输出被并发覆盖。
- 隔离目录 `build/maintenance-multiplayer-live/{server,clientA,clientB,control}`；测试服只绑定 `127.0.0.1:25576`，两玩家上限、独立平坦测试世界，测试目录内 `online-mode=false`、`enforce-secure-profile=false`。正式 `run`/发布配置未改。
- 测试 helper 通过 control 文件协调阶段并收集确认；放入/取回均由真实 Screen 的点击处理调用正常 Minecraft 网络按钮包，不通过共享文件转移物品或伪造客户端 BE。
- 重跑应使用新的隔离目录或备份并清理上轮 control 标记，避免旧阶段被客户端读取。所有测试进程已正常保存退出。

## 服务器逻辑结果

`MaintenanceMultiplayerTests` 的 A/B 是两个独立 FakePlayer，不是客户端覆盖证明：

- AFL_Test_A：`8ca95e40-5d79-4000-8000-000000000001`
- AFL_Test_B：`8ca95e40-5d79-4000-8000-000000000002`
- Bench 为 GameTest 模板绝对坐标，日志记录实际位置。

| 项目 | 结果与证据范围 |
| --- | --- |
| Owner 放入/原槽取回 | PASS；slot index 4，真实转移，UUID/slot 清理，完整 ItemStack 比较 |
| 状态保留 | P9 原生 `AflGunAmmo.ammoInMagazine=7`、SIGHT 红点 NBT、marker 全量比较 |
| Non-owner Take | PASS；B 获得，A 不获得；伪造 owner RETURN 被拒绝 |
| 双方回退 | PASS；原槽/首槽占用→其他快捷栏、快捷栏全满→主背包、36 格满→真实 ItemEntity；石头堆未覆盖，掉落 NBT 不变 |
| 竞争 | PASS；50 轮，每方向 25 轮，两个请求在同一服务端 tick 顺序执行，且每轮只有首请求成功 |
| 数量守恒 | 每轮统计 Bench + A 全库存 + B 全库存 + 附近 ItemEntity，`FINAL_GUN_COUNT=1`；未发现复制或丢失 |
| stale / invalid | PASS；重复 RETURN/TAKE、空台、非法 slot、伪造 owner、非当前 menu、距离超限、另一 Bench menu、另一维度 BE 拒绝；这是服务器 action 测试，不是网络模糊测试 |
| 双台隔离 | PASS；A 台 P9、B 台 BR51，单台返回不清空另一台 |
| 持久化 | PASS；完整 BE 序列化→清空→load，origin UUID/slot 和枪状态保留，B 随后可拿取；不将此项描述为双客户端跨进程重启测试 |
| 多部件拆台 | PASS；先拆上部再拆根，不重复掉落；P9 一把、维护台一个；既有矿工具测试仍在回归套件内 |
| UI 状态条件 | PASS；A 原槽虚影、B 无他人虚影且有 Take；另有归属同步等待顺序回归 |

构建及完整套件 19/19 PASS，日志：`build/maintenance-multiplayer-tests-final.log`。每轮 race 输出初始/最终数量、赢家和 Bench 最终状态。

## Dedicated 与真实双客户端

Dedicated `Done` 于 15:10:21；AFL 加载成功，无维护台 Dist.CLIENT/renderer 类加载崩溃。日志：`build/maintenance-mp-server.log`。

两独立真实 Minecraft 客户端经 TCP 连接同一 Dedicated Server：

- A：`f40f240d-7d71-3474-a43f-9d7f1229a25c`，日志 `build/maintenance-mp-client-a.log`。
- B：`3242eddd-c6a8-3787-8c51-0227b83ede3d`，日志 `build/maintenance-mp-client-b.log`。
- 共用 Bench `(0,100,0)`；不是同一集成客户端内的两个 FakePlayer。

| 场景 | 结果 |
| --- | --- |
| A 放 P9 | PASS；原槽真实为空、淡化图标可见，桌面真实 P9 可见 |
| B 查看同台 | PASS；无 A 的虚影，右侧独立 Take 按钮、同一桌面 P9 可见 |
| B 先拿 | PASS；B 库存得到 P9，A 虚影和双方桌面枪/入口实时消失，无需重开 |
| A 先取回 | PASS；A slot 4 恢复，B 按钮及双方桌面枪消失 |
| 双端竞争 | PASS；两端在同一协调阶段通过真实 Screen 点击发包，最终 A 获得一把、B 无枪、台为空；不声称网络到达严格同一 tick，确定性两顺序由上述50轮覆盖 |

截图已人工检查：`build/maintenance-multiplayer-live/clientA/screenshots/mp_A_stored_2.png`、`clientB/screenshots/mp_B_stored_2.png`、`clientA/screenshots/mp_A_empty_4.png`、`clientB/screenshots/mp_B_empty_8.png`（后3项同属该 live 根目录）。另有阶段6/10/12截图。B 世界画面较暗，但独立按钮和桌面有枪/无枪可辨；未调整视觉风格。截图中的新玩家教程/离线聊天提示属于测试客户端。

## 边界

- 测试证明本轮覆盖事务的防复制/不丢枪，不是对全部任意恶意网络包或无限操作序列的形式证明。
- 无真实双客户端环境限制；已实际执行。未测试跨机器延迟、丢包、断线重连、跨进程带枪重启。BE reload 是本轮持久化自动证据。
- 本轮未发现需修改生产逻辑的多人缺陷。发布不包含新增 dev helper。
- 两端既有 `AFL NATIVE GUN SMOKE` 仍记录独立工作目录缺少 `src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel` 的 `NoSuchFileException`；不是本轮多人测试失败，也不将该 smoke 计为通过。平坦世界旧 bunker 生成诊断亦有失败日志，未阻止 Dedicated 启动与测试连接；未在本轮扩展修复。
