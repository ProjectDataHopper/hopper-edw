# Data Hopper MCP Server

The **Data Hopper Model Context Protocol (MCP)** server allows external AI coding assistants (Claude Desktop, Cursor, Antigravity, VS Code, Goose) to access domain architecture, coding standards, model specifications (.hdv, .hbv, .hdm, .hsm), Hop plugin catalog, and documentation.

## Architecture

- **Neo4j 5 LTS Community**: Graph database holding entities (`Repository`, `Class`, `CodingRule`, `FileType`, `DocArticle`, `Concept`).
- **Knowledge Harvester (`harvester/`)**: Extracts Java annotations, AsciiDoc documentation, and file schemas into Neo4j Cypher and standalone JSON.
- **FastMCP Server (`server/`)**: Provides MCP tools, resources, and prompts over Streamable HTTP / Server-Sent Events (SSE).

## Tools Provided

1. `get_coding_rules(topic)`: Coding rules for HopVfs, grouped GuiCompositeWidgets, Lombok, i18n quote escaping, Java 21 pin.
2. `get_model_schema(file_type)`: Model specification, XML/JSON schema, anti-patterns, and sample references for `.hdv`, `.hbv`, `.hdm`, `.hsm`.
3. `search_knowledge_base(query, category)`: Fast search across docs, rules, concepts, and codebase classes.
4. `lookup_plugin_component(keyword, plugin_type)`: Locates Hop transforms, actions, metadata objects, and GUI dialogs.
5. `query_knowledge_graph(cypher)`: Safe read-only Cypher queries against Neo4j.

## Resources Provided

- `hopper://rules/all`: Complete catalog of architectural standards.
- `hopper://schemas/overview`: Overview of all EDW model file types.

## Running Locally

```bash
# 1. Harvest knowledge
python3 harvester/harvest.py

# 2. Run unit tests
python3 tests/test_mcp_server.py

# 3. Start server
python3 server/main.py --transport sse --port 8000
```
