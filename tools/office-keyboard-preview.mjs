import {execFileSync} from 'node:child_process';

const call = (name, args = {}) => JSON.parse(execFileSync(
  process.execPath,
  ['tools/water-dispenser-mcp.mjs', name, JSON.stringify(args)],
  {encoding: 'utf8', maxBuffer: 4_000_000}
));

const views = {
  threequarter: [40, 25, -38, 1.1],
  top: [8, 48, -10, 1.1],
  front: [8, 10, -40, 1.2]
};
const view = process.argv[2] || 'threequarter';
const camera = views[view];
if (!camera) throw new Error(`Unknown view: ${view}`);
const [x, y, z, zoom] = camera;
const code = `(() => {if(Project.name!=='office_keyboard')throw Error('Wrong project');for(const preview of Preview.all){if(!preview.controls||!preview.camera)continue;preview.controls.target.set(8,.45,8);preview.camera.position.set(${x},${y},${z});preview.camera.zoom=${zoom};preview.camera.updateProjectionMatrix();preview.controls.update();}Canvas.updateAll();return '${view}';})()`;
console.log(call('risky_eval', {code}));
