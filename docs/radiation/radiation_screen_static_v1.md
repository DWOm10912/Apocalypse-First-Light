# Radiation Screen Static V1

## 实现与范围

Java 程序生成的客户端稀疏灰白静电，非后处理 Shader，无外部 PNG。`radiation/client/RadiationStaticTextures.java` 在资源 reload 时生成并注册 8 张 64×64 DynamicTexture；固定种子 `0xAF1201 + frame * 104729`。每帧 160 次颗粒投放，目标约 3–8% 非透明像素；灰度 150–235，像素 alpha 20–80。以 1×1 为主，混合少量 2×1、1×2、2×2 和 3–5px 短横线。

TextureManager 使用固定 8 个 `apocalypse_firstlight:dynamic/radiation_static_N` ID；重载前 release，关闭旧 DynamicTexture/NativeImage，异常时释放已注册资源，游戏关闭由 TextureManager 释放。纹理以 GL_REPEAT 平铺，一个全屏 quad；64×64 对应 128×128 GUI px，基础颗粒为 2 GUI px。

## 数据、曲线与时序

复用 `RadiationSyncPacket → RadiationAtmosphereClient.setTargetRadiation` 的环境 `finalRadiation`，通过只读 `screenRadiation()` 访问。不依赖手持计数器，不新增同步包、游戏数值或第二套辐射系统。该来源包含建筑屏蔽后的环境辐射，不等同于仪器在安全区显示的残留/携带物读数。Geiger 音频按区域选声，视觉按环境读数变化，二者互不调用。

正式自然辐射范围为 1–10、10–60、60–240 RU/h。视觉归一化分段线性映射 `(0,0)、(10,0.30)、(60,0.70)、(240,1)`，超过 240 clamp 到 1。避免全程除以 240 导致低区几乎完全不可见；不修改既有雾效 /10 曲线。

每客户端 END tick：`display += (target-display)*0.25`，差值小于 0.001 时归位。约 8 tick/0.4 秒完成 90% 变化；alpha 为 `min(0.25, display²*0.20*INTENSITY)`，再乘纹理像素 alpha。默认 INTENSITY=1；归零后无 draw/blend。跨世界/退出清零，来源换世界或超过 40 game tick 未同步时目标归零。

每 3 tick 换一帧（约 6.7Hz），8 帧循环；不依赖 render FPS。暂停冻结。无每帧 Random、NativeImage、像素遍历、纹理 upload 或集合创建。

## 干扰与可读性

display>0.45 时每 tick 以 display×0.02 概率产生一条 2 GUI px 高、20–80% 屏宽的灰白横线，持续 2 tick，至少 20 tick 冷却；alpha≤0.025。无全屏脉冲、RGB 分离、VHS 或高亮闪白。

Forge registerBelowAll 注册，在 HUD 前绘制；恢复 blend/depth/shader/texture/color 状态。主菜单、加载、暂停、任意 Screen（包括容器）、F1 隐藏 HUD、死亡时不绘制。维护台若使用世界交互 HUD，仍在静电上方；若打开 Screen，静电隐藏。

无统一成熟 Client Config，本版关键参数集中在 `RadiationScreenStaticOverlay` 的 ENABLED、INTENSITY、MAX_ALPHA、SMOOTHING、FRAME_TICKS、TILE_GUI_SIZE 常量。不新增配置框架。

低覆盖率、低透明度和低频固定帧循环旨在降低视频编码压力；不等于完成录制后二压验收。Shader、夜间、1080p/1440p、GUI scale、维护台和 60/120/240 FPS 对比须按实际证据报告。

## 验证记录

- 构建：`build/radiation-static-build.log`，主体编译通过。
- 专用服务器：`build/radiation-static-server.log`，正常启动并完成已有 28/28 GameTest，BUILD SUCCESSFUL；无客户端类加载崩溃。这些既有测试不是静电视觉断言。
- 客户端 fixture：`src/dev/radiation-static-client.init.gradle`、`RadiationStaticProbe.java`；只注入客户端视觉同步值，不改世界辐射或玩家物品。
- 客户端：`build/radiation-static-client.log`，fixture PASS：8 帧不同、3–8% 覆盖率、灰度/像素 alpha 范围、归一化和 alpha 曲线、Minecraft.reloadResourcePacks 重建后 8 帧可用。不是实际按下 F3+T，也未做长时间 GPU 泄漏测量。
- 截图：`build/thermal-fluid-client/screenshots/radiation_static_{zero,low,medium,high,reloaded,fadeout}.png`，2560×1417 窗口，已检查六张；普通 HUD 与 BR51 清晰，高强度颗粒仍克制，无整屏发白，重载无 missing texture。测试注入 0/10/45/240 RU/h，非真实辐射村庄行走验收。
- 日志有旧 `[AFL NATIVE GUN SMOKE]` 的 Blockbench 相对路径缺失错误，与静电 fixture 独立；不把整份客户端日志宣称为零错误。
- 待验收：Shader/夜间、维护台、真实辐射村庄过渡、1080p/1440p 多 GUI scale、60/120/240 FPS 对比、录制后二压、GC/GPU 性能分析、反复重载泄漏。已实现相应低开销/渲染顺序约束，不用代码审查代替这些实机结论。
