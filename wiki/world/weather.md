# 天气

## 雨

<StatusTag s="verified" d="2026-10-17" />

- 不开光影时，雨由 AFL 自己渲染：雨丝倾斜，随风飘动，偶尔有阵风
- 屋顶、树冠和车辆会挡住雨，地面和水面会溅起水花
- 车窗上会积雨水，可以用雨刷刮掉，见 [T15](../vehicles/t15#雨刷)
- 开启光影时，雨交给光影包处理

<Shot k="rain" zh="雨（截图待补）" en="Rain (screenshot coming)" />

## 闪电

<StatusTag s="verified" d="2026-10-18" />

- 雷雨天的闪电由 AFL 自己渲染：一条主通道带分叉，每次闪电连续闪击 2–5 次，后几次只有主通道发亮
- 闪电落地时会照亮周围地面（不开光影时），天空也随之闪亮
- 雷声按距离延迟传来，声速 <V j="AflLightning.SOUND_SPEED" u="m/s" />：看到闪光后数几秒，就能估算闪电离你多远
- 被闪电直接击中必定死亡

<StatusTag s="planned" /> 高处和金属更容易引雷、雷击树木、在车内避雷

### 雷声

- <StatusTag s="test" /> 雷声 V1：只在分支上，用户 2026-10-21 实机试过

- 雷声按离闪电通道最近一点的距离分四档，每档是不同的录音：<V j="Thunder.CRACK" u="m" /> 内头顶炸响，<V j="Thunder.CLOSE" u="m" /> 内撕裂一样的一声再轰隆滚开，<V j="Thunder.MID" u="m" /> 内闷的一声接长长的滚雷，再远只剩低沉的隆隆声。越远越小声，能听出从哪边传来
- <V j="LightningStrikes.FAR_RANGE" u="m" /> 内的落雷都听得到，超过视距的远处闪电也看得到（不开光影时）；<V j="AflLightning.SKY_RANGE" u="m" /> 以外的闪电不会让整片天空一亮
- 在屋里或关着门的 T15 驾驶室里，雷声和雨声变闷，只剩低沉的部分；门开着、离门口越近越清楚
- 落点 <V j="LightningStrikes.NOISE_RADIUS" u="m" /> 内的落雷像爆炸一样会引来感染者，远处的雷声不会

<StatusTag s="planned" /> 分地区的动态天气：雨区从远处移过来、各地天气不同、雷暴前先起风，以后还有大风天

## 雨和体温

- 下雨时气温降低 <V d="temperature/temperature_v1#weather.rain" x="-1" u="°C" />，雷雨时降低 <V d="temperature/temperature_v1#weather.thunder" x="-1" u="°C" />
- 在雨中约一分钟就会湿透，淋湿后更冷，见 [生存状态](../guide/survival#体温)

## 动态光源

<StatusTag s="verified" d="2026-10-17" /> 不开光影时，T15 的车灯会实际照亮周围环境（需要 Embeddium）。手电和火把还没有动态光源
