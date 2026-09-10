import {test} from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {BridgeClient} from '../bridge_client.mjs';
import {tools} from '../models.mjs';
import http from 'node:http';
import {mkdtemp,mkdir,writeFile} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

test('MCP stdio initialize/list/status error are protocol compatible',async()=>{
  const client=new Client({name:'afl-test',version:'1.0.0'});
  const transport=new StdioClientTransport({command:process.execPath,args:['server.mjs','--game-dir','test/nonexistent-game'],stderr:'pipe'});
  await client.connect(transport);
  try {const list=await client.listTools();assert.ok(list.tools.length>=25);assert.ok(!list.tools.some(t=>t.name.includes('execute_command')||t.name==='authoring_export'));const result=await client.callTool({name:'minecraft_status',arguments:{}});assert.equal(result.isError,true);assert.match(result.content[0].text,/MCP_NOT_CONNECTED/);}
  finally {await client.close();}
});
test('write tools tagged and no autonomous reference area setter',()=>{
  for(const t of tools.filter(t=>t.name.startsWith('we_')))assert.equal(t.annotations.readOnlyHint,false);
  assert.ok(!tools.some(t=>t.name==='reference_area_set'));
});
test('restricted camera schema and mutation annotations',()=>{
  const camera=tools.filter(t=>t.name.startsWith('camera_'));
  assert.deepEqual(camera.map(t=>t.name).sort(),['camera_move','camera_restore','camera_status']);
  for(const t of camera)assert.equal(t.inputSchema.additionalProperties,false);
  const move=camera.find(t=>t.name==='camera_move');
  assert.deepEqual(move.inputSchema.required,['position','yaw','pitch']);
  assert.deepEqual(Object.keys(move.inputSchema.properties).sort(),['dry_run','pitch','position','yaw']);
  assert.equal(move.inputSchema.properties.position.items.type,'number');
  assert.equal(move.inputSchema.properties.yaw.minimum,-180);assert.equal(move.inputSchema.properties.yaw.maximum,180);
  assert.equal(move.inputSchema.properties.pitch.minimum,-90);assert.equal(move.inputSchema.properties.pitch.maximum,90);
  for(const t of camera)assert.equal(t.annotations.readOnlyHint,t.name==='camera_status');
  assert.ok(!tools.some(t=>/^(execute_command|teleport|set_gamemode|send_chat)$/.test(t.name)));
});
test('disconnected discovery fails closed',async()=>{await assert.rejects(()=>new BridgeClient('test/not-a-world').call('we_set',{block:'minecraft:air'}),/MCP_NOT_CONNECTED/);});
test('world handshake, token forwarding, and changed discovery rejects write before HTTP',async()=>{
  const dir=await mkdtemp(path.join(os.tmpdir(),'afl-bridge-test-'));await mkdir(path.join(dir,'afl_authoring_bridge'));let calls=0;
  const server=http.createServer(async(req,res)=>{calls++;assert.equal(req.headers.authorization,'Bearer fake-test-token');let body='';for await(const c of req)body+=c;const input=JSON.parse(body);assert.equal(input.tool,'minecraft_status');res.setHeader('Content-Type','application/json');res.end(JSON.stringify({world_session:'first',dimension:'minecraft:overworld'}));});
  server.listen(0,'127.0.0.1');await once(server,'listening');
  const file=path.join(dir,'afl_authoring_bridge/session.json');const discovery={host:'127.0.0.1',port:server.address().port,ready:true,token:'fake-test-token',world_session:'first'};
  try {await writeFile(file,JSON.stringify(discovery));const client=new BridgeClient(dir);await assert.rejects(()=>client.call('we_set',{}),/WORLD_SESSION_CHANGED/);await client.call('minecraft_status');await writeFile(file,JSON.stringify({...discovery,world_session:'second'}));for(const tool of ['we_set','camera_move','camera_restore'])await assert.rejects(()=>client.call(tool,{}),/WORLD_SESSION_CHANGED/);assert.equal(calls,1);}
  finally {server.close();}
});
