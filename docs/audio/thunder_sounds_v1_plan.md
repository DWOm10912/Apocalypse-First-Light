# 雷声 V1（用户 2026-10-21 实机 PASS）

D 工作区，分支 `feature/lightning-injury`（用户 2026-10-21 定：雷声和天气系统都继续在这个分支上做，不新开）。

## 1. 用户的要求

用户 2026-10-21：「我想完善一下雷电，也就是先音效，再做我们自己的动态天气系统，替换原版的那些天气，按照地区实现」。天气系统见 [regional_weather_v1_plan.md](../gameplay/regional_weather_v1_plan.md)。这份只管雷声。

## 2. 原来的样子（这次之前）

闪电 V1（[lightning_v1.md](../rendering/lightning_v1.md)）：

- 声音还是原版的两段。
  - `LIGHTNING_BOLT_THUNDER`：音量 10000，全图都听得到。
  - `LIGHTNING_BOLT_IMPACT`：落点的炸裂声。
- 两段都按距离 / 343 m/s 延迟（`client/lightning/AflLightning`）。雷声从通道上离镜头最近的点算，炸裂声从落点算。
- 远近都是同一段录音，只有音量不同。近处没有撕裂一样的炸响，远处也没有低沉的滚雷。
- 客户端只知道实体追踪范围里的闪电：视距 10 时大约 220 m。再远的雷既看不到也听不到，所以远处的雷暴是哑的。

## 3. 现实里的雷声

| 距离 | 听到的 | 为什么 |
|---|---|---|
| < 约 80 m | 先是很短的嘶嘶声或咔哒声，几乎同时一声极响、极干脆的爆裂，像近处开炮，耳朵发麻，后面跟一段短的轰鸣 | 冲击波还没衰减成声波；高频全在 |
| 80–600 m | 撕裂一样的"咔啦"一声，接着一声大的轰响，再滚 3–6 秒 | 通道上近的那段先到，远的一段段后到 |
| 0.6–3 km | 先是闷的一声"轰"，然后长长地滚，5–10 秒，没有尖的头 | 高频被空气吸收；通道各段到你的距离差大，所以滚得长 |
| 3–15 km | 低沉的隆隆声，慢慢起来、慢慢下去，10–20 秒，音量小 | 只剩低频，起头被拉平 |
| > 约 15 km | 一般听不到，只看得到闪光（"热闪"） | 声音被地面和大气折射上去 |
| 云内闪电（不落地） | 从头顶或远处一大片地方传来的低沉嘟囔，没有爆裂的头 | 通道是横的，离你都远 |

滚雷有多长：大约 = （通道最远点 − 最近点）/ 343 m/s。所以离得越远、通道越横，滚得越长。

在室内或车里：高频被墙和玻璃挡掉，只剩低沉的闷响。近的雷会让窗户嗡嗡响。

## 4. 要录的声音

按"一段录音切开用"的规矩，每个距离档在 Firefly 里生成一次，切成几段变体。

当初计划的（实际切出来的见第 7 节）：

| 事件 | 距离（通道最近点） | 变体 | 长度 | 从哪段录音切 |
|---|---|---|---|---|
| `thunder_crack` | < 80 m | 3 | 3–5 s | ① 近雷 |
| `thunder_close` | 80–600 m | 3 | 5–8 s | ② 近中雷 |
| `thunder_mid` | 0.6–3 km | 3 | 8–12 s | ③ 中远雷 |
| `thunder_far` | 3–15 km | 3 | 10–20 s | ④ 远雷 |
| `thunder_cloud` | 云内闪电（等天气系统） | 2–3 | 10–20 s | ⑤ 云内雷 |

Firefly 提示词（英文，不要音乐、不要人声）：

1. **近雷**
   `Lightning striking extremely close, about 30 meters away, recorded outdoors in heavy rain: a split-second electric hiss, then one deafening explosive crack like a cannon shot right beside you, a short harsh roar and quick decay. Three separate strikes with about four seconds of only rain between them. No music, no voices.`
2. **近中雷**
   `Close thunderclap about 300 meters away during a storm: a sharp ripping, tearing crack that turns into a loud booming roar, then rumbles for about five seconds and fades. Three separate thunderclaps with rain-only gaps between them. Outdoors, realistic field recording, no music, no voices.`
3. **中远雷**
   `Thunder from about 2 kilometers away: no sharp crack, a dull heavy boom followed by a long rolling rumble that rises and falls for about ten seconds. Two separate rolls of thunder with quiet gaps between them. Outdoors, light rain, realistic, no music, no voices.`
4. **远雷**
   `Very distant thunder about 10 kilometers away: a soft deep low-frequency rumble that slowly swells and slowly fades over fifteen seconds, no crack, no high frequencies. Two separate distant rumbles with silence between. Quiet summer evening, no rain, no music, no voices.`
