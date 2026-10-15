# AFL 全局字体 V1（AFL Sans SC）

状态：**V1，用户实机 PASS（2026-10-14）**；同日修复放大文字的发软和细框（第 4 节第 4、5 项），修复后用户实机 PASS。开发位置：Worktree `AFL-survivor-v0`（分支 `feature/survivor-v0`），未合并。

## 1. 决定

2026-10-14 用户决定：

- 整个游戏的文字换成现代平滑黑体，摆脱原版像素字体。原版英文是 8 像素位图，中文是 Unifont 点阵，放大后很难看。
- 不能假设玩家装了美化类模组，字体随 AFL 提供。
- 不用像素字体。
- AFL 不商用，但会公开发布。

微软雅黑是微软和方正的商业字体，不能随模组分发。选定 **Noto Sans SC**（思源黑体简体，Google / Adobe）：风格与微软雅黑同类，SIL OFL 1.1 授权，允许修改和随软件公开发布。

## 2. 字体文件与授权

| 项 | 内容 |
|---|---|
| 来源 | Google Fonts 官方仓库 `github.com/google/fonts`，`ofl/notosanssc/NotoSansSC[wght].ttf`（可变字体，17,772,300 字节，版本 2.004）与 `OFL.txt`。原文件不进仓库 |
| 授权 | SIL Open Font License 1.1；版权头 “(c) 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source'.” |
| 运行时文件 | `src/main/resources/assets/apocalypse_firstlight/font/afl_sans_sc.ttf`（字重 400，2,238,440 字节） |
| 授权文件 | `src/main/resources/assets/apocalypse_firstlight/font/afl_sans_sc_OFL.txt`（OFL 全文，随 jar 分发） |
| 生成工具 | `tools/build-afl-sans-sc.py`（Python + fontTools） |

裁剪属于“修改版本”，所以：

- 内部字体名改为 `AFL Sans SC`（PostScript 名 `AFLSansSC-Regular`），不用保留字体名 Source，也不沿用上游的 Noto 家族名。
- 保留原版权声明（name ID 0）和授权说明（13、14），描述（10）写明来源与授权。
- 字体文件不单独出售。

生成方法（字重 400 进 jar）：

```bash
python -I tools/build-afl-sans-sc.py --src <NotoSansSC[wght].ttf> --weight 400 --out src/main/resources/assets/apocalypse_firstlight/font/afl_sans_sc.ttf --lang <原版 zh_cn.json>
```

- 从可变字体取一个静态字重（默认 400）。
- 去掉排版表（GSUB / GPOS 等）和微调指令；Minecraft 用不到。
- 原版 `zh_cn.json` 在 Gradle 资源缓存里（`assets/indexes/5.json` 中 `minecraft/lang/zh_cn.json` 的哈希）。

字符集：

- GB2312 全部 7445 个码位（6763 个汉字，以及标点、假名、希腊和西里尔字母、拼音）。
- ASCII、Latin-1、拉丁扩展 A、通用标点、货币符号、CJK 标点、全角形式。
- AFL `en_us` / `zh_cn` 与原版 `zh_cn` 语言文件里出现的全部字符。
- 去掉制表符和方块元素（U+2500–U+259F），这些继续用原版字形。
- 结果：7763 个码位，其中 6765 个汉字。另有 237 个想要的码位 Noto Sans SC 本身没有（主要是拉丁扩展 A 的一部分），由原版字体显示。

字体里没有的字（生僻字、其他文字、表情符号等）按顺序退回原版字体：原版位图字体，再到 Unifont。

## 3. 接入

`src/main/resources/assets/minecraft/font/default.json` 与 `uniform.json`（“强制使用 Unicode 字体”选项用的字体）各放一个 provider：

```json
{"type": "ttf", "file": "apocalypse_firstlight:afl_sans_sc.ttf", "size": 8.5, "shift": [0, 7]}
```

- 原版把所有资源包的同名字体定义合并，优先级高的包排在前面。模组资源高于原版、低于玩家自己选的资源包，所以 AFL 字体排在原版字体前面；玩家装的字体资源包仍然可以覆盖它。
- 只有文件在 AFL 命名空间里的 `ttf` provider 才由 AFL 加载（`mixin/client/AflTrueTypeDefinitionMixin`），字段含义与原版不同：
  - `size`：em 大小，界面单位；
  - `shift[1]`：基线到行顶的距离，界面单位；
  - `shift[0]` 不用；`oversample` 忽略。
