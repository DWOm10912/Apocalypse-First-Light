# Gun Maintenance Attachment Interaction V1

2026-09-27 entry policy: every single-stack `NativeGunItem` defaults to allowing Field inspection and maintenance, even without supported attachment slots. `NativeGunItem.inspectionRefusalReason()` returns `null` by default; C.A.T. explicitly overrides it with the existing refusal message. `AttachmentModificationPolicy.allowed` shares this opt-out policy across client entry and server transactions. No registry namespace allowlist is required. This does **not** grant attachment compatibility: `AttachmentInteractionCore.valid` still requires a supported target slot and a compatible source attachment. No-slot guns get inspection only, without attachment hotspots. Client/GameTest verification of this policy change remains pending.

> 当前附件入口：维护台与 Z Field Attachment View V1 共用附件业务、候选 HUD、音效及服务端原子交易。共享通道协议为 **31**（2026-10-02 加入容器搜索声音包后），客户端/服务端须匹配；以下旧协议和验证记录属于历史。V 仍为 Inspect，快捷安装未恢复。详见 `docs/native_guns/field_attachment_view_v1.md`。

Current scope: P9-01 SIGHT, MUZZLE and MAGAZINE, plus BR51-01 SIGHT with [Rifle Red Dot](rifle_red_dot_01_v1.md) and MUZZLE with [Rifle Suppressor](rifle_suppressor_01_v1.md), plus MAGAZINE with [BR51 35R](br51_extended_magazine_35_v1.md) (obsolete since 2026-10-21: the BR-51 V3 uses the magazine system with 20 / 30 / 50-round magazines, see [BR-51 V3](../br51_01_v3_game_model_v1.md)). Supported hotspots and server transactions read non-empty slot compatibility from each gun definition, not a P9-only gate. The initial instant commit is superseded by [UI/SFX polish](gun_maintenance_attachment_ui_sfx_polish_v1.md): shared 2.480-second sound, 51-tick action window, then server revalidation and commit. The [24R magazine](../p9_01_extended_magazine_v1.md) adds capacity switching and transactional excess-ammo return. Since 2026-10-19 the P9-01 uses the [magazine system](../magazine_system_v1.md) (uncommitted, not checked in game): a MAGAZINE change returns the old magazine with its own rounds and fits the new one with its rounds, so there is no excess ammo; removing the magazine leaves only the chambered round. No new arm/tool animation or repair. Player attachment changes use the maintenance bench or Z Field Attachment View; the V shortcut remains removed.

## 选枪栏和取回（2026-10-15，分支 `feature/inventory-grid-v0`，用户实机 PASS）

网格背包第 10 节以后，快捷栏只剩武器位和“手上”（0 主武器、1 手枪、2 近战、3 手上，4–8 锁住），枪也可以放在口袋、胸挂、背包里，维护台底部原来那条 9 格快捷栏已经对不上。用户 2026-10-15 指出后改成下面这样（方案经用户同意）。

**选枪栏**（`GunMaintenanceScreen`）

- 底部只列身上的枪，顺序：主武器（1）、手枪（2）、手上，然后口袋、胸挂、背包里的枪（按分区、行、列）。没有枪的位置不占格，近战不列；创造 / 旁观照旧列整条快捷栏里的枪。最多 16 格，居中，宽度随枪数变化。
- 每格左上角一个小标记：武器位是键位数字 `1` / `2`，手上 `手`，网格 `口` / `胸` / `背`（英文 H / P / R / B）。悬停显示枪名和所在位置（主武器 / 手枪 / 手上 / 口袋 / 胸挂 / 背包）。身上没枪时提示“身上没有枪”。
- 点一下放上台。台上是自己放的枪时，原来那格留半透明的影子，点影子取回；别人放的枪照旧显示右边的取回按钮。
- 修改策略不允许的枪（C.A.T.）照旧在客户端给拒绝提示，服务器也再查一遍。

