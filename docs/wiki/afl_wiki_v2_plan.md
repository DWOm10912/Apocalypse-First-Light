# AFL Wiki V2：内部开发资料库

状态（2026-10-19）：**已上线** `https://wiki.staticgrove.net`，受 Cloudflare Access 保护（只允许用户的邮箱）。Main 在 2026-10-19 部署了两次（部署 9dff22f2、4c513374），内容是 master b41c6f7c 加上还没提交的 Wiki 改动。用户确认了 Access 设置；部署后用未登录的请求查过：自定义域名、`afl-wiki.pages.dev`、每次部署的专属地址、`main` / `probe` 预览、任意子域名和静态文件，都跳到 Access 登录页。页面内容是在本地用同一份构建检查的（我不能替用户登录），用户还没在手机上打开过线上站点。

## 1. 定下来的事（用户 2026-10-18 批准）

- Wiki 先作为用户个人用的在线开发资料库，不对外公开。保留 V1 的 B 版设计和 VitePress（见 [afl_wiki_v1_plan.md](afl_wiki_v1_plan.md)），不重做。
- 地址 `wiki.staticgrove.net`，托管在 Cloudflare Pages，用 Cloudflare Access 限制访问。不靠"不公开网址"保护。
- 本地构建，用 Wrangler Direct Upload 上传；不用 GitHub 自动构建。
- 图片放现有 R2 Bucket `antaurora-assets` 的 `afl/wiki/<分类>/`，地址走 `img.staticgrove.net`，复用 Page Studio 的上传代码和凭据。那是公开域名：内部保密图片不能传上去。
- 先用一个没有内容的测试页验证 Access，确认生产地址、预览地址和静态资源都要登录以后，才能部署完整 Wiki。
- 部署前必须通过校验；失败时不覆盖线上版本。**只有 Main 部署**，C、D 只维护资料。
- GitHub 仓库要改成 Private：用户自己在 GitHub 设置里改。只读检查结论见第 8 节。

## 2. 页面状态

每条功能说明前面放一个 `<StatusTag>`：

| 写法 | 页面上 | 什么时候用 |
|---|---|---|
| `s="verified" d="YYYY-MM-DD"` | 已实机验收 · 日期 | 已经在 master 里，用户在游戏里验过。日期必须写 |
| `s="integrated"` | 已集成，未实机验收 | 已经在 master 里，还没人在游戏里看过 |
| `s="test"` | 测试中，普通世界还没有 | 只在测试存档、开发命令或工作区分支上 |
| `s="planned"` | 规划中，还没有 | 只有设计或计划 |
| `s="creative"` | 只能在创造模式拿到 | 做好了，但生存模式拿不到（没有配方或战利品） |

- 分支上的东西不能标 `integrated` 或 `verified`，只能是 `test` 或 `planned`。
- `check-wiki` 会拦：未知的值、`verified` 没写日期、别的值带日期、frontmatter `status` 不是这几种。
- 一行开头的 `<StatusTag>` 在 markdown 里会变成 HTML 块，两段这样的行连在一起会挤成一行。连着写两条时用列表（`- <StatusTag ... />`）。
- 设定页（世界观、企业）另外用"已冻结 / 已确认 / 候选"三个词，和 `docs/world/federation_companies_v1.md` 一致；frontmatter 写 `internal: true`。以后如果要出公开版，先按这个标记把它们排除。

## 3. 新增的内容（V2 这一轮）

| 页面 | 数据来源 |
|---|---|
| `guide/survivor`：模式、背景、身体、特质 | `survivor_backgrounds` / `survivor_body` / `survivor_traits` JSON、语言文件；`SurvivorSetup.SUCCESSOR_MIN/MAX`、`SurvivorCatalog.NEGATIVE_POINT_CAP` |
| `guide/growth`：枪械、耐寒耐热 | `survivor_growth` JSON；枪械每级的瞄准晃动和后坐力回复用正则从 `SurvivorGrowth.adsSway / recoilRecovery` 读出公式 |
| `guide/statuses`：HUD 状态图标 | 状态列表和好坏按 `StatusBadgeHud.java` 手写在 `wiki/.vitepress/lib/survivor.mjs`（STATUSES），名字和效果读语言文件，口渴的数字读 `thirst_v1.json` |
| `world/lore`：联邦、六州、16 座城镇 | 城镇名和规划点读 `RoadNames.TOWNS`、`FederalTemplate`，中文名读 `docs/worldgen/federal_towns_v1.md` 第 1 节的表 |
| `world/companies`：七家企业、枪械归属、候选库 | `docs/world/federation_companies_v1.md`（手抄，改档案时要同步） |
| `world/places`、`world/roads`：Linden、路口和立交 | C 分支 `feature/c3-towns-wiki` 的提交 12052746，Main 合进来（Linden 改成已实机验收 2026-10-18，去掉中文括注） |
| `world/weather`：闪电 | `AflLightning.SOUND_SPEED` |
| `changelog/updates`：开发记录 | master 的 first-parent git log（最近 80 条），按改动的目录标 wiki / 数据 / 代码 / 文档 |

