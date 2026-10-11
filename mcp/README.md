# Data Hopper MCP Server

Public [Model Context Protocol](https://modelcontextprotocol.io) server for **hopper-edw**. AI coding assistants use it to read coding rules, model file schemas (`.hdv`, `.hbv`, `.hdm`, `.hsm`, `.hsl`, `.hem`, `.hlv`), documentation, and the Hop plugin catalog.

Production URL: `https://mcp.data-hopper.com/mcp`

The sandbox that publishes that name is [sandboxConfig](https://github.com/ProjectDataHopper/sandboxConfig) (`mcp/rebuild-image.sh`, then `./bin/stack up mcp`). This directory is the server and the harvester. Private ProjectDataHopper repositories are not part of this public endpoint.

## Tools

1. `get_coding_rules(topic)` — HopVfs, grouped GuiCompositeWidgets, Lombok, i18n quotes, Java 21 pin.
2. `get_model_schema(file_type)` — specification, schema text, anti-patterns, and sample paths.
3. `search_knowledge_base(query, category)` — short snippets across docs, rules, schemas, and classes.
4. `lookup_plugin_component(keyword, plugin_type)` — transforms, actions, metadata, and GUI classes. Matches plugin id and name.
5. `query_knowledge_graph(cypher)` — one read-only Cypher statement against Neo4j (optional locally).

Lookup tools read `knowledge_graph.json`. Only the Cypher tool needs Neo4j. If Neo4j is down, the other four tools still answer.

## Run locally

Python 3.12.

```bash
pip install -r mcp/requirements.txt
python mcp/harvester/harvest.py
python mcp/tests/test_mcp_server.py
python mcp/server/main.py --port 8000
```

Client URL: `http://127.0.0.1:8000/mcp`. Health check: `http://127.0.0.1:8000/health`.

The tests harvest into a temporary directory. `python mcp/harvester/harvest.py` writes `mcp/harvester/output/`, which the server uses when `KNOWLEDGE_GRAPH_JSON` is unset.

## Image

Build from the repository root so the harvest can see `docs/` and `src/main/java/`:

```bash
docker build -f mcp/Dockerfile -t data-hopper/mcp:local .
```

The container listens on port 8000. Neo4j is not inside this image. To load the graph, run `mcp/harvester/load_graph.py` with `NEO4J_URI`, `NEO4J_PASSWORD` (admin), and `MCP_NEO4J_READER_PASSWORD`. The loader wipes the database and reloads `init-graph.cypher`. Neo4j Community Edition authenticates `neo4j_ro` but does not enforce a read-only role; Bolt stays on a private Docker network.

## Client configuration

```json
{
  "mcpServers": {
    "datahopper": {
      "url": "https://mcp.data-hopper.com/mcp"
    }
  }
}
```

The same block is in [`.mcp.json`](../.mcp.json) and [`.cursor/mcp.json`](../.cursor/mcp.json).