5. **云内雷**（做天气系统时再录也行）
   `Long low grumbling thunder rolling across the whole sky from inside the clouds, no impact or crack, deep and continuous for about fifteen seconds, distant and spread out. Two separate rolls. No music, no voices.`

一段生成出来不够长或不够干净时，同一个提示词再生成一次，挑一段用，不拼合成。

原版的 `LIGHTNING_BOLT_IMPACT` 炸裂声不再用：近处的炸响已经在①里。

## 5. 怎么放（推荐的做法）

- **选哪段**：按通道上离镜头最近的点算距离，落在哪一档就放那一档的随机变体。档的边界加 ±15% 的随机，免得一个距离永远是同一种声音。
- **延迟**：距离 / 343 m/s，和现在一样。
- **音量和方向**：自己按距离算音量（近雷最响，远雷小），声音放在镜头往最近点方向 16 m 的地方，这样左右声道有方向，音量不被原版的衰减改掉。
- **室内、车里闷掉**（建议做）：镜头头上有遮挡（雨顶图那一列在镜头上面），或者坐在关了门的 T15 驾驶室里，天气类声音走一个 OpenAL 低通滤波（LWJGL 的 EFX 扩展，原版带着），只剩低频。雨声也能用同一个滤波。现在代码里还没有任何滤波，这是新加的一层。
- **远处的闪电**（建议和雷声一起做）：落雷时服务器给 15 km 内、但追踪范围外的玩家发一个小包（落点、通道高度、种子）。客户端只放雷声，在天上画一下远处的闪光。这样远处的雷暴有声音，也是闪电 V1 第 5 节 V2 里的"远处闪电"。天气系统做好以后，同一个包也用来发云内闪电。
- **听力**：①近雷照样走听力负荷（耳鸣 V2 `HearingLoad`），离得越近加得越多。直接被劈中的情况，雷击伤已经处理了。
- **感染者**：雷声不算感染者噪声（全图都在打雷，算了的话会把整片尸群拉来）。只有 80 m 内的落雷像爆炸一样算一次噪声。这和"声音范围 = 噪声半径"那条规矩不一样，需要用户点头。
- **字幕**：每个事件一条（中英文同时写）。

## 6. 用户的决定（2026-10-21，都按推荐）

1. 室内和车里的低通滤波：做。
2. 远处闪电的小包：和雷声一起做。新包要改网络协议号，协议号归 Main 管，这个分支先用 64 不动，合并时由 Main 给号。
3. 雷声算不算感染者噪声：只有 80 m 内的落雷算一次（像爆炸），远处的滚雷不算。
4. 分支：继续在 `feature/lightning-injury`。

## 7. 做了什么（2026-10-21，用户实机 PASS）

### 7.1 录音和切段

用户在 Firefly 里每档生成了一次（五段都是 10 s、48 kHz、立体声），原件放在 `design/audio/thunder/source/`（`1_crack.wav` … `5_cloud.wav`）。

- 生成出来的不是"几声雷中间隔着雨声"，而是一整段连着的雷，所以每档从录音里两个不同的起头切两段变体，不是三段。
- 远雷和云内雷在 1 kHz 以上有一层很轻的底噪（"悉悉索索"，比雷声低 10–17 dB）。按距离做的低通把它去掉了，这也正是空气对远雷做的事。
- 切段工具：`tools/thunder-sounds/cut.py`（量音量和底噪的是 `analyze.py`，找起头的是 `onsets.py`）。淡入淡出用 sin² / cos²，每段峰值归一到 −1 dBFS，Vorbis q5。
- 近的四档转成单声道（游戏里要能分左右）；云内雷保持立体声。

| 声音 | 文件 | 从录音的哪里切 | 低通 | 长度 |
|---|---|---|---|---|
| `thunder_crack` | `sounds/weather/thunder_crack_1.ogg` | ① 0.10–2.45 s | 无 | 2.35 s |
|  | `thunder_crack_2.ogg` | ① 2.52–10 s | 无 | 7.48 s |
| `thunder_close` | `thunder_close_1.ogg` | ② 0.10–7.00 s | 7 kHz | 6.90 s |
|  | `thunder_close_2.ogg` | ② 3.22–10 s | 7 kHz | 6.78 s |
| `thunder_mid` | `thunder_mid_1.ogg` | ③ 0.08–10 s | 3 kHz | 9.92 s |
|  | `thunder_mid_2.ogg` | ③ 3.12–10 s | 3 kHz | 6.88 s |
| `thunder_far` | `thunder_far_1.ogg` | ④ 0–10 s | 900 Hz | 10.00 s |
|  | `thunder_far_2.ogg` | ④ 3.4–10 s | 900 Hz | 6.60 s |
| `thunder_cloud` | `thunder_cloud_1.ogg` | ⑤ 0–10 s，立体声 | 1.2 kHz | 10.00 s |
|  | `thunder_cloud_2.ogg` | ⑤ 4.4–10 s，立体声 | 1.2 kHz | 5.60 s |