- 当前取值：em 8.5，基线 7。原版行高固定 9，原版英文基线也在 7。
  - 汉字墨迹约 7.6 单位高，和原版 Unifont 汉字差不多。
  - 英文大写约 6.2 单位高，比原版的 7 略小。
  - 汉字字宽 8.5 单位，原版 Unifont 是 9，所以中文略窄，不会让原有界面溢出。
- 加载失败时原版会拒绝这个 provider，游戏退回原版字体。

## 4. 渲染修补

原版 TTF 渲染（`TrueTypeGlyphProvider`）有三个问题，直接用会发糊或笔画粗细不均。AFL 用自己的 provider（`client/font/AflSmoothFontProvider`）替代（第 1–3 项）。第 4、5 项是用户实机发现放大文字的问题之后补的：

1. **分辨率跟随界面缩放。** 原版按 JSON 里固定的 `oversample` 栅格化，再按界面缩放最近邻放大，缩放一不对就粗细不均。另外原版把 `shift` 和左侧距各算了两次。AFL：
   - 每个字形按 `em × 界面缩放` 像素栅格化，画回屏幕时一个字形像素对一个屏幕像素；
   - 字宽和基线取整到整屏幕像素，在整像素位置画的文字正好落在像素格上。
   - 界面缩放变化时（`mixin/client/AflWindowGuiScaleMixin`，在界面重新排版之前），所有含 AFL 字体的字体集切到新分辨率，并清空字形缓存和图集纹理（`mixin/client/AflFontSetMixin`，provider 不关闭，不需要重载资源）。
2. **阴影和粗体偏移变细。** 原版两者都是错开 1 个界面单位再画一遍（缩放 4 时是 4 像素）。AFL 改为半个界面单位，取整为整屏幕像素，至少 1 像素：
   - 缩放 1、2：1 像素；
   - 缩放 3、4：2 像素；
   - 缩放 5、6：3 像素。
3. **线性过滤。** 含 AFL 字体的字体集，其灰度图集页（只放 TTF 字形）改用线性过滤的文字渲染类型（`client/font/AflFontRenderTypes`，着色器与原版文字相同）。
   - 界面里 1:1 绘制，线性与最近邻取到的是同样的像素；
   - 放大的文字是插值，不是方块像素。
   - 位图和 Unifont 字形在彩色图集页上，保持原版的最近邻过滤。
4. **放大的界面文字按实际尺寸栅格化（2026-10-14 修复）。** 原版标题放大 4 倍、副标题放大 2 倍；以前是把界面尺寸的字形拉大，边缘发软。
   - 现在每个字画出来之前（`mixin/client/AflFontStringOutputMixin`，替换 `Font.StringRenderOutput.accept` 里的 `FontSet.getGlyph`），按文字矩阵的缩放算出实际的“屏幕像素 / 界面单位”：`round(界面缩放 × 文字缩放)`，最多 16（`AflSmoothFonts.rasterFor`）。
   - 超过界面缩放时，AFL 字形按这个尺寸重新栅格化，按尺寸分别缓存（`AflFontSetMixin` 的 `afl$glyph`），和普通字形放在同一批图集页上。
   - 字宽、阴影和粗体偏移仍取普通字形的数值，所以排版和测量完全不变。
   - 原版后备字体里的字（位图、Unifont）和世界里的文字（告示牌、头顶名牌，缩放远小于 1）仍用普通字形。
5. **字形周围留空、新图集页清零（2026-10-14 修复）。** 用户在放大的标题和副标题里看到每个字外面有一圈细框。
   - 原因：原版图集在字形之间留 1 像素空隙，但新建图集页时只分配显存、不清零，空隙里是驱动留下的旧数据（界面缩放变化、图集重建后尤其明显）。原版最近邻采样读不到这些空隙；AFL 线性采样时，放大字形的边缘会把它混进来。
   - 修复：每个 AFL 字形四周多留 1 像素透明边（`AflSmoothFontProvider.PAD`）；新的灰度图集页创建后先上传一张全零图（`mixin/client/AflFontTextureMixin`）。彩色图集页不动。

## 5. 已知问题与限制

- 用户只给了整体结论；以下没有逐项确认：
  - 界面缩放 1；
  - 告示牌和头顶名牌（名牌要多人才看得到）；
  - 其他模组的界面；
  - 着色器包下的世界内文字。
