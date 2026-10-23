# 辐射

战后的落尘仍留在地面上。起始区域很干净，离得越远，辐射越强

## 辐射区

| 区域 | 剂量率 |
|---|---|
| 安全 | 0 |
| 有辐射 | 1–10 RU/h |
| 重度落尘 | 10–60 RU/h |
| 极端 | 60–240 RU/h |

剂量率表示每小时（现实时间）累积的剂量。离开辐射区后，身上还会残留一段时间的辐射，回到安全区才会慢慢消散

## 剂量和辐射病

累积剂量不会自行下降，剂量越高，辐射病越重

| 阶段 | 累积剂量 | 影响 |
|---|---|---|
| 1 | ≥ <V j="RadiationSicknessManager.MILD_DOSE_THRESHOLD" /> | 移动速度 −5% |
| 2 | ≥ <V j="RadiationSicknessManager.MODERATE_DOSE_THRESHOLD" /> | 移动速度 −10%，生命恢复 ×0.75 |
| 3 | ≥ <V j="RadiationSicknessManager.SEVERE_DOSE_THRESHOLD" /> | 移动速度 −15%，生命恢复 ×0.5，间歇性恶心 |
| 4 | ≥ <V j="RadiationSicknessManager.CRITICAL_DOSE_THRESHOLD" /> | 移动速度 −20%，生命恢复 ×0.25，恶心更频繁，每 5 秒失去 1 点生命值 |

<StatusTag s="planned" /> 治疗辐射病的药物

## 屏蔽

站在墙后可以挡住一部分辐射：

- 钢筋混凝土每层只透过 <V j="RadiationShielding.RC_TRANSMISSION" pct />
- 铅屏蔽砖每层只透过 <V j="RadiationShielding.LEAD_TRANSMISSION" pct />
- 只计算 <V j="RadiationShielding.MAX_DISTANCE" u="格" /> 以内的方块

## 喝水

脏水带有取水地点的辐射，煮沸无法去除。见 [生存状态](./survival#口渴)

## 盖革计数器

<ItemIcon id="geiger_counter" />拿在手上时，数字屏显示当前剂量率，并发出咔哒声。它只测量剂量率，不显示身上的累积剂量

<StatusTag s="integrated" /> 电池和开关：

- 装一节 9 V 电池。拿在手上按右键开机 / 关机；开机时整块屏幕亮 <V j="GeigerCounterItem.SELF_TEST_TICKS" x="0.05" u="秒" /> 自检，然后出读数
- 开着就耗电，放在背包、挂点、箱子里或地上都一样：每 <V j="GeigerCounterItem.TICKS_PER_FE" x="0.05" u="秒" /> 用 1 FE，一节新碱性电池能开约 3 个游戏日，镍氢充电电池约 2 个游戏日。不用时记得关
- 屏幕右边的电池图标：电量多于 66% 是 3 格，多于 33% 是 2 格，多于 15% 是 1 格；再低只剩外框闪烁，没电时整块屏幕熄灭
- 关机或没电时不响，HUD 上的计数器窗口也不显示
- 换电池在 [检视界面](./inventory#检视物品) 里做：翻到背面，打开电池盖，取出旧电池，装上新的
- 装着镍氢电池时，整台计数器可以直接放到充电站托盘上充；碱性电池不能充，只能换。对着托盘时准星提示会说明原因

### 9 V 电池

| | <ItemIcon id="battery_9v_alkaline" />碱性电池 | <ItemIcon id="battery_9v_nimh" />镍氢充电电池 |
|---|---|---|
| 容量 | <V j="Battery9vItem.ALKALINE_CAPACITY" u="FE" /> | <V j="Battery9vItem.NIMH_CAPACITY" u="FE" /> |
| 计数器能开多久 | 约 3 个游戏日 | 约 2 个游戏日 |
| 充电 | 不能 | 放在充电站托盘上，每秒最多 <V j="Battery9vItem.NIMH_MAX_RECEIVE" x="20" u="FE" />，空到满约 2.5 分钟 |
| 堆叠 | 满电的新电池最多 4 节一叠 | 不能叠 |

鼠标提示显示剩余电量的百分比

<StatusTag s="creative" />
