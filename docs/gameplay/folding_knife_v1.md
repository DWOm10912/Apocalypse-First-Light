# 折刀 V1

状态：2026-10-22 出两版概念图，用户选 **A 版（传统背锁）**；同日做成物品（第 5 节）：Mesh、PBR、手持、模型渲染图标、近战栏、开刀 / 收刀 / 刺击动作，音效已接入。第一轮实机：图标、开合、声音 PASS；近战栏剪影缩小、预备姿势和刺击按调研重做（5.3），编译通过，**等第二轮实机**。只在创造栏，没有配方。

## 1. 为什么要折刀

- 木拉手的工作台做法改成「手锯 + 折刀」：先锯下料，再用折刀削圆（见 `furniture_recipes_v1.md` 待办）。真实做法是削或车，单用锯做不出圆头。
- 折刀同时是近战武器：打开并锁住时进近战栏（`HotbarRoles.MELEE_SLOT`，3 键），和撬棍、伐木斧一样走 `apocalypse_firstlight:weapons/melee` 标签。

## 2. 两版概念

概念图和生成脚本在 `design/items/folding_knife_v1/`：

| 文件 | 内容 |
|---|---|
| `folding_knife_a_concept.png` | A 版：传统背锁折刀 |
| `folding_knife_b_concept.png` | B 版：现代衬锁折刀 |
| `fk_build.py` | Blender 建模和渲染（在 Blender 里 exec，建独立场景 `FK_concept`，单位 cm） |
| `fk_sheet.cjs` | 用渲染图合成概念图：`node fk_sheet.cjs <输出目录>` |

| 项 | A 传统背锁 | B 现代衬锁 |
|---|---|---|
| 刀形 | 斜切尖（clip point），缎面不锈钢，平磨 | 落尖（drop point），石洗不锈钢，高平磨 |
| 刀柄 | 胡桃木贴片，前后黄铜枕，黄铜铆钉，钢背簧 | 橄榄绿 G10 贴片，不锈钢衬板，黑色背垫，防滑齿，口袋夹 |
| 锁 | 背锁，按刀背锁片收刀 | 衬锁，拇指推开衬板收刀 |
| 开刀 | 两只手，指甲槽 | 一只手，拇指钉 |
| 收起 / 打开 | 12.6 / 21.1 cm | 11.6 / 19.2 cm |
| 露刃 | 约 8.5 cm | 约 7.5 cm |
| 估重 | 140 g | 100 g |
| 哪里找到 | 老农舍抽屉、工具箱、旧车手套箱 | 户外店、杂货店、背包、车门储物格 |

两版都能锁住刀身，所以都可以当近战武器。刀面都不刻品牌。

## 3. 做成物品时要做的事（选版之后；已按第 5 节做完，木拉手一条除外）

- Mesh 物品：完整模型、PBR、第一 / 第三人称手持从握点适配、3D 渲染图标；打开和收起两种状态（收起时进普通物品格，不能攻击）。
- 近战：加 `weapons/melee` 标签；近战栏剪影 `textures/gui/melee/<id>_hud.png`（侧视，刀头朝左）；第一人称攻击动作参考 `CrowbarFirstPerson`，按「沉稳」规则做开刀、攻击、收刀，切换时走 CameraDirector 淡入淡出。
- 开刀动作：A 版两只手，比 B 版慢；开刀和落锁有声音。
- `CarriedWeaponsLayer` 现在还不画近战武器，折刀收起后挂在身上也不显示（以后统一补）。
- 木拉手：配方工具改成手锯 + 折刀，拉手头从 2.8 cm 改 2.4 cm（板厚 2.5 cm），工作台动画约 10 秒（锯 3 秒、换工具 1 秒、削 5 秒、收尾 1 秒）。
- 伤害、攻速、耐久等数值在做物品时另定，不在这里写。

## 4. 音效清单（A 版，用户补）

单声道 OGG，放 `assets/apocalypse_firstlight/sounds/folding_knife/`。一种声音生成一条长录音也行（按「一条录音切段」的习惯，由我切成多段）。传播距离按感染者噪声半径定：开合和削木很近，刺入稍远。

