# BR51-01 35发扩容弹匣 V1

| 项目 | 当前实现 |
| --- | --- |
| ID / 名称 | `apocalypse_firstlight:br51_extended_magazine_35` / BR51-01 35发扩容弹匣 / BR51-01 35-Round Extended Magazine |
| 槽位 / 兼容 | MAGAZINE，仅 BR51-01；与 P9 弹匣不通用 |
| 容量 | 标准 20；有效附件覆盖为 35，不自动装弹 |
| 来源 | 原 BR51 `mag_extended_2`：20 个主体 cube + 4 个原底板 cube，保留 UV 和原贴图 |
| 独立源 / 生成器 | `src/main/blockbench/br51_extended_magazine_35.bbmodel` / `extract_br51_magazine_35.cjs` |
| Runtime | `assets/apocalypse_firstlight/geo/br51_extended_magazine_35.geo.json`、`textures/item/br51_extended_magazine_35.png`、`models/item/br51_extended_magazine_35.json`，均位于 `src/main/resources/` |
| Item | builtin/entity + 共用 NativeMagazineRendering.ItemRenderer，真实独立 3D 物品，武器与弹药创造标签页，堆叠 1 |
| 安装基准 | 原 `mag_standard` / `mag_extended_2` 共用 pivot `[0,9.41883,4.52607]`；独立模型减去该 pivot，挂回原骨骼时还原 |
| 模型替换 | 仅有效附件替换 `mag_standard` 和 `empty_old_mag_standard` 的整个几何子树，避免保留标准底板造成双重渲染 |
| 动画 | 复用现有 `reload_tactical` / `reload_empty`；不改动画文件，不接入 xmag、mag1 或 mag3 |
| 维护台 | 仅维护台装拆、更换；共用 Context HUD / 候选页 / 原版点击声 / 2.480 s 操作声 / 服务端 51 tick 后重验提交 |
| 热点 | BR51 magazine_slot 指定 `hotspot_anchor=mag_standard`、`hotspot_y=-4`；P9 保持原 magazine / -6.2 |
| HUD | 所有枪统一单行 `当前装弹 \| 备弹`；容量仅在枪械 Tooltip 规格行显示，BR51 标准 20 / 扩容 35 |

## 资产审计

源组 UUID `46cc9283-b086-1a54-5f74-226d18d42f6f`，源文件中 export=false、visibility=false。与标准匣处于同一 `magazine` driver；正式 runtime 只有标准几何及 `additional_magazine` 下的 `empty_old_mag_standard` 副本，没有 mag2 内嵌几何。源文件当前只有 8 条正式动画，没有待恢复的 xmag 动画。没有单独的 mag2 reload 副本，本次让现有旧弹匣副本也消费同一个独立附件。

提取保留顶部接口、5° 主体倾角和原底板子组旋转，仅把源坐标平移为独立 root；没有修改 BR51 源几何或动画。Runtime 转换沿用当前 X 镜像 / cube rotation / face UV 规则，贴图逐字节复制 BR51 atlas。

## 容量与服务端事务

`NativeMagazineItem` 从附件配置获取兼容枪、容量、替换骨骼与物品构图偏移；默认 P9 配置仍为 24。`NativeGunAmmo.capacity` 继续从有效真实附件解算，没有全局把 BR51 容量写成 35。

- 12/20 安装后为 12/35，不凭空补弹。
- 27/35 拆下后为 20/20；多出的 7 发使用 BR51 正式 `762x51mm_round` ItemStack 返还到既有背包备弹体系，背包满时按共用规则安全掉落。
- 源附件消耗、旧附件返还、弹药溢出和枪械写回复用 `MaintenanceAttachmentTransaction`；过期请求拒绝，不能重复返还溢出。
- SIGHT、MUZZLE、MAGAZINE 独立存储；换弹、满匣检查及 HUD 均读取当前容量；伤害、半自动射击、后坐力、散布、射速及换弹时间保持不变。

## 验证

服务端：`build/br51-mag35-tests.log`，28/28 必需 GameTest 通过。BR51 测试循环 20 次，覆盖兼容隔离、三槽共存、安装/更换/拆卸、12→12 不补弹、27→20 恰好返还 7 发、stale 安装/重复拆卸、BE 与 ItemStack 序列化；包含既有 P9 回归。

客户端：`build/br51-mag35-client.log` 中 `[BR51 MAG35 CLIENT] PASS`；真实维护台依次安装三个附件，检查三个操作声、49 tick 内不提前提交，实际 C2S tactical / empty 换弹均装至 35。截图位于 `build/thermal-fluid-client/screenshots/br51_mag35_*.png`，已复核背包独立 3D、维护台 mag2、第三人称、换弹采样画面与 HUD；用户反馈“看起来没问题了”。未进行两个真实客户端的多人同步视觉验收，FakePlayer stale/conservation 通过不等于真实多人验收；换弹截图是采样检查，不宣称所有帧均无穿插。

打包记录：`build/br51-mag35-build.log`。不改 BR51 原 source / runtime 基础 geo / 动画文件；新独立贴图与原图逐字节相同。无新方块，不涉及挖掘工具/掉落等级变更。

HUD 后续修正：恢复全枪统一单行“当前装弹 | 备弹”，移除 show_capacity 配置与分行分支；BR51 枪械 Tooltip 规格改为本地化容量参数（20/35）。未修改 NativeGunAmmo、换弹或溢出事务。修正构建记录 `build/br51-hud-single-line-build.log`；此前截图的两行 HUD 已过时，本次未重新启动图形客户端验收。
