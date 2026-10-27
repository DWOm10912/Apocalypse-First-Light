# AFL 感染者 Z0 设计稿 r1（2026-10-26，B-Workbench，只是设计稿，游戏不读）

用户 2026-10-26：
- "开始 Z0 丧尸设计，按你的推荐：旧玩家身体做底、方块加少量 Mesh 连接、先出几种样子的渲染图和行为设计稿"
- "我们的皮肤库准备升级到128px"
- "你先别用我们皮肤库里面的皮肤，你参考一下其他游戏的丧尸是怎么设计的，先设计一个初版脸"
- "另外，还有一版的设计，也就是我们现在替换的原版僵尸贴图 / 你直接设计3版完整的模型+贴图，先不做姿势，给我看 / 模型用main现在在弄的最新旧版模型（改了髋部和肩部）"

规划上属于 `docs/gameplay/afl_fauna_plan_v0.md` 第 5 节的 Z0。

## 1. 交付

文件都在 `design/creatures/infected_z0/`：

| 文件 | 内容 |
|---|---|
| `z0_a.png` `z0_b.png` `z0_c.png` | 三版 128×128 贴图，由 `node tools/build-infected-z0-skins.mjs` 生成 |
| `z0_blender_build.py` | 在 Blender 5.x 里重建审图场景：搭模型、贴图、渲染、存 `.blend` |
| `z0_infected_r1.blend` | 审图场景：四版各转四个角度，加头部近景 |
| `z0_lineup_r1.png` | 审图：四版，每版有正面、左前、右前、背面，下面是两张头部近景 |
| `z0_fronts_r1.png` | 四版正面并排 |
| `z0_f2.png`–`z0_f7.png`、`z0_fresh_r2.png` | r2 刚感染的变体（第 7 节） |
| `z0_fresh_r3.png`、`z0_faces_r3.png`、`z0_fresh.blend` | r3 每张脸加区别（第 8 节） |

第 4 版就是现在替换原版的 `assets/minecraft/textures/entity/zombie/zombie.png`，原样放进来对比。它本来就是 128 px，原版 64 网格放大一倍。

## 2. 模型

**身体用的是旧方块身体 `afl_humanoid_v1`，关节环按 Main 未提交的 SkinMesh 2.1.1：**
- 手臂环位于离上端 0、2（肩轴）、6（肘）、10（腕）、12 px；
- 腿环位于 0（贴骨盆）、2、4（两道髋环）、6（膝）、10（踝）、12 px；
- 躯干环位于 0、6、12 px；头是一个 8 px 方块。
- 站直不做姿势时，形状和原版方块完全一样。环只在弯关节时起作用。

**层：**
- 底层是丧尸的身体，包括皮肤、伤口和尸斑；
- 外层往外 0.25 px（头发和帽子 0.5 px），画撕破的衣服。
- 衣服破洞的地方能看到下面的伤口，这两层就是这么用的。

**贴图：**
- 原版 64×64 网格放大一倍，每个模型像素 2×2 个像素，和 Main 的 128 px 幸存者样本一样。
- SkinMesh 的 UV 按网格比例算，128 贴图不用改网格。

**审图光照：**
- 和原版实体一样，0.4 环境光加两盏固定方向光，在 sRGB 里乘；
- 最近点采样，外层透明处剪掉。

**三版的模型差别：**

| 版本 | 手臂 | 额外网格 |
|---|---|---|
| A | 4 px | 无 |
| B | 4 px | 帽檐：8.6 × 0.5 × 3.1 px 的薄片，在额头上沿，上面藏青、下面墨绿 |
| C | 3 px 细手臂，骨架里已有 slim 选项 | 无 |

帽檐是这次"方块加少量 Mesh"的例子。帽子的其余部分画在头部外层上。

**进游戏前要注意：** 原版 `ZombieModel` 只画头部外层，身体外层根本不画。这些贴图要等 Z1 的 AFL 感染者实体（旧身体 + SkinMesh，两层都画）才能看到衣服。现在不能直接替换 `zombie.png`。

## 3. 参考了什么