**上台请求**：原版的按钮包只带一个字节，装不下网格位置，所以加了 `GridPackets.BenchInsert`（容器 id、来源、预期的枪）。来源是快捷栏号（0–8）或 `GridSource` 编码（和配件从网格取用同一套）。服务器在 `GunMaintenanceMenu.insert` → `GunMaintenanceBenchBlockEntity.insertFrom(player, source, expected)` 里核对：菜单还开着、台子可用而且空着、来源处还是同一把枪（物品和 NBT 完全一致）、策略允许，才把枪从那里拿走放上台。旧的按钮路径（按钮 0–8 = 从快捷栏那格放上台）还在，GameTest 和旧探针用。包加在 `GridPackets.register` 的最后，协议号这个分支当时是 44，2026-10-15 合并进 master 时定为 46。

**原位**：台子记 `OriginSource`（快捷栏号或 `GridSource`）和 `OriginRotated`（网格里转没转），替换原来的 `OriginHotbarSlot`；旧存档只有 `OriginHotbarSlot` 时照读。菜单的 `originFor(viewer)` 返回自己那把枪的来源，否则 -1。

**取回**（`takeBack`，按钮“取回”和别人的“拿取”都走这里）：

1. 取的人就是放枪的人，原位还空着，而且原位现在收这把枪（快捷栏按武器位规则 `PlayerStorageCapacity.rejects`：步枪要有背带才进主武器位，手枪要穿枪套才进手枪位；网格按 `fits`，转向和放上去时一样），就放回原位。
2. 否则走正常拾取顺序 `Inventory.add`：对应的武器位，然后口袋 → 胸挂 → 背包。
3. 都放不下就掉在脚边（`player.drop`），掉下的枪在背包页“附近”栏里能拖回来。掉落失败时枪留在台子上。
4. 以前原位被占时用原版 `getFreeSlot()` 找空格；第 10 节以后它在生存模式只认“通用格”（没有），结果枪只会掉在地上，写回原位时也不看武器位规则。这两点都改掉了。

## Shared implementation after Field Attachment View V1

`MaintenanceAttachmentTransaction` retains the menu/BlockPos/revision/distance checks and delegates inventory exchange to `AttachmentInteractionCore`. `AttachmentModificationPolicy` denies C.A.T. in both entry points. `MaintenanceAttachmentHud` now accepts `AttachmentHudHost`; its original constructor supplies the unchanged bench adapter. `AttachmentHotspotDefinition` shares slot/anchor/offset definitions; maintenance retains its world-camera projection. The bench renderer, return-origin metadata and 51-tick operation remain intact. No repair feature was added. This refactor has no new runtime regression claim; user verification is pending.

## Spatial UI

### Historical maintenance-only policy (superseded by Field Attachment View V1)

The V key mapping (`NativeSightInput`) and its translations are removed. The production `NativeAttachments` no longer exposes the hand-exchange implementation. Historical stack regression fixtures were moved to `src/dev/java/.../LegacyAttachmentFixture.java`, excluded from the release jar; their success is not evidence of a supported quick-install feature.

The old `SightExchangePacket` discriminator remains reserved with a no-op handler, preserving later message IDs and protocol 22. `requestSightExchange` is inert for legacy development probes. Even a received old packet cannot install/remove an attachment. All player changes use the existing maintenance Begin → 51-tick delay → revalidation → commit path. Already stored attachments, compatibility, effects and bench transaction semantics are unchanged.

Verification for this policy change: compilation/packaging completed; release jar contains neither `NativeSightInput` nor `LegacyAttachmentFixture`. Final server regression passed 28/28 (`build/maintenance-only-attachments-verified.log`), including maintenance transactions and delayed operations. Earlier runs exposed persistent test-world gun/workstation drops contaminating the multiplayer fixture counts; its setup now clears only prior fixture drops within the test footprint. Legacy exchange fixture assertions cover historical stack semantics, not a current player feature. No new graphical client or real two-client test was run for this policy change.

