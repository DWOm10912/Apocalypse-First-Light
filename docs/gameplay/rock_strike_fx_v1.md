# 凿石头的粒子和声音 V1（2026-10-24，C 分支 `feature/underground-u2`；用户实机全部 PASS：碎石片、火星、声音，粉尘第 2 版）

用户 2026-10-24：凿石头的粒子还是原版的（方块碎屑和暴击星星），通用音效也还缺。本文是镐凿岩石的特效：巨石、露天岩石地面（R2）、岩体雕刻（U3a-3 第 2 轮）三处一起换。

相关：`docs/worldgen/underground_u3_rocks_ores_design.md` 第 13 节（岩体雕刻）、`docs/gameplay/rock_r2_rock_face_mining_design.md`（露天凿）、`docs/gameplay/rock_r1_stones_boulders_design.md`（巨石）。

---

## 1. 粒子（已做）

服务器每下发一条 `boulder/RockStrikeFx` 给 48 格内的玩家：落点、朝外的方向（从落点指向凿的人的眼睛）、凿到的岩石、这一下凿掉的升数、有没有凿穿一格或凿碎巨石、这种岩石会不会冒火星。客户端 `client/RockStrikeParticles` 按它生成三种东西：

| 东西 | 样子 | 怎么动 | 多少 |
|---|---|---|---|
| 碎石片 | 8×8 像素的不规则小片，分亮暗两个面（`textures/particle/rock_chip_0..5`，`tools/rock/build-rock-particle-sprites.mjs` 生成，`--check` 通过），颜色是这种岩石新断口的平均色（从 `textures/block/rock/cut/` 读），四分之一偏暗（风化的外皮） | 朝凿的人这边呈锥形飞出，翻滚，受重力，落地弹一两下，滑一下停住，最后缩没；会撞上凿出来的坑壁 | 5 + 升数 / 7（花岗岩一下约 12 片）；凿穿或凿碎巨石时 3 倍，部分碎片大一倍 |
| 粉尘 | 自己的贴图 `textures/effect/rock_dust`（第 2 版，见下）：稀薄、有颗粒感的粉雾，里面夹着碎砂点；颜色是岩石粉末色，比岩石浅 45–60% | 从岩面前 5 cm 喷出来、散开、变大，慢慢往下沉，不往上飘，不变灰，碰到东西就停，约 2 秒散尽 | 2 + 升数 / 40；凿穿时 3 倍、团更大 |
| 碎砂 | 碎石片的贴图缩到 1 cm 左右，颜色比岩石浅 20–40% | 从粉尘里撒下来，不弹，0.75–1.5 秒没 | 4 + 升数 / 15；凿穿时 3 倍 |
| 火星 | 子弹打在金属上的火星（用户 2026-10-24 要求复用）：沿飞行方向拉长的亮痕，白色冷却到橙色，落地弹，打中的地方一闪 | 比子弹的慢、少：每次 2–5 道，速度 0.12–0.3 格/tick，亮 4–9 tick | 只有含石英的岩石（花岗岩、砂岩的外观：花岗岩、片麻岩、片岩、砂岩和对应的巨石），每下 45% 的机会 |

