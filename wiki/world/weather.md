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

## 雨和体温

- 下雨时气温降低 <V d="temperature/temperature_v1#weather.rain" x="-1" u="°C" />，雷雨时降低 <V d="temperature/temperature_v1#weather.thunder" x="-1" u="°C" />
- 在雨中约一分钟就会湿透，淋湿后更冷，见 [生存状态](../guide/survival#体温)

## 动态光源

<StatusTag s="verified" d="2026-10-17" /> 不开光影时，T15 的车灯会实际照亮周围环境（需要 Embeddium）。手电和火把还没有动态光源
