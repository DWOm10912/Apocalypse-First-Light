

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
- 感染者（用户 2026-10-06 确认可以写）：能听见枪声并循声走过去，会挖开一些易碎方块；目前只有这一种生物，介绍里不写"多种感染者"。
- 不写：城市、公路这类世界生成还没完成。

## 描述卡片（2026-10-06）

为什么：用户要求正文用带字的图片卡片，风格和之前的标题图、徽章一致，并要一个 Discord 链接卡片。标题图和徽章的原件在 `design/branding/`（生成器从这里读标题图）。

- 生成器：`tools/build-curseforge-cards.mjs`，运行 `node tools/build-curseforge-cards.mjs [输出目录]`，默认输出到 `E:/Download/AFL_CurseForge_Cards`（用户要求放在下载目录；E 盘上实际的文件夹是 `E:/Download`）。
- 画法：像素画，按单位格画好后最近邻放大 4 倍；颜色从标题图和徽章取样（夜空紫、钢灰、桃色顶边、锈色、日出橙）。边框照徽章：黑描边、3 格钢边（顶上一条桃色）、靠下的锈点。
- 大字：5×7 点阵加粗，向下的暗色厚度和黑描边，分"钢"（像 APOCALYPSE：灰面、桃色顶边、锈迹）和"日出"（像 FIRST LIGHT：从上到下的日出渐变）两种。
- 小字：5×7 点阵加黑色投影，`{o:...}` 里的字是橙色。
- 图标（Discord、书、代码、警告、对勾、手枪、温度计、放大镜、油壶、闪电、辐射三叶、感染者头像）在脚本里用字符画定义，辐射三叶按角度算。
- 文字只支持英文大写、数字和常用标点（点阵字库里只有这些）；提示卡的一行太长、按钮放不下字时脚本直接报错，不会悄悄溢出。

| 文件 | 尺寸 | 用途 |
|---|---|---|
| `hero.png` | 2400×1200 | 页首：标题图按原尺寸叠在夜空色带上 |
| `header_features.png` | 1200×144 | "FEATURES" 分节标题 |
| `feature_gun / survival / loot / fuel / power / radiation / infected.png` | 1200×160 | 七个特性的标题条（说明文字写在卡片下面的正文里，方便修改和翻译）；感染者那张 2026-10-06 加，用户确认感染者可以写进介绍 |
| `link_discord / link_wiki / link_source.png` | 528×120 | 链接按钮，在 CurseForge 编辑器里给图片加链接 |
| `notice_alpha.png` | 1200×264 | 早期 Alpha 提示 |
| `notice_modpacks.png` | 1200×264 | 整合包随意使用，请链接官方页面、不要重新上传 jar（按上面的许可） |
| `notice_coming_next.png` | 1200×544 | 路线图，标题下第一行写明"计划中，不在这个版本里"，避免被当成已有内容。内容取自 `02 - 制作清单.md` 的 V1、V2 里还没做的项（2026-10-06 逐项查过代码，都还没有；流血只有通用的液体喷射和污渍底层，没接到生物上），加上待办里的柴油发电机、燃油储罐。"其他原版改动"太笼统，没写 |
| `notice_shaders.png` | 1200×304 | 光影：可配 Oculus 和 Embeddium；测过 Complementary Reimagined、Sundial、Sundial Lite（Sundial 透明层后的发光问题已修，见 animated_block_mesh_runtime_v1.md）；枪和机器用 LabPBR 材质 |
| `notice_requirements.png` | 1200×264 | 前置：Minecraft 1.20.1 / Forge；必需 GeckoLib、Curios、TerraBlender；可选 JEI、Jade（按 mods.toml 的 mandatory） |

待定：Discord 邀请链接、Wiki 地址；源码仓库公开后再放源码按钮。

---

## 正文排版草稿（2026-10-06）

为什么：卡片做齐了，用户要开始排版；页面要短，每个特性只露一张最能说明问题的 GIF，其余的收进"折叠块"，点开才显示。

折叠块：Modrinth 的 Markdown 支持 `<details><summary>…</summary>…</details>`。CurseForge 编辑器里有没有对应的折叠（spoiler）功能，2026-10-06 没查到官方说明，**需要在编辑器里确认**；没有的话，折叠里的图挪进画廊。

下面 [GIF: …] / [IMG: …] 是要录、要截的素材，括号里是文件建议名。

1. `hero.png`
2. 一句话简介（见"项目简介 Summary"的长版）
3. `notice_alpha.png`
4. `link_discord.png`（Wiki、源码按钮等有了再并排加上）
5. `header_features.png`
6. `feature_gun.png`
   - 文字：Five firearms built from scratch, from the P9 pistol to the Silverwood side-by-side shotgun. Swap red dots, suppressors and magazines on the spot (Z) or at a maintenance bench. Bullets hit the real model surface and leave holes where they land.
   - 露出：[GIF: Silverwood 开膛换弹 (gun_reload.gif)]
   - 折叠：[GIF: 按 Z 换红点，带右上角按键提示 (gun_attach.gif)]、[GIF: 打油桶，弹孔贴在圆面上 (gun_holes.gif)]
