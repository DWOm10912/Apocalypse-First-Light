# 挖土和凿石

- <StatusTag s="test" /> <StatusTag s="creative" /> 本页的工具和石头都只在创造模式物品栏里，还在测试分支上。世界生成里，巨石和散石现在只出现在深世界测试世界的洞口附近，别的地方的散石还只能用开发命令放

地面可以用铲子一铲一铲地挖开、填平。地表散落着半埋在土里的花岗岩巨石，铲子挖不动，要用鹤嘴锄凿开。露出地面的岩石也用鹤嘴锄凿，凿一下地面就低一点。地上还有随手能捡的石子和石块

## 工具

| 工具 | 用途 | 重量 |
|---|---|---|
| <ItemIcon id="round_point_shovel" /> 圆头铲 | 挖土、倒土、刮平 | <V d="item_mass/core_v1#items.apocalypse_firstlight:round_point_shovel.unit_mass_kg" u="kg" /> |
| <ItemIcon id="pick_mattock" /> 鹤嘴锄 | 凿巨石、凿岩石 | <V d="item_mass/core_v1#items.apocalypse_firstlight:pick_mattock.unit_mass_kg" u="kg" /> |

## 铲子

拿着圆头铲对准松软的地面，按一下使用键铲一下，按住不会连续铲

- 铲子是空的：挖起一铲土，扣 <V d="stamina/stamina_v1#costs.shovel_dig" /> 耐力
- 铲子装着土：把土倒在准星处，扣 <V d="stamina/stamina_v1#costs.shovel_pour" /> 耐力
- 潜行时：刮平地面，扣 <V d="stamina/stamina_v1#costs.shovel_scrape" /> 耐力

圆头铲全长 1.6 m，一铲能挖约 300 L 土，在脚前方不远处挖出一个浅坑。挖一铲大约 2.2 秒：铲头插进土里，右脚踩一下把它踩进去，往后撬，再把一铲土端起来。土留在铲子上，找个不碍事的地方，对准那里再按一下使用键倒掉，倒一次大约 1.2 秒。刮平一次大约 1.4 秒

拿着铲子时，两只手横着端在身前，铲头在右前方。装着土的铲子不能再挖，要先倒掉

铲子顶上是 D 形把手。空铲子能挂在木质工具架的木钉上，铲头朝下，会垂到下面一格；装着土的挂不上

挖到石头时提示“石头上挖不动”：从巨石旁边往下挖，就能把它埋在土里的部分挖出来。挖到下面的岩石时也一样，要改用鹤嘴锄凿

挖过、倒过的地方只能用铲子动，空手敲不动，会提示“这块地得用铲子挖”

草皮下面直接是岩石的薄土层也能挖、能倒。这几种地方铲子动不了，倒不下去时提示“这里倒不下”：

- 三格以上高的天然土坎、崖壁，顶上边缘那一格。<StatusTag s="test" /> 两格高的土坎顶上能挖、能倒
- 路、房子、树根旁边
- 头顶两格以内有方块挡着的地方

## 巨石

巨石是半埋在地里的石头，有圆的漂砾、平板石、碎石块、石堆等十几种样子。空手、铲子和别的工具都打不动它，对着它会提示“要用镐”

- <StatusTag s="test" /> 洞口附近常能看到巨石：塌陷坑底是顶板塌下来的平板石和碎石块，覆盖型落水洞底是从土里露出来的圆漂砾，大天坑底散着塌下来的顶板碎块，落石堆脚下是滚下来的石块，越大的滚得越远。山坡洞口前的坡面两边偶尔有一两块。巨石不会挡在下洞的路上
- <StatusTag s="test" /> 洞口的巨石和散石看得出是什么岩石：多数是浅灰的石灰岩，也有黄褐的砂岩、深灰的页岩；覆盖型落水洞里的圆漂砾是冰川带来的花岗岩。凿碎后掉的是那种岩石的石块：石灰岩巨石掉石灰岩石块，砂岩掉砂岩石块，页岩掉页岩碎片，花岗岩掉花岗岩石块

用鹤嘴锄凿：

