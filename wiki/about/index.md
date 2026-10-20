# 数值与版本

<VersionCard />

## 数值是怎么来的

本站不手抄数值。构建时，脚本直接读取模组仓库里的游戏数据：

| 内容 | 来源 |
|---|---|
| 枪械参数 | `data/apocalypse_firstlight/native_guns/*.json` |
| 重量、背包占格 | `item_mass/*.json`、`item_size/*.json` |
| 耐力、口渴、体温 | `stamina/`、`thirst/`、`temperature/` 下的 JSON |
| 背景、身体、特质、成长 | `survivor_backgrounds/`、`survivor_body/`、`survivor_traits/`、`survivor_growth/` 下的 JSON |
| 机器耗电、容量 | `machine_balance/*.json` |
| 写在代码里的数值 | 直接读取 Java 源码中的常量 |
| 名称 | 游戏的 `zh_cn` / `en_us` 语言文件 |

正文里像 <V d="stamina/stamina_v1#max" /> 这样的琥珀色等宽数字都读自游戏数据，鼠标悬停可查看出处

## 版本对应

- 页面顶部和底部的版本号取自模组的 `gradle.properties`；数据提交号是生成这些数值时所用的那次提交
- 本站是内部开发资料库。Main 每次把功能集成进主线后重新构建并部署，页脚的提交号和同步时间对应这次部署时的主线状态
- 各工作区分支上尚未集成的内容不会出现在本站
- 本地预览时如果有未提交的数据改动，版本卡片会给出提示

## 数值历史

每把枪页面底部的“数值历史”和 [数值变更](/changelog/values) 页根据游戏数据文件的 git 历史自动整理，记录每项数值在哪天、哪个版本从多少改为多少。弹道、后坐力等手感参数只统计改动次数

## 状态标记

功能按进度分四档，只会逐档向前，不会跳档：

- <StatusTag s="verified" d="日期" /> 已集成进主线，并已通过实机验收；后面是验收日期
- <StatusTag s="integrated" /> 已集成进主线，但尚未实机验收，可能存在问题
- <StatusTag s="test" /> 只出现在测试世界或开发命令里，或者仍在工作区分支上
- <StatusTag s="planned" /> 尚未制作，只有设计或计划

另有一个标记不表示进度：

- <StatusTag s="creative" /> 内容已经做好，但生存模式中还无法获得
