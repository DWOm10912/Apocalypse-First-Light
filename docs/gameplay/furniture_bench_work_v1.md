# 木家具台前动作和按量扣耗材 V1（2026-10-22，用户已定，已做，编译通过，未实机）

用户 2026-10-22："先出木家具台前动作的分段设计（钉钉子、刷漆等），配方格式按升 / 按根扣一起定"。之前的要求："不能让玩家站在那里干等"；"不要单独的刷子"；家具做出来是通用胡桃木色。

用户 2026-10-22 定的（第 9 节）："时间按照你上面写的做就行，中断后接着做"；"钉钉子也需要判定，后面也可以成长"；"家具需要几个螺丝就做几个QTE，不会拧花。如果学习了配方就会拧，可以通过多次干活来降低QTE的难度和拧的速度，钉子也是一样"。

用户 2026-10-22 定了最后两条："钉子按看得见的根数做 QTE，螺丝刀也照推荐加，开始照设计稿做"。分支 AFL-Workbench 已照本文做完（第 10 节），只跑过离线编译，没进游戏。用量（板材、钉子、漆、五金）沿用 [木家具配方 V1](furniture_recipes_v1.md) 第 1、2.2 节，不改。台前干活的基础（站位、停下的条件、服务端 `Work` / `WorkOf`、木屑）沿用 [AFL 工作台 V1](afl_workbench_v1_design.md) 第 17 节和木拉手（`furniture_recipes_v1.md` 2.1.1）。

## 1. 原则

1. **按真实的顺序做一件家具：** 下料（横截锯）→ 拼合、钉钉子 → 上漆、擦掉余漆 → 装五金。现实里五金在漆干了以后才装，免得沾上漆；游戏里不等漆干。
2. **台上看得到进度：** 锯下来的短料一根根码到台子左边；钉子钉进去的地方留下钉帽；擦过漆的地方颜色变深；合页、拉手装上去就留在料上。玩家在任何时候看一眼台面，都知道做到哪一步了。
3. **一件家具 = 几段动作按顺序播：** 每段是一个玩家动作文件（同木拉手，`player_actions/furniture/*.json`），配方只写"哪几段、每段几次"。段的长度由动作文件定，配方时间由各段加起来算出，不手写。
4. **看得见的次数少于真实次数：** 一把椅子要 16 根钉，台前只钉 5 根，扣还是扣 16 根。真的一根根钉，一把椅子要几分钟。**螺丝例外**：用多少颗就拧多少颗（用户 2026-10-22），每颗一个 QTE（3.5.1）。
5. **动作沉稳：** 每一下都有缓入缓出，不赶（用户 2026-10-22 定的动画规则）。下面的时长已经按这个定，不再压短。
6. **不加新的必带物品做道具：** 抹布、搅拌棍不是物品。抹布是动作里画出来的一块旧布（工作台旁边本来就有），搅拌棍是下料时锯下的一截边角料。只有螺丝刀要不要加成工具，还没定（第 9 节）。

## 2. 台面布置（北向坐标，前沿在 z 0 一侧）

- 台面 1.92 × 0.69 m（x 0.04..1.96，进深 0.04..0.73），面高 0.9 m。从正面看，x 小的一头是右端：右端下面是前台钳（x 0.2..0.6），木拉手的台钩也在这头（x 0.10）。左半边前面一排狗洞（x 0.79..1.84），上面是挂板。
- **下料在右端：** 板材顺着台子长边平放在前半边，右端伸出台端 25 cm，锯口在伸出的部分上（锯的平面和锯原木一样是竖直的、垂直于板长，锯沿着板宽方向前后走）。锯下的短料从右端掉下去，玩家左手接住。
- **拼合和上漆在左半边：** 料件（第 3.6 节）放在 x 0.75..1.75、靠前沿，正对玩家；玩家站在料件正前方。
- **台上的耗材：** 开始钉钉子时，钉子盒打开放在料件右后方（台面后半边，不挡视线）；开始上漆时，漆罐放在料件右后方，盖子翻过来放在旁边。都用这两个物品自己的模型。
- 衣柜侧板长 1.96 m，比台面长，不放台上。衣柜和储物箱在台上只做门、抽屉、箱盖这些部件，整体组装不画（第 4 节）。

## 3. 动作段

每段一个动作文件，循环一次是"一次"。时长是一次的长度。噪声是给感染者的 INTERACTION 噪声，按动作文件里的 `events` 发（同木拉手）。

### 3.1 横截下料 `furniture/crosscut`（6.5 秒一次，手锯）

| 时刻 | 动作 |
|---|---|
| 0–0.9 s | 右手从右胯边把锯提到锯口，左手按住板材（锯口左边 8 cm），锯齿对准划好的线，拇指靠着锯身轻拉两下起锯口 |
| 0.9–5.7 s | 6 个来回，每个 0.8 秒（同锯原木：推是切削，拉回）；锯口从板的前沿走到后沿 |
| 5.7–6.5 s | 最后一推锯断，左手接住短料，放到台子左边的料堆上；锯垂回胯边 |

- 声音：沿用 `hand_saw_push` / `hand_saw_pull`。木屑：每推一次从锯口下面落 1–2 粒木粉（`WoodChipParticle`），锯断时多落一撮。噪声：每推一次 8 格（同锯原木）。
- 第一刀锯下来的那一截短料（约 30 cm）留在台上，后面上漆时当搅拌棍。
- 板材的树种贴图跟着身上第一种能用的板材（客户端按身上的东西猜，服务端扣哪种板材不受影响）。

### 3.2 钉钉子 `furniture/nail`（按键判定，羊角锤）

