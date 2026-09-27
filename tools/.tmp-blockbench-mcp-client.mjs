import readline from 'node:readline';

const url = 'http://127.0.0.1:3000/bb-mcp';
const session = '86c59e6b-3cd2-4e29-8596-79fbba673a82';
const headers = {
  'content-type': 'application/json',
  accept: 'application/json, text/event-stream',
  'mcp-session-id': session,
};

const input = readline.createInterface({input: process.stdin, terminal: false});
for await (const line of input) {
  if (!line.trim()) continue;
  try {
    const request = JSON.parse(line);
    if (request.quit) break;
    const response = await fetch(url, {
      method: 'POST', headers,
      body: JSON.stringify({jsonrpc: '2.0', id: request.id,
        method: 'tools/call', params: {name: request.tool, arguments: request.args ?? {}}}),
    });
    const body = await response.text();
    process.stdout.write(JSON.stringify({id: request.id, status: response.status,
      result: JSON.parse(body)}) + '\n');
  } catch (error) {
    process.stdout.write(JSON.stringify({error: String(error)}) + '\n');
  }
}
