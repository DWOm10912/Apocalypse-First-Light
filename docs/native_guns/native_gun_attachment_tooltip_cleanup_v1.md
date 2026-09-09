# Native Gun & Attachment Tooltip Cleanup V1

状态：已实现。

Native Gun 与 Native Attachment 的普通物品 Tooltip 统一为两行正文：枪械显示灰色非斜体“口径｜射击模式｜静态弹匣容量”及深灰斜体的一句定位描述；附件显示灰色非斜体“类型｜兼容类别”及深灰斜体的一句功能描述。名称继续使用 Minecraft 默认物品名称样式，AFL 不手动追加 Mod 名。所有正文来自 `zh_cn.json` / `en_us.json`，Java 不硬编码正式文案。

当前覆盖 P9-01、BR51-01、手枪微型红点瞄具和手枪消音器。P9/BR51 不再显示实时弹匣余量；实时弹药仍由持枪 HUD 表示。红点与消音器不再显示主副手、V 键、拆卸顺序或动作状态教程，安装交互继续由枪械维护台承担。

本次只修改 `appendHoverText`、样式和中英文本地化；不修改枪械伤害、射程、后坐力、容量、ADS、噪声、消音器倍率、附件安装、维护台、渲染、模型、动画或音效。

验证：中英文 JSON 解析与旧键/实时弹量代码静态扫描通过；离线构建记录于 `build/native-tooltip-cleanup-build.log`。本轮未启动图形客户端，因此实际换行宽度、JEI/创造栏显示及第三方 Mod 来源行尚未实机验收。