每根看得见的钉子是一次 QTE（3.5.1 同一根时机条）：先是动作把钉子立住，再由玩家按键一锤一锤钉进去。

| 阶段 | 动作 |
|---|---|
| 立钉（动作，1.2 秒 × 速度） | 左手从钉子盒里捏一根钉，钉尖放到下一个钉位，锤子手腕轻敲一下立住（`furniture_nail_tap`），左手退到料件边上压着 |
| 每一锤（按键） | 按准：一下整锤（手腕加小臂，0.35 秒 × 速度），钉子进去一截，放 `furniture_nail_strike`。按偏：锤面擦过钉帽，钉子不动，放敲偏声（7.1） |
| 最后一锤 | 放 `furniture_nail_seat`，钉帽齐平，锤子停在钉帽上一下再抬起 |

- 一根要钉几锤、速度、绿区按木工成长的档（3.7）：新手 4 锤，有经验 3 锤，熟练 2 锤。
- 按偏不弯钉、不废钉，只是多花时间（同螺丝不会拧花）。
- 钉位在料件上按固定顺序排好（每块料件自己的钉位表，第 3.6 节），钉完的位置留下钉帽。
- 噪声：每一下整锤 10 格（和斧头砍树一样），立钉轻敲和敲偏 4 格。
- 镜头：第一人称看着钉位（第一人称视角随动作走，同木拉手的 `first_person_view`）。

### 3.3 上漆 `furniture/wipe`（4 秒一次，空手拿抹布）

| 时刻 | 动作 |
|---|---|
| 0–0.8 s | 抹布在罐口蘸一下，在罐沿上刮掉多余的 |
| 0.8–3.5 s | 顺着木纹擦三下，每下 0.9 秒，从料件一头擦到另一头；擦过的那一条变成胡桃木色，表面有一层湿光 |
| 3.5–4 s | 把抹布翻一面 |

- **第一次进这一段前先开罐（只开一次，约 6.5 秒）：** 用羊角锤的羊角插进盖沿，绕着撬三处，盖子掀开翻过来放在台上（3.1 秒，跟录音 `can_open_1` 走）；拿起那截边角料，在罐里慢慢搅（油性着色漆的色粉沉底，要搅匀，不能摇），约 3 秒，放一段 `stain_stir`，搅拌棍斜靠在罐边。
- **整批做完时盖罐（2 秒）：** 盖子扣回去，用手掌按紧。**没有声音**：用户 2026-10-22 说盖罐的声音不好生成，先不做；以后录到了再改成用锤面轻敲两下。中途被打断（走开、被推开）时不播，罐子和盖子直接不画。
- 抹布不是物品：一块旧棉布，只在这段里画在右手上；不用时搭在罐边。不另做刷子。
- 声音：开罐撬盖、搅拌、抹布蘸漆和擦木头（很轻）（第 7 节）。噪声：撬盖 4 格，搅拌和擦漆 2 格（听得到的范围和噪声半径一样），盖罐没有。

### 3.4 擦掉余漆 `furniture/wipe_off`（2.5 秒，一件一次）

- 抹布折一下换干净的一面，整块料件顺着木纹从头到尾擦一遍，湿光变淡（现实里擦涂着色漆停 5–15 分钟再擦掉余漆，游戏里不等）。
- 声音复用上漆的抹布擦木头，音量压低。噪声 2 格。

### 3.5 装五金 `furniture/fit_*`

| 段 | 一次 | 动作 |
|---|---|---|
| `furniture/fit_hinge` 合页 | 1 秒 + 6 颗 | 合页贴到门边或箱盖边的位置上，左手按住；拧 6 颗螺丝 |
| `furniture/fit_knob` 拉手 | 1 秒 + 1 颗 | 拉手贴在正面，左手扶着；从里面拧 1 颗螺丝（木拉手拧进榫头） |
| `furniture/fit_escutcheon` 锁眼盖 | 1 秒 + 2 颗 | 锁眼盖对准锁孔，拧 2 颗小螺丝 |

每颗螺丝一次 QTE（3.5.1），按准时一颗约 2.7–5.7 秒（看木工的档，3.7）。

- 拧螺丝要螺丝刀，现在没有这件工具，见第 9 节第 2 条。有了 QTE 以后螺丝刀是必须的（推荐加）。
- 装上去的五金用各自物品的模型，留在料件上。拉手装铁的还是木的，看身上先扣到哪个（同扣板材）。
- 螺丝从螺丝盒里扣（`screw_box`，按颗，`furniture_recipes_v1.md` 1.6 节）：合页每片 6 颗、拉手 1 颗、锁眼盖 2 颗。开始装五金时螺丝盒打开放在料件旁边。
- 噪声：拧螺丝 2 格。

#### 3.5.1 拧螺丝 QTE（用户 2026-10-22 定）

用户："拧螺丝可以做成QTE，完成了就播放一个螺丝被拧好的动画，还可以和后面的特质/开局天赋做联动"；"家具需要几个螺丝就做几个QTE，不会拧花。如果学习了配方就会拧"。按现有的 QTE 约定做（[便携柴油发电机](../machines/portable_diesel_generator_v1.md)"拉绳"：锁视角、时机条、按键、不一定一次成功、服务端定结果）。钉钉子用同一套（3.2）。

