# 森林

<Shot k="hero_forest" zh="AFL 的森林" en="An AFL forest" />

AFL 用自己做的树替换了原版的树，树干是圆的，树冠是成片的枝叶

六种树：白橡、红橡、粗皮山核桃、红枫、白松、东部铁杉。不同的生物群系里树种搭配不一样

## 砍树

- 要用斧头，空手砍不动
- 每砍一下 <V j="FellingRules.CHOP_TICKS" x="0.05" u="秒" />，砍 <V j="FellingRules.STAGES" /> 下树就倒
- 树会朝砍口的反方向倒，**被砸到会受伤**
- 每下消耗 <V d="stamina/stamina_v1#costs.chop" /> 点体力，附近的感染体能听见砍树声

<StatusTag s="creative" /> 木材还没有合成配方，见 [材料与加工](../industry/materials#木材)
