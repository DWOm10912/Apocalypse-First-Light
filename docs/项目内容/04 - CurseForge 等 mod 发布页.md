

## 项目简介 Summary（2026-10-06，Alpha 占位版用）

为什么写：用户准备先在 CurseForge 发一个 Alpha 版本占住名字（2026-10-06 查过，CurseForge、Modrinth 上 "Apocalypse First Light" 都还没人用），发布页的 Summary 栏要一段简介。只写已经实现的内容，城市生成、国家系统等还没做的不写。

英文（Summary 栏，较短）：

> A grounded post-apocalyptic survival overhaul for Forge 1.20.1: realistic guns, temperature, thirst, stamina and weight, radiation and the infected, searchable loot containers, and working fuel stations, power and fluid pipes.

英文（较长，可放描述开头）：

> Apocalypse: First Light is a grounded post-apocalyptic survival mod for Forge 1.20.1. Survive with realistic firearms and ammo, body temperature, thirst, stamina and carry weight, while radiation and the infected wait outside. Search abandoned lockers, dumpsters and vending machines for supplies, and get the old world running again: fuel stations, generators, batteries, cables and fluid pipes. Early Alpha: expect missing content and breaking changes.

中文：

> 一个偏写实的末日生存模组（Forge 1.20.1）：真实手感的枪械与弹药、体温、口渴、耐力与负重、辐射与感染者；搜刮废弃的储物柜、垃圾箱和售货机，让加油站、电力和管道重新运转。当前为早期 Alpha，内容不完整，版本之间可能不兼容。

## 发布页结构（2026-10-06 讨论，Alpha 用）

为什么这样排：用户之前的 ANANKE 页面是"一段文字 + 一张图"一路排下去，太长，大多数人看不完。改成短页面，图放画廊，细节以后放自建 wiki。

正文从上到下：
1. 横幅图（mod 名 + 一句话），用和游戏内界面一致的现代风格（深色半透明底、暖白字、琥珀强调色），不用像素奇幻风。
2. 一句话简介（见上面"项目简介 Summary"）。
3. Alpha 提示：内容不完整、版本之间存档可能不兼容。
4. 5–6 个特性块，每块一个小标题 + 一两句话 + 一张图或 GIF（见下面"可以写进 Alpha 介绍的内容"）。
5. 链接（Wiki、Discord、源码）、兼容性（Forge 1.20.1、前置）、许可（本文件后面的代码 / 资产分离说明）。

正文里的"带字图片"（标题横幅、分节标题、提示框）是作者自己做的图片，不是平台生成的。

可以写进 Alpha 介绍的内容（只写已实现的，按文档状态核对过）：
- 枪械：P9、Blackridge .50、BR51、HR55、Silverwood 12 并列双管；红点、消音器、扩容弹匣、弹鼓等配件；按 Z 即时改装、枪械维护台；弹孔贴在真实的模型表面上。
- 生存：体温、口渴、耐力、负重，新的生存仪表 HUD；冷热屏幕效果。
- 搜刮：储物柜、垃圾箱、售货机、冷柜、冰柜、收银机、货架等，打开后逐格搜索；撬棍砸售货机玻璃。
- 燃油：加油站（加油机、加油岛、顶棚）、地下储罐、取液泵、管道；油壶、油桶、手摇油泵、倒油动画；漏油、起火、子弹打漏和爆炸。
- 电力：电缆、电池、充电站，冷柜 / 冰柜 / 售货机 / 饮水机用电，热能发电机。
- 辐射：盖革计数器、铅箱、辐射屏幕噪点；工业矿石和机器（合金炉、粉碎机、压缩机、化学反应器、工业熔炉）。
- 待确认再写：感染者（代码里有 AI、破门、感知，状态需用户确认）；城市、公路这类世界生成还没完成，不写。

## 描述卡片（2026-10-06）

为什么：用户要求正文用带字的图片卡片，风格和之前的标题图（E:/Download/AFL/afl_title.png）、徽章（afl_badge.png）一致，并要一个 Discord 链接卡片。

