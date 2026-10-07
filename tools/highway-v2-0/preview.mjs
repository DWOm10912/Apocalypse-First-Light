import {png} from '../cube-slab-mesh-lib.mjs';
export function previewFiles(scene,samples,p) {
  const W=1440,H=700,rgba=Buffer.alloc(W*H*4);
  for(let i=0;i<W*H;i++)rgba.set([22,32,44,255],i*4);
  function polygon(points,color){
    const minY=Math.max(0,Math.floor(Math.min(...points.map(p=>p[1])))),maxY=Math.min(H-1,Math.ceil(Math.max(...points.map(p=>p[1]))));
    for(let y=minY;y<=maxY;y++){const xs=[];for(let i=0;i<points.length;i++){const a=points[i],b=points[(i+1)%points.length];if((a[1]<=y+.5&&b[1]>y+.5)||(b[1]<=y+.5&&a[1]>y+.5))xs.push(a[0]+(y+.5-a[1])/(b[1]-a[1])*(b[0]-a[0]));}xs.sort((a,b)=>a-b);for(let i=0;i+1<xs.length;i+=2)for(let x=Math.max(0,Math.ceil(xs[i]));x<=Math.min(W-1,Math.floor(xs[i+1]));x++)rgba.set([...color,255],(y*W+x)*4);}
  }
  const project=([x,y,z])=>[80+x*4.65,220+z*4.65-y*2.4];
  for(const face of scene)polygon(face.points.map(project),face.color);
  // Actual straight-road chunk plane x=64, highlighted beyond the pavement.
  const sx=80+64*4.65;polygon([[sx-1,100],[sx+1,100],[sx+1,345],[sx-1,345]],[64,209,208]);
  // Profile in lower panel; X=station, vertical exaggeration 18x relative to top plan.
  polygon([[80,485],[1270,485],[1270,486],[80,486]],[92,110,125]);
  for(let i=0;i+1<samples.length;i++){const a=samples[i],b=samples[i+1],x=80+a.s*4.65,xx=80+b.s*4.65,y=625-a.y*28,yy=625-b.y*28;polygon([[x,y-2],[xx,yy-2],[xx,yy+2],[x,y+2]],[64,209,208]);}
  const html=`<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>AFL Highway V2-0 offline geometry</title><style>body{margin:0;background:#16202c;color:#e1e9f1;font:15px system-ui}header,footer{padding:16px 24px}h1{font-size:22px;margin:0 0 10px}button,input{margin-right:12px}canvas{width:100%;height:65vh;display:block;touch-action:none}small{color:#b2c3d0}#readout{font-family:monospace;white-space:pre-wrap}</style><header><h1>Highway V2-0 · 256 m 离线样段</h1><button id="iso">透视示意</button><button id="plan">俯视</button><button id="profile">纵断面</button><label>里程 <input id="station" type="range" min="0" max="256" value="64"></label><div id="readout"></div></header><canvas id="view"></canvas><footer>拖动旋转。灰色：路面／路肩；绿色：14 m 未铺装中隔带；白黄线：开发标线；青线：x=64 模拟 chunk 接缝。<br><small>直接读取导出顶点生成此预览；使用占位颜色，不模拟 Minecraft 纹理、光照、PBR、碰撞。纵断面 Y 放大 12 倍。5% 示意纵坡不代表真实设计速度验收。四个 64 m 里程窗口，仅 x=64 是本示例精确的 chunk 平面。</small></footer><script>
const faces=${JSON.stringify(scene)},samples=${JSON.stringify(samples)};
const c=document.getElementById('view'),ctx=c.getContext('2d'),slider=document.getElementById('station');let mode='iso',angle=-0.12,tilt=.48,down=null;
function draw(){c.width=c.clientWidth*devicePixelRatio;c.height=c.clientHeight*devicePixelRatio;const W=c.width,H=c.height,scale=Math.min(W/310,H/100);ctx.fillStyle='#16202c';ctx.fillRect(0,0,W,H);
function project(v){let x=v[0]-128,y=v[1]-2,z=v[2]-10;if(mode==='profile')return [W/2+x*scale,H/2-y*12*scale,0];if(mode==='plan')return[W/2+x*scale,H/2+z*scale,z];let a=x*Math.cos(angle)-z*Math.sin(angle),b=x*Math.sin(angle)+z*Math.cos(angle);return[W/2+a*scale,H/2+(b*Math.sin(tilt)-y*Math.cos(tilt))*scale,b*Math.cos(tilt)+y*Math.sin(tilt)];}
if(mode==='profile'){ctx.strokeStyle='#40d1d0';ctx.lineWidth=3;ctx.beginPath();samples.forEach((f,i)=>{const a=project([f.s,f.y,0]);i?ctx.lineTo(a[0],a[1]):ctx.moveTo(a[0],a[1]);});ctx.stroke();}else{const sorted=faces.map(f=>({...f,screen:f.points.map(project)})).sort((a,b)=>a.screen.reduce((s,v)=>s+v[2],0)/a.screen.length-b.screen.reduce((s,v)=>s+v[2],0)/b.screen.length);for(const f of sorted){ctx.fillStyle='rgb('+f.color.join(',')+')';ctx.beginPath();f.screen.forEach((a,i)=>i?ctx.lineTo(a[0],a[1]):ctx.moveTo(a[0],a[1]));ctx.closePath();ctx.fill();}const a=project([64,0,-24]),b=project([64,0,24]);ctx.strokeStyle='#40d1d0';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(a[0],a[1]);ctx.lineTo(b[0],b[1]);ctx.stroke();}
const f=samples[+slider.value],a=project(mode==='profile'?[f.s,f.y,0]:[f.x,f.y,f.z]);ctx.fillStyle='#fc827d';ctx.beginPath();ctx.arc(a[0],a[1],5*devicePixelRatio,0,7);ctx.fill();document.getElementById('readout').textContent='s='+f.s+' m  xyz=['+[f.x,f.y,f.z].map(v=>v.toFixed(3)).join(', ')+']  grade='+(100*f.grade).toFixed(2)+'%  curvature='+f.curvature.toFixed(6)+' /m';}
for(const id of ['iso','plan','profile'])document.getElementById(id).onclick=()=>{mode=id;draw();};slider.oninput=draw;window.onresize=draw;c.onpointerdown=e=>{down=[e.clientX,e.clientY];c.setPointerCapture(e.pointerId);};c.onpointermove=e=>{if(down){angle+=(e.clientX-down[0])*.005;tilt=Math.max(.1,Math.min(1.5,tilt+(e.clientY-down[1])*.005));down=[e.clientX,e.clientY];mode='iso';draw();}};c.onpointerup=()=>down=null;draw();</script></html>`;
  return new Map([['preview.png',png(rgba,W,H)],['preview.html',html]]);
}
