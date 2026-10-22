# 按部位包扎和倒酒精 V1（2026-10-21）

状态：**已实现，编译通过，离线检查通过，没进游戏看**（D 工作区分支 `feature/lightning-injury`，协议 64 不变）。

## 1. 用户的要求

用户 2026-10-21（看过第一人称爬行手臂之后）：「不行，你的包扎/倒酒精的动画只做了手臂的，其他部位并没有做」「腹部怎么自己包扎，现在胸部的受伤在背部，逻辑错了，一个人根本不可能包扎到自己背后吧」。

用户按推荐选了这几条：

1. 胸、腹的伤只出现在正面和两侧。
2. 背枪烫出来的烫伤留在背上，自己够不到，不能自己处理，背包身体图上写明"够不到"，只能等它自己好。
3. 头、胸、腹的包扎是"垫 + 绕圈"：先把一块纱布垫按在伤口上，再绕部位缠三圈。
4. 腿坐下来处理：站着的玩家先坐下，处理完继续坐着。

倒酒精（告诉过用户的做法）：头是歪头、往那一侧倒；胸、腹是稍微后仰、往正面倒；腿坐着倒。每个部位都是 4.6 秒，声音（`MedicalItem.ALCOHOL_CUES`）和液流的时间窗（2.1–2.9 秒）都不变。

## 2. 哪个部位用哪个动作

用的时候按这次使用开始时对准的伤口部位选动作（`client/BandageWrapRenderer#treatedPart`，用的过程中不换），第一、第三人称共用：

| 部位 | 绷带 | 时长 | 酒精 | 时长 | 镜像 |
| --- | --- | --- | --- | --- | --- |
| 前臂 | `item/bandage_wrap`（原来的） | 4 s | `item/alcohol_clean`（原来的） | 4.6 s | 按伤的那只手臂（左臂为原版本） |
| 头 | `item/bandage_head` | 5 s | `item/alcohol_head` | 4.6 s | 右手为准，左撇子整体镜像 |
| 胸 | `item/bandage_chest` | 6 s | `item/alcohol_chest` | 4.6 s | 同上 |
| 腹 | `item/bandage_belly` | 6 s | `item/alcohol_belly` | 4.6 s | 同上 |
| 腿（坐着） | `item/bandage_leg` | 5 s | `item/alcohol_leg` | 4.6 s | 按伤的那条腿（左腿为原版本） |

- 选择在 `client/skeleton/SkeletonActions`；找不到按部位的动作时退回前臂动作。
- 时长在 `health/MedicalItem`：`BANDAGE_TICKS = 80`（前臂）、`BANDAGE_HEAD_TICKS = 100`、`BANDAGE_TORSO_TICKS = 120`、`BANDAGE_LEG_TICKS = 100`、`ALCOHOL_TICKS = 92`。原版拿到的使用时长是最长的 120 tick（绷带）/ 92 tick（酒精），短的在 `onUseTick` 到点就结束（`MedicalItem#useTicks`）。
- 声音：前臂照旧；头、胸、腿的绷带按各自动作的关键帧另排了一套（`BANDAGE_HEAD_CUES`、`BANDAGE_TORSO_CUES`、`BANDAGE_LEG_CUES`：拿卷、按住布头、三圈、贴上、撕断、拍两下、放下）。酒精所有部位共用 `ALCOHOL_CUES`。

## 3. 绷带（垫 + 绕圈）

- 动作：先把纱布垫按在伤口上（右手），然后两只手轮流把纱布卷绕过部位：头从脑后、胸腹从背后、小腿从下面接过卷，另一只手在前面压住。三圈缠完贴上布头、撕断、拍两下、放下。
- 时间和 `BandageWrapRenderer` 的缠绕进度一致（`wrapFrom` / `wrapSeconds`）：头 0.8 s 起每圈 1 s；胸、腹 0.9 s 起每圈 1.4 s；小腿 0.7 s 起每圈 1 s（前臂照旧 0.6 s 起共 2.5 s）。
- 纱布是真的几何圈（和前臂的一样），缠的位置：头在眉毛到头顶（从头顶往下 0.6–3.4 px 的行），胸在腋下（身体的第 1–6 行），腹在腰上（第 6.6–11.4 行）；一圈宽头 1.3 px、胸腹 2.0 px。纱布垫画在皮肤伤痕层上（[skin_wound_marks_v1_plan.md](skin_wound_marks_v1_plan.md)）。
- 头的伤痕现在只出现在头顶四行（眉毛和头皮，正好在缠的那一圈下面）。
- 生成：`tools/medical-actions-v1a/author_dress_v1.cjs`，手用 AFL Rig 核心的手臂 IK 解出来再烘成 FK 关键帧；共用的身体姿势（坐、坐着够小腿 `SHIN_WORK`）在 `pose_lib.cjs`。

