---
outline: [2, 2]
---

<GunHead :id="$params.gun" />

<!-- @content -->

## 与其他枪械对比

<GunStats :id="$params.gun" part="compare" />

## 完整参数

<GunStats :id="$params.gun" part="table" />

## 伤害衰减

<GunStats :id="$params.gun" part="falloff" />

## 配件

<GunStats :id="$params.gun" part="attachments" />

## 获取方式

<StatusTag s="creative" /> 现在生存模式里还没有能找到枪的地方：没有配方，也没有放枪的战利品表。新拿到的枪弹匣是满的。

## 数值历史

<RecentChanges :gun="$params.gun" />
