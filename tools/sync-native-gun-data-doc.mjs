import fs from 'node:fs';
import path from 'node:path';
const root=process.cwd(), dir=path.join(root,'src/main/resources/data/apocalypse_firstlight/native_guns');
const target=path.join(root,'docs/01 - 系统设计/枪械/枪械.md');
const lang=JSON.parse(fs.readFileSync(path.join(root,'src/main/resources/assets/apocalypse_firstlight/lang/zh_cn.json'),'utf8'));
const registry=fs.readFileSync(path.join(root,'src/main/java/com/antaurora/apofirstlight/registry/AflItems.java'),'utf8');
const accuracy=fs.readFileSync(path.join(root,'src/main/java/com/antaurora/apofirstlight/weapon/NativeAccuracyProfile.java'),'utf8');
const headshots=fs.readFileSync(path.join(root,'src/main/java/com/antaurora/apofirstlight/weapon/NativeHeadshots.java'),'utf8');
const headshotDefault=Number(headshots.match(/defineInRange\("headshotMultiplier",\s*([\d.]+)/)[1]);
const crouch=Object.fromEntries([...accuracy.matchAll(/(DEFAULT|BATTLE_RIFLE) = new NativeAccuracyProfile\(([\d.]+)/g)].map(m=>[m[1].toLowerCase(),Number(m[2])]));
const ids=[...registry.matchAll(/ITEMS.register\("([^"]+)"/g)].map(m=>m[1]).filter(id=>fs.existsSync(path.join(dir,id+'.json')));
const label=id=>lang['item.'+id.replace(':','.')]||id;
const fixed=(x,n=2)=>Number(x).toFixed(n);
let result='# 枪械数据\n\nAFL 使用 Native Gun Framework；本页只记录当前正式枪械的最终数据。\n';
for(const id of ids){
 const g=JSON.parse(fs.readFileSync(path.join(dir,id+'.json'),'utf8')),r=g.recoil,d=g.damage;
 const rows=[
 ['Registry ID','`apocalypse_firstlight:'+id+'`'],['类型',g.accuracy.profile==='battle_rifle'?'战斗步枪':'手枪'],
 ['使用弹药',label(g.ammo)],['弹匣容量',g.magazine_capacity+' 发'],['开火模式',g.fire.mode==='semi'?'半自动':g.fire.mode],
 ['最短开火间隔',fixed(g.fire.interval_ticks/20)+' 秒（'+g.fire.interval_ticks+' tick）'],['理论射速',1200/g.fire.interval_ticks+' RPM'],
 ['基础伤害',d.base],['爆头倍率',headshotDefault+'×（默认；可配置）'],['伤害衰减起点',d.falloff_start+' 格'],
 ['标称有效射程',d.effective_range+' 格'],['最大射程',d.max_range+' 格'],['最大射程最低伤害倍率',fixed(d.min_damage_multiplier)+'×'],
 ['基础散布',fixed(g.accuracy.base_spread_degrees)+'°（锥形半角）'],
 ['蹲姿静止散布',Number((g.accuracy.base_spread_degrees*crouch[g.accuracy.profile]).toFixed(5))+'°（稳定后，锥形半角）'],
 ['噪声半径',g.noise.radius+' 格'],['枪声耳鸣',g.noise.tinnitus?'是':'否'],
 ['普通换弹时间',fixed(Math.ceil(g.reload.tactical_seconds*20)/20)+' 秒'],['空仓换弹时间',fixed(Math.ceil(g.reload.empty_seconds*20)/20)+' 秒'],
 ['ADS','机械瞄准；疾跑时不可使用；无额外精度加成'],['ADS 时间','进入 / 退出均 '+fixed(g.ads.time_seconds)+' 秒'],
 ['ADS FOV 倍率',fixed(g.ads.fov_multiplier)+'×'],
 ['垂直后坐',fixed(r.verticalMin)+'°–'+fixed(r.verticalMax)+'° / 发（累计上限 '+fixed(r.maxVertical,1)+'°）'],
 ['水平偏移','左 '+fixed(r.horizontalLeftMin)+'°–'+fixed(Math.abs(r.horizontalMin))+'° / 右 '+fixed(r.horizontalRightMin)+'°–'+fixed(r.horizontalMax)+'° 每发；累计上限 ±'+fixed(r.maxHorizontal,1)+'°'],
 ['弹壳',label(g.casing)],['备注','创造模式备弹无限、弹匣仍扣弹；默认仅僵尸可爆头']];
 result+='\n## '+label('apocalypse_firstlight:'+id)+'\n\n| 属性 | 当前值 |\n| --- | --- |\n'+rows.map(v=>'| '+v.join(' | ')+' |').join('\n')+'\n';
}
if(process.argv.includes('--check')){
 const normalize=s=>s.replaceAll('\r\n','\n').trim().split('\n').map(line=>
   line.startsWith('|')?line.split('|').map(cell=>/^\s*-+\s*$/.test(cell)?'---':cell.trim()).join('|'):line).join('\n');
 if(normalize(fs.readFileSync(target,'utf8'))!==normalize(result))throw Error('Gun data document is stale; run node tools/sync-native-gun-data-doc.mjs');
 console.log('Native gun data doc PASS: '+ids.length);
}else {fs.writeFileSync(target,result);console.log('Generated '+ids.length+' gun tables');}