另外：中文页面里的"体力"按术语表统一成"耐力"；`guide/index` 的建世界流程改成新的世界页 + 幸存者设置。

新组件：`TraitTable`、`BackgroundTable`、`LevelTable`、`StatusTable`、`GunLevelTable`、`TownTable`、`UpdateLog`。

## 4. 版本和更新记录

- 页脚：`分支@提交 · 同步 本地时间`；版本卡多了分支和同步时间。同步时间是构建时间。
- `changelog/updates` 列出 master 最近的提交。只显示已经进 master 的东西，分支上的内容不会出现。
- 部署时生成 `build.json`（提交、分支、提交说明、构建时间）。

## 5. 图片

`wiki/images.json` 每张图有 `category`（guns、vehicles、items、industry、survival、world、companies、ui）和 `visibility`：

- `internal`：输出到 `wiki/public/img/`，只跟着受 Access 保护的站点走。现在是 `afl_title`、`favicon`、`world_map`。
- `public`：上传到 R2，地址记进 `wiki/images.lock.json`，页面用这个地址。还没传的放在 `public/img/staged/`（git 忽略），本地预览能看，正式部署会被拦。
- 拿不准的一律写 `internal`。未公开剧情、隐藏设施、内部资料的图不能写 `public`。
- **2026-10-19 用户决定：** 之前截的测试图（首页大图、HUD 概念、维护台）先不上传，改成 `internal`；优先上传枪械平面图这类 3D 渲染图。6 张枪械平面图（`design/wiki/gun_renders/`，Blockbench 渲染，没有文字或场景）和 7 张模型大图（第 5.1 节）是 `public`，已传到 R2。以后正式截图出来，再一张张改成 `public` 上传。

命令（在 `wiki/` 里）：

| 命令 | 作用 |
|---|---|
| `npm run images` | 转图：internal 输出，public 里没传过的放进 staged |
| `npm run images:upload` | 列出要上传的图（演练，不传） |
| `npm run images:upload -- --yes` | 上传。只能在 Main（主仓库、master）上跑 |
| `node scripts/images.mjs --check --remote` | 对每个公开地址发 HEAD，检查是否还在、大小对不对 |

- 对象名：`afl/wiki/<分类>/<key>.<宽>w.<sha256 前 14 位>.<格式>`。内容变了就是新名字，旧对象不删不改。
- 上传用 Page Studio 的 `server/cloud/store.ts`（条件写入，从不覆盖），凭据在 `~/.page-studio/r2.json` 或环境变量 `PAGE_STUDIO_R2_*`，不进仓库。Page Studio 不在旁边时设 `PAGE_STUDIO_DIR`。
- lock 里记着源文件签名（源文件字节 + 转换设置）。源图或设置变了，下次 `npm run images` 会重新放进 staged，提示重新上传。
- 检查：internal 的图出现在 lock 里直接报错；部署后的检查会扫一遍输出，内部图的 key 不能出现在公开域名下。

### 5.1 物品图标和模型渲染图（2026-10-19）

用户 2026-10-19 要求：物品和方块都用 3D 图，不用平面贴图。图标和大图都由 Main 在 Blockbench 里用游戏自己的模型渲染。

- 页面写法：表格或列表里用 `<ItemIcon id="<物品 ID>" />` 放小图标；章节里用 `<ModelShot id="<物品 ID>" zh="…" en="…" />` 放大图。
  - 小图标是 128 px 的 webp，放在 `wiki/public/img/icons/`，跟着站点打包，不传 R2。
  - 大图要在 `images.json` 里登记 `model_<id>`，`src` 填 `design/wiki/icons/shots/<id>.png`。这些大图是模型渲染，没有文字、没有场景，和枪械平面图同类，所以设成 `public`。
