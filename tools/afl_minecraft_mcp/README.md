# AFL Minecraft Authoring MCP

Development-only local STDIO MCP server (Node, pinned official MCP SDK). It talks to the running development client's authenticated loopback bridge. No OpenAI API key, cloud service, save editor, shell tool, arbitrary game-command tool or autonomous export.

Full setup, safety contract and acceptance status: [minecraft_authoring_mcp_v1.md](../../docs/dev/minecraft_authoring_mcp_v1.md).

```powershell
cd 'D:\Minecraft Modding\Apocalypse First Light\tools\afl_minecraft_mcp'
npm ci --ignore-scripts
npm test
```

Configure `codex.mcp.example.toml` paths for this computer and merge only its MCP table into your Codex configuration. STDIO owns stdout; do not launch this interactively expecting a web page. The MCP host launches it. Start Minecraft separately.

`--reference-source <exact ZIP or world-folder>` can be repeated; only these explicit sources become numbered importer inputs. `--python <executable>` selects Python 3.10+ (offline importer is standard-library only). Reference mappings are reviewed file edits, not arbitrary paths supplied by MCP.

Inspectors return bounded JSON; screenshots return absolute PNG paths readable with the agent's local image viewer. Offline scan/extract may take up to 120 seconds; the host tool timeout is 150 seconds. Game-thread calls are separately bounded and are never automatically retried.