## 4. 倒酒精

- 开盖、拿着盖子、盖回去的时间和 `alcohol_clean` 完全一样，所以声音和液流时间窗对所有部位都成立：瓶子 2.15–2.85 s 倒在伤口上，左手指尖捏着盖子放在一边。
- 头：头往左歪（身体也带一点），右侧朝上，在右耳上方倒，瓶口沿着头往后移。
- 胸、腹：稍微后仰、低头看，在正面倒，瓶口横着移过伤口。
- 腿：坐着，左膝抬起（和包扎小腿同一个坐法，身体少前倾一点），顺着小腿骨往下倒，最后回到坐姿，右手落回右膝。
- 身体是动作片段（`medical.alcohol_<部位>`，`hold_body`，头和躯干的还有 `hold_head`：头跟片段走，不跟视线）；两只手在游戏里对着道具实时 IK。腿的动作管整个身体，第一人称相机在动作摆出来的眼睛位置（`first_person_eye`）。
- 液流：`client/MedicalPourJets` 只看瓶子道具的瓶口点和瓶轴，所以新动作不用改它。
- 生成：`tools/medical-actions-v1a/author_alcohol_parts_v1.cjs`（`--write` 写出片段和 `player_actions/item/alcohol_<部位>.json`）。

## 5. 背上的伤和"够不到"

- 胸、腹的伤痕只放在正面和两侧（`client/WoundSkin`，皮肤上身体的第 3–12 列），背面留给背枪的烫伤。
- 背枪烫伤（胸部的深度烫伤）自己够不到：`Wounds.View#selfReach` 为假，绷带和酒精都不会选它，它只能自己愈合。
- 只剩够不到的伤时，用绷带或酒精会提示"背上的烫伤自己够不到"（`message.apocalypse_firstlight.wound.out_of_reach`）；背包身体图上这处伤显示"在背上，自己够不到，只能等它自己愈合"（`screen.apocalypse_firstlight.inventory.body.out_of_reach.back`）。

## 6. 腿：坐下来处理

- 伤在腿上时，站着按住使用：服务端先让玩家坐下（`sit/GroundSit`），坐稳之后按住的键再次开始使用（和腿上夹板同一套 `SplintItem#legReady`）。
- 处理的时候不能站起来（`GroundSit#treatingLeg`），处理完继续坐着。用的过程中离开座位会中断。
- 坐着时的腿：别的物品动作只管上半身，腿夹板和腿的包扎、倒酒精管整个身体（`GroundSitClient` 的 `FULL_BODY`）；处理过右腿以后坐姿会换成伸着右腿。

## 7. 检查

`node tools/check-medical-actions-v1a.cjs`（D 工作区根目录）：每个动作的 `use_ticks` 和 `MedicalItem` 一致，手 IK 偏差、手腕、前臂扭转、手掌和躯干的距离。2026-10-21 的结果：8 个新动作全部没有报告（扭转最大 30°，IK 偏差 0）。`splint_leg` 的 8 帧（0.85–0.9 s 右手偏 0.64–0.67 px）是这次以前就有的，文件没动。

两个生成脚本自己还查：道具、手掌和身体的距离，倒的时候瓶轴朝下多少，液流离伤口中心多远，第一人称画面里手和瓶子在哪。

## 8. 没做 / 已知限制

- 没进游戏看：动作在游戏里的样子、第一人称视角、坐下再开始使用的手感都要用户实机看。
- 头的伤在左侧时，酒精还是在右耳上方倒（左撇子才镜像）；胸、腹同理，倒的位置是固定的正面偏右。
- 绷带的手是烘焙好的 FK，不会跟着视线变化；倒酒精的手是实时 IK。
- 别人看到的是同一份动作；GameTest 没跑。