- 模型来源由 `wiki/scripts/icons.mjs` 按游戏解析物品模型的顺序自动判断：
  - 由渲染器画的物品（`builtin/entity`、`separate_transforms` 的 3D 部分、`forge:composite`）：用 `src/main/blockbench/<id>.bbmodel`。文件名不同时，在 `wiki/icons.json` 里用 `bbmodel` 指定。现在指定了三个：热能发电机用 `thermal_generator_3d`，配电箱用 `distribution_panel_v1`，发电机进线盒用 `generator_inlet_box_v1`。
  - 方块元素模型（矿石、机器、台阶等）：把模型和原版父模型展平后渲染。
  - `forge:obj`：直接读 OBJ，贴图取 `.mtl` 里写的那张。
  - 没有 3D 模型的平面物品（矿粉、精矿、碳酸锂等）：把贴图挤出 1 像素厚，和游戏里拿在手上、掉在地上时的样子一样。
- 视角：
  - 先用游戏自己的物品栏角度：沿着物品模型和它的 AFL 父模型往上找，取第一个 `display.gui.rotation`。设备的模型都是正面朝北建的，靠这个角度把正面转向镜头，比如柴油发电机是 `[20, 200, 0]`，插座是 `[10, 200, 0]`。
  - 游戏里没写角度的：方块用原版物品栏角度 `[30, 225, 0]`，手持物品默认 `[25, -32, 0]`（3/4 俯视）。
  - 2026-10-19 第一版没有读游戏角度，设备统一用了默认角度，结果柴油发电机等拍成了背面朝前（用户指出），当天改正。
  - 个别物品在 `icons.json` 里用 `rot` 改角度：背包、枪套 `[25, 148, 0]`（正面朝外），撬棍 `[12, -18, -45]`（侧面）。
  - 渲染脚本拍完会检查模型有没有碰到画面边缘，碰到就缩小重拍，避免裁边。
  - R2 上第一版那 7 张背面朝前的大图没有删（上传工具只新建、不删除），页面已经不再引用它们。
  - 野外求生指南（2026-10-22，合并 AFL-Workbench 时补渲）：物品栏用的是平面图，脚本取不到游戏视角，按默认角度拍成了书背；`icons.json` 里给了游戏图标的视角 `rot` [10, 25, 0]。Blockbench 和游戏在 x 方向是镜像的，封面字拍出来是反的，所以加了 `mirror: true`：`icons.mjs` 生成图标和大图前把渲染图左右翻转（只有这一件用）。
  - 普雷里公路地图 `prairie_road_map`（2026-10-22）：Blockbench 加载这个方块模型时，只要铅笔在，地图纸就画不出来（单独加载纸是正常的），渲染出来只有一支铅笔。所以这一张不走 Blockbench：`wiki/scripts/map-icon.mjs` 按游戏的物品栏视角 [62, 0, 0] 直接从贴图拼出正交投影（纸的顶面按 sin 62° 压扁，铅笔顶面转 22.5°），写到 `raw/prairie_road_map.png`，再照常跑 `icons.mjs`。模型或贴图改了要重跑这个脚本。
  - 不该出现的部件用 `hide`（正则）隐藏：配电箱隐藏上下接线口和穿线管，充电站隐藏亮灯。
  - P9-01 17 发弹匣（2026-10-19）没有自己的 bbmodel：用 `p9_01_v3_native.bbmodel`，`hide` 掉枪身、滑套、换弹用的两份弹匣，只留 `magazine` 组的三个部件。`rot` [170.257, 40.967, -159.464] = 物品栏角度 [20, 135, 0] 再乘上物品渲染器把弹匣扳正的 +22°（绕 x）。2026-10-20 和翻盖打火机、17+ 加长弹匣一起渲染，三张都看过：正面朝前、弹匣竖直，同一天部署。
  - P9-01 17+ 加长弹匣（2026-10-20）：按 ID 自动找到的 `p9_01_extended_magazine.bbmodel` 是旧的方块版（只作历史保留），游戏用的是 `p9_01_extended_magazine_mesh.bbmodel` 导出的网格（用户指出）。`icons.json` 里指定了 `bbmodel`，`rot` 和 17 发弹匣一样（网格里同样带握把的 22° 倾角，物品渲染器会扳回来）。重新渲染后已经部署。同一天扩容弹匣按 V3 原装弹匣重做，这个 `_mesh.bbmodel` 改由 V3 生成器写出，图标重新渲染。Blockbench 里开着别的工程时不要跑渲染脚本（它会先关掉所有工程）。
