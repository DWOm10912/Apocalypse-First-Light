# 枪械工作台 V1

| 项目 | 当前行为 |
| --- | --- |
| Registry ID | `apocalypse_firstlight:gun_workbench`（一个 Block 和 BlockItem） |
| 名称 | 枪械工作台 / Gun Workbench |
| 实际与视觉占位 | **宽 2 × 深 1 × 高 2 格**，沿用用户最终确认比例 |
| 状态 | `facing=north/east/south/west`；`part=base/side/upper/upper_side` |
| 组成 | 根块 base，side 沿 facing 顺时针方向一格；upper / upper_side 为对应上层格 |
| 放置 | 面向玩家；完整四格可替换、无液体、无实体碰撞、在已加载区块/世界边界/高度内；下方两格均须有支撑；成功只消耗一个物品 |
| 拆除 | 任意格拆除同朝向的三个组成格；正常采集只走最初被破坏格的一次掉落流程 |
| 采集工具 | **钻石级及以上的镐**；`requiresCorrectToolForDrops()`、`minecraft:mineable/pickaxe`、`minecraft:needs_diamond_tool` |
| 掉落 | 合格工具生存采集 1 个工作台；空手、低级镐、非镐工具及创造模式无掉落；爆炸至多一个，受原版爆炸掉落规则影响 |
| 硬度 / 抗爆 / 声音 | 3.0 / 6.0 / METAL |
| 创造标签页 | AFL 方块，铅箱之后；武器与弹药页不重复添加 |
| 碰撞 / 选框 | 同一组分格旋转结构盒：钢腿、桌面、后墙/侧板、下层架、灯罩和主要储物体；保留开口，不用四格实心盒 |
| 渲染 | 四格普通静态 baked model；每片局限单格，源几何与材质不变；无 BER / BlockEntity |
| 工作灯 | 模型保留，光照等级 0，无 LIT / 动态发光 / 电源接口 |
| 交互 | 各 part 解析至 root，`useAtRoot` 消费交互但不执行功能；不输出测试聊天或打开 UI |
| 未实现 | GUI、Menu/Screen、库存、维修、配件、制造、配方、JEI/Jade、动画、电池槽、FE / Power Network |

## 完整性与资源

放置在 BlockItem 的标准 Forge 快照/放置流程中写入四格，失败回退本次写入。联动清理由“世界 + 根位置”短期重入保护隔离，不影响旁边独立工作台。禁止活塞推动。

邻居更新触发延后完整性检查；缺失 part 的残留格不再掉物。区块加载事件仅扫描包含工作台状态的区段并安排一次检查，缺少相邻已加载区块时延后重试，不强制加载。命令移除清理已加载组成格；跨区块残留在相关区块恢复加载后自清理。

- 源与贴图：[模型说明](gun_workbench_model_v1.md)。
- 注册：`registry/AflBlocks.java`、`AflItems.java`、`AflCreativeTabs.java`。
- 逻辑：`block/GunWorkbenchBlock.java`、`GunWorkbenchIntegrity.java`、`item/GunWorkbenchBlockItem.java`（均在 `src/main/java/com/antaurora/apofirstlight/` 下）。
- 资源：`src/main/resources/assets/apocalypse_firstlight/blockstates/gun_workbench.json`、`models/block/gun_workbench/*.json`、`models/item/gun_workbench.json`、`lang/{zh_cn,en_us}.json`。
- 掉落表：`src/main/resources/data/apocalypse_firstlight/loot_tables/blocks/gun_workbench.json`。
- 同步：`node tools/export-gun-workbench-parts.mjs`（含源资产、UV、几何体积完整性检查）。

后续入口仍未实现：GUI 可在 root 扩展；电池槽仅为工作灯供电，无电池时工作台功能仍可用，不接工业电网。本轮无相关状态或假功能。

## 验证

开发测试：`src/dev/java/com/antaurora/apofirstlight/dev/GunWorkbenchGameTests.java`，通过 `src/dev/gun-workbench-gametest.init.gradle` 使用独立测试世界与测试命名空间，不访问玩家存档。

独立 Forge GameTest：**5/5 通过**。覆盖四向放置/组成格/root 解析、碰撞边界与开口、零发光/无 BE/禁止活塞、方块状态 NBT 往返；16 种阻挡位置及缺支撑/水体拒绝；32 种生存工具×部位组合（钻石/下界合金镐各掉一个，其余不掉）；四向任意 part 的创造/命令/正常掉落清理、爆炸不重复掉落、延后孤块清理。

自动化日志：`build/gun-workbench-test-console.log`。`compileJava processResources build --offline --stacktrace -PaflWithoutTacz`、源资产/分片校验和 `git diff --check` 通过；构建日志 `build/gun-workbench-build.log`。

图形客户端通过 `runClient --offline -PaflWithoutTacz` 启动，已进入单人游戏。窗口截图确认工作台完整放置、贴图正常、热栏图标可辨认、中文名称正常；没有新增 Jade 集成，顶部名称来自原有通用显示。真实世界保存重进、四向视觉/远处剔除、手持/地面物品以及玩家碰撞体感仍待用户完整验收，不能以单张截图或 NBT 测试代替。客户端日志 `build/gun-workbench-client.log`。
