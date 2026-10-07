

## 项目简介 Summary（2026-10-06，Alpha 占位版用）

为什么写：用户准备先在 CurseForge 发一个 Alpha 版本占住名字（2026-10-06 查过，CurseForge、Modrinth 上 "Apocalypse First Light" 都还没人用），发布页的 Summary 栏要一段简介。只写已经实现的内容，城市生成、国家系统等还没做的不写。

英文（Summary 栏，较短）：

> A grounded post-apocalyptic survival overhaul for Forge 1.20.1: realistic guns, temperature, thirst, stamina and weight, radiation and the infected, searchable loot containers, and buildable fuel and power systems.

英文（较长，可放描述开头）：

> Apocalypse: First Light is a grounded post-apocalyptic survival mod for Forge 1.20.1. Survive with realistic firearms and ammo, body temperature, thirst, stamina and carry weight, while radiation and the infected wait outside. Search abandoned lockers, dumpsters and vending machines for supplies, and get the old world running again: fuel dispensers, underground tanks, generators, batteries, cables and fluid pipes. Early Alpha: expect missing content and breaking changes.

中文：

> 一个偏写实的末日生存模组（Forge 1.20.1）：真实手感的枪械与弹药、体温、口渴、耐力与负重、辐射与感染者；搜刮废弃的储物柜、垃圾箱和售货机，让加油机、地下油罐、电力和管道重新运转。当前为早期 Alpha，内容不完整，版本之间可能不兼容。

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
- 燃油：加油机、加油岛套件、顶棚、地下储罐、取液泵、管道（都是可以放置的部件，玩家自己搭；世界里还不会生成现成的加油站，用户 2026-10-06 指出，所以介绍里不写"加油站"）；油壶、油桶、手摇油泵、倒油动画；漏油、起火、子弹打漏和爆炸。
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
| `hero.png` | 840×420（2026-10-06 起，原 2400×1200） | 页首：标题图按原尺寸叠在夜空色带上 |
| `header_features.png` | 840×108 | "FEATURES" 分节标题 |
| `feature_gun / survival / loot / fuel / power / radiation / infected.png` | 840×120 | 七个特性的标题条（说明文字写在卡片下面的正文里，方便修改和翻译）；感染者那张 2026-10-06 加，用户确认感染者可以写进介绍 |
| `link_discord / link_wiki / link_source.png` | 396×90 | 链接按钮，在 CurseForge 编辑器里给图片加链接 |
| `notice_alpha.png` | 1200×264 | 早期 Alpha 提示 |
| `notice_modpacks.png` | 1200×264 | 整合包随意使用，请链接官方页面、不要重新上传 jar（按上面的许可） |
| `notice_coming_next.png` | 1200×544 | 路线图，标题下第一行写明"计划中，不在这个版本里"，避免被当成已有内容。内容取自 `02 - 制作清单.md` 的 V1、V2 里还没做的项（2026-10-06 逐项查过代码，都还没有；流血只有通用的液体喷射和污渍底层，没接到生物上），加上待办里的柴油发电机、燃油储罐。"其他原版改动"太笼统，没写 |
| `notice_shaders.png` | 1200×304 | 光影：可配 Oculus 和 Embeddium；测过 Complementary Reimagined、Sundial、Sundial Lite（Sundial 透明层后的发光问题已修，见 animated_block_mesh_runtime_v1.md）；枪和机器用 LabPBR 材质 |
| `notice_requirements.png` | 1200×264 | 前置：Minecraft 1.20.1 / Forge；必需 GeckoLib、Curios、TerraBlender；可选 JEI、Jade（按 mods.toml 的 mandatory） |

待定：Discord 邀请链接、Wiki 地址；源码仓库公开后再放源码按钮。

---

## 只推荐创造模式试玩（2026-10-06 用户定）

