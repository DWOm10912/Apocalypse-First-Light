# Camera / Layout / Transition Polish V1

后续摆放规则：P9/BR51 及默认 profile 改为 Y/Z 均 −90°，与维护垫长边平行。静态基础枪体的纵向包围边界中点自动居中，按模型缓存；附件不参与计算，避免装卸时整枪移动。逐枪 scale/offset 保留，热点共用同一 transform。构建日志 `build/maintenance-parallel-build.log`；本次未做新的实机视觉验收。

用户确认当前版本并要求结束，停止追加测试。

- 构图：73° 斜俯视，目标上方 0.98 格，稳定 FOV 75°；相较旧版 67.5°/0.82 格，提高并增加镜头到目标距离。
- 过渡：进入 0.22 秒、退出 0.16 秒，smoothstep；Camera setup 后与原版 pose 混合，FOV 平滑衔接。UI 到位才启用；退出立即取消服务端菜单操作，保留 Screen 输入屏障直到镜头结束。
- 布局：删除 `vise` 与 `parts_tray` 共 55 cubes 和对应分组；维护垫宽 25.6、深 9.8 模型单位，中心与高度不变。392 cubes；贴图不改，不增加 BER。
- 源：`src/main/blockbench/gun_maintenance_bench.bbmodel`；同步完整 Java、四分片与 Geo。迁移脚本 `tools/polish-maintenance-layout.mjs`，验证/导出沿用工作台工具。
- 点击：快捷栏枪、origin placeholder、non-owner Take 使用 Vanilla Button Click，沿用候选/Context 声音规则；无新音频。附件数据、服务端事务、51 tick 延迟及逐枪 MaintenanceViewProfile 不改。
- 后续形状修正：`StaticWorkstationBlock.createShapes(false)` 删除旧虎钳 AABB `(24.8,16.5,0.05)–(30,20.1,5.5)`，加入扩大维护垫的薄层 `(3.8,16.5,2.4)–(29.4,16.62,12.2)`。零件盒原先无独立形状。碰撞与选取共用该形状，继续分四格并按四向旋转；精密制造台分支不变。
- 已验证：资源一致性/共面重叠检查；构建；隔离客户端 P9/BR51 摆放、取回、重开、最终镜头坐标/角度、正常退出；两枪截图检查。
- 未完成：追加四向测试被用户叫停；全部中断场景、完整附件声音/热点回归及真实双客户端未在本轮重新验证。未新增手臂动画。挖掘标签/方块逻辑未改，本轮未重跑生存掉落测试。

主报告：[俯视交互 V2](gun_maintenance_bench_topdown_interaction_v2.md)。日志：`build/maintenance-camera-layout-client.log`；中止日志：`build/maintenance-camera-facings.log`。

形状补漏后 `build runGameTestServer --offline` 成功，既有 25 项回归通过（`build/maintenance-layout-shape-tests.log`，包含工作台挖掘掉落回归）。本次未重启图形客户端确认新轮廓，也未增加专门的形状逐点断言。
