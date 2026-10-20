# P9-01 第一次掏枪上膛 V1（2026-10-19）

状态：用户 2026-10-19 实机 PASS（和"新枪是空的"一起）。

用户 2026-10-19：给每把枪加一个"已上膛"标记，第一次掏枪时播 `first_draw`（拉套筒上膛）。

同一天用户又定："新枪现在默认是满的，我觉得不应该，所有枪都一样。"所以新枪改成空的（1.1 节），`first_draw` 变成少见的情况。

## 1. 规则

### 1.1 新枪是空的（所有枪）

- 新拿到的枪（创造物品栏、`/give`、合成，以后的战利品也一样）：弹匣 0 发，膛里也没有弹。
- 实现：`NativeGunAmmo.read` 在枪没有弹药数据（`AflGunAmmo`）时读成 0（以前读成满弹匣）；服务端第一次物品 tick（`initialize`）把 0 写进去。已经有弹药数据的枪不变，原来几发还是几发。
- 写进弹药数据不算"换了一把枪"（`shouldCauseReequipAnimation` 只比较去掉弹药数据后的标签），不会反复重新掏枪。
- 要一把装好弹的枪，得明确写发数（`NativeGunAmmo.set`）。2026-10-19 查过：出生物品、新世界生存者设置、战利品表、建筑 NBT、开发命令都不给枪，所以没有要改成"装好弹"的地方。GameTest 里要满弹匣的都改成自己写发数。
- 一般的流程：拿到新枪（0 发，空仓待机：P9 `empty_idle` 套筒在后，BR51 / HR55 等 `static_bolt_caught`）→ 空仓换弹，**播完**时上膛 → 之后都是普通掏枪。
- 双管霰弹枪（Silverwood 12）：没装过弹的新枪两个膛都是空的，不是两个空壳（`NativeBreakActionChambers.State.EMPTY`，用 `NativeGunAmmo.everLoaded` 判断：最后一发和空仓换弹播完都会写 `chambered`，新枪两样都没有）。所以第一次换弹不冒膛口烟（`NativeChamberGasFx` 只认空壳）。但第一人称换弹动画里的退壳是动画自己做的，新枪第一次空仓换弹还是会看到两个空壳被退出来。
- HUD 布局编辑器在手里没枪时用的示例 P9 自己写成满弹匣，预览和以前一样（`NativeGunHud.editorStack`）。
- 负重跟着变：新枪不算弹匣里的弹（`StackMassCalculator` 的 `loaded_ammo` 为 0）。

### 1.2 上膛标记

- 枪的弹药数据（`AflGunAmmo`）多一个 `chambered`：膛里有没有弹。**没有这一项就当作没上膛**。
- 掏枪时（`NativeGunActions.drawClip`）：枪的动画里有 `first_draw`、膛是空的、弹匣里有弹 → 播 `first_draw`，否则播普通的 `draw`。新枪弹匣是空的，所以掏出来是普通的 `draw`。
- 现在会播 `first_draw` 的情况：
  - 空仓换弹在弹匣插进去以后、播完以前被打断（切走、死亡、换维度）：有弹、没上膛。
  - 这次更新之前就有的枪：有发数、没有 `chambered`，下次掏出时拉一下。
  - 以后刷出来的、弹匣装着弹的枪（存放的枪一般弹匣满、膛空）：刷的时候写发数、不写 `chambered`，捡到后第一次掏出会拉一下。
- 什么时候改这一项（都在服务端）：
  - `first_draw` **播完**：上膛。中途切走（换快捷栏、死亡、换维度）不算，下次掏出还会再拉一次。
  - 打出最后一发：膛空了。
  - 空仓换弹**播完**、弹匣里有弹：上膛。中途切走不算，套筒还挂在后面，下次掏出会拉一下。
  - 战术换弹：不变，膛里那发还在。
- 弹药数不变：上膛只是把一发从弹匣挪进膛里，总数没变。暂时不做"弹匣 15 + 膛 1"这种分开计数。
- 不检查开火：没上膛的枪只能是刚掏出来，掏枪动作本来就锁着开火（`first_draw` 1.2 s，普通 `draw` 0.5 s）。
- 只有动画里有 `first_draw` 的枪会这样，现在只有 P9-01（`AflItems` 里 P9 的动画列表加了 `first_draw`）。别的枪也会记这一项，但不影响掏枪。
- `chambered` 放在 `AflGunAmmo` 里，和发数一样不算"换了一把枪"（`shouldCauseReequipAnimation` 只比较去掉弹药数据后的标签），改它不会触发重新掏枪。

## 2. 客户端

- 开镜：`first_draw` 播放期间不能开镜（`NativeGunAds.action`，原来只认 `draw`，现在认名字里带 `draw` 的动作）。
- 镜头骨骼：`first_draw` 和 `draw` 一样跟镜头骨骼（`NativeCameraBoneConsumer.supportedAction`）。
- 第三人称：`SkeletonGunStance` 本来就按名字里带 `draw` 处理，`first_draw` 走同一条路。从枪套拔出的过渡还是按 `draw` 的 0.5 s 算，之后跟 `first_draw` 的手和枪；第三人称看起来对不对，要进游戏看。

## 3. 动画和声音

`p9_01_v3_native.animation.json` 的 `first_draw`（1.2 s）：升到胸前偏左、绕前臂内翻，左手从左侧反手拉套筒（0.45–0.65 s），再回到双手待机。声音 0.0 `draw`、0.45 `slide_back`、0.615 `slide_release`（撞击对准套筒 0.65 s 回位，见 [p9_01_fire_sound_v2.md](p9_01_fire_sound_v2.md) 第 5 节）。

现实里：插着满弹匣的手枪，膛里一般没有弹，要拉一下套筒才能打。打空后套筒挂在后面，插上新弹匣也不会自己复位，要按空仓挂机解脱杆或拉一下套筒。

## 4. 代码

- `weapon/NativeGunAmmo`：`chambered(stack)`、`setChambered(stack, definition, value)`（先写发数，避免只有 `chambered` 的弹药数据被读成空弹匣）。
- `weapon/NativeGunActions`：`FIRST_DRAW`、`drawClip`；`operation` 接受 `first_draw`（枪支持这条动画时）；最后一发清掉上膛；会话播完时 `first_draw` 和空仓换弹设为上膛。
- `registry/AflItems`：P9 的动画列表加 `first_draw`（这样 GeckoLib 能触发它）。
- `weapon/client/NativeGunAds`、`weapon/client/NativeCameraBoneConsumer`：见第 2 节。

## 5. 之后

- 如果做实物弹匣（每个弹匣自己记发数），`chambered` 就是"膛里那一发"，正好变成"弹匣 N + 膛 1"：退出弹匣时膛里那发还在，插回别的弹匣不用再拉。
- 其他枪要第一次掏枪上膛，就在动画里加 `first_draw`，并加进 `AflItems` 里它的动画列表。

## 6. 要在游戏里看

1. 创造模式拿一把新的 P9 → 掏出来会拉套筒，1.2 s 内不能开火、不能开镜。
2. 切走再切回 → 普通掏枪。
3. 打空 → 空仓换弹 → 切走再切回 → 普通掏枪。
4. 空仓换弹到一半切走 → 切回时拉套筒。
5. 旧存档里已有的 P9：第一次掏出会拉一下。
6. 第三人称看别人（或环绕镜头看自己）掏一把新枪。
