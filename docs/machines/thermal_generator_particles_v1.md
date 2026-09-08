# 热力发电机 Particles V1

| 效果 | 原版粒子 | 正常工作频率 |
|---|---|---|
| 固体火焰 | SMALL_FLAME | 每 3 tick 1 个 |
| 腔内烟 | SMOKE | 每 9 tick 1 个，缩小并限制为 12 tick |
| 火星 | LAVA | 每 16 tick 50% 概率 1 个 |
| 固体排烟 | CAMPFIRE_COSY_SMOKE | 每 4 tick 1 个 |
| 液体排烟 | CAMPFIRE_COSY_SMOKE | 每 6 tick 1 个 |
| 熔岩活性 | LAVA | 每 12 tick 50% 概率 1 个 |

- 仅客户端 BE tick 生成，不依赖 renderer 帧率或随机 animateTick 批量喷发。读取同步的 VisualState / ActiveFuelSource；OFF、FULL、ERROR、NONE 不生成。已生成的原版粒子按原版生命周期消散。
- SOLID 使用统一火焰/烟/火星；已消耗到内部燃烧余量的最后一份固体仍是 SOLID，不依赖剩余库存猜测。LIQUID 不生成固体火焰；只有真实槽液为 lava 时点缀 LAVA，其他液体仅排烟。
- `energy/ThermalParticleAnchors.java` 由现有 `solid_flame_fx_anchor`、`solid_ember_fx_anchor`、`liquid_fx_anchor` 导出；排气点是现有 `stack_cap` 顶部中心上方 0.005 格。不修改源模型。
- NORTH 局部坐标绕方块中心按 FACING 转换，和 baked/BER 相同。燃烧点抬到燃料堆上方，水平扰动限制为 X ±0.05 / Z ±0.009；液体高度跟随真实液位，位置在玻璃后。
- 使用原版粒子实例，缩小内部粒子并覆盖 LAVA 自带的大幅初速度；无新粒子注册、Entity、存档或自定义大火焰模型。
- 篝火烟尺寸为原版 28%，寿命 80 tick，保持连续细烟而非大型烟团。两片原玻璃改由 BER 的正常受光透明 pass 绘制，保留深度测试但不写深度，避免原版后绘制的腔内粒子被整片透明窗挡住；机壳仍正常遮挡。不修改任何玻璃几何或贴图。
- 32 格客户端距离裁剪，暂停不生成，遵守玩家粒子“较少/最少”设置；各机器用位置错开周期。正常固体平均约 14.5 个直接生成粒子/秒，液体约 4.2 个/秒；原版 LAVA 自带的少量烟另计。
- `energy/ThermalParticleProfile.java` 的固体/液体排烟间隔是未来 soot 扩展点；当前固定正常烟量，没有积碳、故障或彩色光。模型、贴图、BER、FE/Fluid IO/Jade/JEI 均不改。

## 验证

- `build/thermal-particles-tests.log`：16/16 GameTest 通过，包含状态门控、频率上限、四向锚点变换及原机器/流体回归。
- `build/thermal-particles-client-fixed.log`：修正玻璃深度与烟柱尺寸后，实体客户端十种场景运行完成，构建通过；煤炭腔内火焰及细烟截图已检查，16 台同时运行完成（快照约 586 个全场景原版粒子，无量化 FPS 基准）。测试过程中有手动视角操作，四向数学检查由 GameTest 覆盖。
- 用户已确认“这版目前可以了”，保留当前效果，不继续扩展。OFF/FULL 停止新生成；已经离开烟口的原版烟自然消散。
- 最终收尾 `build/thermal-particles-final-tests.log`：BUILD SUCCESSFUL，16/16 GameTest 通过；此后不再启动客户端或调整视觉参数。
- 隔离客户端仍有既有 Native Gun smoke 相对源码路径报错，非本轮粒子故障；没有把该检查计为通过。
