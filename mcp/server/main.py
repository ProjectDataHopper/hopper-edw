"""
Data Hopper Model Context Protocol (MCP) server.

Streamable HTTP at /mcp, plus GET /health. Lookup tools read the harvested
JSON file. query_knowledge_graph is the only tool that talks to Neo4j.
"""

import os
import json
from typing import Optional, List, Dict, Any

from mcp.server.fastmcp import FastMCP
from starlette.requests import Request
from starlette.responses import PlainTextResponse, Response

from db import kb

SUPPORTED_MODELS = ".hdv, .hbv, .hdm, .hsm, .hsl, .hem, .hlv"

mcp = FastMCP(
    "DataHopper Knowledge MCP",
    instructions=(
        "Public knowledge for the hopper-edw repository: coding rules, model file "
        "schemas, documentation search, and Hop plugin lookup. Cypher is read-only."
    ),
    host="0.0.0.0",
    port=int(os.getenv("PORT", "8000")),
    stateless_http=True,
    json_response=True,
    streamable_http_path="/mcp",
)


@mcp.custom_route("/health", methods=["GET"])
async def health(_: Request) -> Response:
    return PlainTextResponse("ok")


@mcp.tool()
def get_coding_rules(topic: Optional[str] = None) -> List[Dict[str, Any]]:
    """
    Retrieve architectural and coding rules for hopper-edw.
    Topics include: VFS (HopVfs vs java.io.File), GUI (GuiCompositeWidgets grouped layouts),
    Lombok, i18n single-quote escaping, and Java 21 hard pins.
    """
    return kb.get_rules(topic)


@mcp.tool()
def get_model_schema(file_type: str) -> Dict[str, Any]:
    """
    Retrieve the model specification, schema text, anti-patterns, and sample paths
    for an EDW file (.hdv, .hbv, .hdm, .hsm, .hsl, .hem, .hlv).
    """
    schemas = kb.get_schemas(file_type)
    if not schemas:
        return {"error": f"Schema not found for file type '{file_type}'. Supported: {SUPPORTED_MODELS}"}
    return schemas[0]


@mcp.tool()
def search_knowledge_base(query: str, category: Optional[str] = None) -> List[Dict[str, Any]]:
    """
    Search documentation, rules, model schemas, and codebase classes.
    Each hit is a short snippet with a path or id. category is an optional substring
    filter (for example "Data Vault", "action", "Model").
    """
    return kb.search(query, category)


@mcp.tool()
def lookup_plugin_component(keyword: str, plugin_type: Optional[str] = None) -> List[Dict[str, Any]]:
    """
    Find Hop plugins, transforms, actions, metadata objects, and GUI dialogs by keyword.
    Matches class name, plugin id, and plugin name.
    Optional plugin_type filter: 'transform', 'action', 'metadata', 'gui'.
    """
    return kb.lookup_plugins(keyword, plugin_type)


@mcp.tool()
def query_knowledge_graph(cypher: str) -> List[Dict[str, Any]]:
    """
    Execute one read-only Cypher query against the Neo4j knowledge graph.
    The statement must start with MATCH, WITH, RETURN, SHOW, EXPLAIN, or PROFILE.
    Mutating statements and CALL are rejected. At most 100 rows are returned.
    """
    return kb.query_cypher(cypher)


@mcp.resource("hopper://rules/all")
def resource_rules() -> str:
    """Returns the full catalog of architectural and coding standards."""
    return json.dumps(kb.get_rules(), indent=2)


@mcp.resource("hopper://schemas/overview")
def resource_schemas() -> str:
    """Returns an overview of the EDW model file types."""
    overview = []
    for schema in kb.get_schemas():
        overview.append(
            {
                "repository": schema.get("repository"),
                "extension": schema.get("extension"),
                "name": schema.get("name"),
                "root_xml": schema.get("root_xml"),
                "summary": schema.get("summary"),
                "anti_patterns": schema.get("anti_patterns"),
                "sample_files": schema.get("sample_files"),
            }
        )
    return json.dumps(overview, indent=2)


@mcp.prompt()
def new_hop_transform(transform_name: str, purpose: str) -> str:
    """Prompt template and checklist for authoring a new Apache Hop transform."""
    return f"""You are developing a new Apache Hop transform: '{transform_name}'
Purpose: {purpose}

Follow these strict rules:
1. Meta class must be annotated with @GuiPlugin and Lombok @Getter / @Setter.
2. Define a public static final String GUI_PLUGIN_ELEMENT_PARENT_ID constant.
3. Every user-visible field must have @HopMetadataProperty and @GuiWidgetElement.
4. Put every field in a group using GuiWidgetGroupType.BOXES or TABS. Never create a flat layout.
5. In the Dialog class open():
   - createShell(...)
   - buildButtonBar().ok(...).cancel(...).build()
   - GuiCompositeWidgets.addScrolledComposite(shell, variables, wTransformName, wOk, PARENT_ID, input)
6. All labels/tooltips must be defined in messages/messages_en_US.properties with variable quotes like '${{VAR}}'.
7. Use HopVfs and FileObject for all file access, never java.io.File.
"""


@mcp.prompt()
def review_pr_conventions(diff_summary: str) -> str:
    """Prompt template for reviewing pull requests against Data Hopper conventions."""
    return f"""Please review the following code changes against Data Hopper standards:
{diff_summary}

Verify:
- Are there any raw java.io.File usages that should be HopVfs?
- Are all classes using Lombok to avoid boilerplate getters/setters?
- Are GUI dialogs using GuiCompositeWidgets grouped layouts instead of hand-crafted FormAttachment rows?
- Are resource bundle messages properly escaping variable expressions as '${{VARIABLE}}'?
"""


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(description="Data Hopper MCP server (Streamable HTTP)")
    parser.add_argument("--host", default=os.getenv("HOST", "0.0.0.0"), help="Bind address")
    parser.add_argument("--port", type=int, default=int(os.getenv("PORT", "8000")), help="Port number")
    args = parser.parse_args()
    mcp.settings.host = args.host
    mcp.settings.port = args.port
    print(f"[*] Starting Data Hopper MCP server on {args.host}:{args.port} at /mcp")
    mcp.run(transport="streamable-http")


if __name__ == "__main__":
    main()
