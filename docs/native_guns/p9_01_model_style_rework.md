# P9-01 资产风格统一

> 此页记录美术重构当轮结果。后续动画、骨架新增驱动、80% 背包图缩放及静态右腕校正以 [动画重做 V1](p9_01_animation_rebuild_v1.md) 为准；下文“8180 keys/无 draw/put_away/256×256 背包图”与该轮基线验证脚本是历史记录，不再描述当前资产。原枪体美术保持。

本轮以 BR51-01 的深灰块面、克制边缘明暗和清晰机械分层为参考，保留 P9-01 的制式手枪比例与轮廓。没有复制 BR51 的几何或贴图，也没有使用图像生成。

## 资产变化

| 项目 | 结果 |
| --- | --- |
| 套筒 | 增加前部操作纹、侧面浅层次、底部接缝及后盖板；保留原后部操作纹与外轮廓 |
| 枪口 | 前端面分为上下桥与两侧面，露出原有独立枪管冠口；枪管轴线、枪口锚点不变 |
| 机匣/护圈 | 细化防尘罩、分解控制件、销轴及护圈侧边；不增加战术附件 |
| 握把 | 防滑面、面板边界、拇指区及前后握持纹；保留原倾角与握持端 |
| 弹匣 | 压筋、观察孔贴图、底板接缝/锁片；全部随原 magazine 骨骼运动 |
| 瞄具 | 补齐低矮燕尾底座；不改变原准星顶、照门开口或瞄线 |
| 贴图 | 从 128×128 大色块改为 256×256 分面图集；仍为 128×128 逻辑 UV，与 BR51 的物理/逻辑分辨率关系一致 |
| 材质 | 灰黑聚合物、略亮套筒、少量冷灰金属；面差、细边高光与克制防滑像素，不做脏污噪声 |
| 规模 | 源 cube 81 → 161；运行时 77 → 157；4 个参考手臂仍仅供预览 |
| 背包图 | 用新模型在 Blockbench 渲染 256×256 透明图，四边留空；沿用既有 inventory 资源引用，HUD 剪影不改 |

## 同步与边界

- 源：`src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel`。
- 运行时：`src/main/resources/assets/apocalypse_firstlight/geo/p9_01.geo.json`。
- 贴图：同 assets 目录的 `textures/item/p9_01.png`、`textures/item/p9_01_inventory.png`；嵌入源图集与运行时 PNG 字节一致，纹理 UUID 不变。
- 全部 24 个源组、20 个导出骨骼、8180 个源关键帧及所有 Display 保留；锚点位置修正 **0**。原动画 JSON 未修改。
- Java、gameplay JSON、注册、ADS/后坐力/散布、声音、抛壳以及 BR51 资产均未修改。
- `tools/rework-p9-style.mjs --write` 是已执行的一次性美术生成脚本，会拒绝重复执行；后续正常编辑以 bbmodel 为准，使用 `tools/export-native-gun.mjs --write` 同步。
- 两个现有导出器改为按实际源 cube 数校验，而非锁死旧版 77 个。`tools/verify-p9-01.ps1` 同步新图集分辨率及当前 inventory 引用。

## 验证（2026-09-08）

| 检查 | 结果与证据范围 |
| --- | --- |
| 自动资产回归 | PASS：`node tools/verify-p9-style.mjs`，对本轮 `build/p9-style-before/` 快照精确比较骨架/锚点/关键帧/Display/参考手臂；检查正尺寸、UV 范围与绑定及运行时导出 |
| 常规导出校验 | PASS：`node tools/export-native-gun.mjs --check`；`tools/verify-p9-01.ps1` 验证 157 cubes、20 bones、8180 keys 和原声音资源 |
| Blockbench | 直接检查新模型三分之四视角、枪口、面板及透明背包渲染；这些不是实机动画验收 |
| 构建 | PASS：compileJava/processResources/build 离线构建；更新背包图后再次 build，`p9-style-final-build.log`，11 秒成功 |
| 图形客户端 | 已实际启动 `runClient --offline -PaflWithoutTacz` 并进入 test 单人世界；直接观察到新版 HIP、手臂、HUD 和背包图标，非仅进程存活 |
| ADS / 射击 / 普通及空仓换弹 | 用户在新版客户端完成所请求回归，并回复“没有问题”；记录为用户实机通过，不声称逐帧截图全部由代理获取 |
| 切枪 / F5 持枪 / 地面掉落 | 同一次用户实机回归确认通过 |
| draw / put_away / inspect | P9 无独立具名 draw、put_away、inspect 动画；切枪使用现有装备流程，未新增或冒充这些动画 |
| diff | `git diff --check` 通过；不提交、不推送 |

本轮未报告剩余视觉问题。多人、额外皮肤/资源包组合没有新增专项测试。枪械主数据页无需改动：本轮没有改变任何玩法数值。