- 粉尘和火星画在 `client/FireFx` 自己的世界通道里，和火焰烟、子弹火星同一个上限（烟团 300、火星痕 200）。`FireFx` 加了 `rockDust`（带颜色、沉降的粉尘团）和 `pickSparks`。
- **粉尘第 2 版（用户 2026-10-24 实机：其他部分 PASS，粉尘不行）：** 第 1 版借的是火焰烟的贴图，翻滚的烟团看着就是烟；而且是黑的：粉尘从岩面里 5 cm 生成、贴着岩面沉降，取光取在实心岩石格子里，光照是 0（开光影是棕色）。现实参照：凿击扬起的是一小股很淡的浅色粉，比岩石本身浅得多（粉末细，散射光）；看得见的部分主要是较粗的颗粒，一两秒就落下来；最细的那部分肉眼看不见、会飘很久（矿山粉尘资料：小于 5 µm 的粉尘云肉眼看不见，大于 10 µm 的按重力沉降）。CC0 里找到的现成贴图（Kenney 烟雾包等）都是卡通烟团，不合适，所以程序生成：`tools/rock/build-rock-dust-sprites.mjs`（`--check` 通过），8 张 128 px，形状不规则、拉长，密度是扭曲的分形噪声取幂（有丝缕和空隙，不是圆团），叠像素颗粒（粉，不是汽），每张 30–60 个 1–2 像素的砂点。新渲染类型 `LiquidRenderTypes.DUST`，在烟之后单独画（`FireFx.renderDust`）。取光：粒子所在格子是实心方块时，取六个相邻非实心格子里最亮的方块光和天空光（`FireFx.lightAt`，烟也用它，烟本来不会进实心格子，没影响）。每下另撒碎砂。
- 碎石片是普通不透明粒子（`AflParticles.ROCK_CHIP`）。原版粒子第一次落地就永远停住，所以碎片有自己的移动，能弹能滑。
- 原版粒子设置：减少时碎片减半，最少时只剩几片、没有火星；粉尘由 `FireFx` 自己按设置减。
- 巨石每下按 50 L、凿碎时按 120 L 算碎片；露天凿按土方实际凿掉的升数；岩体雕刻按实际凿掉的小格。
- 原版的方块碎屑、暴击星星、往下落的粉尘全部去掉了。
- 光影：只看不开光影和我们自己的 Aureveil（用户 2026-10-24：不适配 Sundial）。

## 2. 声音（已接入，2026-10-24，编译通过，没进游戏听）

用户 2026-10-24 用 Firefly 生成了三条录音（`E:/Download/硬岩石.wav`、`软岩石.wav`、`大块掉下来.wav`，10 秒、48 kHz 立体声，左右相关 0.986–0.995）。`tools/rock/build-pick-strike-sounds-v1.mjs` 按 SHA-256 核对录音、按静音切开，输出单声道 Ogg：

| 声音事件 | 文件 | 来源 | 时长 | 响度（最大 400 ms） |
|---|---|---|---|---|
| `pick_strike_hard` | `sounds/rock_strike/hard_1..5` | 硬岩石的 5 下 | 0.58–1.34 s | -23.2 到 -24.9 LUFS |
| `pick_strike_soft` | `sounds/rock_strike/soft_1..5` | 软岩石 6 下里的前 5 下（第 6 下的碎石声被录音结尾截断） | 0.48–1.07 s | -23.0 到 -25.8 LUFS |
| `rock_break` | `sounds/rock_strike/break_1..4` | 大块掉下来：录音要 3 次，实际第 2 次滚落中途又重重落地一次，在 6.2 s 的短停顿处切开，共 4 个 | 1.79–2.69 s | -22.3 到 -23.6 LUFS |

- 响度参照金属垃圾桶开盖（-24.3 LUFS）：凿击 +3（和砍树一样），石块掉落 +2（叠在凿击上听）；峰值都不超过 -1 dBFS。凿击先高通 40 Hz，掉落 30 Hz。
- 凿击开头 1–3 ms 的钢尖脆响比主体高 20 多 dB，峰值上限把它们压到比目标低 2–6 dB、彼此差 4 dB；工具里加了瞬态限幅（峰值不超过周围 30 ms RMS 以上 12 dB），现在 5 个变体差 2.8 dB，算正常的轻重差别。
- 掉落的第一次落地在文件 0.25 s 处（石块先掉半米），和凿击同一 tick 放，听起来是先凿、后掉。
- 播放：`RockStrikeFx.sound`，巨石、露天岩石、岩体雕刻三处共用。每下按岩石软硬放凿击（音高 0.95–1.05，带噪声，半径 16 格 `BoulderStrike.RADIUS`）；凿穿一格或巨石裂开、凿碎时再叠一声 `rock_break`（不另算噪声）。原版 `STONE_HIT` / `STONE_BREAK` 占位全部去掉。
- 硬软：花岗岩、砂岩外观（和冒火星的一样）算硬，石灰岩、页岩外观算软；岩石表里没有的石头（原版石头）算硬。
- 字幕：`镐：凿击岩石`（Pick strikes rock）、`石块：崩落`（Rock breaks loose）。