| 优先 | 文件 | 内容 | 长度 | 条数 |
|---|---|---|---|---|
| 必须 | `open_1..2` | 指甲勾住刀身拉开，背簧有阻力的摩擦声，最后落锁「咔」一声 | 0.8–1.3 秒 | 2 |
| 必须 | `close_1..2` | 按下刀背锁片「咔哒」，刀身转回，背簧把刀「啪」地合上 | 0.6–1.0 秒 | 2 |
| 必须 | `swing_1..3` | 短刀挥空，轻、短的破风声，比撬棍细 | 0.2–0.35 秒 | 3 |
| 必须 | `stab_flesh_1..3` | 刀刺进肉里的闷湿声，带一点拔出的尾音 | 0.3–0.6 秒 | 3 |
| 必须 | `whittle_1..4` | 刀削硬木，一刀一下的刨花声；有长有短，有一两刀刮细 | 0.4–0.8 秒 | 4 |
| 可选 | `hit_hard_1..2` | 刀尖戳到木头、石头或金属，短促「叮」或「笃」 | 0.1–0.2 秒 | 2 |
| 可选 | `bench_tool_down` | 手锯放到工作台面上，换工具时用 | 0.3–0.5 秒 | 1 |

已有、直接复用：`hand_saw_push` / `hand_saw_pull`（木拉手先锯下料）。

### 4.1 Firefly 提示词

每条一次生成，里面录几下、中间留空，由我切段。都要求干声：不要混响、不要房间回声（用户 2026-10-22）。

1. 开刀（切成 `open_1`、`open_2`）：`Close-mic foley of a traditional lockback pocket knife with a stiff backspring being opened by hand, twice: a fingernail catching the nail nick, the blade drawn out against spring tension with soft metal-on-metal friction, then one crisp click as the back lock snaps into place. About two seconds of silence between the two openings. Dry studio recording, no reverb, no room echo, no music, no voice.`
2. 收刀（切成 `close_1`、`close_2`）：`Close-mic foley of a traditional lockback pocket knife being closed by hand, twice: a thumb presses the lock bar on the spine with a small click, the blade swings back with light friction, then the backspring snaps it shut with a firm snap. About two seconds of silence between the two closings. Dry studio recording, no reverb, no room echo, no music, no voice.`
3. 挥空（挑 3 段）：`Short whooshes of a small pocket knife blade slashing quickly through the air, five separate swings with one second of silence between them. Thin, light, fast swish, not a heavy sword or stick. Close mic, dry studio recording, no reverb, no room echo, no music, no voice.`
4. 刺入生物（挑 3 段）：`Foley of a knife stabbing into raw meat, five separate stabs with about one and a half seconds of silence between them: a dull wet thrust with a short squelch, then a quick wet pull-out. No bone crunch, no screams. Close mic, dry studio recording, no reverb, no room echo, no music, no voice.`
5. 削木头（挑 4 段）：`Close-mic foley of whittling a small piece of hardwood with a sharp pocket knife: eight separate strokes with about one second of silence between them, each a crisp curl of wood shaving carved off; some long strokes, some short, the last two light scraping strokes. Dry studio recording, no reverb, no room echo, no music, no voice.`
6. 戳到硬物（可选，挑 2 段）：`The tip of a steel pocket knife jabbing hard surfaces, six separate hits with one second of silence between them: two into wood with a short dull tock, two on stone with a sharp tick, two on sheet metal with a small bright tink. Close mic, dry studio recording, no reverb, no room echo, no music, no voice.`
7. 手锯放到台面（可选，挑 1 段）：`A hand saw being set down on a wooden workbench top, three separate times with two seconds of silence between them: a light wooden thud with a faint rattle of the steel blade. Close mic, dry studio recording, no reverb, no room echo, no music, no voice.`

### 4.2 已切好的音效（2026-10-22）

用户的 Firefly 录音在 `E:/Download`，用 `design/items/folding_knife_v1/fk_cut_sounds.py` 切成单声道 OGG：每组统一增益，组里最响的一段峰值 −3 dBFS，开头 3 ms、结尾 40 ms 淡入淡出。2026-10-22 已登记进 `sounds.json`（事件名见 5.5 节）。

| 文件 | 来源 | 说明 |
|---|---|---|
| `folding_knife/open_1` | 开刀 | 录音只有一次开刀（1 秒），没有第二段；游戏里用音调随机补变化 |
| `folding_knife/close_1` | 收刀 | 同上，只有一次 |
| `folding_knife/swing_1..3` | 挥空 | 0.15–0.18 秒；第 4 段被录音结尾截断，没用 |
| `folding_knife/stab_flesh_1..4` | 刺入 | 用户指定前 4 段，每段带拔出的尾音，0.8–1.4 秒；手动定的切点 |
| `folding_knife/whittle_1..6` | 削木头 | 13 刀里挑 6 刀，0.2–0.66 秒 |
| `folding_knife/hit_metal_1` | 戳硬物第 1 下 | 3 kHz 有余振，判断为金属 |
| `folding_knife/hit_stone_1..4` | 戳硬物第 2、3、4、6 下 | 短促、没有低频，判断为石头；第 5 下是两下连击，没用 |
| `workbench/tool_down_1` | 手锯放到台面 | 0.41 秒 |
| `folding_knife/hit_wood_1..4` | 单独生成的戳木头（`Firefly_audio_The_tip_of_a_steel_pocket_knife_jabbing_into_a_thi_variation2.wav`） | 4 下闷声，0.16–0.19 秒；每下后面的轻微杂响没要 |