`MaintenanceGunRendering.interactionPoint` traverses the same cached immutable bind-pose bones as desktop rendering. It prefers `maintenance_sight_anchor` / `maintenance_muzzle_anchor`; otherwise uses the gun-definition mount anchor (including sight mount offset). Independent anchors can be added without changing attachment mounting. No model edits this round.

`MaintenanceHotspots` transforms that position through bench-facing and maintenance-gun transforms, then projects using the fixed maintenance camera basis, 75° FOV and current GUI aspect ratio. Radius is 20 GUI pixels. No whole-gun return box, persistent labels or outline. Hover text fades over 150 ms. Grey Context HUD remains locked after click and is clamped to the screen. Text uses zh_cn/en_us language keys.

`MaintenanceAttachmentHud` owns context/selection/pending states. Modify/Replace switches the existing bottom nine-cell area to safe candidate copies with a short fade-in. Escape/inventory-key cancels selection (also closes context), otherwise closes context, otherwise exits maintenance. Right-click cancels the open layer. Ordinary origin placeholder/non-owner take are hidden during selection and restored afterward. Whole-gun return remains disabled.

## Inventory and transaction

`AttachmentCandidatePage`: scan main inventory indices 0–35 (since 2026-10-15 also the pockets, rig and backpack grids, sources as `GridSource`, see [Grid Inventory plan](../../ui/afl_inventory_grid_v0_plan.md) 13), filter NativeAttachment slot and `NativeAttachments.compatible`, skip empty, group only matching item+NBT, retain first source slot/copy and aggregate count. Nine per page, scroll paging supports more than nine. In the maintenance screen, paging now only receives wheel input over the bottom candidate-list area; the new [Inspection View V1](../gun_maintenance_inspection_view_v1.md) uses the viewport wheel for zoom when no modal attachment operation is open. Context panels and hotspots take priority over inspection; their projections use the smoothed maintenance FOV (the Field page instead uses the final weapon root transform). Field Attachment View now shares the inspection controller and input priority, including full Orbit; maintenance only permits FOV Zoom/Reset, leaving both the camera pose and gun transform fixed; no dragging. The Field transaction contract is unchanged. No inventory relocation, no ghost stacks, no drag/drop. GunMaintenanceMenu retains original nine hotbar slots and gun slot indices, appending hidden main-inventory slots for live synchronization (the bottom gun picker no longer draws those nine slots; see “选枪栏和取回” above).

`MaintenanceActionRequest` carries containerId, root, revision, expected full gun, target and exact source stack/slot. This feature introduced `AflNetwork` protocol **22**; the current shared protocol is **29** after Field Attachment View integration (matching client/server required), with direction-bound C2S Begin and S2C start/result unchanged. Server thread validates live menu, alive/non-spectator, world/root/distance/complete bench, native gun with supported target slot, revision, full stack and source compatibility at Begin and before delayed mutation. BE persists `AttachmentRevision`; any synced mutation advances it, rejecting stale and duplicate transactions.

`MaintenanceAttachmentTransaction` consumes one real source item even in creative mode. `AttachmentInteractionCore` returns old attachments and excess ammo through the normal pickup order `Inventory.add` since 2026-10-15 (Survival/Adventure: into the grids; previously it merged into and filled the open slots directly). Remaining items use the existing player-near drop fallback. Overflow slots remain available as attachment sources but cannot receive returns; see [Locked Inventory V1](../../gameplay/locked_inventory_slots_v1.md). A failed drop restores the complete 36-slot inventory snapshot and leaves the gun unchanged. Gun commit preserves origin ownership and synchronizes BE plus inventories. Other slot/NBT are preserved. Failed requests show a small localized retry notice, refresh candidates, keep maintenance open and consume nothing. On 2026-09-29 the user confirmed in-game testing and accepted all Locked Inventory Slots V1 checks as PASS; this is user-reported acceptance, not a new GameTest run.