1. **进入：** 五金贴好、螺丝放进孔里、螺丝刀刀头卡进槽（动作，0.6 秒 × 速度，放 `furniture_screw_pick`）。台前干活本来就锁着视角，不另挪镜头；右手握着螺丝刀一直不放，左手按着五金。
2. **时机条：** 和拉绳同一根条（准星下方，`AflUiDraw` 的暗底细描边）：白色标记 1.4 秒来回扫一趟，每拧一下绿区换一个位置，宽度按木工等级（3.7）。条下面提示"右键 拧    Shift 停下"（按键名跟着玩家设置；钉钉子写"右键 钉"）。干活面板的进度条在 QTE 里停着，第二行写"装合页 3/6"这种第几颗。
3. **按准：** 拧四分之一圈：手腕转、松开、换握（0.5 秒 × 速度，沉稳），放一段 `furniture_screw_turn`，螺丝往下进一截；标记闪绿。一颗拧几下按木工的档：新手 4 下，有经验 3 下，熟练 2 下（录音里的手拧也是一颗几下，不做真实的十几圈）。
4. **按偏：** 刀头从槽里滑出来（打滑），手腕一抖，螺丝不动；标记闪红，放打滑声（7.1）。**不会拧花**，不废螺丝，只是多花时间。
5. **拧好的动画（0.8 秒 × 速度）：** 最后一下放 `furniture_screw_seat`：手腕多压一点、停住，螺丝头贴平五金面；刀头从槽里拔出来，螺丝刀稍稍抬起，槽口留在随机角度。五金上的螺丝从那一刻起一直画着，标记闪绿一下。这一件五金的螺丝都拧好，接下一件五金或下一段。
6. **谁会拧：** 学会了这条配方就会拧（钉钉子一样），不要另外的本事；木工等级只改难易和快慢（3.7）。
7. **离开：** 按潜行停下，同走开、被推开、误按 E 打开物品栏、死亡：都按第 6 节的中断处理（这一件记住做到第几颗，回来从那一颗接着拧，拧好的不用重来）。30 秒不按也停下。
8. **服务端：** 客户端发"按了、在不在绿区"，同拉绳；服务端判两下之间最少隔一个动作的长度，拧了几下、第几颗、扣量都在服务端。防刷按累计干活时间，QTE 的时间也算进去。

### 3.6 料件

台上那块"正在做的东西"。按家具选一种，生成真实的模型（不做占位方块），每种有自己的钉位表、上漆的分条和五金位：

| 料件 | 是什么 | 尺寸（约） | 用在 |
|---|---|---|---|
| `panel` 拼板 | 2–4 根短料并排，背后两根横档 | 90 × 40 cm | 椅子座面、桌面一半、背板、搁板 |
| `frame` 框 | 四根料角接成的框（框板门、箱盖的框） | 90 × 45 cm | 床头柜、衣柜门、储物箱盖 |
| `box` 抽屉 | 四块侧板加底板的开口盒 | 50 × 35 × 15 cm | 床头柜、衣柜抽屉 |
| `rack` 挂板 | 一根长挂板 + 一块小搁板 + 托架 | 100 × 18 cm | 工具架、枪架 |

- 上漆按长边分 8 条：每擦一下，下一条换成胡桃木色；8 条擦完，剩下的几下来回擦已经上过漆的地方。
- 一件家具只画一块料件，不在台上逐件拼出整把椅子：椅子、桌子这类的形状已经很清楚，没必要把整件家具搬到台上。

### 3.7 木工成长（用户 2026-10-22："可以通过多次干活来降低QTE的难度和拧的速度，钉子也是一样"）

加一项成长 `afl:carpentry` 木工，照 [背景与技能方案](survivor_background_skills_v1_plan.md) 第 7 节的成长做：数据文件 `survivor_growth/carpentry.json`，0–10 级，普通人 0 级，经验和等级存在现有的成长记录里（只加一项 ID，不改 `SurvivorRecord` 的格式），成长页自动多一行。

| 档 | 等级 | 一根钉几锤 | 一颗螺丝几下 | 节点 |
|---|---|---|---|---|
| 新手 | 0–3 | 4 | 4 | 0 级 `carpentry_novice`：一锤一锤小心敲 |
| 有经验 | 4–6 | 3 | 3 | 4 级 `carpentry_trained` |
| 熟练 | 7–10 | 2 | 2 | 7 级 `carpentry_practised`：两锤钉平，两下拧紧 |

- **每一级（普通格子）：** 绿区宽 14% + 1.2% × 等级（0 级 14%，5 级 20%，10 级 26%）；钉、拧的动作时长 ×(1.2 − 0.04 × 等级)（0 级 ×1.2，5 级 ×1，10 级 ×0.8）。下料、上漆、擦余漆不跟着变（锯子已经 PASS，不动）。
- **绿区还乘** `qte_window`（所有 QTE；现有特质"手笨"×0.8 自动生效），最多 0.36。
- **经验（第一版，实机试过再调）：** 钉进一根钉 1，拧好一颗螺丝 1，做完一件家具再加 3；一件做完时一起给（中途放弃、换配方丢掉的半成品不给，防止反复开工刷经验），乘学习速度 `growth_xp`。升级要的经验同枪械（0→1 30 … 9→10 520，0→10 共 1955）。按这个数：0→4 级要 225 点，大约 28 把椅子（一把 5 + 3 = 8 点）或 5 个衣柜（14 + 28 + 3 = 45 点）。
- **开局：** 木工的特质或背景（比如"会做木工"从 4 级开始、木匠背景从 7 级开始）写进背景与技能方案，那份文档由负责的会话改，这里只提建议。
- 以后开罐、拆旧家具卸五金也用同一套 QTE 和这项成长。

## 4. 每件家具的段落

次数是台上看得见的次数（螺丝是真实颗数）；时间按第 3 节加起来，不再用 2.2 节暂定的时间。第一次开罐和搅漆（约 6.5 秒）、最后盖罐（2 秒）不算在每件里，同锯原木开头的提锯。钉钉子和拧螺丝按每下都按准、每下平均等绿区 0.4 秒估算，分木工三档（3.7），实际看玩家按得准不准。