- 拿着鹤嘴锄对准巨石，按一下使用键凿一下，一下约 <V j="BoulderStrike.DURATION" x="0.05" u="秒" />
- 只能凿离脚不远的地方：从眼睛算起 <V j="BoulderStrike.REACH" u="格" /> 以内
- 每凿中一下扣 <V d="stamina/stamina_v1#costs.pick_strike" /> 耐力。耐力用光时凿不动
- 声音很响，附近的感染体能听到
- 大巨石凿够次数后先**裂开**成几块，每块再凿，凿够次数后**碎掉**，掉下这种岩石的石块（如<ItemIcon id="field_stone" />花岗岩石块），有时还有几颗<ItemIcon id="pebble" />石子
- 一整块巨石从开始到全部凿碎，最多凿 50 下
- 巨石凿掉以后留下一个坑，坑边的土会塌下来

## 凿岩石

- <StatusTag s="test" /> 露在地面上的天然石头不能直接打掉。生存模式里空手和别的工具都打不动，会提示“石头得用镐凿”

岩石凿一下就低一点，不用铲：

- 拿着鹤嘴锄对准露天的岩石，按一下使用键凿一下。动作、耐力和声音和凿巨石一样
- 凿的地方直接凿低，不留碎石。一下最多凿掉约 50 L
- 一下一下往下凿，凿出来的是连续的坑，坑壁可以很陡
- 岩石上还盖着土的地方，先用铲子把土挖掉，露出岩石再凿。凿的地方有碎石或倒下的土时，镐会先把它们凿走
- 最深凿到原来地面往下 <V j="Earthwork.ROCK_DEPTH" u="格" />。下面是洞穴时，会留一层石头不凿穿
- 三格以上的崖壁顶上那一圈、路和房子旁边凿不动，提示“这里凿不动”。两格高的台阶顶上能凿。崖壁脚下的地面能凿，贴着三格以上崖壁的那一圈边留着不动
- 凿下来的石头按体积掉落：平均每凿两下掉一块石块，每凿一下掉一两颗<ItemIcon id="pebble" />石子。石块是凿的那种岩石的（花岗岩凿出<ItemIcon id="field_stone" />花岗岩石块）。软的岩石一下凿掉得多，页岩、泥灰岩比花岗岩快
- <StatusTag s="test" /> 每凿一下都会飞出碎石片，扬起一小股浅色石粉，撒下细砂，颜色跟着岩石走；花岗岩、砂岩这类含石英的硬岩石偶尔会冒火星。硬岩石凿起来声音清脆，石灰岩、页岩发闷；凿穿一格或凿碎巨石时能听到石块掉下来滚落，凿击声多远，感染者就能听到多远（16 格）

旧存档里凿出来的碎石还在：它是一种松土，和沙、砾石一样能装进铲子、倒出来、堆成坡，倒在地上平整以后是砾石

- <StatusTag s="planned" /> 地下的岩石（隧道、竖井）和花岗岩、安山岩、闪长岩团块的开采
- <StatusTag s="test" /> 深世界测试世界的地下换成了真实的岩层：花岗岩、片麻岩、片岩、砂岩、石灰岩、白云岩、页岩、泥灰岩、岩盐，按地质分布，路堑上露出来的也是当地的岩石。和天然石头一样，生存模式里要用镐凿，凿下来是那种岩石的石块。贴图除了花岗岩都还是临时的
- <StatusTag s="test" /> 露天地面以外的天然岩石（洞壁、洞顶、崖壁）也能凿：用鹤嘴锄对着岩面凿一下，落点就掉下一块，缺口马上看得见，每次的形状都不一样，新断面颜色偏浅。凿掉一整格的量，花岗岩大约要 20 下，页岩 13 下，泥灰岩 11 下；一格只剩不到八分之一时整格清空，剩下的照样算进掉落。往下凿坑或竖井时（坑上面两边以上是岩壁），坑底整层往下走，贴着井壁的边角一起带走，井壁是直的，不会越挖越窄；井里清空的格子能透进天光，凿出来的坑和凹处里的掉落物、凿面和你手里的东西都按坑口的光照亮，越往里越暗。缺口按实际形状碰撞，可以站进去。土方挖过的地方和巨石底下不能这样凿，坑底往下也还不行
- <StatusTag s="verified" d="2026-10-24" /> 洞壁、洞顶、崖壁和露天的岩石不再是一格一格的方块面，而是连续、不规则的岩面：花岗岩是节理切出来的斜平面，石灰岩是溶蚀的圆坑和鼓包，页岩、砂岩是一层凸一层凹的横向岩层。地板和岩台顶面也不平：石灰岩是平缓的流石起伏，页岩、砂岩、花岗岩是一级级很浅的石板台阶，走起来没有影响；岩台和露头的边是圆的，但 1 格高的岩石台阶要跳上去。岩面按看到的形状碰撞，凹进去的地方能站进去、能凿；贴着岩面放火把、梯子或方块，那一面就是原来的平面。陡的土坎、洞口的土壁和悬空土块的底面也是连续的面，边缘圆钝，和岩面接在一起；普通的土坡照旧是平滑的斜坡。深世界的洞里不再有泥土地板和泥土墙，土只铺在地表；原版矿也去掉了，以后的矿见下一条
- <StatusTag s="test" /> 矿埋在岩石里，凿含矿岩会掉原矿，见下面的「矿石」