为什么：用户说这个版本不推荐生存游玩，硬要玩可以开创造摆东西，目前只推荐创造模式试玩（没有剧情、大部分物品没有配方、世界不生成战利品）。

- 正文 Alpha 卡下面改成一段粗体开头的说明："This alpha is for a look around in Creative mode. Survival is not recommended yet ..."；"Good to know" 第一条也写明创造模式。
- 用户已经在 CurseForge 编辑器里上传了图片并用 spoiler 折叠了截图；改好的完整正文（保留用户上传后的图片地址）在 `E:/Download/AFL_CurseForge_Page/description_curseforge.md`，整份粘贴即可。
- 同时修正：用户那版的 Discord 按钮链到了 GitHub 仓库、GitHub 按钮没链接；改成 GitHub 按钮链仓库，Discord 按钮链 `DISCORD_INVITE_URL`（有邀请链接再填，没有就删掉）。感染者第三条改成 "More kinds of infected are on the way."
- 如果 Alpha 卡是在加"没有配方"那行之前上传的，要重新上传 `cards/notice_alpha.png` 并替换地址。
- 页面链到了 GitHub 仓库：仓库公开前要把新的 `LICENSE.txt`（2026-10-06 改的 ARR 许可）提交上去，否则仓库里还是 Forge 模板的 LGPL 说明。

## 注意事项段落 "Good to know"（2026-10-06）

为什么：用户问页面要不要写注意事项。玩家装上后最容易当成 bug 的是原版内容被改，所以按代码里**实际改了的**写（`docs/项目内容/01 - 设计/世界与环境/原版改动.md` 是删减目标清单，不等于都已实现，没照抄），放在前置要求卡后面：
- 用新世界：`data/minecraft/worldgen` 改了主世界地形（macro terrain、continents），`tags/worldgen/biome/has_structure/*` 关掉了村庄、神殿、废弃矿井、沉船、林地府邸、海底神殿、远古城市、废弃传送门等，要塞 `structure_set` 频率为 0。
- 原版 HUD 被替换（生存 HUD、负重条占经验条位置，看不到经验等级）；`data/minecraft/advancements` 112 个原版进度全部 `forge:false`，进度界面被 `AflAdvancementScreenEvents` 挡掉。
- 桶、打火石配方关闭（`data/minecraft/recipes`），`NoMilkingEvents` 禁止挤奶，液体按升计。
- HUD 类 mod 可能冲突；依赖进度、村庄、要塞的 mod 受影响。
- 性能建议 Embeddium + Oculus；语言中英；bug 去 Discord。
- **待用户确认**：多人 / 专用服务器测试情况（没测过要写明，不能默认能用）；要塞不生成导致进不了末地是否是有意的，下界是否已关闭没有核实。

## 发布类型：Alpha（2026-10-06 用户定）

为什么：用户说"不能挂羊头卖狗肉"——还没有剧情、大部分东西没有配方，只是想让路过感兴趣的人看看。所以 CurseForge 的 Release Type 选 **Alpha**（和版本号 `1.0.0-alpha`、"EARLY ALPHA" 卡片、更新日志一致），接受 Alpha 文件不出现在 CurseForge 桌面 App、只能从网页下载。等城市、战利品等核心内容做完再发 Beta。

- 其它上传选项：审核通过后自动发布；Environment 选 Server + Client；Modloader 只选 Forge（NeoForge 没测）；Java 17；Minecraft 1.20.1；Project distribution 选允许第三方分发（和"整合包随意使用"一致）。
- 因为大部分物品还没有配方，Alpha 提示卡加了一行 "MOST ITEMS HAVE NO RECIPES YET - TRY THEM IN CREATIVE MODE."，正文 Alpha 卡下面加一句同样意思的说明，更新日志的已知问题也写上。

## CurseForge 正文图片宽度限制（2026-10-06）

