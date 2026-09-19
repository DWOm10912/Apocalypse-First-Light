# Native Gun HUD 布局 V1

## 现行布局与资源

当前源布局将枪械剪影放左侧、白色竖线放中间、三行文字放右侧：本地化名称、当前装弹 `|` 备弹/∞、小字号开火模式。这只是初始排版；五个子元素均可在 HUD 框内独立移动，不存在固定左右分栏。保留空仓红色、开火短闪及模式颜色。适用于所有 Native Gun，无武器 ID 特判。

编辑 `src/main/resources/assets/apocalypse_firstlight/gui/layout/native_gun_hud.json`。
资源 ID 为 `apocalypse_firstlight:gui/layout/native_gun_hud.json`，不是服务端 `data/` 数据，也不生成 `config/` 文件。发布环境从资源管理器读取，资源包可同路径覆盖。确认存在可写项目源目录的开发环境，启动与 F3+T 直接读取源 JSON，不再要求先复制到 build/resources。仅开发环境可用 `/afl hudlayout edit native_gun` 可视化编辑、原子写回源文件，详见 [通用 HUD 编辑器](../ui/hud_layout_editor_v1.md)。

源 JSON 使用 `schema_version: 2`。缺失字段使用内置默认值；非法类型、超范围数值、非有限值、非法颜色逐字段回退并记录警告。资源缺失、JSON 损坏或根节点不是对象则整体回退。读取不覆写文件；HUD 每帧只读配置快照。无版本号的旧 JSON 按中心点坐标解析并一次性换算到新版左上角坐标；编辑器载入旧草稿时也先转换，保存后写 v2，保留未知字段。

## 字段与内置回退值

下表列出 loader fallback / 编辑器“重置默认”值。当前作者微调的源 JSON 与之不同，例如 global.scale=1.2、divider.height=30、weapon_name.scale=1.4；实际值以源 JSON 为准。新增元素宽高已写入该源文件；老资源缺字段时仍使用回退值。

单位都是 Minecraft GUI 缩放后的坐标。`anchor` 仅支持 `bottom_right`，只定位整个 HUD；`global` 偏移正值向右/下。v2 中所有子元素的 `offset_x/offset_y` 都表示其布局框左上角相对于 HUD 左上角的位置，编辑框和运行时共用此坐标。框内文字居中、剪影等比 contain，属于内容对齐，不改变元素位置。

| 区域 | 默认参数 | 含义 |
| --- | --- | --- |
| global | offset_x/y=0；scale=1；width=84；height=38；right_margin=10；bottom_margin=61 | 整体位置、缩放与固定边界 |
| silhouette | offset_x=0；offset_y=0；width=36；height=38；scale=0.65；alpha=1；color=#FFFFFF；empty_color=#FF2626 | 图片等比 contain 到自己的布局框内，scale 是额外尺寸上限，不拉伸轮廓、不依赖 divider |
| divider | offset_x=39；offset_y=3；width=1；height=32；color=#FFFFFF；alpha=0.9 | 偏移是竖线左上角 |
| weapon_name | offset_x=45；offset_y=2；width=38；height=8；scale=0.75；max_width=38；color=#FFFFFF | 宽高约束文字；文字在框内居中，不拉伸字体 |
| ammo | offset_x=45；offset_y=14；width=38；height=12；current_scale=1.15；reserve_scale=1；separator_scale=0.9；max_width=38；gap=2 | 宽高约束整组文字；保留三段各自字号，gap 是分隔符左右间隔 |
| ammo 颜色 | current_color=#FFFFFF；reserve_color=#D0D0D0；separator_color=#B8B8B8；empty_color/flash_color=#FF3333 | 只影响展示 |
| fire_mode | offset_x=45；offset_y=28；width=38；height=9；scale=0.78；max_width=38；semi_color=#9FC7D9；burst_color=#D6A15F；auto_color=#D97878 | 独立的小字号第三行，宽高是区域约束 |

颜色使用 `#RRGGBB`。偏移允许[-500,500]；global.scale [0.25,2]，width [40,160]，height [24,100]，margin [0,500]；其余 scale [0.25,3]，文字 width [8,160]、height [1,100]、旧 max_width [8,160]，silhouette width [1,160]、height [1,100]，gap [0,12]；divider.width [0.5,4]、height [1,100]；alpha [0,1]。非法或缺失尺寸逐字段回退，宽高不会为 0。子元素可在 HUD 内自由摆放，不再受 divider 或右侧栏位 clamp；超出整个 HUD 框的部分仍受整体 scissor 裁剪。新 `width` 优先于旧 `max_width`，旧字段保留以兼容旧资源。

名称和模式按真实 font.width 居中，字号从不超过配置 scale，也受区域 height 限制；长文字按 width 缩小（常规最小0.55，若配置更小则尊重配置），仍不足时添加省略号。原源文件的名称 scale=1.4，短 C.A.T 曾直接保留此大字号、长枪名却因宽度自动缩小，造成视觉反差；现在源 JSON 的 weapon_name.height=6 形成通用字号上限，没有武器 ID 特判。弹药整行按 width/height 等比缩小，不放大、不截断数字；不同字号用 font.lineHeight-1 补偿基线。剪影保持原 hudWidth/hudHeight 比例，不改贴图。模块有裁剪边界及屏幕4单位安全边距。

## 位置与盖革 HUD

默认模块左上为 `(屏幕宽-94, 屏幕高-99)`，保留此前右侧10单位与纵向参考线。内置回退 84×38；作者调过的源 JSON 当前约 106.93×39.39。开发编辑器可修改整体宽高，不自动随长名称/备弹扩张。默认底边为屏幕高-61；作者源 JSON 的实际位置还受 global.offset 影响。

当前盖革 HUD 高48、scale=0.85、底边距8，其顶边为屏幕高-48.8，二者默认有12.2 GUI单位间隔；盖革默认回退scale=1时仍有5单位间隔。编辑器提供红框避让提示，没有自动碰撞系统；用户自定义双方偏移或缩放后仍需自行避免重叠。

## 实现与验证边界

- NativeGunHud：绘制和客户端事件注册。
- NativeGunHudLayout：仅 HUD 几何计算。
- client/config/NativeGunHudConfig：默认值、字段解析与验证。
- client/config/NativeGunHudConfigManager：客户端资源 prepare/apply 快照重载，沿用盖革模式。
- src/dev/tests/NativeGunHudLayoutTest.java：独立 Java/Gson 轻量检查，覆盖源资源、旧坐标迁移、非法字段回退、左/中/右位置、剪影比例与独立放置、文字尺寸、GUI 边界和默认盖革间隔；不代表客户端目视验证。

本任务仅允许 compileJava、processResources 及上述轻量检查。不启动客户端；字体、∞、不同语言/GUI Scale、F3+T 实际显示由用户测试。无战斗、弹药逻辑、网络、开火模式、ADS、音效、动画及其他HUD行为改动。
