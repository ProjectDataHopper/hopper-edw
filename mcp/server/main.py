"""
DataHopper Model Context Protocol (MCP) Server.
Provides tools, resources, and prompts for AI coding assistants and contributors.
Backed by Neo4j and the harvested knowledge base.
"""

import sys
import os
import json
from typing import Optional, List, Dict, Any

# Ensure server package path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from db import kb

try:
    from mcp.server.fastmcp import FastMCP
    mcp = FastMCP("DataHopper Knowledge MCP", dependencies=["neo4j"])
except ImportError:
    # Minimal stub if running outside MCP container environment for testing
    class FastMCPStub:
        def __init__(self, name, **kwargs):
            self.name = name
        def tool(self):
            def decorator(f):
                return f
            return decorator
        def resource(self, uri):
            def decorator(f):
                return f
            return decorator
        def prompt(self):
            def decorator(f):
                return f
            return decorator
        def run(self, **kwargs):
            print(f"[+] DataHopper MCP server stub running with kwargs: {kwargs}")
    mcp = FastMCPStub("DataHopper Knowledge MCP")

@mcp.tool()
def get_coding_rules(topic: Optional[str] = None) -> List[Dict[str, Any]]:
    """
    Retrieve architectural and coding rules for hopper-edw and ProjectDataHopper.
    Topics include: VFS (HopVfs vs java.io.File), GUI (GuiCompositeWidgets grouped layouts),
    Lombok, i18n single-quote escaping, and Java 21 hard pins.
    """
    return kb.get_rules(topic)

@mcp.tool()
def get_model_schema(file_type: str) -> Dict[str, Any]:
    """
    Retrieve model specification, XML/JSON schema, anti-patterns, and sample references
    for an EDW model format (.hdv, .hbv, .hdm, .hsm).
    """
    schemas = kb.get_schemas(file_type)
    if not schemas:
        return {"error": f"Schema not found for file type '{file_type}'. Supported: .hdv, .hbv, .hdm, .hsm"}
    return schemas[0]

@mcp.tool()
def search_knowledge_base(query: str, category: Optional[str] = None) -> List[Dict[str, Any]]:
    """
    Search across documentation, rules, concepts, and codebase classes in ProjectDataHopper.
    """
    return kb.search(query, category)

@mcp.tool()
def lookup_plugin_component(keyword: str, plugin_type: Optional[str] = None) -> List[Dict[str, Any]]:
    """
    Find Hop plugins, transforms, actions, metadata objects, and GUI dialogs by keyword.
    Optional plugin_type filter: 'transform', 'action', 'metadata', 'gui'.
    """
    return kb.lookup_plugins(keyword, plugin_type)

@mcp.tool()
def query_knowledge_graph(cypher: str) -> List[Dict[str, Any]]:
    """
    Execute a read-only Cypher query against the live Neo4j knowledge graph.
    Mutating statements (CREATE, DELETE, SET, etc.) are strictly forbidden.
    """
    return kb.query_cypher(cypher)

@mcp.resource("hopper://rules/all")
def resource_rules() -> str:
    """Returns the full catalog of architectural and coding standards."""
    return json.dumps(kb.get_rules(), indent=2)

@mcp.resource("hopper://schemas/overview")
def resource_schemas() -> str:
    """Returns the overview of all EDW model formats (.hdv, .hbv, .hdm, .hsm)."""
    return json.dumps(kb.get_schemas(), indent=2)

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
    """Prompt template for reviewing pull requests against DataHopper conventions."""
    return f"""Please review the following code changes against DataHopper standards:
{diff_summary}

Verify:
- Are there any raw java.io.File usages that should be HopVfs?
- Are all classes using Lombok to avoid boilerplate getters/setters?
- Are GUI dialogs using GuiCompositeWidgets grouped layouts instead of hand-crafted FormAttachment rows?
- Are resource bundle messages properly escaping variable expressions as '${{VARIABLE}}'?
"""

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="DataHopper MCP Server")
    parser.add_argument("--transport", default="sse", choices=["sse", "stdio"], help="Transport mode")
    parser.add_argument("--host", default="0.0.0.0", help="Host address")
    parser.add_argument("--port", type=int, default=int(os.getenv("PORT", "8000")), help="Port number")
    args = parser.parse_args()

    print(f"[*] Starting DataHopper MCP server on {args.host}:{args.port} via {args.transport}...")
    if args.transport == "sse":
        # FastMCP sse transport
        try:
            mcp.run(transport="sse")
        except TypeError:
            # Depending on FastMCP version
            mcp.run()
    else:
        mcp.run(transport="stdio")
