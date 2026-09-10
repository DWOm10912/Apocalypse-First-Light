import {readFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import path from 'node:path';

export class BridgeClient {
  constructor(gameDir) {this.gameDir=path.resolve(gameDir);this.epoch=null;this.dimension=null;}
  async call(tool,args={}) {
    let session;
    try {session=JSON.parse(await readFile(path.join(this.gameDir,'afl_authoring_bridge','session.json'),'utf8'));}
    catch {throw new Error('MCP_NOT_CONNECTED: enable bridge and open the development singleplayer world');}
    if(session.host!=='127.0.0.1'||!Number.isInteger(session.port)||session.port<1||session.port>65535||!session.ready||typeof session.token!=='string')throw new Error('INVALID_BRIDGE_DISCOVERY');
    if(tool!=='minecraft_status'&&(!this.epoch||this.epoch!==session.world_session))throw new Error('WORLD_SESSION_CHANGED: call minecraft_status, review new world before writing');
    // No implicit retries: transport failure after a write means its outcome must be inspected.
    let response;
    try {response=await fetch(`http://127.0.0.1:${session.port}/call`,{method:'POST',redirect:'error',headers:{'Authorization':`Bearer ${session.token}`,'Content-Type':'application/json'},body:JSON.stringify({tool,arguments:args,request_id:randomUUID(),world_session:this.epoch,dimension:this.dimension}),signal:AbortSignal.timeout(25000)});}
    catch {throw new Error('BRIDGE_TRANSPORT_FAILED: do not retry writes blindly; inspect status/world first');}
    const result=await response.json();if(!response.ok||result.error)throw new Error(result.error??`BRIDGE_HTTP_${response.status}`);
    if(tool==='minecraft_status'){this.epoch=result.world_session;this.dimension=result.dimension;}
    return result;
  }
}