`thunder_cloud` 已注册，但现在没有东西放它：云内闪电要等天气系统 W1。

### 7.2 怎么放

- `client/lightning/Thunder`（`AflLightning#thunder` 调它，原版的 `LIGHTNING_BOLT_THUNDER` 和 `LIGHTNING_BOLT_IMPACT` 都不放了）：
  - 距离 d = 镜头到主通道上最近一点；档的边界 80 / 600 / 3000 m，每次落雷整体随机移动 ±15%；超过 15 km 不放。
  - 音量 = max(0.15, min(1, √(250 / d)))：80 m 1.0，600 m 0.65，3 km 0.29，15 km 0.15。
  - 音调 0.92–1.06 随机；变体由原版按 `sounds.json` 随机挑。
  - 延迟 = d / 343 m/s（15 km 约 44 s）。
  - 声音放在镜头往最近点方向 16 m 处，不按距离衰减（`Attenuation.NONE`），只用来分左右。
- 字幕（`subtitles.apocalypse_firstlight.thunder_*`，中英文同一节）：雷声：头顶炸响 / 轰隆作响 / 隆隆作响 / 远处闷响 / 云里滚过。

### 7.3 室内和车里闷掉

`client/weather/WeatherMuffle`，加一个 mixin `client.SoundChannelAccessor`（拿 `Channel` 的 OpenAL source）：

- 每个客户端 tick 算一个闷的程度 m（0–1，约半秒平滑过去）：
  - 镜头头上有挡雨的方块时，m = (13 − 天空光) / 9，夹在 0–1：门口天空光 13 以上不闷，屋里深处天空光 4 以下全闷；
  - 坐在 T15 驾驶座或副驾驶座：两扇门都关 m = 1，开着一扇 0.4；两者取大的。
- 天气类声音（`SoundSource.WEATHER`：AFL 的雷声和原版的雨声）开始播放时（Forge `PlaySoundSourceEvent` / `PlayStreamingSourceEvent`，声音线程），给它的 source 挂一个 EFX 低通：总音量 1 → 0.65，高频（约 5 kHz 以上）1 → 0.06，按 m 线性。只在开始时定一次。
- 声源会被重复使用，所以这里挂过滤波的 source 拿去放别的声音时会把滤波摘掉；不碰别人的 source。
- 声卡没有 EFX 时，记一条警告，什么都不滤。

### 7.4 远处的闪电

- 服务器（`world/LightningStrikes#struck`，`EntityJoinLevelEvent`，不含读档）：每次落雷给同一维度里水平 15 km 内的每个玩家发 `LightningPackets.FarStrike`（闪电的 UUID 和落点）。
- 客户端（`client/lightning/FarLightning`）：等 3 tick。如果真的闪电实体已经到了（追踪范围内），什么都不做；没到就自己造一个同 UUID 的闪电（所以通道形状一样），每 tick 走 `AflLightning#clientTick`：雷声、天空闪一下。
- 天空闪（原版整片天变亮）只给 1.5 km 内的闪电（真的和自己造的都一样），远处的只看得到通道本身。
- 不开光影时在 `AFTER_WEATHER` 自己画：整根闪电朝镜头缩小到远平面 0.7 以内，屏幕上大小不变；画的时候关掉雾（不然超过视距就被雾吃掉）。开了光影时远处的通道不画（Oculus 把这种画法推迟，画不对），只有雷声，光影包从 `LightningShaderData` 拿到它的位置和亮度，按它自己的做法照亮雷暴；追踪范围内的真闪电照旧画。
- 新包排在 `LightningPackets` 已有三个包后面，后面所有包的编号都往后挪一个，所以要改网络协议号。协议号归 Main 管，分支上还是 64，合并时由 Main 给新号。

### 7.5 感染者

同一个地方：不是纯视觉的闪电（避雷针的火花、三叉戟那种不算）在落点发一次 `NoiseType.EXPLOSION` 噪声，半径 80 m（`LightningStrikes.NOISE_RADIUS`）。听力负荷照旧走 `hearing/HearingExposure#lightning`。

### 7.6 没做

- 用户 2026-10-21 实机 PASS：各档音量、屋里和驾驶室里闷的程度、远处闪电（开关光影）。
- 云内闪电和 `thunder_cloud` 等天气系统 W1。
- 近雷时窗户嗡嗡响没做。
- 没跑 GameTest（用户自己在世界里走）。

调试：`/summon lightning_bolt ~ ~ ~50` 这种近的；远的需要另一个玩家或用命令在远处召唤，例如 `/execute positioned ~2000 ~ ~ run summon lightning_bolt`（落点区块要加载：远处没加载的区块里召唤不出来，可以用 `/forceload`）。
