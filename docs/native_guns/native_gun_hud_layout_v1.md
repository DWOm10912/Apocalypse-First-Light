# Native Gun HUD 布局（现行：HUD V2 B，2026-10-17）

状态：已实现，编译和离线布局检查通过；**还没有进游戏看过**（字号、间距、胶囊、键帽、进度条都等用户实机确认）。

## 现行布局（HUD V2 B 右下装备区）

按 `design/ui/hud_v2/sheets/hud_v2b_3_gear.png`（源 `design/ui/hud_v2/source/hud_v2b_concept.html`）做。拿着任何 Native Gun 时，右下角两行，都右对齐在布局框（`global`）的右边，弹药行贴着框底：

1. **名字行**：射击模式胶囊（高 7，圆角 2.5，底色 `AflUiStyle.BACK` 0.6，描边 `LINE`；模式字 0.6 倍，`TEXT_DIM`；`hud.apocalypse_firstlight.fire_mode.semi/burst/auto`）+ 间隔 4 + 短枪名（0.75 倍，`TEXT_DIM`，最宽 60，超过就缩小）。
   - 短枪名：显示名第一个空格之前的部分（型号，例如 P9-01、BR-51、HR-55、C.A.T）；玩家用铁砧改过名就显示全名。
2. **弹药行**：弹匣里的发数大字（1.5 倍）+ 间隔 3 + "/ 备弹"小字（0.75 倍，`TEXT_DIM`；无限备弹显示 ∞）。
   - 用弹匣物品的枪（2026-10-19 起 P9-01，[magazine_system_v1.md](magazine_system_v1.md) 第 5 节，未提交，没进游戏看过）：
     - 大字是弹匣里的发数，膛里有一发时后面跟小的「+1」（CS2 式）；
     - 备弹是胸挂和口袋里能用的弹匣的子弹总数，创造模式也显示真实数字（2026-10-20 起，不再是 ∞）；
     - 低弹量门槛按弹匣容量算；
     - 空仓时：找得到更满的弹匣才提示"换弹"；找不到、但枪里的弹匣没满时提示"取下弹匣"（`hud.apocalypse_firstlight.gun.unload_magazine`，Remove magazine），位置、大小、键帽和"换弹"一样（2026-10-20，同一个换弹键，见 magazine_system_v1.md 第 3.1 节）。
   - 剩 ≤ 弹匣容量 1/4 且不为 0：数字琥珀 `AflUiStyle.ACCENT` `#E0A84A`。
   - 空仓：数字红 `#E8867C`；还有备弹时，数字左边 5 处是"换弹"（0.65 倍）和它左边的换弹键帽（`AflKeyHint`，0.7 倍，键取自 `NativeGunInput.reloadKey()`）。
   - 换弹中：数字换成"换弹"（1 倍，`hud.apocalypse_firstlight.gun.reload`），下面一条 1.5 高的进度条（底 `BACK` 0.6，进度 `ACCENT`）。

两行间隔 4。旧版的剪影、白色竖线、居中三行文字、模式颜色、开火短闪都已去掉（开火的反馈留给动态准星，`NativeDynamicCrosshair`）。武器列表（`client/inventory/LoadoutHud`，武器位 1–3）总在布局框上方 4 处，换手不跳；没拿枪时框里空着。

### 换弹进度怎么来

只在客户端看：主手枪的 GeckoLib `action` 控制器（`NativeGunItem.ACTION_CONTROLLER`）正在放的片段名是 `reload` 或以 `reload_` 开头，就算在换弹。从客户端第一次看到这个片段起计时（游戏刻 + partialTick），除以 `gun.reloadTicks(stack, empty)`；片段名以 `gun.reloadClip(stack, true)` 开头（含 `_ads`、熟练档 `_1.._4`）算空仓换弹。片段停了或换了就回到数字。没有新的网络包、没有改换弹逻辑。

已知限制：熟练档换弹如果比 `reloadTicks` 快，进度条可能没走满就回到数字。

## 资源和配置

`src/main/resources/assets/apocalypse_firstlight/gui/layout/native_gun_hud.json`（资源 ID `apocalypse_firstlight:gui/layout/native_gun_hud.json`，客户端资源，资源包可同路径覆盖；不是 `data/`，不生成 `config/` 文件）。`schema_version: 2`。

V2 B 只读 `global`：

| 字段 | 现值 = 内置回退 | 范围 |
| --- | --- | --- |
| offset_x / offset_y | 0 / 0 | [-500, 500] |
| scale | 1 | [0.25, 2] |
| width / height | 64 / 24 | [40, 160] / [24, 100] |
| right_margin / bottom_margin | 8 / 8 | [0, 500] |

默认框左上在 (屏幕宽 − 72, 屏幕高 − 32)，右边、底边离屏幕边 8（和盖革面板、概念图一致）。`silhouette`、`divider`、`weapon_name`、`ammo`、`fire_mode` 几节仍会被解析（旧文件能读、`NativeGunHudConfig` 记录没变），但不再画，源 JSON 里已删掉。解析规则不变：缺字段用回退值，非法值逐字段回退并警告，JSON 损坏整体回退；无版本号的旧 JSON 照旧一次性迁移。

开发环境 `/afl hudlayout edit native_gun` 现在只有 `global` 一个框（移动、滚轮缩放、改宽高），见 [通用 HUD 编辑器](../ui/hud_layout_editor_v1.md)。两行是右对齐画在框右下的，框宽只决定编辑器红框和左边界，不裁剪文字（V1 的 scissor 已去掉，空仓提示本来就在框外左边）。

## 和盖革 HUD

盖革面板（`client/GeigerHudOverlay`，62×24，离右下边 8）只在主手拿着盖革计数器时出现，副手已经取消，所以不会和枪械 HUD 同时出现；它正好占没拿枪时空着的弹药位置，武器列表在它上方。概念图里盖革面板画在武器列表上方，这里没照做（那样要移动盖革面板），等用户看了再定。

## 实现

- `weapon/client/NativeGunHud`：绘制（`render`）、换弹进度（`reloadProgress`）、短名（`shortName`）、编辑器预览（`preview`，没拿枪时用 P9 样本，不写物品）、客户端事件注册。
- `weapon/client/NativeGunHudLayout`：框的几何（`frame`）；V1 子元素的几何方法还在（只给旧检查用）。
- `client/config/NativeGunHudConfig` / `NativeGunHudConfigManager`：回退值、解析、资源重载。
- `client/hudlayout/NativeGunHudLayoutAdapter`：编辑器只出 `global`。
- `client/inventory/LoadoutHud`：武器列表固定在框上方。
- `src/dev/tests/NativeGunHudLayoutTest.java`：独立 Java/Gson 检查（源资源有效且等于回退框、默认位置和边距、旧坐标迁移、非法字段回退、屏幕边界等），2026-10-17 跑过 93 项通过；不代表游戏里目视验证。