戳硬物那条录音里没有听得出来的木头声（木头应该有 200–1500 Hz 的闷声，这条里每下都几乎没有），金属也只有一下。木头用下面这条单独生成，已切好（上表 `hit_wood`）：

`The tip of a steel pocket knife jabbing into a thick wooden plank, six separate jabs with one second of silence between them: a short dull wooden tock with a little body, no ringing, no metal sound. Close mic, dry studio recording, no reverb, no room echo, no music, no voice.`

## 5. 实现（2026-10-22，分支 AFL-Workbench，编译通过，没进游戏看过）

### 5.1 物品

- `folding_knife`（`item/FoldingKnifeItem`），一把一格，耐久 600，不能修；创造栏「近战与装备」，在伐木斧后面；没有配方。
- 状态存在物品 NBT 里（和翻盖打火机一样，服务端决定）：`Open`；`Act` / `ActAt` 最近一次动作（`open`、`close`）和开始的刻；`Snd` / `SndAt` 延后播放的音效。
- 右键（主手）：收着时打开；开着时潜行右键收起；正在开合时右键无效。
- 放下（换到别的格）或丢出时安静地合上。
- 质量 0.14 kg（估），背包 1×1，耐力消耗 `melee_items` 4（撬棍 8、伐木斧 10）。

### 5.2 近战

| 状态 | 伤害 | 攻速 | 说明 |
|---|---|---|---|
| 打开锁住 | 4（玩家 1 + 3） | 每秒 2 次（10 刻恢复） | 单体，没有横扫；打中消耗 1 点耐久 |
| 收起 | 空手 | 空手 | 打中不耗耐久、不出刺入声 |
| 正在开合 | 不能攻击 | — | `onLeftClickEntity` 取消 |

- 数值用 Forge 的按物品堆属性（`getAttributeModifiers(slot, stack)`），收起时没有加成。
- 在 `weapons/melee` 标签里，开着、收着都能放近战栏（3 键）；近战栏剪影 `textures/gui/melee/folding_knife_hud.png`。
- `CarriedWeaponsLayer` 还不画近战武器，折刀放近战栏时身上看不到（以后统一补）。

### 5.3 动作和镜头

动作脚本 `tools/author-folding-knife-actions.mjs`（`--check`、`--report`），写 `player_props/folding_knife.json`（缩放 0.4）和 4 段动作：

| 动作 | 时间 | 内容 |
|---|---|---|
| `item/folding_knife` | 淡入 0.25 秒 | 收着，拳里握着，在肚子前 |
| `item/folding_knife_open` | 1.8 秒 | 0–0.4 秒抬到胸前、刀柄横向左；0.40 秒左手指甲勾进指甲槽（开刀声开头）；0.42–1.20 秒刀身转出，左手跟着捏住刀面；1.23 秒落锁（声音里的「咔」），手顿一下；1.25–1.55 秒左手离开；1.3–1.8 秒刀放到胸口下方的预备姿势 |
| `item/folding_knife_close` | 1.65 秒 | 0–0.35 秒回到胸前；0.40 秒左手拇指按锁片（收刀声的「咔哒」）；0.45–1.08 秒推刀转到 150°，松手；1.18 秒背簧把刀合上；1.2–1.65 秒放回肚子前 |
| `item/folding_knife_stab` | 0.5 秒 | 0–0.09 秒拳头沉到右胯旁、刀尖上翘；0.1–0.2 秒从下往前上方捅出，到身前约肚子的高度，手臂几乎伸直；0.24–0.5 秒收回预备姿势；从原版挥手开始时播放（`SkeletonActions.knife`），只在打开锁住时 |