| 家具 | 料件 | 下料 | 钉钉子 | 上漆 | 擦余漆 | 五金（螺丝） | 一件：新手 / 有经验 / 熟练 | 原暂定 |
|---|---|---|---|---|---|---|---|---|
| 木质工具架 | `rack` | 1 | 4 | 1 | 1 | — | 32 / 27 / 22 s | 12 s |
| 木质枪架 | `rack` | 2 | 5 | 1 | 1 | — | 43 / 37 / 31 s | 15 s |
| 木椅 | `panel` | 2 | 5 | 1 | 1 | — | 43 / 37 / 31 s | 20 s |
| 木桌 | `panel` | 3 | 6 | 2 | 1 | — | 58 / 51 / 44 s | 30 s |
| 木床头柜 | `box` | 2 | 8 | 2 | 1 | 拉手 1（1 颗） | 68 / 56 / 46 s | 30 s |
| 木制储物箱 | `frame` | 3 | 10 | 3 | 1 | 合页 2、拉手 2、锁眼盖 1（16 颗） | 177 / 139 / 106 s | 45 s |
| 木衣柜 | `frame` | 4 | 14 | 4 | 1 | 合页 4、拉手 4（28 颗） | 278 / 216 / 161 s | 60 s |

- 一根钉：新手 4.7 s、有经验 3.5 s、熟练 2.3 s；一颗螺丝：5.7 / 4.1 / 2.7 s。
- 原来全是动作时（2026-10-22 第一版，用户说"时间按照你上面写的做就行"）是 22.6–110.1 s。加了按键判定、螺丝按真实颗数以后变长了：没有五金的几件长约 20–40%，储物箱和衣柜长一倍多，主要是螺丝（衣柜 28 颗，新手光拧螺丝就要 2.7 分钟）。
- 钉子要是也按真实根数做 QTE（衣柜 80 根），衣柜新手要近 10 分钟，所以钉子还是按看得见的根数（第 9 节第 6 条）。
- 漆的次数按扣的量：0.1 L 一次，0.2–0.3 L 两次，0.4 L 三次，0.7 L 四次。
- 做好的家具照旧直接进身上（`CarriedItems.give`），整件组装不画。衣柜和储物箱在台上做的是门 / 箱盖，玩家看到的就是做部件的过程。
- 觉得长的话，减看得见的钉子次数最直接（每少一根 2.3–4.7 秒），不压每一下的速度。

## 5. 配方格式

### 5.1 例子（木椅）

```json
{
  "format_version": 1,
  "result": { "item": "apocalypse_firstlight:wooden_chair" },
  "materials": [
    { "tag": "apocalypse_firstlight:boards", "count": 1 },
    { "item": "apocalypse_firstlight:nail_box", "pieces": 16 },
    { "item": "apocalypse_firstlight:wood_finish_can", "litres": 0.1 }
  ],
  "tools": ["apocalypse_firstlight:hand_saw", "apocalypse_firstlight:claw_hammer"],
  "work": [
    { "step": "furniture/crosscut", "times": 2 },
    { "step": "furniture/nail", "times": 5, "piece": "panel" },
    { "step": "furniture/wipe", "times": 1 },
    { "step": "furniture/wipe_off" }
  ],
  "unlock": [
    { "type": "background", "background": "forest_resident" }
  ]
}
```

### 5.2 按量扣的材料行

| 字段 | 说明 |
|---|---|
| `litres` | 按升扣，写升（`0.1`），存成 mL（四舍五入到 1 mL，至少 1 mL）。只能用在按 mL 计的物品上（`MeteredSupplyItem` 的 `litres` 为真，现在是 `wood_finish_can`） |
| `pieces` | 按根 / 个扣，整数，至少 1。只能用在按个计的物品上（现在是 `nail_box`） |

- 一行里 `count`、`litres`、`pieces` 只能写一个。按量扣的行只能写 `item`，不能写 `items` / `tag`（以后有别的漆、别的钉子再放开）。
- 单位写在字段名里：写配方的人一眼看出是 0.1 L 还是 100 mL，写错单位的那一行加载时就拒绝，日志写原因（例：`litres on a pieces item`）。
- 用量检查和扣量都按身上**所有**同种容器剩下的加起来算：两罐各剩 0.05 L 也够 0.1 L。

### 5.3 `work` 写成列表

| 字段 | 必填 | 说明 |
|---|---|---|
| `step` | 是 | 动作段的 ID，即 `player_actions/<step>.json`；文件不存在时整条配方拒绝加载 |
| `times` | 否 | 播几次，1–64，默认 1 |
| `piece` | 否 | 这一段起换哪种料件（第 3.6 节）；不写就沿用上一段的；第一段不写就是一根板材 |

- `work` 写成列表时不写 `seconds`：时间 = 每段动作的长度 × 次数，加起来。写了 `seconds` 就拒绝加载，免得两边对不上。
- `work` 仍然可以写成一个字符串（一个循环动作，现在的锯横梁、木拉手），这时照旧要写 `seconds`。旧配方不用改。
- 开罐（含搅漆）、盖罐这种"一批只做一次"的写在动作段自己的文件里（`lead_in` / `lead_out` 两个新字段，指向另一个动作），配方不管。锯原木开头的提锯（现在写死在 `WorkbenchWork.STARTS`）以后也搬进这两个字段。

## 6. 扣量和中断

