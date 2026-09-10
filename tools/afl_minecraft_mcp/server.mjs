import {Server} from '@modelcontextprotocol/sdk/server/index.js';
import {StdioServerTransport} from '@modelcontextprotocol/sdk/server/stdio.js';
import {CallToolRequestSchema,ListToolsRequestSchema} from '@modelcontextprotocol/sdk/types.js';
import {BridgeClient} from './bridge_client.mjs';
import {tools} from './models.mjs';
import {ReferenceClient} from './reference_client.mjs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const index=process.argv.indexOf('--game-dir');
const bridge=new BridgeClient(index>=0?process.argv[index+1]:path.join(root,'run'));
const pythonIndex=process.argv.indexOf('--python');
const sources=process.argv.flatMap((v,i)=>v==='--reference-source'?[process.argv[i+1]]:[]);
const reference=new ReferenceClient(root,bridge.gameDir,pythonIndex>=0?process.argv[pythonIndex+1]:'python',sources);
const server=new Server({name:'afl-minecraft-authoring',version:'1.0.0'},{capabilities:{tools:{}}});
server.setRequestHandler(ListToolsRequestSchema,async()=>({tools}));
server.setRequestHandler(CallToolRequestSchema,async req=>{
  try {
    if(!tools.some(t=>t.name===req.params.name))throw new Error('UNKNOWN_TOOL');
    const offline=['reference_import_status','reference_map_scan','reference_candidates','reference_extract','reference_compatibility_report','reference_prepare_paste'].includes(req.params.name);
    const result=offline?await reference.call(req.params.name,req.params.arguments??{},bridge):await bridge.call(req.params.name,req.params.arguments??{});
    return {content:[{type:'text',text:JSON.stringify(result)}],isError:false};
  }catch(e){return {content:[{type:'text',text:e.message}],isError:true};}
});
await server.connect(new StdioServerTransport());