- 流程（在 `wiki/` 里）：
  1. `node scripts/icons.mjs jobs`：给缺图或过期的 ID 写 `design/wiki/icons/jobs.json`。
  2. 在 Blockbench 里跑 `tools/blockbench/wiki_icons.js`：需要先用 Blockbench MCP 建一个 1024×1024 的离屏视图 `icon`，再 `eval` 这个文件并执行 `__aflWikiIcons.run()`。脚本逐个打开模型，用正交相机按物品的展示旋转拍一张透明底 PNG，存到 `design/wiki/icons/raw/<id>.png`，并在 `raw/manifest.json` 里记下源文件签名。
  3. `node scripts/icons.mjs`：裁掉透明边，生成图标；用到 `<ModelShot>` 的 ID 再生成 1600×1200 的大图，存到 `design/wiki/icons/shots/`。
  4. `npm run images` 和 `images:upload`：和其他公开图片一样处理。
- 检查（`check-wiki` 第 5b 步）：模型、贴图或视角变了，对应的渲染算过期，给警告。缺图标的 ID 平时只警告（C、D 可以在分支上先写 `<ItemIcon>`，页面上会自动隐藏这张坏图）；部署时的 `--strict` 检查遇到缺图标会报错。
- 2026-10-19 第一批共 61 件：装备 10 件、燃料容器和加油站 6 件、矿石 7 种、矿物和加工产物 14 种、机器 10 台、发电机 3 台、电气件 10 件、枪械维护台 1 件。7 张大图已传 R2：油桶、加油机、柴油发电机、便携发电机、热能发电机、配电箱、枪械维护台。配件页原来那张维护台测试截图换成了模型渲染图；测试截图本身还在 `images.json` 里，是 `internal`。
- 图标的光照用的是 Blockbench 预览光，不是游戏里的光照，所以和物品栏里的样子不完全一样。图都还没在游戏里对照看过。
### 5.2 状态效果图标（2026-10-19）

用户 2026-10-19：状态效果页每一行前面放上 HUD 里对应的圆形图标，透明背景。

- 这些图标不是贴图，是 HUD 代码里画的：用 SDF 字形加上按种类区分的底色圆、高光和描边，见 `StatusBadgeHud#badgeBands` / `#badgeGlyph`，以及 `AflGauge#raster`。
- 离线导出：运行 `gradlew wikiStatusIcons`（测试代码 `client/StatusBadgeIcons`），直接调用游戏里的这几段代码，按 8 倍画成 106×106 的透明 PNG，存到 `wiki/public/img/status/<id>_<阶段>.png`，跟站点一起打包，不上传 R2。导出的图不带剩余时间的弧线，也不带新状态的闪光。
- 每一档用什么字形、什么颜色，照搬 `StatusBadgeHud#collect`。新增状态，或者改了某一档的颜色，要同时改 `StatusBadgeIcons`，并重新导出。
- `StatusTable.vue` 在每行名称前显示图标；`check-wiki` 遇到缺图会报错。
- 第一批 25 张，和状态页的 25 行一一对应。

## 6. 部署

`npm --prefix wiki run deploy`（`wiki/scripts/deploy.mjs`）：

1. 只能在 Main 跑：主仓库（不是关联工作树）、master 分支；`src/main/resources`、`src/main/java`、`gradle.properties` 不能有未提交的改动（游戏数值必须是那个提交的）；`wiki/deploy.config.json` 的 `accessVerified` 必须是 `true`。`wiki/` 自己可以有未提交的改动（用户自己安排提交）：这时页脚在提交号后面写"+ 未提交的 Wiki 改动"，版本卡也会提示，`build.json` 记 `uncommittedWikiFiles`。
2. `check-wiki --strict`：数值引用、游戏数据、幸存者和城镇数据、两种语言、状态标签、图片（含等待上传的图和远程 HEAD）。
3. VitePress 构建到临时目录（站内死链会让构建失败）。
4. 构建后检查：必需文件；每个 `img.staticgrove.net` 地址都在 lock 里；没有引用具体的 staged 图片文件（主题代码里本身有 `/img/staged/` 这个备用前缀，不算）；外部链接 HEAD（只警告）。
5. 生成 `robots.txt`（全部 Disallow）、`_headers`（noindex、禁止嵌入、nosniff、assets 长缓存）、`build.json`。
6. `wrangler pages deploy` 到生产分支。任何一步失败都会停下，线上保留上一个版本。

- `npm --prefix wiki run deploy -- --dry-run`：除了上传都做，任何工作区都能跑。
- `npm --prefix wiki run deploy:probe`：只上传一个没有内容的测试页和一个测试文件，生产分支和 `probe` 预览分支各一份；第一次跑时会建 Pages 项目。不需要 Main。
- Wrangler 固定 4.147.0（`wiki/package.json`），用 node 直接跑它的 JS 入口，不经过 shell。