- **什么时候扣：** 同现在，一件做满时一次扣齐（板材、钉子、漆、五金），中途停下什么都不扣。开始干活和每件开头都检查一遍够不够，不够就不开始（"材料不够"），免得做了一分钟才发现。
- **扣哪一罐：** 先扣剩得最少的那个（已经开过的罐子先用完），扣光的罐子和盒子没了（`MeteredSupplyItem.setLeft`，现有逻辑）。创造模式也扣（同现在的工作台规则）。
- **中断以后接着做（用户 2026-10-22 定："比如说玩家误触了E，可以把之前做到的部分继续渲染在工作台上（不管是区块缓存还是静态obj都行）"）：**
  - 工作台记住没做完的那一件，存在方块实体里（存档、退出重进都在）：哪条配方、第几件、做到哪一段、这一段第几次、钉了几根、拧了几颗、累计干了多少 tick。
  - 台上照旧画到那一段的料件：锯了几根短料、钉帽、上漆分条、装上的五金和拧好的螺丝，漆罐开着、钉子盒开着也照旧。
  - 怎么画：没人干活时这件半成品是静止的，同台上挂着的工具一样交给区块画（块实体渲染器画一次录下来，`WoodGunRackChunking` 那套，见 `WorkbenchRenderer`），不占每帧的块实体渲染；有人干活时由块实体渲染器画（要动）。
  - 谁都能接：会这条配方的幸存者站到台前点"制作"同一条，从那一段的开头接着做，钉好的钉、拧好的螺丝不用重来（半成品就在台上，不认是谁开的头）。
  - 制作页里这条配方标"台上有半成品：木椅（钉钉子 3/5）"。点别的配方时先问一句"会丢掉台上的半成品"，确定才丢（材料本来没扣，丢了不亏材料，只亏时间）。
  - 服务端防刷按累计干活时间算，不按这一次开始的时间。经验在一件做完时给（3.7）。
  - 工作台被拆掉时半成品没了（材料没扣，不掉东西）。

## 7. 声音（2026-10-22 提示词；能复用的复用）

听得到的范围和给感染者的噪声半径一样（用户规则）。录音每条一次生成，里面录几次、中间留空，由我切段：单声道，开头 3 ms、结尾 40 ms 淡入淡出，每组统一增益（同折刀）。

### 7.1 复用

| 用在 | 复用 | 怎么放 |
|---|---|---|
| 横截下料的推、拉 | `hand_saw_push` / `hand_saw_pull` | 同锯原木，8 格 |
| 锯子、锤子、螺丝刀放到台面 | `workbench_tool_down` | 原样，4 格 |
| 短料码到料堆上、钉子盒 / 漆罐 / 螺丝盒放到台面 | `workbench_tool_down` | 音调 1.35、音量 0.45（同木拉手放到台面），4 格 |
| 合页、拉手、锁眼盖拿到料件上 | `workbench_tool_down` | 音调 1.7、音量 0.3（同木拉手落下），2 格 |
| 从螺丝盒里拿螺丝 | 新录的"拿钉子"（7.2 第 2 条） | 事件 `furniture_screw_pick`：音调 0.9，2 格 |
| 擦掉余漆 | 新录的"抹布擦木头"（7.2 第 6 条） | 音量 0.6，2 格 |
| 钉钉子的轻敲 | 新录的整锤 `nail_strike_*`（录音里没有轻敲） | 事件 `furniture_nail_tap`：音量 0.35、音调 1.2，4 格 |
| 拧螺丝打滑（3.5.1） | 新录的"拿钉子" | 事件 `furniture_screw_slip`：音调 1.3、音量 0.5，2 格；以后可以单录一条刀头打滑 |
| 钉钉子敲偏（3.2） | 新录的整锤 `nail_strike_*` | 事件 `furniture_nail_glance`：音量 0.5、音调 1.35，4 格 |
| 一件做完 | 原版 `WOOD_HIT`（现有） | 不变 |

### 7.2 新录的（2026-10-22 已切好、已登记）

用户用 Firefly 生成了 6 条（盖罐没录），由 `design/items/furniture_bench_work_v1/fb_cut_sounds.py` 切成 35 个单声道 OGG，放 `sounds/furniture/`，在 `sounds.json` 登记了 12 个事件（加上 7.1 的敲偏、打滑共 14 个），字幕 en_us / zh_cn 同一行号（`subtitles.apocalypse_firstlight.furniture_*`）。代码里还没有调用（等动作做出来时登记到 `AflSounds`）。

| # | 录音 | 切成 | 事件 | 半径 | 字幕 |
|---|---|---|---|---|---|
| 1 | 钉钉子（13 下整锤） | `nail_strike_1..6`、`nail_seat_1..3` | `furniture_nail_strike` / `furniture_nail_seat`（轻敲见 7.1） | 10 格 | Hammer drives nail / 锤子：钉钉子 |
| 2 | 拿钉子 | `box_open_1`、`nail_pick_1..5` | `furniture_box_open` / `furniture_nail_pick` | 2 格 | Box opens / 纸盒：打开；Nails rattle / 钉子：碰响 |
| 3 | 撬开漆罐（只开了一次） | `can_open_1`（3.1 s，整段） | `furniture_can_open` | 4 格 | Paint can pried open / 漆罐：撬开 |
| 4 | 盖罐 | 没录（用户 2026-10-22：不好生成，先不做） | — | — | — |
| 5 | 搅漆（约 10 s） | `stain_stir_1..3`（2.9 / 2.9 / 4.2 s，第 3 段带搅拌棍提出来） | `furniture_stain_stir` | 2 格 | Stain stirred / 木器漆：搅拌 |
| 6 | 抹布上漆 | `rag_dip_1..2`、`rag_wipe_1..4` | `furniture_rag_dip` / `furniture_rag_wipe` | 2 格 | Rag dipped / 抹布：蘸漆；Rag wipes wood / 抹布：擦木头 |
| 7 | 拧螺丝（第二版提示词） | `screw_turn_1..8`、`screw_seat_1..2` | `furniture_screw_turn` / `furniture_screw_seat` | 2 格 | Screwdriver turns / 螺丝刀：拧螺丝 |