7. `feature_survival.png`
   - 文字：Body temperature, thirst, stamina and carry weight, shown in one compact HUD. Freezing nights frost your screen; heat blurs it.
   - 露出：[GIF: 走进雪地，体温表盘下降、屏幕结霜 (survival_cold.gif)]
   - 折叠：[IMG: HUD 特写 (survival_hud.png)]
8. `feature_loot.png`
   - 文字：Lockers, dumpsters, vending machines, coolers, freezers and shelves are searched slot by slot. Bring a crowbar for the vending machine glass.
   - 露出：[GIF: 掀开垃圾箱盖，逐格搜出物品 (loot_dumpster.gif)]
   - 折叠：[GIF: 撬棍砸售货机玻璃 (loot_crowbar.gif)]
9. `feature_fuel.png`
   - 文字：Working fuel stations with underground tanks, pumps and pipes. Carry fuel in cans and drums. Spilled fuel burns, and a bullet through a drum starts a leak - or a fire.
   - 露出：[GIF: 打穿油桶 → 漏油 → 点着 → 爆炸 (fuel_fire.gif)]
   - 折叠：[GIF: 加油机加油 (fuel_nozzle.gif)]、[GIF: 油壶往卸油口倒油 (fuel_pour.gif)]
10. `feature_power.png`
    - 文字：Cables, batteries and a charging station. Coolers, freezers, vending machines and water dispensers light up when powered.
    - 露出：[GIF: 接上电缆，冷柜灯亮 (power_on.gif)]
11. `feature_radiation.png`
    - 文字：Radiation zones, a Geiger counter and lead storage; industrial ores and machines to process them.
    - 露出：[GIF: 走进辐射区，盖革计数器读数上升、屏幕噪点 (radiation.gif)]
    - 折叠：[IMG: 几台机器 (industry.png)]
12. `feature_infected.png`
    - 文字：The infected hear your gunshots and come looking. Fragile blocks won't keep them out for long.
    - 露出：[GIF: 开枪后感染者循声赶来、挖开玻璃 (infected.gif)]
13. `notice_shaders.png`
14. `notice_coming_next.png`
15. `notice_modpacks.png`
16. `notice_requirements.png`
17. 许可（Custom License，见下）

画廊（不放正文的大图）：封面图 [IMG: 黄昏加油站，开光影，手持枪 (cover.png)]，以及每个特性各 1–2 张静态截图。

素材规格：
- 截图 1920×1080，界面缩放 3；风景图按 F1 隐藏界面，展示 HUD / 界面的图保留界面；封面开光影、黄昏；尽量同一个场景。
- GIF：OBS 录 1080p，再用 ScreenToGif 或 ffmpeg 转；宽 640–800、10–15 帧、3–6 秒、首尾能接上的循环；单个控制在约 5 MB 以内（平台上限未核实）。
- 声音是这个 mod 的卖点（枪声、盖革计数器），GIF 没声音；以后可以剪一个 60–90 秒的带声预告片放 YouTube，嵌进正文。

## 许可（2026-10-06 用户定）

**All Rights Reserved（保留所有权利），附明确的允许和禁止条款**，正文在仓库根目录 `LICENSE.txt`，署名 Antaurora。CurseForge 的许可选 "Custom License"，贴同样的内容。

为什么这样定：用户不想弄复杂的许可，同时希望源码公开，但不允许别人重新打包上传、不允许提取资源出售或传播，整合包可以随便用。正式开源许可（MIT、LGPL 等）都允许别人编译后再发布，和"不能重新打包上传"矛盾，所以用 ARR 加例外条款。对外说"源码公开"（source available），不说"开源"（open source）。

允许：
- 阅读、学习源码，提 issue 和 pull request；
- 在任何整合包里使用，公开或私人都行，前提是整合包从官方 CurseForge / Modrinth 页面下载（这两个平台的整合包本来就只是引用项目文件，不打包 jar）；
- 做依赖本模组的附属 mod，但不复制本模组的文件。

禁止：
- 在其它地方重新上传、分发本模组、它的 jar 或自己编译的版本，改没改过都不行；
- 提取、出售或传播其中的代码、模型、贴图、动画、音效或其它资源。

这一版取代了之前"代码允许查看、学习、修改、分发，只有资产保留权利"的说法：现在代码和资产都保留权利，只开放上面列出的用法。原来仓库根目录的 `LICENSE.txt` 是 Forge 开发模板自带的 LGPL 说明，已替换（新文件末尾注明 Forge 本身是 LGPL 2.1）。

## 模组元数据（2026-10-06）

- 版本：`mod_version=1.0.0-alpha`（用户说"alpha 1.0.0"；写成 `1.0.0-alpha` 是为了版本比较正确，按 Maven 规则它排在正式的 1.0.0 之前，以后的 1.0.1 等都比它新；文件名里也不会有空格）。CurseForge 上文件的显示名可以写 "Alpha 1.0.0"。
- 作者：`mod_authors=Antaurora`（原来是 "Ant Aurora"）。
- 许可字段：`mod_license=All Rights Reserved (see LICENSE.txt)`。
- 模组列表里的介绍：`mod_description` 换成英文一句话简介（只写已实现的内容）。
- 图标：徽章 `src/main/resources/afl_badge.png`（528×528，像素画），`mods.toml` 里 `logoFile="afl_badge.png"`、`logoBlur=false`（像素画不做模糊缩放）。徽章和标题图的原件放在 `design/branding/`。

---

