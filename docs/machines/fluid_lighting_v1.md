# 流体光照 V1

- `fluid/FluidLighting.java` 统一读取 FluidStack 的 FluidType light level，不按 Registry ID 特判熔岩。
- `client/FluidRenderHelper.java` 仅将发光流体的顶点 lightmap 设为 FULL_BRIGHT；外框、玻璃保留原渲染。公共 helper 的机器观察窗及储罐物品流体层同样适用。
- `fluid_light` 方块状态同步世界光照：储罐上限 9，管道上限 5；公式 `max(1, round(light × maximum / 15))`，空/亮度 0 则为 0。
- 储罐每 tick 根据成员实际液体切片校验，仅等级改变写状态；拓扑重建或重新加载后重新核对。不改库存。
- 管道亮度跟随既有 20 tick 内容保持，而非 10 tick 流动保持；流动/堵塞及 3999/4000 mB 波动不切换光照。到期清除，定时检查处理重新加载后的旧亮度；不强载区块。
- 使用原版标准光照，没有彩色光、粒子或整体外壳 full-bright。

## 验证

- `build/fluid-light-tests.log`：构建与 15/15 GameTest 通过，包含新增 `fluid_lighting`：熔岩 9/5 映射、水不发光、储罐真实光照传播、空罐熄灭、管道保持及过期后实际光照清除；既有 14 项机器与流体回归通过。
- 未进行本轮夜间图形客户端目测，不把编译/服务端光照测试当作视觉验收。