- 生成器：`tools/build-curseforge-cards.mjs`，运行 `node tools/build-curseforge-cards.mjs [输出目录]`，默认输出到 `E:/Download/AFL_CurseForge_Cards`（用户要求放在下载目录；E 盘上实际的文件夹是 `E:/Download`）。
- 画法：像素画，按单位格画好后最近邻放大 4 倍；颜色从标题图和徽章取样（夜空紫、钢灰、桃色顶边、锈色、日出橙）。边框照徽章：黑描边、3 格钢边（顶上一条桃色）、靠下的锈点。
- 大字：5×7 点阵加粗，向下的暗色厚度和黑描边，分"钢"（像 APOCALYPSE：灰面、桃色顶边、锈迹）和"日出"（像 FIRST LIGHT：从上到下的日出渐变）两种。
- 小字：5×7 点阵加黑色投影，`{o:...}` 里的字是橙色。
- 图标（Discord、书、代码、警告、手枪、温度计、放大镜、油壶、闪电、辐射三叶）在脚本里用字符画定义，辐射三叶按角度算。
- 文字只支持英文大写、数字和常用标点（点阵字库里只有这些）；提示卡的一行太长、按钮放不下字时脚本直接报错，不会悄悄溢出。

| 文件 | 尺寸 | 用途 |
|---|---|---|
| `hero.png` | 2400×1200 | 页首：标题图按原尺寸叠在夜空色带上 |
| `header_features.png` | 1200×144 | "FEATURES" 分节标题 |
| `feature_gun / survival / loot / fuel / power / radiation.png` | 1200×160 | 六个特性的标题条（说明文字写在卡片下面的正文里，方便修改和翻译） |
| `link_discord / link_wiki / link_source.png` | 528×120 | 链接按钮，在 CurseForge 编辑器里给图片加链接 |
| `notice_alpha.png` | 1200×264 | 早期 Alpha 提示 |
| `notice_requirements.png` | 1200×264 | 前置：Minecraft 1.20.1 / Forge；必需 GeckoLib、Curios、TerraBlender；可选 JEI、Jade（按 mods.toml 的 mandatory） |

待定：整合包许可卡片（许可里没写整合包能不能用，等用户决定）；Discord 邀请链接、Wiki 地址、源码仓库是否公开。

---

**本模组采用代码与资产分离的许可方式。**

### 代码 / Source Code

本项目的程序代码允许社区查看、学习、修改、分发，并允许基于 AFL 制作兼容 Mod、附属 Mod、扩展内容或其他技术集成。

代码部分的具体权利与义务以项目仓库中提供的 `LICENSE` 文件为准。

我们欢迎其他开发者基于 AFL 的公开接口、数据格式和系统制作扩展内容。附属 Mod 可以依赖 AFL 本体并调用其公开内容，无需将 AFL 自身的美术资产重新打包进附属 Mod。

### 美术、音频与其他资产 / Assets

**AFL 的模型、贴图、动画、音效、粒子、美术源文件及其他非代码资产不随源代码许可证开放。**

除非获得项目作者的明确许可，不得从 AFL 中提取、复制、重新打包、重新分发、出售或用于其他独立项目。

这包括但不限于：

- 3D 模型、Blockbench / Mesh 资产与源文件
- 贴图、GUI、图标及其他美术素材
- 枪械、生物、机器及其他动画
- 音效与音频资源
- 粒子与视觉效果素材
- 经过 AFL 项目制作或修改的其他资源文件

不得将上述资产直接或经过轻微修改后重新发布，也不得将其声明为自己的原创资产、资产包或作品进行销售、授权或分发。

### 附属 Mod 与兼容项目 / Add-ons & Compatibility

允许制作依赖 **Apocalypse: First Light** 的兼容 Mod、附属 Mod和扩展内容。

这类项目可以引用 AFL 已注册的内容、API、Tag、数据和公开接口，但原则上应要求玩家同时安装 AFL 本体，而不是从 AFL 中提取模型、贴图、动画或音效并重新包含在自己的发布包中。

---

