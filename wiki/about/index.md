# 数值与版本

<VersionCard />

## 数值是怎么来的

这个网站不手抄数字。构建网站时，脚本直接读取模组仓库里的游戏数据：

| 内容 | 来源 |
|---|---|
| 枪械参数 | `data/apocalypse_firstlight/native_guns/*.json` |
| 重量、背包占格 | `item_mass/*.json`、`item_size/*.json` |
| 体力、口渴、体温 | `stamina/`、`thirst/`、`temperature/` 下的 JSON |
| 机器耗电、容量 | `machine_balance/*.json` |
| 写在代码里的数值 | 直接从 Java 源码的常量读取 |
| 名称 | 游戏的 `zh_cn` / `en_us` 语言文件 |

正文里<V d="stamina/stamina_v1#max" />这样的琥珀色等宽数字都是从游戏数据读出来的，鼠标停在上面能看到出处。

## 版本对应

- 页面顶部和底部标出的版本号来自模组的 `gradle.properties`，数据提交号是生成这些数值的那次提交
- 公开网站只在发布新版本时更新，所以网站上的数值和你下载的那个版本一致
- 本地预览如果有没提交的数据改动，会在版本卡片里提示

## 数值历史

每把枪页面底部的“数值历史”和 [数值变更](/changelog/values) 页是从游戏数据文件的 git 历史自动整理的：哪天、哪个版本、哪个数从多少改成多少。弹道和后坐力之类的手感参数只统计次数

## 状态标记

- <StatusTag s="live" /> 正常游戏里就有
- <StatusTag s="new" /> 刚加入，数值可能还会调
- <StatusTag s="creative" /> 东西做好了，但生存模式还拿不到
- <StatusTag s="test" /> 只在测试世界里出现
- <StatusTag s="planned" /> 还没做，只是计划