为什么改：用户上传 hero 时 CurseForge 报 "Image width is too big, should be less than 850px"，正文图片必须窄于 850 像素。原来的卡片 1200 宽、hero 2400 宽、截图 1920 宽都超了。

- 卡片生成器改成 `UNIT = 3`、`CARD_W = 280`：卡片 280 格 × 3 = 840 像素（仍是整数倍放大，像素边缘清楚）；链接按钮 396 宽；hero 先按原尺寸合成，再按面积平均缩到 840 × 420。
- 卡片变窄后有几行放不下（生成器会报错），所以改了：特性条 "RADIATION & INDUSTRY" → "FALLOUT & INDUSTRY"；路线图几行改短（"CITIES, LOOT AND A NEW SPAWN SYSTEM"、"FOOD SPOILAGE, MEDICINE AND NEW GEAR"、"DIESEL GENERATORS, FUEL TANKS, RECIPES"、"LATER: SPAWN REGIONS, SEASONS"）；Alpha、整合包、光影卡的长句拆成两行。特性条加了标题宽度检查。
- `E:/Download/AFL_CurseForge_Page/images/` 的截图都缩到 840 宽（JPG 质量 92，每张 40–110 KB）。
- 画廊没有 850 像素的限制，本应用高清图；但用户已经把原始截图从 E:/Download 挪走，这次没做高清画廊版，暂时用 840 宽的图，或者用户从原图另传。

## Alpha 页面成品（2026-10-06）

为什么：用户要求先用 E:/Download 里已有的截图排版，没有图的特性只写文字；视频以后自己录。

- 位置：`E:/Download/AFL_CurseForge_Page/`
  - `description.md`：可直接粘贴的英文正文（Markdown）。上传图片后把每个本地路径换成编辑器给的地址；`DISCORD_INVITE_URL` 换成邀请链接。
  - `gallery.md`：画廊上传顺序和标题（16 张，第一张是全配件 P9）。
  - `preview.html`：本地预览，模拟 CurseForge 的深色正文栏。
  - `cards/`：卡片副本；`images/`：截图转成的 JPG（质量 88–90，大多 1920 宽，每张约 100–330 KB）。生存 HUD 用用户自己裁的版本放大 2 倍；起火两张是用户从旧截图里裁掉硬件监控框后的版本。
- 每个特性：标题条卡片 + 2–4 条英文短列表（-）+ 1–2 张截图；特性之间、开头和结尾的提示卡前后用 --- 分割线隔开（用户 2026-10-06 提出：列表比整段好扫读，分割线让分界更清楚）。七个特性都有图。
- **搜刮的说法改了**：Alpha 里世界还不会自然生成带战利品的容器（用户 2026-10-06 指出），所以正文写"可以打开、逐格搜索"，并明确说"带战利品的容器会随城市生成到来"，不写"满世界搜刮"。截图用撬棍砸售货机玻璃，不用搜索界面（没有自然生成的战利品可截）。
- 没用的旧图：烧着的村民一类画面放在画廊（`fire_on_mobs`），不放正文；带硬件监控框、旧版快捷栏、中文提示框的旧截图不用。

## 改用视频（2026-10-06，取代下面草稿里的 GIF 和折叠块）

为什么改：用户决定不做 GIF，自己在 YouTube 发视频。视频有声音（枪声、盖革计数器是卖点，GIF 表现不了）、画质高、只做一次。

- 预告视频 1–2 分钟，**嵌进正文**（放在简介和 Alpha 提示之后、FEATURES 之前），不只挂外链；CurseForge / Modrinth 正文能否嵌 YouTube 未核实，在编辑器里确认，不行就用链接。
- 每个特性保留一张**静态截图**（标题条 + 两三句 + 一张图），给不点视频的人看；不再需要 GIF 和折叠块。
- 下面草稿里的 [GIF: …] 改作视频分镜：Silverwood 换弹、按 Z 换配件、打油桶漏油起火、垃圾箱搜刮、撬棍砸售货机、雪地结霜、辐射区盖革计数器、感染者循声赶来；每段 5–10 秒，保留原声。
- 原来草稿保留作参考，特性正文和截图建议仍然适用。

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
   - 文字：Fuel dispensers, underground tanks, intake pumps and pipes - build your own station. Carry fuel in cans and drums. Spilled fuel burns, and a bullet through a drum starts a leak - or a fire.
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

