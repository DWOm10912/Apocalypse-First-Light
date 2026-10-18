# AFL 玩家 Wiki V1

状态：本地站点 V1 已搭好（2026-10-18），中英双语，`npm --prefix wiki run build` 通过。还没有上线，用户还没有在浏览器里看过实际站点（只看了截图）。

## 为什么要做

- 准星提示和机器界面只写一个动作或读数，说明和规则统一放到 wiki（见 `docs/models/building_power_v1.md`、`docs/machines/diesel_standby_generator_v1.md`）。
- CurseForge 发布页改成短页面，细节链接到自建 wiki（见 `docs/项目内容/04 - CurseForge 等 mod 发布页.md`）。
- 用户自己写的 Obsidian 中文文档经常忘记更新。wiki 的数值必须从游戏数据生成，不靠手抄。

## 定下来的方向

- 域名：用户的 `staticgrove.net`（子域名待定，例如 `wiki.staticgrove.net`）。
- 形式：VitePress 1.6.4 静态站，源文件在仓库 `wiki/`。托管计划用 Cloudflare Pages；账号和 DNS 由用户自己操作。
- 风格：概念 B（现代游戏官网式），吸收 A 的完整参数表、伤害衰减曲线、更新历史和版本标识（用户 2026-10-17 选定）。不再出第三套风格。
- 首页不突出枪械：首屏轮换森林、载具、建筑供电、体温与天气、枪械五个系统。
- 优先级：自动数据同步、中英双语、移动端、和实际游戏版本一致。
- 只写已经实现的内容；未实装的用状态标签明确标出。

## 结构

| 路径 | 作用 |
|---|---|
| `wiki/.vitepress/config.mts` | 站点配置：中文根目录 + `/en/`、导航、侧栏（枪械列表从数据生成）、本地搜索、强制深色 |
| `wiki/.vitepress/lib/gamedata.mjs` | 读游戏数据：`gradle.properties` 版本、git 提交、lang、`native_guns`、`native_attachments`、`item_mass`、`item_size`、`worldgen/biome` + `temperature_v1`，以及少量 Java（弹匣发数、消音器默认系数、`NativeAccuracyProfile`）。枪械数值历史来自 `git log` |
| `wiki/.vitepress/lib/values.mjs` | 页面里的 `<V d="文件#键.路径" />`（datapack JSON）和 `<V j="类名.常量" />`（Java 数值常量）在构建时解析 |
| `wiki/.vitepress/data/*.data.mjs` | VitePress 数据加载器；开发服务器在游戏数据改动时自动刷新 |
| `wiki/.vitepress/theme/components/` | 首页、版本卡、枪械头卡 / 对比条 / 参数表 / 衰减曲线 / 配件、枪械表、弹药表、配件表、生物群系表、精度表、数值历史、状态标签、截图 |
| `wiki/arms/guns/[gun].md` | 每把枪一个动态页，数据来自 `native_guns`；介绍文字在 `wiki/content/guns/<id>.<zh|en>.md` |
| `wiki/images.json` + `scripts/sync-images.mjs` | 截图清单，用 sharp 转成 `public/img/*.webp` |
| `wiki/scripts/check-wiki.mjs` | 构建前检查（见下） |

页面：首页；新手指南（开局、按键、生存状态、背包与负重、辐射）；枪械与装备（总览、6 把枪、弹药、配件、装备）；载具（总览、T15、燃料与加油）；工业制造（总览、发电机、建筑供电、材料与加工）；世界与探索（总览、生物群系、道路、地点、森林、天气）；感染体；更新日志（版本、数值变化）；关于数据。每页都有英文版。

## 数据同步

- 数值不手写：datapack 和 Java 常量用 `<V>` 引用；枪、弹药、配件、生物群系的表直接从数据生成。
- 少数值还写死在代码逻辑里（T15 的发动机 / 变速箱 / 油耗在 `VehicleSpec` 构造器里，感染体破门时间在 `InfectedBreachRules.breakTicks`，走路 / 跑步噪音是 private 常量），页面上是手写文字，改这些代码时要手动同步。
- 页脚和版本卡显示 mod 版本、MC / Forge 版本、数据所在的提交和构建日期；数据文件有未提交改动时版本卡会提示。
- 枪械伤害衰减按 `NativeGunShot.damageAt`：衰减起点前满伤害，之后线性降到最大射程处的 `min_damage_multiplier`，超过最大射程没有伤害；`effective_range` 只是物品说明里的“射程”。