联网查到的资料不多，能用的有这些：
- Valve 在 GDC 讲过 Left 4 Dead 2 普通感染者的做法：同一套身体，用贴图上的伤口和血渍变化、换衣服换头，做出大量不同的样子，伤口是挖进身体的洞。来源：[Alex Vlachos 的 GDC 演讲介绍](https://lesterbanks.com/?p=2742)、[Left 4 Dead Wiki：Common Infected](https://left4dead.fandom.com/wiki/Common_Infected)。
- Project Zomboid 的丧尸随时间腐烂，社区模组按"新鲜 → 腐烂 → 干枯"分阶段换贴图。来源：[Steam 创意工坊的腐烂阶段模组说明](https://steamcommunity.com/sharedfiles/filedetails/comments/2793502459)。
- 低多边形风格的丧尸常用一张 128×128 的贴图把全身画完。来源：[Superhive 上的风格化低模丧尸](https://superhivemarket.com/products/zombie-low-poly-stylized)。
- 我自己对常见设计的总结，没有单独的出处：
  - 原版 Minecraft 僵尸靠绿皮肤和黑眼洞，远处一眼认出；
  - 《最后生还者》按感染时间分阶段，越往后越不像人；
  - 各家共同的读法：发灰发青的皮肤、深陷的眼窝、浑浊或充血的眼睛、松垮张开的嘴、嘴边和下巴的血、伤口旁扩散的深色血管、尸斑、左右不对称、撕破的脏衣服。

**定下来的原则：**
1. **按感染时间分版本，不按职业分。** 职业靠衣服换，同一阶段的身体可以配不同衣服，和 L4D2 的做法一样。
2. **远看靠三处对比认出来：** 深色眼窝、深色的嘴、血。其他细节近看才看得到，不靠它们认人。
3. **衣服是北美普通人的日常衣服**，不画品牌和文字。
4. **尸斑在身体低处：** 手、脚、小腿发紫发暗，因为它们是站着的。
5. **画法和 Main 的 128 幸存者一致：** 平涂加明暗档位，不贴照片噪点；伤口一律是平涂色块。

## 4. 三版

### A 刚转化（数小时到数天）
- **人物：** 男性，浅蓝短袖工作衬衫扎进卡其裤，皮带，黑皮鞋。
- **皮肤：** 灰绿，还看得出是个人。
- **脸：**
  - 眼窝发暗；眼白充血，下眼睑发红；虹膜浑浊发灰，看不清瞳孔；
  - 嘴松垮张开，嘴唇发紫；嘴角一道血流到下巴。
- **伤口：**
  - 转化的原因：左侧脖子到肩膀被咬了一口，衬衫从那里撕开、浸了血，深色血管从伤口往胸口和脖子侧面扩散；
  - 右前臂有抓痕。
- **头发：** 棕色短发，有点乱。
- **参考：** L4D、行尸走肉一类的"刚死不久"。

### B 数周
- **人物：** 男性，敞开的红黑格子法兰绒衬衫，里面是灰 T 恤；袖子卷到手肘，旧牛仔裤右膝磨破，棕色工装靴，卡车司机帽。
- **皮肤：** 橄榄灰绿，斑驳，有尸斑。
- **脸：**
  - 两颊凹陷，嘴唇萎缩，露出牙；
  - 眼睛乳白浑浊，左眼皮耷拉半闭，鼻尖发暗；
  - 有胡茬，左耳缺一块。
- **伤口：** 右前臂被咬掉一块，露出肌肉；左肋有一道撕裂伤，衬衫在那里破开。血已经干成褐色。
- **参考：** Project Zomboid 中期。

### C 数月
- **人物：** 女性，3 px 细手臂；破烂的墨绿开衫，里面是脏得发黄的吊带背心；深色牛仔裤磨烂到小腿；光脚。
- **皮肤：** 褐灰色，干硬，有裂纹。
- **脸：**
  - 眼窝空了，只剩右眼一点暗淡的反光；
  - 鼻子塌成黑洞，嘴唇没了，整排牙露在外面；右脸颊烂穿，看得到臼齿。
- **伤口：** 左胸和左肋皮肤破了，露出肋骨；右前臂露出筋，指关节骨头顶出来，小腿胫骨突出。
- **头发：** 长发稀疏，头顶一块块秃，几缕垂到背上。
- **参考：** 《最后生还者》后期阶段、Project Zomboid 干枯阶段。

### 现版（替换原版的 `zombie.png`）
- 长黑发，青色 T 恤，深蓝裤子，灰白皮肤，白眼。
- 画法偏照片涂抹，近看有噪点。和 Main 的 128 幸存者、和 A / B / C 都不是一个画风。
- 建议 Z1 时换掉。

## 5. 行为设计草案（待用户定，还没做）

现有的感染者 AI 叠加在原版 `minecraft:zombie` 上（`infected/`），包括：
- 听觉：`InfectedHearingSystem`，按噪声半径；
- 视觉：`InfectedVisionSystem`，有遮挡判断；
- 调查声源：`InvestigateNoiseGoal`；
- 找入口、破门：`InfectedEntrySeekingSystem`、`InfectedBreachGoal`。

Z1 把这些搬到 AFL 实体上。外形三版对应三档行为，草案如下：

| | A 刚转化 | B 数周 | C 数月 |
|---|---|---|---|
| 移动 | 走得最快，能短距离小跑 | 正常走，追人时快走 | 慢，拖着走 |
| 视觉 | 好 | 一般（眼睛浑浊） | 很差，眼窝空了，主要靠听 |
| 听觉 | 一般 | 一般 | 好，噪声半径内反应更早 |
| 破门 | 能 | 能 | 力气小，破门慢 |
| 受伤 | 和人差不多 | 普通 | 身体脆，四肢容易打断（接 Z2 布娃娃和断肢） |
| 出现 | 事发不久的地方，城镇里多 | 到处都有，最常见 | 偏远、老地方，数量少 |

**还要用户定的：**
- 三档是同一个实体按阶段切换（随世界时间变老），还是生成时固定；
- 衣服是画在外层贴图上（现在的做法），还是按 Main 幸存者的方向做成套在旧身体上的衣服网格。如果做成网格，皮肤贴图只画身体，衣服贴图单独画，换衣服更灵活；
- 要不要按职业加更多衣服（警察、医护、加油站店员等，对应规划里的"平民、工人、警察"）。

## 6. 下一步

- 用户看图挑方向，或者指出要改的地方；
- 定下来后进 Z1：
  - AFL 感染者实体（旧身体 + SkinMesh）；
  - 把现有 AI 搬过去；
  - 替换原版僵尸的生成；
  - 动作（走、追、扑、破门）另出设计。

## 7. r2：刚感染的 7 个变体（2026-10-26）

用户看完 r1："能不能就用现在的贴图版本，但是做6-7个变体，不同头发、衣服什么的 / 设定上就是刚感染的样子"。

**做法：**
- 沿用 A 的画法：灰绿皮肤，充血的眼睛加浑浊虹膜，松垮的嘴，一侧嘴角流血，一处咬伤加扩散的血管，外层是衣服，破口处浸血。
- 变的是：性别和手臂粗细、生前肤色、发型、整套衣服、咬伤位置。
- 生前肤色按同一个规则变灰：往它自己的灰度靠，带一点绿（`ashen()`，比例 0.78）。所以深肤色转化后仍然偏深，只是发灰。
- 生成器里每个变体是一段参数（`FRESH`：肤色、发型、上衣 / 裤子 / 鞋、咬伤、血迹），以后加变体只加参数。
- 审图：`design/creatures/infected_z0/z0_fresh_r2.png`，三排分别是正面、背面、头部四分之三近景；场景用 `z0_fresh_build.py` 重建，存成 `z0_fresh.blend`。

| # | 文件 | 人物 | 手臂 | 发型 | 衣服 | 咬伤 |
|---|---|---|---|---|---|---|
| 1 店员 | `z0_a.png` | 男 | 4 px | 棕色短发，有点乱 | 浅蓝短袖衬衫、卡其裤、皮带、黑皮鞋 | 左颈到肩 |
| 2 护士 | `z0_f2.png` | 女，深肤色 | 3 px | 黑色马尾，红色发圈 | 青色刷手服上衣和裤子、白运动鞋 | 右前臂 |
| 3 卫衣 | `z0_f3.png` | 男，浅肤色 | 4 px | 黑色短发 | 灰色连帽卫衣（帽子、抽绳、口袋）、牛仔裤、白运动鞋 | 左手背 |
| 4 上班族 | `z0_f4.png` | 女，中等肤色 | 3 px | 深棕波波头 | 米白长袖衬衫扎进深灰西裤、细皮带、黑平底鞋 | 右肩，衬衫在那里撕开 |
| 5 修车工 | `z0_f5.png` | 男，年长 | 4 px | 秃顶、灰白两侧、灰胡子和胡茬 | 蓝灰连体工作服（拉链、空白名牌）、黑工装靴，油污多 | 左前臂，袖子破开 |
| 6 T 恤短裤 | `z0_f6.png` | 男，深肤色 | 4 px | 黑色卷发 | 红 T 恤、卡其工装短裤、白袜、深色运动鞋 | 右小腿后面 |
| 7 开衫 | `z0_f7.png` | 女，年长 | 3 px | 灰白短波波头 | 淡紫开衫配白 T 恤、藏青西裤、棕皮鞋 | 左脸颊到下颌 |

**还没做的：**
- 变体在游戏里怎么选（随机、按地点，比如医院附近多护士、修车行多修车工），等 Z1 定。

## 8. r3：每张脸加区别（2026-10-26）

用户："这 7 个可以，接着给每个的脸加区别（眉形、皱纹、胡子等）"。

**生成器里每个变体多了一组 `face` 参数：**
- 眉形：平直、细、挑眉、粗、浓密；可以加一道疤把眉毛断开；
- 眼睛：窄眼，上眼皮压下来一行；
- 鼻子：宽、窄，鼻梁长短；
- 嘴唇：厚、薄，残留口红；
- 皱纹：1 档是鱼尾纹和法令纹，2 档再加抬头纹和下颌松垂；
- 胡子：胡茬、小胡子、下巴上的山羊胡；
- 配饰：
  - 眼镜：画在头部外层，镜框离脸 0.5 px，镜片透明；
  - 耳钉：耳垂下面一个亮点；
  - 晕开的睫毛膏：眼下往下流的深色道子。

审图：
- `z0_faces_r3.png`：上排是 7 个头的正面，下排是四分之三侧面；
- `z0_fresh_r3.png`：全身。

| # | 眉 | 眼 / 鼻 / 嘴 | 皱纹 | 胡子 / 配饰 |
|---|---|---|---|---|
| 1 店员 | 棕色平直 | 普通 | 无 | 稀疏胡茬 |
| 2 护士 | 黑色挑眉 | 宽鼻、厚嘴唇 | 无 | 金色耳钉 |
| 3 卫衣 | 黑色细眉 | 窄眼、小鼻子、薄嘴唇 | 无 | 无 |
| 4 上班族 | 深棕挑眉 | 残留暗红口红，睫毛膏晕开流到脸上 | 1 档 | 无 |
| 5 修车工 | 灰白浓眉 | 长鼻梁、宽鼻 | 2 档 | 深灰小胡子、灰胡茬 |
| 6 T 恤短裤 | 黑色粗眉，左眉有一道旧疤 | 宽鼻 | 无 | 下巴上一小撮山羊胡 |
| 7 开衫 | 灰色细眉 | 薄嘴唇、淡粉口红 | 2 档 | 细框眼镜、珍珠耳钉 |

**自查时改掉的：**
- 胡茬原来太密，像下半张脸长了脏斑，改成稀疏、颜色更淡；
- 6 号原来加了小胡子和整片山羊胡，和张开的嘴、深色嘴唇连成一大块黑。改成只在下巴留一小撮，嘴唇提亮；
- 5 号的灰胡子在灰绿皮肤上看不出来，压暗一档。

**说明：**
- A（1 号）也加了眉毛和胡茬，所以 r1 的审图和现在的 `z0_a.png` 不完全一样；
- 7 张脸还是同一套眼睛和嘴的位置。真要像不同的人，以后可以再动五官位置，比如眼距、嘴宽。


## 9. r4、r5：方向调整（2026-10-26）

- r3 用户不认可（"你这些做的太呆了，我们现在的僵尸贴图就很不错，按照这种风格去做"）。
- r4 改用购买的 zombie.png 的笔触派生 6 个变体：`tools/build-infected-z0-variants.mjs` → `z0_s2.png`–`z0_s7.png`，审图 `z0_style_r4.png`。
- 用户叫停 r4："丧尸可以完全不需要和玩家的模型一样吧""变体本身的思路不对""参考一下其他游戏是怎么做丧尸的，不要只看脸""模型还是继续用blockmesh/mesh，因为丧尸可以有很夸张的肢体动作"。
- r5 只是方块白模加噪声，质量被否（"这就是你的设计？"）；用户还指出 B5 风格已放弃，前面的"审计"没真正看图。

## 10. r6：参考研究 + 三种感染者概念（2026-10-26）

**前提（用户定）：** 玩家模型不改；感染者和玩家同一画风（旧方块身体 + 连续关节环 + 128 手绘贴图），骨架和比例可以不同（"同意，丧尸和玩家用同一种画风，骨架比例可以不同"）。

**参考研究：** `docs/gameplay/afl_infected_reference_study_v1.md`，图板 `design/creatures/infected_z0/reference/z0_reference_board_v1.png`（14 张截图，内部参考）。结论：普通感染者共用身体、靠衣服区分；姿态是第一识别；特殊种类靠轮廓；伤和血有因果。

**感染者骨架（概念用，Z1 实体照这个做）：**
- 骨骼：pelvis、belly、chest、neck、head、jaw、upper / fore × 2、thigh / shin × 2，共 14 根；
- 躯干在腰部一道共享环（chest / belly 各半），肩顶环一半跟胸、一半跟上臂，髋部 0 / 1 / 2 / 3 px 四道环线性混合（同玩家 SkinMesh 2.1.1），肘、膝各一道 1 px 过渡带；
- 下巴是一块 4×2×4 px 的下颌块，比脸窄，凸出脸面 0.25 px，铰在头里，张开时下面露出画在脸上的嘴洞（上牙一排、缺一两颗），不再在脸上画张开的嘴；
- 头可以下沉进躯干（`neck_drop`），四肢和躯干长宽按种类给。

**三种：**

| 种类 | 尺寸（px） | 姿态 | 贴图 | 打破轮廓 |
|---|---|---|---|---|
| 游荡者（普通，约七成） | 和玩家相同 | 驼背、头前伸歪向一侧、下巴张开、一臂垂一臂半抬、拖脚 | 购买的 zombie.png 原样，下巴和胸口加流下的血 | 姿态本身 |
| 快尸 | 手脚 3 px 宽，前臂 7.5（+1.5），腿各 6.5（+1） | 低姿态冲刺，上身前倾约 50°，抬头，嘴张大 | r4 的 s3（金发、灰 T 恤）；新鲜血从嘴流到胸口，手全是血，左肩咬伤 | 背包（7×8×3） |
| 重型 | 躯干 11×12×6，四肢 5 px，腿各 5.5，头下沉 1.5 | 头陷进肩膀、肚子顶出、上身微前倾一肩低、手臂外张 | 新底图 c3（秃顶、胡茬、灰 T 恤、牛仔裤）；工作靴；右前臂咬伤 | 敞开的荧光背心（两道横向反光带 + 过肩竖带）、肚子（9×6.5×3.5） |

**文件：**
- `design/creatures/infected_z0/z0_concept_r6_build.py`：在 Blender 里执行，`build_all()`、`pose_all()`、`render_final()`；渲染图在 `r6/`，场景存成 `z0_concept_r6.blend`；
- 审图 `z0_concept_r6.png`（三种：四分之三侧面、侧面、头部近景），`z0_concept_r6_silhouettes.png`（同一比例的剪影，加玩家站姿对比）；
- 生成器新增底图 `c3`：`node tools/build-infected-z0-variants.mjs c3`。

**已知：**
- 快尸金发贴图脸颊上还有一两个黄点（r4 的 s3 遗留）；
- 剪影图背景是灰色，不是白色；
- 只是概念图，游戏不读。下巴块、头下沉、背心、背包在游戏里怎么做（模型部件还是 SkinMesh 加部件）等 Z1 定。

**下一步：** 用户 2026-10-26 认可 r6（"R6的很不错，我就喜欢这样的，很Minecraft Story Mode风格"）。接着定普通感染者的衣服库和 3–4 套姿态步态，以及特殊种类的数量。

## 11. 做 Z1 之前：清掉原版僵尸和旧 AI（2026-10-26）

用户："在做之前，能不能彻底禁掉原版的僵尸，也就是说AFL默认不生成任何原版的生物""破玻璃、听觉、视觉什么的也可以先暂时禁用"。

- 原版僵尸进了退役清单，正常世界里没有任何原版生物；旧存档里的僵尸等敌对生物加载时去掉（`docs/gameplay/vanilla_survival_rework_v1.md` 4.16）。
- 旧感染者 AI（破障和破窗、听觉、视觉）用 `infected/InfectedLegacyAi` 关掉，代码留着（`docs/项目内容/01 - 设计/感染者/感染者基础规则.md` 开头）。
- GameTest 服务器上僵尸和旧 AI 都还在，给枪、噪声、闪电的测试当靶子。
- Z1 是 AFL 自己的实体，不再挂在 `minecraft:zombie` 上；感知和破障到时候重新设计。

## 12. Z1 第一个：游荡者（2026-10-26；Z1a 已写，见 12.3）

用户："先只做第一个普通丧尸看看，也就是R6的第一个"。下面是方案；Z1a 的实际做法见 12.3。

**实体：** AFL 自己的实体 `apocalypse_firstlight:infected`（不挂在 `minecraft:zombie` 上），r6 游荡者的尺寸和骨架（14 根骨骼，带下颌和两节脊柱），旧方块身体 + 连续关节环，128 贴图（购买的 zombie.png）。

**动作：** 在 Blender 里做（r6 的骨架、贴图、姿态已经在 `z0_concept_r6.blend` 里），脚本导出成游戏读的关键帧 JSON；游戏里只有走路速度、转头、受击叠加这几样按程序算。第一批：
1. 站立待机（两种）；
2. 游荡步态（拖一只脚）；
3. 追赶步态（快一些，身体更前倾）；
4. 原地转身；
5. 发现（猛地转头看过去）；
6. 扑抓攻击；
7. 挥打攻击；
8. 受击退缩（前、后，叠加在别的动作上）；
9. 被打倒和爬起（前、后）；
10. 砸门（两手捶，破障那一步才用）。

死亡不做动作，直接用布娃娃（`skeleton/ragdoll`，游荡者和玩家比例相同，可以用同一套刚体）。

**声音：** 待机低吼、发现时的嘶吼、追赶喘息、攻击、受伤、死亡、拖步脚步、倒地、砸门，每种 3–4 个变体；按惯例给 Firefly 提示词、每种一次生成；声音距离等于噪声半径。

**分步：**
- Z1a：实体、模型、贴图、待机和走路、简单追打、死亡布娃娃、留下能搜索的尸体（还不腐烂）、开发用生成命令；
- Z1b：视觉和听觉重新写（接现有 `noise/NoiseSystem`，不用旧代码）、发现和搜索；
- Z1c：攻击和抓人、受击、打倒和爬起、砸门和破障；
- Z1d：尸体腐烂（最后原地换成区块烘焙的骨头 OBJ 方块，同时最多 100 具尸体）、肢解、肢体当生物燃料原料；
- 嗅觉：等流血系统接上再做。

### 12.1 游荡者声音：Firefly 提示词（2026-10-26，等用户生成）

用户："你先把声音的提示词给我，时长和一次一声音效"。每条提示词只要一个声音，Firefly 一次给的几份就当随机变体，挑 3–4 份。第一版没写 zombie，用户否了（"如果不加丧尸提示词，就会奇奇怪怪的"），现在每条都以 zombie 开头，再用 realistic、horror film 压住卡通感。全部要干声：不要混响、背景、音乐。

| 文件名（存成） | 用途 | 时长 | 提示词 |
|---|---|---|---|
| `infected_idle` | 待机低吼 | 2.5 s | Realistic zombie groan, horror film sound effect: a single low, slow, raspy groan from a decaying zombie with a wet, ruined throat, no words. One groan only, close-miked, dry, no reverb, no background, no music. |
| `infected_alert` | 发现目标 | 1.2 s | Realistic zombie alert, horror film sound effect: a zombie suddenly notices its prey, one sharp gasp followed by a short rising snarl, guttural and wet, no words. A single vocal burst only, close-miked, dry, no reverb, no background, no music. |
| `infected_chase` | 追赶喘息 | 1.5 s | Realistic zombie breathing, horror film sound effect: one ragged, rattling breath from a zombie chasing its prey, a wet inhale and a hoarse growling exhale, no words. A single breath only, close-miked, dry, no reverb, no footsteps, no background. |
| `infected_attack` | 扑抓 | 0.8 s | Realistic zombie attack, horror film sound effect: one short, violent snarl as a zombie lunges to grab, throaty and wet, teeth clenched, no words, no impact. A single snarl only, close-miked, dry, no reverb, no background. |
| `infected_hurt` | 受伤 | 0.5 s | Realistic zombie pain, horror film sound effect: one short choked grunt from a zombie being struck, guttural, no words. A single grunt only, close-miked, dry, no reverb, no impact sound, no background. |
| `infected_death` | 死亡 | 2 s | Realistic zombie death, horror film sound effect: a zombie's final choking gurgle fading into a long rattling exhale and then silence, no words. A single sound only, close-miked, dry, no reverb, no body fall, no background. |
| `infected_step` | 拖步（一步） | 0.8 s | Realistic zombie footstep, horror film foley: one dragging step, a zombie's worn leather boot lands heavily on a dirt road and the toe scrapes along the gravel as the foot is dragged forward. One step only, close-miked, dry, no reverb, no breathing, no groan, no background. |
| `infected_door_bang` | 砸门（一拳） | 0.6 s | Realistic zombie at the door, horror film foley: one heavy pound of a zombie's fist against a closed wooden front door, a dull solid thud with a slight rattle of the door in its frame. A single hit only, heard from inside the house, close-miked, dry, no reverb, no voice, no background. |

- 倒地不用新录：用现有的 `health/body_fall`（4 个变体，玩家摔倒用的那组）。
- 游戏里怎么播：喘息、脚步、砸门都是按动作一下一下触发，所以每份只要一声；低吼隔几秒随机播一次。
- 传播距离、字幕、和其他感染者会不会互相听见，Z1a 接入时定。

### 12.2 声音接入（2026-10-26，编译通过，没实机）

用户："音效做完了，除了拖步没做以外，大部分都有变体（E:\Download）你看看怎么做响度平衡，然后按照目前的大部分音效的格式接入"。

- 14 份录音（按 SHA 核对来源）由 `tools/build-infected-sounds-v1.mjs` 处理：人声 60 Hz 高通、砸门 40 Hz；每份最响的 400 ms 瞬时响度对齐参考音（`metal_trash_can/open.ogg`，-24.3 LUFS）再加用途偏移（待机 -2、发现 +2、喘息 -6、攻击 +3、受伤 +1、死亡 0、砸门 +5 dB），峰值不超过 -1 dBFS，单声道 Ogg q6、48 kHz。砸门那份录了两下，切成两个文件。分析工具 `tools/analyze-sound-takes.mjs`。
- 结果在 `sounds/infected/`：idle 3、alert 2、chase 1、attack 3、hurt 2、death 2、door_bang 2；实测：待机 -26.5、发现 -22.6、喘息 -30.6、攻击 -21.5、受伤 -23.4、死亡 -24.4、砸门 -19.3 LUFS，都没被限幅。
- 7 个事件 `infected_idle / alert / chase / attack / hurt / death / door_bang`（`AflSounds`、`sounds.json`），字幕「感染者：低吼 / 嘶吼 / 粗重喘息 / 扑抓 / 受伤 / 死亡 / 砸门」。全部走 RangedSound，距离：待机、攻击、受伤、死亡 16 格，发现、砸门 24 格，喘息 10 格；每次音高随机 ±8%。拖步没录，先用方块自己的脚步声，音量 0.1。
- 新词「感染者」「感染者尸体」不在术语表里，交给 Main 补。

### 12.3 Z1a 实现（2026-10-26，编译通过，没实机）

**模型、贴图、动作（Blender）：** `design/creatures/infected_z0/z1_shambler_export.py` 在 Blender 里重建 r6 游荡者，导出：
- `creatures/infected/shambler_mesh.json`：124 个四边形、14 根骨骼（pelvis、belly、chest、neck、head、jaw、两节手臂和两节腿），格式和 Block-Mesh 身体一样（Java 模型 px，每顶点 x y z u v + 4 骨骼 + 4 权重），每个面都核对过朝外；
- `textures/entity/infected/shambler.png`：r6 的 512 图集；
- `creatures/infected/shambler_clips.json`：`walk`（一个循环两步，17 帧；右腿迈步、左腿僵着拖，胯随支撑腿转和倾，垂着的右臂摆，抬着的左臂上下晃，每步点头）、`idle_1`（喘气、下巴开合、抬着的手臂起落）、`idle_2`（脖子猛地一抽歪过去再回来）、`attack`（0.6 s：两臂往前上方举起，0.3 s 往下抓、胸口前扑、下巴张大，再收回）。每帧存每根骨骼的四元数（Java 轴）和离地抬高量。走路一个循环身体前进 27.3 px（实际 1.60 格，每步约 0.8 格；两步各 13.7 / 13.6 px），按支撑脚算，用来让脚不打滑。（r1 是 20.7 px、每步 0.6 格，两腿摆幅 60° / 40° 不一样；实机看是“步子太小、走得快”，r2 两腿都摆 68°，拖的那条腿只靠僵着的膝盖和低脚尖体现。）
- 关键帧审图：`design/creatures/infected_z0/z1/sheet.png`（走路 8 相位、两种待机、攻击，侧面和斜前方）。
- 离线核对：`z1_shambler_check.py` 用游戏的蒙皮公式算导出的网格和动作，和 Blender 骨架的结果逐顶点比，7 个关键帧最大误差 0.0001 px。

**实体（`creature/infected/`）：**
- `InfectedEntity`（`apocalypse_firstlight:infected_shambler`，MONSTER，0.6 × 1.95）：生命 20、攻击 3、移速 0.165（追赶约 1.18 m/s，游荡 ×0.7 约 0.58 m/s，每分钟约 88 步；生物的地面速度和移速的平方成正比，约 2.16 × 移速² 格 / tick，r1 的 0.19 实际是 1.56 m/s、每分钟 155 步，注释原来按线性算错了）、跟踪 28 格。目标：原版的近战追击（看到跟踪范围内的玩家就追）、游荡、看玩家、四处看；被打会反击。挥打每 1.5 s 最多一次，抬手 6 tick（0.3 s）后才落下，那时目标还在 1.5 倍距离内、看得到才算命中。不掉东西、不给经验、不会因为离远消失。
- 死亡：不倒地 1 秒、不冒白烟，当场换成 `InfectedCorpse`。
- `InfectedCorpse`（`infected_corpse`，1.2 × 0.4，跟着区块存档）：和玩家尸体一样先按布娃娃倒下（玩家骨架的 11 个刚体，`SurvivorRagdolls.creature`：微微弯腰的站姿、死前的速度、死因的冲量；同时最多 8 具在倒），倒完停在那里，不再每 tick 计算；水里、太多尸体在倒时直接仰面躺下。倒的途中被卸载：碰过东西就停在当时的姿势，还在空中就记下来，下次加载放到下面的地上仰躺。右键搜索（渐进搜索，9 格，每格 0.8 s），Z1a 里是空的。打不坏、推不动、能穿过去。还不腐烂、没有 100 具上限（Z1d）。
- 开发命令（OP）：`/afl dev infected spawn [数量]` 在面前 8 格放游荡者（最多 32），`/afl dev infected clear` 清掉当前维度所有感染者和尸体。

**客户端（`client/creature/`）：**
- `InfectedModel`：读网格和动作；待机（按实体 ID 选一种，错开时间）按走路速度混到走路，走路相位 = 走过的距离 ÷ 1.21 格；挥打时上半身叠攻击动作（淡入 0.08 s、淡出 0.12 s），腿照走；头在身体坐标里转向看的方向（左右 60°、上下 40°）。
- `InfectedRenderer`：不是 LivingEntityRenderer，没有受伤红光，`entityCutout`（背面剔除）。
- `InfectedCorpseRenderer`：布娃娃姿势（和玩家尸体同一套插值）映射到游荡者的骨骼：肚子取胸部转动的一半，脖子跟胸、下巴跟头；没有姿势就仰面躺着。
- 尸体姿势包 `SurvivorPackets.CorpsePose` 现在交给任何实现 `creature/CorpsePoseHolder` 的实体（玩家尸体和感染者尸体）。

**和现有系统接上：** `InfectedEntityRules.isInfected` 认新实体，所以生命值 V1 的「感染者近战」伤害换算和抓伤（`Wounds`）对它生效；爆头名单（`apocalypse_firstlight-guns.toml` 的 `headshotEntities`）默认值加了 `apocalypse_firstlight:infected_shambler`，但这是每个存档自己的服务端配置，**旧存档要手动加这一项**才有爆头。爆头判定还是碰撞箱顶上那一截（0.5 格），游荡者弓着背、头比箱顶低，按骨骼算命中部位留到 Z1c。旧的听觉（`isNoiseResponsive`）只认原版僵尸，Z1b 重写。Wiki 等实机通过后再加感染者页面。

**第一次实机（2026-10-26）：** 用户看了走路：“步子太小，然后走的快”。改了步幅和移速（见上），离线核对仍是 0.0001 px，没再进游戏。搜索界面还是原版箱子样子的开发占位（`AflContainerSearchScreen`），用户提出按 AFL 背包界面重做，概念图 r1 和 A 版实现见 `docs/ui/afl_container_search_page_v1.md`。

**复查（2026-10-27）：** 用户看了改过的走路：“没问题”。同时要求：“加一个对着任何生物的尸体，搜索的hint”。准星对着任何尸体（实现 `creature/CorpsePoseHolder` 的实体：感染者尸体、幸存者遗体，以后的生物尸体也一样）时，交互提示显示“搜索”（`hint.apocalypse_firstlight.corpse.search`，右键）；幸存者遗体搜完后照旧显示“查看”（`client/WorldInteractionHint`）。用户同日实机：“没问题，PASS，提交吧”。

**还没做 / 已知：** 骨架只用到 r6 的游荡者；拖步声没有；追赶步态、转身、受击、打倒爬起、砸门在 Z1b / Z1c；世界里不会自然生成。`compileDevJava` 仍有一个与本任务无关的旧错误（`ReactorIntegrationGameTests` 找不到 Jade 的 `IServerDataProvider`）。