切法同折刀（`design/items/folding_knife_v1/fk_cut_sounds.py`）：按频谱图粗定窗口，起点取包络第一次到峰值 30 dB 以内再提前 5 ms，终点取最后一次高于 max(峰值 − 45 dB, 底噪 + 6 dB) 再加 20 ms；开头 3 ms、结尾 40 ms 淡入淡出；每组一个增益，最响的一段峰值 −3 dBFS。例外：

- **钉钉子：** 录音里 13 下都是整锤，没有轻敲；1 kHz 以下最厚的三下（2.48、3.80、7.60 s）当钉平那一下，其余挑 6 下当整锤。
- **抹布：** 生成出来几下之间差到 20 dB，每段单独拉到最响那段的 95% 分位 10 ms 电平（峰值不超过 −3 dBFS）。罐沿上两下尖的磕碰（2.5、7.1 s）不用；8.9–9.9 s 那一下也不用（中间一下磕碰把峰值顶住，拉不上来）。
- **搅漆：** 连续的声音，在手标的分界附近 0.35 s 内找最静的 10 ms 切开，30 ms 淡入淡出。
- **拧螺丝：** 录音是一下一下的手拧，正好一下一段，配 QTE（按准一下放一段）；最后两下又低又紧的（7.36、9.62 s）当拧好那一下。
- 每组的试听拼接在 `design/items/furniture_bench_work_v1/preview/preview_<组>.ogg`（每段之间空 0.6 秒）。切段只看了频谱和电平，没有逐段听过。

### 7.2.1 原来的录音清单（提示词对应的）

| # | 切成 | 事件 | 半径 | 字幕 |
|---|---|---|---|---|
| 1 | `nail_tap_1..4`、`nail_strike_1..6`、`nail_seat_1..3` | `furniture_nail_tap` / `furniture_nail_strike` / `furniture_nail_seat` | 轻敲 4 格，整锤和钉平 10 格 | Hammer taps nail / 锤子轻敲钉子；Hammer drives nail / 锤子钉钉子 |
| 2 | `box_open_1`、`nail_pick_1..4` | `furniture_box_open` / `furniture_nail_pick` | 2 格 | Box opens / 打开纸盒；Nails rattle / 钉子碰响 |
| 3 | `can_open_1..2` | `furniture_can_open` | 4 格 | Paint can pried open / 撬开漆罐 |
| 4 | `can_close_1..2` | `furniture_can_close` | 4 格 | Paint can lid tapped shut / 敲紧漆罐盖 |
| 5 | `stain_stir_1..3` | `furniture_stain_stir` | 2 格 | Stain stirred / 搅拌木器漆 |
| 6 | `rag_dip_1..2`、`rag_wipe_1..6` | `furniture_rag_dip` / `furniture_rag_wipe` | 2 格 | Rag dipped / 抹布蘸漆；Rag wipes wood / 抹布擦木头 |
| 7 | `screw_drive_1..4` | `furniture_screw_drive` | 2 格 | Screwdriver turns / 螺丝刀拧螺丝 |

文件放 `sounds/furniture/`。字幕 en_us / zh_cn 同一行号写。

### 7.3 Firefly 提示词

1. 钉钉子：`Close-mic foley of a steel claw hammer driving 2-inch common nails into a pine board lying flat on a heavy hardwood workbench, four nails one after another with two seconds of silence between nails. For each nail: two light wrist taps to set the nail, a short pause, then three full, unhurried strikes, the last strike sounding flatter and more solid as the head seats flush into the wood. A bright tick of steel on steel over a dull wooden thud from the bench, no anvil ring, no long metallic ringing tail. Dry studio recording, no reverb, no room echo, no music, no voice.`
2. 拿钉子：`Close-mic foley of a small cardboard box of loose steel nails: first the cardboard lid flap is lifted open once, then fingers pick single nails out of the box, six separate picks with one and a half seconds of silence between them, each a short light metallic rattle and clink of nails shifting as one is pinched out. Quiet and small, no handfuls, no pouring. Dry studio recording, no reverb, no room echo, no music, no voice.`
3. 撬开漆罐：`Close-mic foley of opening a one-quart metal paint can with the claw of a hammer, two separate times with three seconds of silence between them: the claw tip catching under the lid rim and levering up at three points around the can, each with a small metallic creak, then the lid coming free with a soft suction pop and being laid down on a wooden bench with a light tin clank. Dry studio recording, no reverb, no room echo, no music, no voice.`
4. 盖罐（没录，用户 2026-10-22：不好生成，先不做）：`Close-mic foley of closing a one-quart metal paint can, two separate times with three seconds of silence between them: the tin lid set back onto the rim with a light scrape, then tapped shut with the face of a hammer, two firm, controlled taps, a dull tin knock, not a ringing bell. Dry studio recording, no reverb, no room echo, no music, no voice.`
5. 搅漆：`Close-mic foley of a flat wooden stick slowly stirring thick oil-based wood stain in a one-quart metal can, about ten seconds of slow circles: a soft, viscous swish of liquid and the stick end lightly scraping the metal bottom and side, then a few gentle drips falling back into the can as the stick is lifted out at the end. Calm and unhurried. Dry studio recording, no reverb, no room echo, no music, no voice.`
6. 抹布上漆：`Close-mic foley of wiping wood stain onto a bare pine board with a cotton rag, two rounds with two seconds of silence between them. Each round: the folded rag dipped into a metal can of thin oil-based stain with a small wet squish, pressed against the can rim to squeeze out the excess, then three long, slow strokes along the grain of the board, a soft damp cloth-on-wood swish for each stroke. Quiet and close. Dry studio recording, no reverb, no room echo, no music, no voice.`
7. 拧螺丝（2026-10-22 第二版；第一版写了 `driving` 和 `No power drill`，生成出来像电动螺丝刀，只描述手拧的断续声）：`Close-mic foley of a person slowly tightening steel wood screws by hand with an old wooden-handled screwdriver into a thick oak board, four screws with two seconds of silence between them. For each screw: a small metal click as the tip finds the slot, then four separate quarter turns, each one a short, dry creak of wood and a faint squeak of steel lasting about half a second, with a quiet pause between turns while the hand lets go and regrips the handle. The creaks get slower and tighter on the last turns until the screw head sits flush. Intermittent, uneven, human pace; between turns there is near silence. Dry studio recording, no reverb, no room echo, no music, no voice.`
   - 还像电动的话用备用条（只录一颗螺丝的最后几圈）：`Close-up foley recording of the last few hand turns of a screw being tightened into hard oak with a manual screwdriver: five slow, separate creaks, each about half a second long, with one second of silence between them, each creak a little tighter and higher than the one before, ending with a tiny squeak as the head seats. Quiet, dry, close mic, no reverb, no music, no voice.`
   - 提示词规则：不写"不要电钻"这类否定的工具名（模型会反着带进来），不用 `driving`，写成 `tightening by hand`、断续、每下多长、中间停多久。