## 矿石

<StatusTag s="test" /> 新建的深世界测试世界里，岩石里有矿了。矿体按地质分布在岩层里。现在有 5 种含矿岩：

| 含矿岩 | 样子 | 凿下来掉 |
|---|---|---|
| 煤层 | 黑色，亮层和暗层相间，夹在页岩、砂岩里一层层延伸 | <ItemIcon id="coal_lump" />煤块 |
| 条带状铁矿 | 栗红色和钢灰色的细条带一层层相间 | <ItemIcon id="raw_hematite" />赤铁矿原矿 |
| 含矿石英脉 | 白色石英的薄脉，夹深色条带和锈斑；一个矿区的脉只出一种金属 | <ItemIcon id="gold_quartz" />含金石英、<ItemIcon id="raw_chalcopyrite" />黄铜矿原矿或<ItemIcon id="raw_galena" />方铅矿原矿 |
| 含铅锌白云岩 | 灰色白云岩里顺着层理的铅灰色、深棕色矿物颗粒 | <ItemIcon id="raw_galena" />方铅矿原矿、<ItemIcon id="raw_sphalerite" />闪锌矿原矿，还有白云岩石块 |
| 含铜角砾岩 | 灰色的岩石碎块，被黄铜色的矿物胶结在一起 | <ItemIcon id="raw_chalcopyrite" />黄铜矿原矿 |

- 同一个矿体中间是富矿，边上是贫矿。富矿上矿物多，凿下来的原矿也多：一整格富矿大约出 4 块原矿，贫矿大约 1 块。煤层出得多（一格十几块煤块），含金石英出得少
- 含矿岩和岩石一样用鹤嘴锄凿，除了原矿还掉石子
- 煤层整层都是煤，大部分是贫的；规划里的煤田范围内是富煤
- 条带状铁矿只在湖区北岸的地盾边缘：越往北，沉积岩越薄，最后花岗岩、片岩直接露出地表，铁矿就埋在这些古老的岩石里

| 原矿 | 重量 | 最多叠 |
|---|---|---|
| <ItemIcon id="coal_lump" /> 煤块 | <V d="item_mass/core_v1#items.apocalypse_firstlight:coal_lump.unit_mass_kg" u="kg" /> | 16 |
| <ItemIcon id="raw_hematite" /> 赤铁矿原矿 | <V d="item_mass/core_v1#items.apocalypse_firstlight:raw_hematite.unit_mass_kg" u="kg" /> | 16 |
| <ItemIcon id="raw_chalcopyrite" /> 黄铜矿原矿 | <V d="item_mass/core_v1#items.apocalypse_firstlight:raw_chalcopyrite.unit_mass_kg" u="kg" /> | 16 |
| <ItemIcon id="gold_quartz" /> 含金石英 | <V d="item_mass/core_v1#items.apocalypse_firstlight:gold_quartz.unit_mass_kg" u="kg" /> | 16 |
| <ItemIcon id="raw_galena" /> 方铅矿原矿 | <V d="item_mass/core_v1#items.apocalypse_firstlight:raw_galena.unit_mass_kg" u="kg" /> | 16 |
| <ItemIcon id="raw_sphalerite" /> 闪锌矿原矿 | <V d="item_mass/core_v1#items.apocalypse_firstlight:raw_sphalerite.unit_mass_kg" u="kg" /> | 16 |

