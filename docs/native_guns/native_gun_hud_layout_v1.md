# Native Gun HUD 布局 V1

## 现行布局与资源

左侧枪械剪影等比缩小，中央白色竖线，右侧三行文字：本地化名称、当前装弹 `|` 备弹/∞、小字号开火模式。三行共用中心轴；保留原有空仓红色、开火短闪及模式颜色。适用于所有 Native Gun，无武器 ID 特判。

编辑 `src/main/resources/assets/apocalypse_firstlight/gui/layout/native_gun_hud.json`。
资源 ID 为 `apocalypse_firstlight:gui/layout/native_gun_hud.json`，不是服务端 `data/` 数据，也不生成 `config/` 文件。客户端启动和资源重载读取；资源包可用同路径覆盖，游戏内 F3+T 重新加载。开发环境改源文件后需先 processResources（或 IDE 同步到运行资源），F3+T 不负责复制源码资源。

缺失字段使用内置默认值；非法类型、超范围数值、非有限值、非法颜色逐字段回退并记录警告。资源缺失、JSON 损坏或根节点不是对象则整体回退。读取不覆写文件；HUD 每帧只读配置快照。

## 字段与默认值

单位都是 Minecraft GUI 缩放后的坐标。`anchor` 仅支持 `bottom_right`。`global` 偏移正值向右/下；其余偏移相对于模块左上角。

| 区域 | 默认参数 | 含义 |
| --- | --- | --- |
| global | offset_x/y=0；scale=1；width=84；height=38；right_margin=10；bottom_margin=61 | 整体位置、缩放与固定边界 |
| silhouette | offset_x=18；offset_y=19；scale=0.65；alpha=1；color=#FFFFFF；empty_color=#FF2626 | 偏移是图片中心，按原枪械 HUD 比例绘制并适配左栏；当前四枪实际约原尺寸60–65% |
| divider | offset_x=39；offset_y=3；width=1；height=32；color=#FFFFFF；alpha=0.9 | 偏移是竖线左上角 |
| weapon_name | offset_x=64；offset_y=2；scale=0.75；max_width=38；color=#FFFFFF | x 是中心，y 是文字行顶端 |
| ammo | offset_x=64；offset_y=14；current_scale=1.15；reserve_scale=1；separator_scale=0.9；max_width=38；gap=2 | 动态测量三段文字，gap 是分隔符左右间隔 |
| ammo 颜色 | current_color=#FFFFFF；reserve_color=#D0D0D0；separator_color=#B8B8B8；empty_color/flash_color=#FF3333 | 只影响展示 |
| fire_mode | offset_x=64；offset_y=28；scale=0.78；max_width=38；semi_color=#9FC7D9；burst_color=#D6A15F；auto_color=#D97878 | 独立的小字号第三行 |

颜色使用 `#RRGGBB`。偏移允许[-500,500]；global.scale [0.25,2]，width [40,160]，height [24,100]，margin [0,500]；其余 scale [0.25,3]，max_width [8,160]，gap [0,12]；divider.width [0.5,4]、height [1,100]；alpha [0,1]。合法值还会受屏幕/分栏边界约束。

名称和模式按真实 font.width 居中，长文字先缩小（常规最小0.55，若配置更小则尊重配置），再添加省略号。弹药整行等比适配，不截断数字；不同字号用 font.lineHeight-1 补偿基线。剪影保持原 hudWidth/hudHeight 比例，不改贴图。模块有裁剪边界及屏幕4单位安全边距。

## 位置与盖革 HUD

默认模块左上为 `(屏幕宽-94, 屏幕高-99)`，保留此前右侧10单位与纵向参考线，但内部从上下堆叠改成左右结构，并非所有元素原坐标不变。固定84×38，不随长名称/备弹向左扩张。默认底边为屏幕高-61。

当前盖革 HUD 高48、scale=0.85、底边距8，其顶边为屏幕高-48.8，二者默认有12.2 GUI单位间隔；盖革默认回退scale=1时仍有5单位间隔。没有修改盖革代码/配置，也没有新增自动碰撞系统；用户自定义双方偏移或缩放后仍需自行避免重叠。

## 实现与验证边界

- NativeGunHud：绘制和客户端事件注册。
- NativeGunHudLayout：仅 HUD 几何计算。
- client/config/NativeGunHudConfig：默认值、字段解析与验证。
- client/config/NativeGunHudConfigManager：客户端资源 prepare/apply 快照重载，沿用盖革模式。
- src/dev/tests/NativeGunHudLayoutTest.java：独立 Java/Gson 轻量检查，覆盖默认资源一致性、错误回退、分栏、比例、三行轴线、GUI边界、默认盖革间隔；不代表客户端目视验证。

本任务仅允许 compileJava、processResources 及上述轻量检查。不启动客户端；字体、∞、不同语言/GUI Scale、F3+T 实际显示由用户测试。无战斗、弹药逻辑、网络、开火模式、ADS、音效、动画及其他HUD行为改动。