- 界面缩放 1 时汉字只有约 8 像素高，平滑汉字看不清；像素字体存在就是为了这种情况。1080p 的自动缩放是 4。
- 放大文字的栅格最多 16 像素 / 界面单位：界面缩放 5、6 时，4 倍标题（20、24）仍有 1.25–1.5 倍的插值放大，比 1:1 略软。
- 世界里的文字（告示牌、头顶名牌）仍用界面尺寸的字形，离得近、在屏幕上很大时会软。
- 放大文字的字形按尺寸各自缓存，只在界面缩放变化或资源重载时清空。栅格 16 时一页 256×256 只放得下约 4 个汉字，长时间显示大量不同的大号中文会一直占用新的图集页。
- 字体集里只要有 AFL 字体，就会在界面缩放变化时清空整个字体集的缓存；第一次画各个字时会有一次栅格化开销。
- 字形图集仍是原版的 256×256 页，缩放越大一页放的字越少：缩放 4 时一页约 49 个汉字，缩放 6 时约 20 个。中文多的界面会用到更多页。
- 粗体仍是错开重画，不是真粗体字重。
- 没有关闭开关：想用原版字体的玩家只能用资源包覆盖 AFL 的字体文件。
- 字重：jar 里是 400（Regular）。开发客户端的 `run/resourcepacks/AFL Sans Medium (font test)` 是 500（Medium）的对比包，不在仓库里，启用后替换同名字体文件。用户没有要求换字重，保留 400。

## 6. 代码与资源

| 路径 | 作用 |
|---|---|
| `client/font/AflSmoothFontProvider.java` | AFL 字体 provider：栅格化、像素取整、阴影 / 粗体偏移 |
| `client/font/AflSmoothFonts.java` | 加载 AFL 命名空间的 ttf，跟踪字体集，界面缩放变化时重新栅格化 |
| `client/font/AflFontSetAccess.java` | 加到 `FontSet` 上的接口 |
| `client/font/AflFontRenderTypes.java` | 线性过滤的文字渲染类型 |
| `mixin/client/AflTrueTypeDefinitionMixin.java` | 拦截 AFL 命名空间的 ttf 加载 |
| `mixin/client/AflFontSetMixin.java` | 跟踪字体集、清空缓存、灰度图集页改线性过滤 |
| `mixin/client/AflWindowGuiScaleMixin.java` | 界面缩放变化时通知 |
| `mixin/client/AflFontStringOutputMixin.java` | 放大的界面文字改用按实际尺寸栅格化的字形 |
| `mixin/client/AflFontTextureMixin.java` | 新的灰度图集页清零 |
| `assets/minecraft/font/default.json`、`uniform.json` | 把 AFL 字体放到原版字体前面 |
| `assets/apocalypse_firstlight/font/afl_sans_sc.ttf`、`afl_sans_sc_OFL.txt` | 字体与授权 |
| `tools/build-afl-sans-sc.py` | 生成字体 |

## 验证

- 2026-10-14：`.\gradlew.bat compileJava --offline` **BUILD SUCCESSFUL**，没有新的编译警告；refmap 解析了三个 mixin 的目标：
  - `TrueTypeGlyphProviderDefinition.load`；
  - `FontSet` 的 `reload`、`stitch`、`close`，以及 `GlyphRenderTypes.createForIntensityTexture`；
  - `Window.setGuiScale`。
- 2026-10-14 实机（本 Worktree 的开发客户端，用户操作）：
  - 日志：字体在启动和两次资源重载时都正常加载（界面缩放 2 和 5，每次两份：`default` 与 `uniform`，各 7765 个字形），没有报错。
  - 用户结论：“可以作为PASS，字体效果蛮不错的”。
- 2026-10-14 幸存者继任测试时，用户截图指出两个问题：
  - 继任标题（姓名，放大 4 倍）和副标题（“第 n 位幸存者”，放大 2 倍）边缘发软；
  - 字外面有一圈奇怪的细框。
- 修复（第 4 节第 4、5 项）：`.\gradlew.bat compileJava --offline` **BUILD SUCCESSFUL**，没有新的编译警告；refmap 解析了 `Font$StringRenderOutput.accept` 与 `FontSet.getGlyph`。
- 2026-10-14 修复后实机（同一个测试世界，用户操作）：
  - 客户端启动、字体加载，以及两个新 mixin 的应用都没有报错。
  - 日志里有一次继任（#5 Dale Miller），标题和副标题照常出现。
  - 用户结论：“PASS”。
  - 是否改过界面缩放再看、标题画面的黄字是否看过，日志里看不出来（界面缩放变化不记日志）。
