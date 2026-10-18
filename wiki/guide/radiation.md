# 辐射

战后的落尘还留在地上。起始区域很干净，越往外走辐射越高

## 辐射区

| 区域 | 剂量率 |
|---|---|
| 安全 | 0 |
| 有辐射 | 1–10 RU/h |
| 重度落尘 | 10–60 RU/h |
| 极端 | 60–240 RU/h |

剂量率是每小时（现实时间）累积多少剂量。离开辐射区后身上还会带一阵残留辐射，回到安全区才会慢慢散掉

## 剂量和辐射病

累积剂量不会自己下降，越高病得越重

| 阶段 | 累积剂量 | 影响 |
|---|---|---|
| 1 | ≥ <V j="RadiationSicknessManager.MILD_DOSE_THRESHOLD" /> | 移动速度 −5% |
| 2 | ≥ <V j="RadiationSicknessManager.MODERATE_DOSE_THRESHOLD" /> | 移动 −10%，回血 ×0.75 |
| 3 | ≥ <V j="RadiationSicknessManager.SEVERE_DOSE_THRESHOLD" /> | 移动 −15%，回血 ×0.5，不时恶心 |
| 4 | ≥ <V j="RadiationSicknessManager.CRITICAL_DOSE_THRESHOLD" /> | 移动 −20%，回血 ×0.25，更频繁地恶心，每 5 秒掉 1 点生命 |

<StatusTag s="planned" /> 治疗辐射病的药物

## 屏蔽

站在墙后能挡掉一部分辐射：

- 钢筋混凝土每层只透过 <V j="RadiationShielding.RC_TRANSMISSION" pct />
- 铅屏蔽砖每层只透过 <V j="RadiationShielding.LEAD_TRANSMISSION" pct />
- 只计算 <V j="RadiationShielding.MAX_DISTANCE" u="格" /> 以内的方块

## 喝水

脏水会带上取水地点的辐射，煮沸不能去掉。见 [生存状态](./survival#口渴)

## 盖革计数器

拿在手里时显示当前剂量率（数字屏），并发出咔哒声。它只测剂量率，不显示身上累积了多少

<StatusTag s="creative" />
