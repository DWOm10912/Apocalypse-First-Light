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

<StatusTag s="creative" />