- 第 7 条已录（第二版），螺丝刀的事见第 9 节第 2 条。
- 撬开漆罐写的是羊角锤的羊角；要是加了螺丝刀、改用螺丝刀撬，把 `the claw of a hammer` 换成 `a flat screwdriver`。

## 8. 界面

- **制作页材料行：** 按量扣的行在"有 / 要"一栏写带单位的数，例：`胡桃木色木器漆　0.65 / 0.1 L`、`圆钉　172 / 16`。不够时标黄，同别的材料。数量选 3 件时"要"跟着乘（0.3 L、48）。
- **干活面板（快捷栏上方）：** 照旧"正在做：木椅 1/3"和进度条，进度条下面加一行这一段在做什么，2–4 个字：下料、钉钉子、上漆、擦余漆、装合页、装拉手、装锁眼盖；钉钉子、拧螺丝时后面跟第几根 / 第几颗（"钉钉子 3/5""装合页 3/6"），准星下方另有时机条（3.5.1）。按钮或面板宽度变化要在游戏里看折行。
- 物品提示里罐子写"还剩 0.65 L"、钉子盒写"还剩 172 根"（已有）。

## 9. 用户的决定（2026-10-22）和还没定的

1. **时间：** 照第一版算的做（"时间按照你上面写的做就行"）。加了按键判定后的时间见第 4 节。
2. **螺丝刀：** 照推荐加（用户 2026-10-22 定）。一字螺丝刀做第三件工具，只给床头柜、储物箱、衣柜三条配方；以后拆旧家具出五金也能用它。开漆罐仍用羊角锤的羊角（10.2）。
3. **中断后接着做：** 做，半成品画在台上（第 6 节）。
4. **钉钉子：** 要按键判定，以后能成长（"钉钉子也需要判定，后面也可以成长，比如说钉的多之类的"）：3.2、3.7。
5. **拧螺丝：** 要几颗做几次 QTE，不会拧花；学会配方就会拧，干得多了绿区变宽、拧得变快，钉子一样：3.5.1、3.7。
6. **钉子按看得见的根数做 QTE：** 照推荐（用户 2026-10-22 定）。螺丝按真实颗数，钉子按台上看得见的根数（配方 `times`），扣料仍按真实根数（第 4 节）。
7. **木工成长的数（第一版）：** 3.7 的绿区、锤数和拧几下、经验，实机试过再调。

## 10. 做了什么（2026-10-22，分支 AFL-Workbench，编译通过，未实机）