画廊（不放正文的大图）：封面图 [IMG: 黄昏，开光影，手持枪 (cover.png)：可以用加油机、加油岛、顶棚、储罐自己搭一个小加油站当背景（画面里是玩家搭的，文字里不说会生成），或者换成垃圾箱、售货机、感染者组成的街景]，以及每个特性各 1–2 张静态截图。

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


## 上传后"failed processing"排查（2026-10-06，只读检查，没跑 Gradle）

CurseForge 对 `apocalypse_firstlight-1.0.0-alpha.jar` 报 "Your file has failed processing, this could be caused due to obfuscated code or corrupt files"。用 Python `zipfile` 逐项检查了这个 jar（21:40 构建，20,969,092 字节，4,726 个条目），**没找到损坏或混淆**：
- ZIP 能正常读，`testzip` 没有坏条目，没有重复条目，没有 zip64，全部 deflate；1980 年时间戳的 234 条都是目录条目，属正常。
- 没有 `docs/`、`tools/`、`.mjs`、`.bbmodel`、`.md`、嵌套 jar、jarJar 元数据。
- `META-INF/mods.toml` 完整（modId、版本、依赖、logo、描述）；MANIFEST 带 `MixinConfigs`；mixin 配置里 32 个类全部在 jar 里，refmap 存在且是 SRG 名。类文件里是 SRG 名（`m_xxxx_`），没有 Mojang 名，说明 `reobfJar` 执行过——这是 Forge 正常的重映射，不是 CurseForge 说的"混淆"。全部类是 Java 17（major 61），魔数正确，JSON 全部能解析。
- 没有网络、进程执行、类加载器、Unsafe 之类的可疑调用；只有光影兼容和地堡事件里用了反射（`AflShaderCompat`、`BunkerWorldEvents`），属正常。

发现的问题（不是损坏，但重新上传前该处理）：
1. **jar 过期**：它是在 22:54 "+old rural Removed" 和 23:00 "+old strcture remove" 两个提交之前构建的，里面还有已退役的旧乡村自然生成（`worldgen/structure_set/rural.json`、8 份 `rural_*` NBT 和配置）以及 `convenience_store_01` / `gas_station_01` / `office_midrise_01` / `suburban_house_01` 四份旧结构。要从当前 HEAD 重新构建。
2. **开发源码混进了正式包**：`build.gradle` 把 `src/dev/java` 加进了 main 源码集，`jar` 只排除了 `com/antaurora/apofirstlight/dev/**`。所以 `src/dev` 里放在 `worldgen/highway`（17 个）和 `worldgen/rural`（33 个）包下的 50 个类都被打进了 jar（含 `HighwayDebugCommand`，它没有在正式包里注册，是死代码）。当前 HEAD 的 `src/dev` 里这两个包仍有 17 + 24 个文件，重新构建也会带上。建议 jar 任务按 `src/dev` 的实际文件排除，或者不再把 `src/dev` 并进 main。
3. `mods.toml` 的许可写着 "see LICENSE.txt"，但 jar 里没有 `LICENSE.txt`，建议打进 jar 根目录。
4. `/afl_author` 建造命令在正式包里，但配置默认关闭（`buildingAuthoringEnabled=false`），不会暴露给玩家。

结论：jar 内容看不出会让 CurseForge 处理失败的原因；这条提示是通用文案，处理失败也可能出在他们那边。建议修完上面 1–3 后重新构建上传；如果还失败，带上文件 ID 联系 CurseForge 支持。