原矿现在还没有用途。以后要手选、破碎、洗选成精矿，再冶炼

- <StatusTag s="planned" /> 第二批：镍铜硫化矿、伟晶岩、云英岩、铝土矿
- <StatusTag s="planned" /> 找矿线索：铁锈色的铁帽、河岸上露出的煤层、河里冲下来的矿石碎块、废弃矿井
- <StatusTag s="test" /> 旧的矿石方块（方铅矿石、闪锌矿石等）拿不到了，存档里放着的还在。打掉方铅矿石、闪锌矿石掉对应的原矿

## 地上的散石

野外的地上零星散着几颗石头：几颗石子，有时有一块石块。河床和河岸上的是河卵石，被水磨得更圆、更扁、更光滑

- <StatusTag s="test" /> 洞口的洼地底和落石堆上散石最多，成片出现，往洼地边上越来越稀。石灰岩、白云岩里的洞口，散石里有时有燧石

- 对着散石按一下使用键捡一个：先捡石块，再捡燧石，最后一颗颗捡石子
- 不需要工具，空手就能捡。捡到的石头算进负重
- 拿着圆头铲时，使用键是挖。挖到散石所在的地方，石头会掉出来

## 石块、石子和燧石

| 物品 | 从哪来 | 重量 | 最多叠 |
|---|---|---|---|
| <ItemIcon id="field_stone" /> 花岗岩石块 | 花岗岩和天然石头，花岗岩的巨石和散石，河卵石 | <V d="item_mass/core_v1#items.apocalypse_firstlight:field_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="gneiss_stone" /> 片麻岩石块 | 片麻岩 | <V d="item_mass/core_v1#items.apocalypse_firstlight:gneiss_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="schist_stone" /> 片岩石块 | 片岩 | <V d="item_mass/core_v1#items.apocalypse_firstlight:schist_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="sandstone_stone" /> 砂岩石块 | 砂岩，砂岩的巨石和散石 | <V d="item_mass/core_v1#items.apocalypse_firstlight:sandstone_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="limestone_stone" /> 石灰岩石块 | 石灰岩，石灰岩的巨石和散石 | <V d="item_mass/core_v1#items.apocalypse_firstlight:limestone_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="dolomite_stone" /> 白云岩石块 | 白云岩 | <V d="item_mass/core_v1#items.apocalypse_firstlight:dolomite_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="shale_shard" /> 页岩碎片 | 页岩，页岩的巨石和散石 | <V d="item_mass/core_v1#items.apocalypse_firstlight:shale_shard.unit_mass_kg" u="kg" /> | 16 |
| <ItemIcon id="marl_lump" /> 泥灰岩块 | 泥灰岩 | <V d="item_mass/core_v1#items.apocalypse_firstlight:marl_lump.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="rock_salt_lump" /> 岩盐块 | 岩盐 | <V d="item_mass/core_v1#items.apocalypse_firstlight:rock_salt_lump.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="pebble" /> 石子 | 散石，凿碎的巨石 | <V d="item_mass/core_v1#items.apocalypse_firstlight:pebble.unit_mass_kg" u="kg" /> | 32 |
| <ItemIcon id="flint" /> 燧石 | 散石里偶尔有 | <V d="item_mass/core_v1#items.apocalypse_firstlight:flint.unit_mass_kg" u="kg" /> | 16 |

燧石很少见，多在石灰岩地区和河里的砾石中。它颜色深、有蜡一样的光泽，和旁边灰白的花岗岩不一样。山里出来的燧石外面裹着一层白色的皮，河里冲下来的皮已经磨掉了

河卵石捡到手里就是普通的石子和花岗岩石块，和田野里捡的叠在一起（河里的石头从上游各处冲下来，不按河床的岩石分）

这些都是材料，现在都还没有用途。以后的建造用材料直接建 Mesh，石块不会再做成方块

- <StatusTag s="planned" /> 世界生成里的散石：森林、田野、河床
- <StatusTag s="test" /> 原版的石头、圆石、石砖、安山岩、花岗岩、闪长岩和它们的楼梯、台阶、墙都拿不到了，原版燧石也是。挖砾石只掉砾石
- <StatusTag s="test" /> 天然石头换成了新的岩石贴图（4 种，随机拼），只改样子
