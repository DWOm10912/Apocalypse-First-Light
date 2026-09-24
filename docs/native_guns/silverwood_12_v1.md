# Silverwood 12 V1 Runtime 接入

状态：代码与正式资源已接入；仅 `compileJava --offline` 为本轮允许的编译检查。游戏内手持、ADS 像素级对准、八弹丸伤害/散布、动画与声音、双膛显示、切枪取消和 `PUT_AWAY_END_FLASH_TEST` 均待用户实机验收；不把静态检查或编译当作实机通过。

## 正式绑定

- 枪：`apocalypse_firstlight:silverwood_12`，`AflItems.SILVERWOOD_12`，`ConfiguredNativeGunItem`；弹药/空壳：`apocalypse_firstlight:12_gauge_round` / `apocalypse_firstlight:12_gauge_casing`，均复用已注册普通 Item。
- 调参：`src/main/resources/data/apocalypse_firstlight/native_guns/silverwood_12.json`。武器为 SHOTGUN、BREAK_ACTION、SEMI，容量2，6 tick/发；每发消耗一发霰弹，8 颗独立圆锥抽样与追踪。每颗基础4.5、爆头1.25×、12格起衰减、28格标称有效、48格最大、最低0.35×。同目标逐颗算伤害后一次结算，避免原版同 tick 受伤保护吞掉后续弹丸。枪声噪声104格并启用耳鸣；不新建弹丸实体。
- 膛室真值源：`NativeGunAmmo.read`，2=上下 LIVE，1=上 LIVE/下 SPENT，0=上下 SPENT。第一枪下膛、第二枪上膛，0发复用通用 dry fire。射击不自动抽壳/抛物品；模型动画在换弹时视觉抛壳。
- 换弹：原生会话及中断规则；空膛 `reload_empty` 60 tick，在第43 tick（第二发于动画2.1167秒落位后）结算；一发 `reload_tactical` 51 tick，在第32 tick（下膛新弹于1.6秒落位后）结算。结算时重新计算真实背包储备，沿用创造无限备弹规则；结束前 HUD 可能已显示新计数，但动画关键帧仍拥有换弹期 shell scale/轨迹的控制权。
- 腰射/ADS：锥形半角2.25°/1.65°，继续叠加现有姿态倍率。ADS进入/退出0.22秒，FOV0.92；配置中的瞄准参考点 `[0,9.88,3.5]` 沿模型顶肋/前珠的 x=0、y≈9.88 轴；hip/ADS roll 均为0，满 ADS 时抑制此枪视觉 sway。只修正模型投影，视角方向仍作为服务端弹道起点与方向；未改前珠几何。精确像素重合待游戏内确认。
- 后坐 V1：垂直2.2°–2.8°，水平±0.10°–0.35°，上限垂直8°/水平1.8°；枪模后坐 roll=0。无额外 shoot-camera 后坐。
- HUD 沿用 NativeGunHud，只显示装弹数2/1/0及真实备弹；Tooltip 仍沿用共通样式，霰弹伤害显示 `4.5 × 8` 并增加此折枪容量2行。创造模式枪械/弹药标签已有枪与12 Gauge 实弹，空壳保持不入标签。

## 资源与控制器

| 资源 | 路径 |
| --- | --- |
| 几何 | `src/main/resources/assets/apocalypse_firstlight/geo/silverwood_12.geo.json` |
| 七动画 | `src/main/resources/assets/apocalypse_firstlight/animations/silverwood_12.animation.json` |
| 贴图 | `src/main/resources/assets/apocalypse_firstlight/textures/item/silverwood_12.png` |
| HUD / 背包 | `src/main/resources/assets/apocalypse_firstlight/textures/gui/gun/silverwood_12_hud.png` / `textures/item/silverwood_12_inventory.png` |
| Item 模型 | `src/main/resources/assets/apocalypse_firstlight/models/item/silverwood_12.json` / `silverwood_12_in_hand.json` |
| 六完整 Ogg | `src/main/resources/assets/apocalypse_firstlight/sounds/weapons/silverwood_12/{fire,reload_empty,reload_tactical,draw,put_away,inspect}.ogg` |

`static_idle` 作基线循环；`reload_empty`、`reload_tactical`、`draw`、`put_away`、`shoot`、`inspect` 均 `PLAY_ONCE`。五个非开火动作从动画0秒 sound cue 播放完整 Ogg；fire 只从服务端 accepted shot 播放，shoot 无重复声标记。摄像机继续使用现有旋转-only consumer，shoot 不使用动画 camera。Renderer 按装弹数隐藏/显示 `live_shell_upper/lower` 与 `spent_shell_upper/lower`；reload 期间解除运行时隐藏，由原动画 scale/运动关键帧控制。导出的 `source_only_reference` 只在 runtime 隐藏，原始 geo/animation/贴图字节不改。枪口上/下 anchor 由开火前弹数和 shoot 状态选择；无 ejection anchor，避免自动抛壳 FX。

本轮未修改任何 Blockbench 可编辑源、音效混音脚本、其他枪数值、游戏外系统；未做配方、游戏资源导出、游戏内测试或提交推送。

2026-09-24 补充：临时 `silverwood_12_mesh_test` 物品和运行资源已移除。正式 `silverwood_12` 继续使用本页列出的原模型；Hybrid Mesh 美术源仍保留在 `src/main/blockbench/silverwood_12_astra_medium_final_benchmark.bbmodel`，尚未接入正式枪。