- 刀身转动由渲染器按 NBT 算（`FoldingKnifeItem.bladeDegrees`），动作里左手跟随的点是写死在道具上的 `pull_150 … pull_0`（刀身转到那个角度时刀面上的捏点）、`nick_180`（收着时的指甲槽）、`release`（锁片）。
- 第一人称开合时镜头低头（导演镜头，至少 28°，退后 0.1 格），开刀到 1.35 秒、收刀到 1.2 秒；手在刀上时锁住视角（开 1.25 秒、收 1.15 秒内）。`client/FoldingKnifeClient`。
- **握法和刺击依据**（用户 2026-10-22 第一轮实机：预备姿势举到下巴前很怪，刺击应该从下往上捅；第一人称刀贴在屏幕右下、怎么转视角都不动也很怪。网上查的资料）：
  - 正握（锤握）最常见，刀身从拳头上方伸出，主要用来直刺，目标是腹部、胸口（[Crate Club：战术刀握法](https://crateclub.com/blogs/loadout/how-to-hold-a-tactical-knife-a-comprehensive-guide)）；拇指压在刀背上能多一点控制，也知道刃朝哪边（[RECOIL：握刀常见错误](https://www.recoilweb.com/knife-hands-avoid-these-common-grip-mistakes-to-sharpen-your-edge-weapon-skills-153220.html)）。
  - 自卫资料里最常见的持刀攻击是一连串从低处往前捅小腹的直刺（[CVPSD：刀具袭击的隐藏和出刀](https://www.cvpsd.org/post/how-criminals-use-knives-concealment-deployment-and-the-signs-of-a-knife-attack)）；从下往上、像勾拳一样捅到肋下的刺击最难挡（[SOFREP：刺与划](https://sofrep.com/98822/slashing-versus-stabbing-knife-tactics-relevance/)）。
  - 所以：预备姿势改到胸口下方、手肘弯着收在身侧，刀尖朝前偏上，刃朝下；刺击先沉到胯旁，再从下往前上方捅出。
- **跟视线还是跟身体**：预备、蓄力、刺出三个姿势都改在胸前坐标系（`chest`），不再用跟着视线转的 `view`。第三人称刀停在胸口下方，不会随抬头举到脸前；第一人称平视时只在右下角露出刀尖一段，低头能看到整把刀和手，抬头刀就出画面，和真人拿刀一样。胸前坐标系只跟一部分视线俯仰（`Poser.chestPitch`：0.3 倍，−15° 到 20°）。
- 离线核对（`--report`，俯仰 −30°、0°、30°、60°）：开合、预备、刺击全程右手都够到（偏差 0）；预备手肘弯 67°，刺出最远时 23°。平视时刀尖在视线下方 29°、偏右 16°（第一人称垂直半视场约 35°，看得到），刀柄 64° 在画面外；低头 60° 时刀柄 77°、刀尖 47°，整把刀在画面里。刺出最远时（平视）刀尖在下方 23°、偏右 10°。

### 5.4 渲染

- `client/FoldingKnifeRenderer`：按部件名把 `blade` 分出来，绕刀轴（烘焙后 (−0.1048, 11.6072) px）转 0–180°；收刀时刃口先进刀柄。
- 物品栏里收着的刀上移到打开时的中心、放大 1.45 倍，图标仍然填满格子（图标用打开的刀的 GUI 变换）。

### 5.5 音效

| 事件 | 文件 | 什么时候 | 传播 |
|---|---|---|---|
| `folding_knife_open` | `open_1` | 开刀动作 0.40 秒 | 4 格（含感染者噪声） |
| `folding_knife_close` | `close_1` | 收刀动作 0.40 秒 | 4 格 |
| `folding_knife_stab` | `stab_flesh_1..4` | 刺中生物（`hurtEnemy`），在目标身上 | 6 格 |
| `folding_knife_hit_metal` / `_stone` / `_wood` | `hit_metal_1`、`hit_stone_1..4`、`hit_wood_1..4` | 打开时左键点方块的第一下（服务端 `LeftClickBlock` START），按方块破坏声的名字分金属、木头、石头；泥土、沙子、羊毛等软方块不出声 | 4 格 |
| `folding_knife_swing` | `swing_1..3` | 打开时左键挥空，只在自己客户端播（挥空服务端收不到，也不产生噪声） | 本地 |
| `folding_knife_whittle` | `whittle_1..6` | 已登记，木拉手的台前削木动作做好后用 | — |
| `workbench_tool_down` | `workbench/tool_down_1` | 已登记，台前换工具时用 | — |

字幕：折刀：打开落锁 / 折刀：合上 / 折刀：挥空 / 折刀：刺入 / 刀尖：戳到硬物 / 削木头 / 工具：放到台面。

### 5.6 没做 / 待验收

- 第一轮实机（用户 2026-10-22）：图标、开合、声音 PASS；近战栏剪影太大、预备姿势和刺击已按上面改，等第二轮实机。
- 别的玩家听不到挥空声（挥空不发包）。
- 木拉手改成手锯 + 折刀、台前削木（第 3 节最后一条）还没做，归家具配方那一轮。
