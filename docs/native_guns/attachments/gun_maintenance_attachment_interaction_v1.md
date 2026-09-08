# Gun Maintenance Attachment Interaction V1

2026-09-09. Only `apocalypse_firstlight:p9_01`, `SIGHT` and `MUZZLE`. The initial instant commit is superseded by [UI/SFX polish](gun_maintenance_attachment_ui_sfx_polish_v1.md): server-approved shared 2.480-second operation sound, 51-tick action window, then revalidation and commit. No arm/tool animation, repair, other guns or new attachments. Existing V-key exchange and weapon stats remain unchanged.

## Spatial UI

`MaintenanceGunRendering.interactionPoint` traverses the same cached immutable bind-pose bones as desktop rendering. It prefers `maintenance_sight_anchor` / `maintenance_muzzle_anchor`; otherwise uses the gun-definition mount anchor (including sight mount offset). Independent anchors can be added without changing attachment mounting. No model edits this round.

`MaintenanceHotspots` transforms that position through bench-facing and maintenance-gun transforms, then projects using the fixed maintenance camera basis, 75° FOV and current GUI aspect ratio. Radius is 20 GUI pixels. No whole-gun return box, persistent labels or outline. Hover text fades over 150 ms. Grey Context HUD remains locked after click and is clamped to the screen. Text uses zh_cn/en_us language keys.

`MaintenanceAttachmentHud` owns context/selection/pending states. Modify/Replace switches the existing bottom nine-cell area to safe candidate copies with a short fade-in. Escape/inventory-key cancels selection (also closes context), otherwise closes context, otherwise exits maintenance. Right-click cancels the open layer. Ordinary origin placeholder/non-owner take are hidden during selection and restored afterward. Whole-gun return remains disabled.

## Inventory and transaction

`AttachmentCandidatePage`: scan main inventory indices 0–35, filter NativeAttachment slot and `NativeAttachments.compatible`, skip empty, group only matching item+NBT, retain first source slot/copy and aggregate count. Nine per page, scroll paging supports more than nine. No inventory relocation, no ghost stacks, no drag/drop. GunMaintenanceMenu retains original nine hotbar slots and gun slot indices, appending hidden main-inventory slots for live synchronization.

`MaintenanceActionRequest` carries containerId, root, revision, expected full gun, target and exact source stack/slot. `AflNetwork` protocol is now **19** (matching client/server required), direction-bound C2S Begin and S2C start/result. Server thread validates live menu, alive/non-spectator, world/root/distance/complete bench, P9, revision, full stack and source compatibility at Begin and before delayed mutation. BE persists `AttachmentRevision`; any synced mutation advances it, rejecting stale and duplicate transactions.

`MaintenanceAttachmentTransaction` consumes one real source item even in creative mode. Replace returns the old full ItemStack, merging matching stacks then using empty 0–35 slots, then player-near drop. A failed drop restores the inventory snapshot and leaves the gun unchanged. Gun commit preserves origin ownership and synchronizes BE plus inventories. Other slot/NBT are preserved. Failed requests show a small localized retry notice, refresh candidates, keep maintenance open and consume nothing.

Client only sends intent; it never writes attachment NBT. Success closes selection/context, and existing BER reads the synchronized real gun. `MaintenanceActionState` tracks pending action; a future animation can be inserted before request dispatch, but must still pass server validation. No animation-completion workflow exists in V1.

## Verification

- Compile passed. Server 24/24 GameTests passed in `build/maintenance-gametest/logs/latest.log`, including 20 alternating A/B first-winner installs, stale remove-vs-replace, exact consumption, same-type replacement conservation, both slots, origin/non-owner metadata, BE save/load and full-inventory single drop. Added cases are in `src/dev/java/com/antaurora/apofirstlight/dev/MaintenanceAttachmentTests.java`.
- Existing suppressor and sight/server weapon regressions run in this suite. No noise/sound/muzzle-exit/ADS code or resources were changed. Graphical and audio behavior are separate from server assertions.
- Initial isolated graphical probe passed C2S installation of both attachments, independent removal, Esc cancellation and origin return; screenshots captured. It directly called Screen.mouseClicked, so it did not verify physical mouse dispatch. The user subsequently reported no response when clicking candidate icons in their client. This remains an open acceptance issue until reproduced and corrected; the initial probe is not evidence that the user's input path works. Final build/acceptance are pending. True two-client synchronization, human hover/fade feel, all-facing/GUI-scale visual acceptance and disk restart after this new transaction are not claimed by GameTests.

Commands: `gradlew -I src/dev/maintenance-gametest.init.gradle runGameTestServer --offline`; isolated client `gradlew -I src/dev/maintenance-attachments-client.init.gradle runClient --offline`. The latter uses only `build/thermal-fluid-client` development world `fluid_probe`.

## Final checkpoint

The user subsequently confirmed “没问题了，可以终止测试”. Testing stopped at their request; no further client launch. The reported no-response issue is no longer an active user blocker, but its cause was not established and no specific input-path fix is claimed. Direct Screen and Forge Pre/Post probe runs passed; the final MouseHandler callback probe exited before a completion marker and is **NOT_TESTED**, not PASS. The outward-opening Context HUD was screenshot-checked after correcting its initial overlap with the gun. Compilation succeeded during client launches; a separate final distributable `build` was not run. Real two-client synchronization and physical-device/scale coverage remain unverified. The user's own client was left untouched.