Client only sends intent; it never writes attachment NBT. Success closes selection/context, and existing BER reads the synchronized real gun. `MaintenanceActionState` tracks pending action; a future animation can be inserted before request dispatch, but must still pass server validation. No animation-completion workflow exists in V1.

## Verification (historical integration results)

The V quick-exchange path in the results below has since been retired. These old probes do not validate current player installation; current player operations use the maintenance bench or Z Field Attachment View; these historical tests do not verify the new Field entry.

- Compile passed. Server 24/24 GameTests passed in `build/maintenance-gametest/logs/latest.log`, including 20 alternating A/B first-winner installs, stale remove-vs-replace, exact consumption, same-type replacement conservation, both slots, origin/non-owner metadata, BE save/load and full-inventory single drop. Added cases are in `src/dev/java/com/antaurora/apofirstlight/dev/MaintenanceAttachmentTests.java`.
- Existing suppressor and sight/server weapon regressions run in this suite. No noise/sound/muzzle-exit/ADS code or resources were changed. Graphical and audio behavior are separate from server assertions.
- Initial isolated graphical probe passed C2S installation of both attachments, independent removal, Esc cancellation and origin return; screenshots captured. It directly called Screen.mouseClicked, so it did not verify physical mouse dispatch. The user subsequently reported no response when clicking candidate icons in their client. This remains an open acceptance issue until reproduced and corrected; the initial probe is not evidence that the user's input path works. Final build/acceptance are pending. True two-client synchronization, human hover/fade feel, all-facing/GUI-scale visual acceptance and disk restart after this new transaction are not claimed by GameTests.

Commands: `gradlew -I src/dev/maintenance-gametest.init.gradle runGameTestServer --offline`; isolated client `gradlew -I src/dev/maintenance-attachments-client.init.gradle runClient --offline`. The latter uses only `build/thermal-fluid-client` development world `fluid_probe`.

## Final checkpoint

The user subsequently confirmed “没问题了，可以终止测试”. Testing stopped at their request; no further client launch. The reported no-response issue is no longer an active user blocker, but its cause was not established and no specific input-path fix is claimed. Direct Screen and Forge Pre/Post probe runs passed; the final MouseHandler callback probe exited before a completion marker and is **NOT_TESTED**, not PASS. The outward-opening Context HUD was screenshot-checked after correcting its initial overlap with the gun. Compilation succeeded during client launches; a separate final distributable `build` was not run. Real two-client synchronization and physical-device/scale coverage remain unverified. The user's own client was left untouched.
> Historical protocol: **22** added P9 MAGAZINE support. Current protocol is **29**; matching client/server required. Field Attachment View runtime acceptance is pending.

## Sling slot (2026-10-22, compiled, not checked in game)

User 2026-10-22: the gun sling is installed like the other attachments, on the Z page and the maintenance bench. `NativeAttachment.Slot` gains `SLING` (appended); `GunSlingItem` implements `NativeAttachment`. Gun JSON `sling_slot` (`NativeSlingMount`: `anchor`, `hotspot_offset` in model pixels from the anchor pivot, `accepts`) sits at the rear swivel: BR-51 `gun_body` (0, 3.72, 21.8), HR-55 `group3` (0, −1.83, 3.5), Silverwood 12 `stock` (0, −3.94, 7.1). `AttachmentHotspotDefinition.SLING` has no preferred maintenance anchor; `MaintenanceAttachmentHud` titles it `gun_maintenance.sling` and reports `INSTALLING_SLING` / `REMOVING_SLING`. Transactions are unchanged (`AttachmentInteractionCore`). The legacy `AflSling` flag reads as a stored gun sling and is cleared on the first SLING write. Since sling physics R1 (same day) a fitted sling hangs from the gun's front and rear swivels as a simulated strap with snap hooks, also round the body when carried: [Sling physics R1](sling_physics_v1.md). The enum value and the removed `GridActions.Kind.SLING` change the wire: the protocol must be bumped at merge. Details in the [Grid Inventory plan](../../ui/afl_inventory_grid_v0_plan.md) 19.
