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
- 被闪电击中不一定会死，见[雷击伤](../guide/survival#雷击伤)

### 落点和避雷针

- <StatusTag s="test" /> 闪电 V2：只在分支上，用户 2026-10-22 实机验证过

- 落点按滚球法：每道雷的击距在 <V d="lightning/lightning_injury_v1#strike.distance.0" />–<V d="lightning/lightning_injury_v1#strike.distance.2" u="m" /> 之间（最常见 <V d="lightning/lightning_injury_v1#strike.distance.1" u="m" />），雷的先导从云里往下走，最先进入击距的东西被劈。越高、越孤立越容易被劈；金属本身不吸雷
- 空地上的一个人，击距 45 m 时大约 12.6 m 内落下的雷会劈到他身上；一棵 20 m 高的孤树大约 37 m。站在孤树底下，雷劈中树以后电流顺着树干下来，树下的人会被侧闪或地电流伤到
- 落雷前没有任何提示
- **避雷针**：两种，铜接闪杆（现代建筑屋顶用，约 1 m 高的实心铜杆，半高处有三脚撑架，引下线贴着屋面走）和谷仓避雷针（老谷仓、农舍屋脊上的扭绞铜杆，铸铁三脚座，带蓝色玻璃球和风向箭头，高 1.8 m，占两格）。针尖按比实际高 <V d="lightning/lightning_injury_v1#strike.rod_reach" u="m" /> 算，会把附近本来落在屋顶、旁边的人身上的雷引过来；劈中避雷针不起火，电流顺着引下线入地，不会沿地面伤人。现在只能在创造模式物品栏里拿到
- **树**：劈中的树每次都会喷出木屑、树干开裂；<V d="lightning/lightning_injury_v1#strike.fell" pct="true" /> 的概率树干炸裂、整棵树倒下；没倒的话树冠可能起火（下雨时 <V d="lightning/lightning_injury_v1#strike.fire_wet" pct="true" />，不下雨的干雷暴 <V d="lightning/lightning_injury_v1#strike.fire_dry" pct="true" />），火最多烧 <V d="lightning/lightning_injury_v1#strike.fire_seconds.0" />–<V d="lightning/lightning_injury_v1#strike.fire_seconds.1" u="秒" ue="s" />，雨会浇灭，不会烧掉树冠，也不会顺着树冠烧到别的树
- **T15**：车也会被劈（落在车顶），车不会坏、不会着火。两扇车门都关着时车里的人不受伤；车外的人照常可能被侧闪、地电流伤到

### 雷声

- <StatusTag s="test" /> 雷声 V1：只在分支上，用户 2026-10-21 实机试过

- 雷声按离闪电通道最近一点的距离分四档，每档是不同的录音：<V j="Thunder.CRACK" u="m" /> 内头顶炸响，<V j="Thunder.CLOSE" u="m" /> 内撕裂一样的一声再轰隆滚开，<V j="Thunder.MID" u="m" /> 内闷的一声接长长的滚雷，再远只剩低沉的隆隆声。越远越小声，能听出从哪边传来
- <V j="LightningStrikes.FAR_RANGE" u="m" /> 内的落雷都听得到，超过视距的远处闪电也看得到（不开光影时）；<V j="AflLightning.SKY_RANGE" u="m" /> 以外的闪电不会让整片天空一亮
- 在屋里或关着门的 T15 驾驶室里，雷声和雨声变闷，只剩低沉的部分；门开着、离门口越近越清楚
- 落点 <V j="LightningStrikes.NOISE_RADIUS" u="m" /> 内的落雷像爆炸一样会引来感染者，远处的雷声不会

## 分地区天气

- <StatusTag s="test" /> 只在分支上，用户 2026-10-22 实机试过

- 主世界不再是全世界同时下雨或放晴。每个地方的天气由世界种子和游戏时间决定，一天和原版一样是 20 分钟，没有季节
- 天气分几种大势，每种持续半天到几天：晴好、不稳定、锋面、阴雨连绵、大风天、寒潮、热浪。大势交替时有约 6 个游戏小时的过渡
- 雨来自一团团移动的雨区：阵雨半径约 1 km，雷暴 1–2.5 km，随风移动。雷暴午后最多，平原和山地多、高原少、海上少；锋面会带着一排雷暴横扫整个联邦
- 地形影响天气：西边的高原最干，南边又热又湿；山地和高原风最大，丘陵风最小
- 雨量达到 <V j="RegionalWeather.WET" u="mm/h" /> 才算下雨：会淋湿、会灭火。站在雨区里看到的雨和天色取决于你所在的位置
- 雷暴会落雷：附近的落雷是真闪电，远处的只能看到闪光、听到雷声
- 雨和雷暴降低气温，寒潮和热浪还会让整片地区变冷或变热
- 不开光影时，雨丝的倾斜方向和风力取决于当地的风
- 已知问题：白天雷暴时无法睡觉；不会积雪、结冰；走到雨区边缘时，雨不会在你面前逐渐变稀
- 开发命令：`/afl weather` 查看当前位置的天气；`regime <大势> [小时]`、`storm`、`shower`、`reset` 用于测试

<StatusTag s="planned" /> 风影响更多东西：风声、粒子、大风天把人吹动、风力发电机

## 雨和体温

- 下雨时气温降低 <V d="temperature/temperature_v1#weather.rain" x="-1" u="°C" />，雷雨时降低 <V d="temperature/temperature_v1#weather.thunder" x="-1" u="°C" />
- 在雨中约一分钟就会湿透，淋湿后更冷，见 [生存状态](../guide/survival#体温)

## 动态光源

<StatusTag s="verified" d="2026-10-17" /> 不开光影时，T15 的车灯会实际照亮周围环境（需要 Embeddium）。手电和火把还没有动态光源