下面是生成用的提示词，留作重录参考。三条每条一次生成，同样的开头，好让音色一致：

**录音 1：硬岩石（花岗岩、片麻岩、片岩、砂岩，和花岗岩、砂岩巨石）**

> Close-up dry foley recording outdoors. A man swings a heavy steel pick mattock and strikes a solid granite rock face, five separate strikes at a slow, steady hand pace, about two seconds apart with silence between them. Each strike is a sharp, hard steel-on-stone impact with a short bright ring, followed by small rock chips and grit pattering down for about half a second. Dry, close microphone, no reverb.

**录音 2：软岩石（石灰岩、白云岩、页岩、泥灰岩、岩盐，和石灰岩、页岩巨石）**

> Close-up dry foley recording outdoors. A man swings a heavy steel pick mattock and strikes soft, layered shale rock, five separate strikes at a slow, steady hand pace, about two seconds apart with silence between them. Each strike is a dull, crunchy thud as the point bites into the rock, followed by thin flakes cracking off and sliding down with fine grit for about half a second. Dry, close microphone, no reverb.

**录音 3：大块掉下来（凿穿一格、巨石碎开），只录石块，不带凿的声音**

> Close-up dry foley recording outdoors. Three separate events, about three seconds apart with silence between them. Each time, a large chunk of stone breaks loose from a rock face with a short dry crack, drops about one metre, lands heavily on rocky ground and tumbles, followed by gravel and small stones settling for about one second. No tools, no metal, no pick or hammer strikes. Dry, close microphone, no reverb.

用户 2026-10-24：原来的第 3 条里也有镐凿的声音，凿穿时会和凿击声冲突。现在第 3 条只录石块裂开、掉下、滚落，凿击声永远由前两条出。

实际生成的第 3 条开头就是落地声，没有单独的裂声，所以掉落声往后放 0.25 s，而不是从裂声开始叠。

## 3. 改了哪些文件

- 新：`boulder/RockStrikeFx`（包，注册在 `PickPacket.register` 末尾；`sound`、`hard`）、`client/RockStrikeParticles`、`particles/rock_chip.json`、`textures/particle/rock_chip_0..5.png`、`tools/rock/build-rock-particle-sprites.mjs`、`tools/rock/build-pick-strike-sounds-v1.mjs`、`sounds/rock_strike/*`（14 个）。
- 粉尘第 2 版：新 `tools/rock/build-rock-dust-sprites.mjs`、`textures/effect/rock_dust.png`；改 `LiquidRenderTypes`（`DUST`）、`ClientFuelStains`（烟之后画粉尘）、`FireFx`（`renderDust`、`lightAt`）、`RockStrikeParticles`（粉尘位置、颜色、透明度，碎砂 `Chip.grit`）。
- 改：`client/FireFx`（`rockDust`、`pickSparks`，烟团加颜色和沉降）、`AflParticles`（`ROCK_CHIP`）、`AflParticleProviders`、`AflSounds`（`PICK_STRIKE_HARD`、`PICK_STRIKE_SOFT`、`ROCK_BREAK`）、`sounds.json`、`en_us` / `zh_cn`（字幕，第 1284–1285 行）、`BoulderStrike`（巨石和露天凿）、`RockMining`（岩体雕刻）。
- 网络协议还是 72（这个分支本轮已经从 69 改到 72，还没提交）。

## 4. 进游戏要看的

1. 凿花岗岩崖壁、洞壁：碎片朝自己飞、颜色对不对、会不会弹；粉尘的浓淡和沉降；火星多不多；
2. 凿石灰岩、页岩：没有火星，粉尘更白；
3. 凿穿一格、凿碎巨石：一大团；
4. 露天凿岩石地面；
5. 不开光影和 Aureveil 各看一次；
6. 声音：花岗岩和石灰岩的凿击听得出硬软；凿穿一格、凿碎巨石时先凿后掉，两声不打架；凿击和砍树比，响度合不合适；连凿几下变体换得自然。
