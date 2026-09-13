/* Run in Blockbench with both vending-machine projects open.
 * Only changes the preview camera and exports real viewport renders.
 */
(() => {
  const fs=require('fs');
  const base='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/previews/';
  const views=[{name:'front',pos:[0,16,-70]},{name:'angle',pos:[37,29,-65]}];
  const sheet=document.createElement('canvas');sheet.width=1600;sheet.height=660;
  const s=sheet.getContext('2d');s.fillStyle='#c4c8cb';s.fillRect(0,0,1600,660);
  s.fillStyle='#283139';s.font='600 28px sans-serif';s.fillText('AFL  /  VENDING MACHINE',44,52);
  s.font='16px sans-serif';s.fillText('Blockbench viewport renders  |  shared body, scale and material atlas',44,83);
  const files=[];
  for(const [vi,variant] of ['intact','broken'].entries()) {
    const project=ModelProject.all.find(p=>p.name==='afl_vending_machine_'+variant);
    if(!project)throw Error('Missing project '+variant);project.select();
    for(const [ai,view] of views.entries()) {
      const p=Preview.selected;p.setProjectionMode(true);
      p.camera.position.set(...view.pos);p.controls.target.set(0,16,0);
      p.camera.zoom=.7;p.camera.updateProjectionMatrix();p.controls.update();Canvas.updateAll();
      Canvas.withoutGizmos(()=>{
        p.render();const frame=new CanvasFrame(p.canvas);frame.autoCrop();
        const out=document.createElement('canvas');out.width=800;out.height=1000;
        const g=out.getContext('2d');g.fillStyle='#c4c8cb';g.fillRect(0,0,800,1000);
        const scale=Math.min(690/frame.width,850/frame.height);
        const w=frame.width*scale,h=frame.height*scale;
        g.drawImage(frame.canvas,(800-w)/2,72+(850-h)/2,w,h);
        g.fillStyle='#283139';g.font='600 21px sans-serif';g.textAlign='center';
        g.fillText(variant.toUpperCase()+'  /  '+(view.name==='front'?'FRONT':'3/4 VIEW'),400,966);
        const path=base+'afl_vending_machine_'+variant+'_'+view.name+'.png';
        fs.writeFileSync(path,Buffer.from(out.toDataURL('image/png').split(',')[1],'base64'));files.push(path);
        s.drawImage(out,(vi*2+ai)*400,125,400,500);
      });
    }
  }
  const path=base+'afl_vending_machine_comparison.png';
  fs.writeFileSync(path,Buffer.from(sheet.toDataURL('image/png').split(',')[1],'base64'));files.push(path);
  ModelProject.all.find(p=>p.name==='afl_vending_machine_intact').select();
  const p=Preview.selected;p.setProjectionMode(true);p.camera.position.set(0,16,-70);
  p.controls.target.set(0,16,0);p.camera.zoom=.7;p.camera.updateProjectionMatrix();p.controls.update();p.render();
  return JSON.stringify(files);
})();
