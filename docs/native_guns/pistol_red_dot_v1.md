# 手枪微型红点 V1

## 配件与属性速查

统一目录和后续属性模板见 [配件总表](attachments/README.md)。

| 项目 | 当前值 / 行为 |
| --- | --- |
| 名称 / ID | 手枪微型红点瞄具 / `apocalypse_firstlight:pistol_red_dot` |
| 槽位 / 当前兼容 | `SIGHT` / P9-01 |
| 主要效果 | ADS 改为红点光学轴对齐；红点全亮，外壳正常受光 |
| ADS 中心 | P9 模型坐标 `[-2.98,12.15,8.13]` |
| FOV / ADS 时间 | 沿用 P9 `0.95` / `0.15 s`，无额外倍率或速度加成 |
| 伤害 / 后坐力 / 散布 | 无额外修改 |
| 安装入口 | 仅枪械维护台安装 / 拆卸 / 更换 |

## 实现说明

当前物品 Tooltip 仅显示“瞄具｜手枪”和一句功能描述。配件装拆仅通过枪械维护台，V 快捷键已移除。统一规则见 [Tooltip Cleanup V1](native_gun_attachment_tooltip_cleanup_v1.md)。

正式物品：`apocalypse_firstlight:pistol_red_dot`，最大堆叠 1，进入「AFL 武器与弹药」标签页配件位置。当前只兼容 P9-01；没有步枪配件、耐久、品质、倍率选项、安装动画或复杂 UI。

## 使用

- 将枪放入维护台，点击瞄具热点，通过背包候选页安装；已装配件可更换或拆卸。
- 安装消耗候选来源物品，拆卸安全返还并保留 NBT；共用音效结束后服务端重新验证并提交。规则见 [维护台交互](attachments/gun_maintenance_attachment_interaction_v1.md)。
- SIGHT、MUZZLE、MAGAZINE 独立保存；移除红点不会拆掉其他配件。手持状态不再支持快捷装拆。

## 资产与比例

源：`src/main/blockbench/pistol_red_dot.bbmodel`，独立 21 cube / 5 group、32×32 像素材质图集。

`sight_root` 下分 `mount_base`、`sight_body`、`window_frame`、`illuminated`；`reticle_dot` 是独立小几何，不是画在镜片上的点。开放镜窗无中央玻璃面，没有半透明排序、反射或折射；仅红点使用全亮渲染，外壳正常受光。

瞄具宽约 2.12、长 2.40、高约 1.75 模型单位；与 P9 2.24 单位宽套筒匹配。短 adapter plate 随瞄具自带，放在后半段平顶且在照门前，不跨抛壳口。不复制 BR51 的模型、导轨或比例；以独立低模硬表面、暗灰面差和克制边缘高光保持 AFL 风格。

P9 的源几何、静态 `sight_anchor`、Display 和全部动画关键帧**均未修改**。白版没有额外顶轨或永久底座。

独立配件的第一/第三人称手持和地面 `display.scale` 从 3.0 调整为 0.8，即原线性尺寸约 27%；背包图标、物品展示框及枪上挂载比例不变。源文件与物品模型同步，不缩改几何本体。

## 挂载、保存与 ADS

- `NativeGunDefinition.sightMount` 来自可选 JSON `sight_slot`；P9 白名单仅含本配件。BR51 不声明该槽，不能安装。
- P9 当前 `sight_anchor` 是前部瞄线定位点 `[-2.98,11.59,-3.36]`，复用它的动画变换，再加局部偏移 `[0,-0.44,10.55]`，底座中心即 `[-2.98,11.15,7.19]`。无需为了安装改变原机瞄定位。
- 红点本地中心 `[0,1,0.94]`，枪体光学轴中心 `[-2.98,12.15,8.13]` 写入 `sight_slot.ads_center`。`NativeAdsProfile.forStack` 有兼容瞄具时用该点逆解，没有时原样返回机械瞄准 profile；FOV、进入时间、枪械后坐力和伤害不变。
- 模型从真实 `sight_anchor` 遍历矩阵渲染，继承套筒后坐/后定、换弹、整枪 ADS、第三人称与地面显示变换，不使用屏幕固定 HUD 点。
- 配件保存在枪 ItemStack 的 `AflAttachments.SIGHT` 完整配件 NBT。服务端原子装拆、正常背包同步负责客户端显示，丢弃/存档随枪保留；不以全局布尔值开关。
- `NativeSightRendering` 是共享静态挂载渲染器，`P901SightLayer` 是首批 P9 接口适配。以后其他手枪需声明兼容 ID/局部安装点/ADS 点，并从其渲染器调用同一 anchor 消费者；不承诺只有 anchor 名称就自动完成所有渲染与 ADS 标定。
- 当前网络协议 **22**；旧快捷装拆报文保留编号但不执行任何变更。玩家装拆仅使用维护台服务端事务。

## 导出与限制

运行时目录 `src/main/resources/assets/apocalypse_firstlight/`：

- `geo/pistol_red_dot.geo.json`：同源几何导出，保留分组。
- `textures/item/pistol_red_dot.png`：独立图集，与源中嵌入图一致。
- `models/item/pistol_red_dot.json`：独立物品模型，含背包/手持/地面 Display。
- `models/item/pistol_red_dot_body.json`、`pistol_red_dot_reticle.json`：挂载时的正常受光外壳和独立全亮红点。V1 实际使用同源 baked item quads，geo 为完整几何交付，不增加第二套动画实体。

`node tools/build-pistol-red-dot.mjs --export` 从编辑源重新导出；`--check` 检查一致性；`--create` 只允许首次创建，不覆盖后续美术修改。

已装瞄具的枪在背包仍沿用现有 P9 平面图标，不动态合成配件图标。V1 为固定几何红点、非真实光学准直/视差模拟；无玻璃染色、镜片反射。暂无合成配方，正式入口为创造标签或 `/give`。

## 验证边界

- 构建：compileJava / processResources / build 离线通过；缩放修正版再次通过，`pistol-red-dot-scale-build.log`。源/导出一致性检查及 `git diff --check` 通过。
- Blockbench：独立模型已载入，21 个 cube 全部绑定同一有效贴图 UUID，截图 `build/pistol-red-dot-blockbench.png`；未覆盖用户打开的 P9 工作页。
- 两轮 GameTest 均为 67 项中 66 项通过；新增装拆测试无失败。整套未全绿：已有的 `nativenoiseflatdistances` 分别在 Hearing boundary 60、40 失败，日志 `pistol-red-dot-gametest.log` / `pistol-red-dot-gametest-final.log`；未扩大范围调整噪声，也未断言已排除其原因。
- 客户端 ADS 数值检查通过；用户实机反馈除独立配件手持/掉落过大外，其余安装、ADS、HIP、F5、换弹项目没有问题。独立配件已缩小至 0.8，调整后的手持/掉落视觉待资源重载复验，不把导出或构建当作视觉通过。

未 commit、未 push。
> Current channel protocol: **22**, adding P9 MAGAZINE support while retaining atomic shot confirmation. Earlier protocol references below are historical. Matching client/server required. See [24R magazine](p9_01_extended_magazine_v1.md).
