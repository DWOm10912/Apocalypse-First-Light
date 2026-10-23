# 分支清理记录 2026-10-22

## 起因

用户 2026-10-22 发现熟练度分档换弹（`feature/gun-reload-tiers` 9ecc8cfe，用户 2026-10-19 实机 PASS）一直没合进 master：「现在合并9ecc8cfe（推送），然后删除没用的旧分支，以防后面再出这种问题」。当天合进 master（ee6eaa1b，已推送），然后清理下面这些分支。

## 规则（以后照这个做）

- 用户实机 PASS 的分支，由 Main 尽快合进 master；不让 PASS 的提交只留在分支上。
- 合并以后，没有 worktree 在用的分支就删掉（本地和 GitHub 上的都删）；还在用的 worktree 分支（C / D / E / B 正在做的）保留。
- 有没提交改动的 worktree 不删，先问用户。
- 查还没合并的分支：`git fetch --prune`，再 `git branch -a --no-merged master`。

## 删掉的分支

删除前记下了提交号，要找回可以 `git branch <名字> <提交号>`（提交只要还在 reflog 或别的分支里就在）。

| 分支 | 提交 | 状态 |
|---|---|---|
| `checkpoint/inventory-grid-v0-pre-merge-20261015` | 49622f52 | 已合并 |
| `checkpoint/pre-federal-merge-20261014` | 2bae125d | 已合并 |
| `checkpoint/pre-survivor-merge-20261014` | a650e8d6 | 已合并 |
| `checkpoint/ragdoll-p9-pre-main-sync-20261017` | 1cde9436 | 已合并 |
| `checkpoint/survivor-v0-pre-main-sync-20261014` | 0548dc36 | 已合并 |
| `feature/county-road-g3` | c5494bb4 | 已合并 |
| `feature/county-road-g3-pads` | f12c1df2 | 已合并 |
| `feature/external-grid-v0` | cfc6f31f | 已合并 |
| `feature/federal-highways-c3` | 09161ebe | 已合并 |
| `feature/federal-highways-c3.3` | 939deb51 | 已合并 |
| `feature/federal-junctions-c3.3b` | e02826ec | 已合并 |
| `feature/federal-tunnels` | 850205b7 | 已合并 |
| `feature/federal-worldgen-v1` | 94a494f7 | 已合并 |
| `feature/first-person-body` | 1eed4ac7 | 已合并 |
| `feature/forest-home-m4` | 1f9ec5fa | 已合并 |
| `feature/gun-proficiency` | 4e1f0323 | 已合并 |
| `feature/inventory-grid-v0` | b740953e | 已合并 |
| `feature/linden-markings` | 10394e8c | 已合并 |
| `feature/player-skeleton-p1` | 75e839ee | 已合并 |
| `feature/player-skeleton-p2` | 63b85a1b | 已合并 |
| `feature/ragdoll-v1` | 018a00f8 | 已合并 |
| `feature/survivor-v0` | 9391e6f3 | 已合并 |
| `feature/t15-smooth-normals` | d34f07c0 | 已合并 |
| `feature/tinnitus-v2` | ce8ecee1 | 已合并 |
| `feature/vextris-0.1.1` | fd620b8e | 已合并 |
| `vextris-migration` | 860f6fbb | 已合并 |
| `codex/highway-m1a-test` | 0dcd6025 | 已合并 |
| `codex/highway-v2-m1b` | 2ab587a5 | 已合并 |
| `fix/c3.3a-turf-seams` | 6fe1b250 | 已合并 |
| `backup-before-history-fix-20260818` | 5d9bf4ab | 没合并，已被取代 |
| `codex/highway-v2-0` | 65d40b0f | 没合并，已被取代 |
| `codex/highway-v2-m1a` | f5c86b84 | 没合并，已被取代 |
| `codex/highway-v2-m2a` | 9f057668 | 没合并，已被取代 |
| `codex/highway-v2-m2b` | fc53835d | 没合并，已被取代 |
| `codex/terrain-v2-phase1` | 5dc68bbc | 没合并，已被取代 |
| `feature/c3-towns-wiki` | 12052746 | 没合并，已被取代 |
| `origin/feature/gun-reload-tiers`（GitHub） | 9ecc8cfe | 已合并（ee6eaa1b） |
| `origin/feature/t15-smooth-normals`（GitHub） | 同本地 | 已合并 |
| `origin/vextris-migration`（GitHub） | 同本地 | 已合并 |

没合并的几个都已被取代：高速 V2 的 codex 沙盒原型（M1-A、M2-A 土方、M2-B 路基）后来由 C3 整个重做；地形 V2 第一阶段规划被地形 V2 r1 取代；`feature/c3-towns-wiki` 是 Linden 的旧 Wiki 草稿，master 的 Wiki 更新；`backup-before-history-fix-20260818` 是 8 月改写历史前的备份，货架功能现在是 `RetailShelfSingle`。

codex 的 5 个 worktree（`C:\Users\willi\.codex\worktrees\` 下的 38ed、highway-m1a-test、highway-m1b、highway-m2a、highway-m2b）和 `D:\Minecraft Modding\AFL-c3-seams` 都没有未提交改动，跟着分支一起删了。

## 保留的

| 分支 / worktree | 原因 |
|---|---|
| `master` | 主线 |
| `AFL-Workbench`（`AFL-vanilla-rework`）、`feature/earthwork-a`（`AFL-earthwork`）、`feature/lightning-injury`（`AFL-survivor-v0`）、`feature/underground-u2`（`AFL-federal-worldgen-v1`） | B / E / D / C 正在用的 worktree |
| `fix/c3.2-structure-crash`（`AFL-c3-hotfix`） | 分支已合并，但 worktree 里有 3 个没提交的改动（`federal_highways_c3.md`、`RoadNetworkRuntime.java`、`RoadNetworkJson.java`），等用户决定 |
| `AFL-vextris-baseline`（没有分支，停在 678d70fa） | 有 2 个没提交的新文件（`MeshMigrationCapture.java`、`mesh-migration-capture.init.gradle`），等用户决定 |
