import {execFileSync} from 'node:child_process';

const call = (name, args = {}) => JSON.parse(execFileSync(
  process.execPath,
  ['tools/water-dispenser-mcp.mjs', name, JSON.stringify(args)],
  {encoding: 'utf8', maxBuffer: 4_000_000}
));

const code = `(() => {if(Project.name!=='office_mouse')throw Error('Wrong project');for(const preview of Preview.all){if(!preview.controls||!preview.camera)continue;preview.controls.target.set(8,.45,8);preview.camera.position.set(38,22,-35);preview.camera.zoom=1.1;preview.camera.updateProjectionMatrix();preview.controls.update();}Canvas.updateAll();return 'mouse threequarter';})()`;
console.log(call('risky_eval', {code}));