## 检查

`npm --prefix wiki run check`（`build` 会先跑）失败条件：

- 任何 `<V>` 引用找不到值；
- 游戏数据读不出来（JSON 错误、没有枪或生物群系）；
- 枪缺中文或英文介绍、缺 lang 名称、精度档案在 Java 里找不到、弹匣没有发数、消音器没有系数；
- 某页只有一种语言；
- `images.json` 里的图片没转出来。

页面里引用了但 `images.json` 没有的截图只报警告（页面显示“游戏截图待补”）。VitePress 构建本身会检查死链。

## 运行

- 开发：`npm --prefix wiki run dev`（端口 5178，`.claude/launch.json` 里的 `afl-wiki`）
- 构建：`npm --prefix wiki run build`，输出 `wiki/.vitepress/dist/`
- 更新截图：改 `wiki/images.json` 后 `npm --prefix wiki run images`

## 枪械主图（透明背景渲染）

- 做法：在 Blockbench 里打开每把枪的源模型（`src/main/blockbench/<id>.bbmodel`；C.A.T 没有 bbmodel，用 `assets/.../geo/cat.geo.json` + `textures/item/cat.png`），隐藏手、手臂参考方块、换弹用的弹匣和相机组，用离屏视图正交“等距左”拍（相机方向约 (80, 70, −86)，即枪口朝右、看到枪的左侧面），预览亮度临时调到 100，背景透明。拍完恢复模型可见性和亮度。
- 原图放 `design/wiki/gun_renders/raw/<id>.png`；`node wiki/scripts/frame-renders.mjs` 裁掉透明边、统一居中到 1600×1000（模型最多占 84% × 76%），输出 `design/wiki/gun_renders/<id>.png`，`wiki/images.json` 的 `gun_<id>` 指向它。
- 枪械页头卡和枪械总表（缩略图）都完整显示、不裁切，底下是浅一点的深灰渐变，黑色枪身才看得清。首页“枪械与装备”卡片改用游戏内截图 `hero_guns`。
- 局限：Blockbench 只画底色贴图，没有法线和高光贴图，金属偏平；银色的 Blackridge 在亮度 100 下偏亮。改了枪模型要重新拍。
- 2026-10-18 已出 6 把：P9-01、Blackridge .50、BR-51、HR-55、Silverwood 12、C.A.T。

## 截图

已用：开发中的截图和概念图（T15 概念图、老宅、森林、寒冷画面、P9 V3、HUD 概念、联邦地图、维护台）。

还要用户在游戏里拍：T15 实车（行驶、夜间车灯、雨中雨刷）、雨（`rain`）、感染体（`infected`）、发电机和配电箱、格子背包界面、生存 HUD。

页面截图（2026-10-18，桌面 1440 / 手机 390）：`design/wiki/v1_build/*.png`。

## 上线步骤（用户操作）

1. Cloudflare 开账号，把 `staticgrove.net` 的 DNS 托管到 Cloudflare。
2. Cloudflare Pages 新建项目：连接 GitHub 仓库，构建命令 `npm --prefix wiki ci && npm --prefix wiki run build`，输出目录 `wiki/.vitepress/dist`；或者本地构建后用 `wrangler pages deploy` 上传。
3. 绑定子域名（例如 `wiki.staticgrove.net`）。
4. 网站数值跟着游戏版本发布：发布 jar 时同一提交构建网站。

## 概念设计第 1 轮（2026-10-17）

`design/wiki/concept_v1/`：A 经典游戏 Wiki、B 现代游戏官网式，各有首页和条目页截图。用户选 B。

## 待办

- 用户在浏览器里看实际站点（`npm --prefix wiki run dev`）。
- 补游戏内截图（见上）。
- 上线（用户的 Cloudflare 账号和 DNS）。
- 写死在代码逻辑里的数值以后挪成常量或数据，让页面也能自动读。