## 7. 上线步骤（2026-10-19 全部完成）

1–7 由用户完成并在 2026-10-19 确认；`accessVerified` 由 Main 按用户的确认打开。第 8 步按用户的决定只传了枪械图。

1. **GitHub 改 Private**：仓库 Settings → General → Danger Zone → Change visibility。
2. **登录 Wrangler**：在 `wiki/` 目录里跑 `npx wrangler login`，浏览器里授权 Cloudflare 账号。凭据存在用户目录，不进仓库。
3. **部署测试页**：`npm --prefix wiki run deploy:probe`。它会建 `afl-wiki` 项目并上传测试页。
4. **开 Access**：Cloudflare 控制台 → Zero Trust → Access → Applications → Add an application → Self-hosted。一个应用里加三个域名：`afl-wiki.pages.dev`、`*.afl-wiki.pages.dev`、`wiki.staticgrove.net`；策略 Allow，只放用户自己的邮箱（登录方式用一次性验证码就行）。也可以在 Pages 项目的 Settings 里先点 "Enable access policy"，再到 Zero Trust 里改策略、补域名。
5. **验证**：用没有登录过的设备或无痕窗口打开下面 4 个地址，每个都必须先跳到 Cloudflare Access 登录页：
   - `https://afl-wiki.pages.dev/`
   - `https://probe.afl-wiki.pages.dev/`
   - `https://afl-wiki.pages.dev/assets/probe.txt`
   - 还有 `wrangler` 打印出来的那次部署的专属地址（`https://<哈希>.afl-wiki.pages.dev/`）
6. **绑定域名**：Pages 项目 → Custom domains → `wiki.staticgrove.net`。绑好后再用无痕窗口打开一次，确认也要登录。
7. **打开开关**：确认都受保护以后，把 `wiki/deploy.config.json` 的 `accessVerified` 改成 `true`，`accessVerifiedAt` 写日期。这一步只由用户做。
8. **第一次传图**：先跑 `npm --prefix wiki run images:upload` 看清单（现在是 13 张：6 张枪械主图、5 张首页大图、HUD、维护台），确认都能公开以后，Main 再跑 `-- --yes`。
9. 之后每次 Main 集成完：`npm --prefix wiki run deploy`。

## 8. GitHub 改 Private 的影响（只读检查，2026-10-18）

- 仓库 `DWOm10912/Apocalypse-First-Light` 现在是公开的。
- 没有阻断项：Wiki 部署不读 GitHub；没有 GitHub Actions、Pages 或别的服务依赖公开仓库；各工作区都是本机的克隆和工作树，推送用的是用户自己的账号。
- 改成 Private 以后，推送和拉取都要认证（用户是仓库所有者，已登录的 git 凭据照常能用）。
- 已经公开过的历史收不回来：别人可能已经克隆或 fork 过。

## 9. 跨工作区规则

写在 `AGENTS.md` 的 "Development Wiki" 一节：

- C、D 改了自己负责的功能或设定，在同一个任务里更新 `wiki/` 对应页面（中英两份）和 `images.json`，分支上的内容标 `test` 或 `planned`。
- Main 合并以后把状态改成 `integrated`，用户实机验收后改成 `verified` 加日期，然后部署。
- 只有 Main 部署、只有 Main 上传图片。
- 报告里写 `WIKI UPDATED = YES/NO/NOT NEEDED (<原因>)`；Main 还要写是否已部署。

## 10. 搜索

VitePress 本地搜索只收录 markdown，组件画出来的表不在里面。`wiki/.vitepress/lib/search-text.mjs` 在建索引前把数据组件（特质、背景、状态、成长项、枪械等级、城镇）换成它们显示的名字，所以搜"运动员"或者 "Bellhaven" 能找到对应的页和小节。页面本身不变。

## 11. 还没做的

- 用户还没在手机上打开过线上站点。
- 首页大图和概念图还是内部图片；正式的游戏截图还没拍。
- 木材、建筑方块、加油站其他部件、家具还没有图标（第 5.1 节的第一批只覆盖现有表格里的物品）。原木走网格树，不是元素模型，要另外处理。
- 游戏内截图还缺：雨（`rain`）、感染体（`infected`）等，见 V1 文档。
- 写死在代码逻辑里的数值（T15 的发动机和变速箱、感染体破门时间、走路噪音）页面上还是手写。
- 企业页是从档案手抄的；档案改了要同步。
- 状态标签只核对了这一轮碰到的页面，以及全部 creative、test、planned 标签（2026-10-19）。