1. **配方和加载：** 7 条家具配方在 `workbench_recipes/furniture/`；材料行可以写 `litres` / `pieces`（只能写一个物品，必须是按量的罐子或盒子），`work` 写成段落列表时不能写 `seconds`，时间按新手的段落时长算（`FurnitureWork.estimate`）。`WorkbenchRecipeData` 拒绝不认识的段落、没有动作文件的段落、钉钉子和装五金写在料件之前。
2. **扣量：** `CarriedItems` 按容器剩量求和、从剩得最少的扣起；一件做满时一次扣齐（第 6 节）。
3. **服务端（`cabin/FurnitureWork`、`WorkbenchCrafting`）：** 方块实体存半成品（配方、段落表、木料、拉手种类、第几段、第几次、第几颗螺丝、这根钉 / 这颗螺丝敲了几下、累计干活 tick）和漆罐开没开。玩家每说一句（`Act`：一段放完，或一次按键和按没按准）服务端算下一段该放什么（`Plan`：一串动作、等什么、按准的反应、按键前后的进度），用 `WorkOf` 发给自己和看得见的人；离上一句太近的不认。开始时 `Work` 带"丢掉别的半成品"，制作页点第二次才发。一件做完给木工经验，接着做下一件；一批做完、漆罐开着就盖罐再停。协议 70。
4. **客户端（`client/workbench/FurnitureWorkClient`）：** 自己干活时一句话发出去就先放下一段（不等服务端），服务端的回包只核对进度，对不上才换；准星下方的时机条同发电机拉绳（绿区宽度按木工等级和 `qte_window`），右键钉 / 拧，Shift 或左键停下，30 秒不按也停下。动作的锚点、声音和木屑都按动作文件的 `events` 在当时的位置放（锯口、罐口、钉子、螺丝、五金）。面板多一行这一段在做什么（"钉钉子 3/5""装合页 3/12"）。
5. **台上画的（`client/FurnitureWorkRenderer`，台面布置在 `client/workbench/FurnitureBench`）：** 下料时木板按还要截的段数加 30 cm 长，推出台端一段，锯口跟着锯走，截断后短料拿到右后方叠起来；之后是料件（四种：面板、框、盒、架），每块板按树种的木板纹理画、纹路顺着板长，上漆按深度分 8 条从前往后一笔一笔变成胡桃木色；钉子、螺丝按敲进去 / 拧进去多少画，螺丝的一字槽跟着转；五金按位置装好；有人干活时画漆罐（盖、搅拌棍、抹布按动作放下的位置）和钉子盒 / 螺丝盒。
6. **动作和道具：** `tools/author-furniture-actions.mjs` 写出 20 个动作和 11 个道具（`--check`、`--report` 离线核对握点和第一人称视角）；网格 `furniture_work`（漆罐、盖、钉子、螺丝、抹布、搅拌棍、短料、两种散堆）、`screw_box`、`screwdriver`，都是 Mesh + PBR。
7. **声音：** 第 7 节的事件放在动作的时刻上，打开钉子盒 / 螺丝盒在每段第一根钉、第一件五金开始时放；盖罐没有声音（用户定）。
8. **木工成长：** `survivor_growth/carpentry.json`（0–10 级，0 / 4 / 7 级三档），钉一根、拧一颗各 1 点经验，一件 3 点。

### 10.1 和设计稿不一样、简化的地方

- **半成品不交给区块画：** 第 6 节说没人干活时交给区块画；现在一直由块实体渲染器每帧画（零件少，一张台最多几十个面）。卡了再改成录下来交给区块。
- **开漆罐仍用羊角锤：** 木椅、餐桌、两种架子不带螺丝刀也要开罐，所以三条带螺丝刀的配方也不换撬法。
- **抽屉的拉手：** 拿来时是竖着的，0.85 秒放到位时一下转平；框上的拉手用顶上一颗螺丝固定（看得见的位置），不是从板里面拧。
- **漆没有湿的反光：** 只变颜色。
- **搅拌棍平放时立着放（窄边朝下）：** 同动作里手放下的姿势，略嵌进台面几毫米。
- **最后一刀截完，木板和短料堆一下换成料件：** 没有拼装的过程。

### 10.2 要在游戏里看的

- 时机条和面板第二行的宽度、折行（"装合页 3/12"这类）。
- 一句话发出去先放下一段，网络慢时服务端的回包会不会让动作跳一下。
- 钉子、螺丝、五金和手里道具交接的那一下有没有跳。
- 上漆分条的颜色和木板纹理。

### 10.2.1 实机第一轮（2026-10-22）

- 用户：不能 PASS，问题多，明天逐条列出再修。
- 用户 2026-10-23 列出的问题（暂缓，Main 在审 AFL 专属 Mesh 人物、手臂和手指，等那边定了再修）：
  1. 第一人称看不到手臂。
  2. 每一次时机条都会把镜头拉到旁边。
- 已修：退出世界时崩溃。台上留着半成品、没人在做时 `nailShown` 拿到空动作，`Set.of(...).contains(null)` 抛空指针（`FurnitureWorkClient`），已加判空，离线编译通过。

### 10.3 原来的待办（已做）

1. 加载：`WorkbenchRecipeData` 解析 `litres` / `pieces` 和列表形式的 `work`；同步包的材料行带量和单位、`WorkOf` 带段落表，分支上协议仍是 69（master 68，合并时 Main 重排）。
2. 扣量：`CarriedItems` 加按容器剩量求和、按剩量从少到多扣；`WorkbenchCrafting.craft` 分开处理按量的行。
3. 动作：`tools/author-furniture-actions.mjs` 写出第 3 节的 9 个动作文件（`--check`、`--report` 用 Blockbench 插件同一套骨骼离线核对握点、穿模、第一人称视角），胸口动作合并进 `tool.animation.json`，不整份重写（见工作台设计 17 节的警告）。
4. 道具模型：4 种料件、短料、抹布、搅拌棍，Mesh + PBR，按真实尺寸；钉子盒、漆罐打开的样子（盒盖、罐盖分开的部件）。
5. 钉钉子和拧螺丝 QTE：客户端时机条和按键（照 `client/PortableGeneratorPull`），C2S 包，服务端计数（3.2、3.5.1）；分支协议跟着 +1。木工成长：`survivor_growth/carpentry.json`、`SurvivorGrowth` 加来源、语言文件的成长名和三个节点（3.7）。
6. 台上的进度：`WorkbenchRenderer` 按段画料件、钉帽、上漆分条、五金和螺丝；中断后的半成品存方块实体，没人干活时交给区块画（第 6 节）；制作页标半成品、换配方先确认。
7. 声音：已切好、已登记（7.2）；动作做出来时登记到 `AflSounds` 并在动作的时刻上放。盖罐没有声音。
8. 配方：7 个 JSON 放 `workbench_recipes/furniture/`。
9. 文档：本文改成"已做"，`afl_workbench_v1_design.md` 18.2 补字段，`furniture_recipes_v1.md` 2.2 移到 2.1，wiki 工业页的工作台一节。
