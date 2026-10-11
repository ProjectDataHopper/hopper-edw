"""
Unit tests for the Data Hopper MCP server, harvest output, and Cypher guard.
"""

import asyncio
import os
import socket
import subprocess
import sys
import tempfile
import time
import unittest
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVER_DIR = ROOT / "mcp" / "server"
HARVEST = ROOT / "mcp" / "harvester" / "harvest.py"

OUTPUT = Path(tempfile.mkdtemp(prefix="mcp-kb-"))
subprocess.check_call(
    [
        sys.executable,
        str(HARVEST),
        "--repo-root",
        str(ROOT),
        "--output-dir",
        str(OUTPUT),
    ]
)
os.environ["KNOWLEDGE_GRAPH_JSON"] = str(OUTPUT / "knowledge_graph.json")
os.environ.pop("NEO4J_URI", None)
os.environ.pop("NEO4J_PASSWORD", None)

sys.path.insert(0, str(SERVER_DIR))
import db  # noqa: E402
import main  # noqa: E402
from db import kb  # noqa: E402


def _free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class TestDataHopperMCP(unittest.TestCase):

    def test_coding_rules(self):
        rules = main.get_coding_rules()
        self.assertTrue(len(rules) >= 5, "Expected at least 5 core coding rules")
        rule_ids = [rule["id"] for rule in rules]
        self.assertIn("RULE_VFS", rule_ids)
        self.assertIn("RULE_GUI_GROUPS", rule_ids)
        self.assertIn("RULE_LOMBOK", rule_ids)
        self.assertIn("RULE_I18N_QUOTES", rule_ids)
        self.assertIn("RULE_JAVA_PIN", rule_ids)
        self.assertTrue(all(rule.get("repository") == "hopper-edw" for rule in rules))

    def test_coding_rules_filter(self):
        rules = main.get_coding_rules(topic="VFS")
        self.assertTrue(any("VFS" in rule["title"] for rule in rules))

    def test_model_schema_hdv(self):
        schema = main.get_model_schema(".hdv")
        self.assertEqual(schema["extension"], ".hdv")
        self.assertEqual(schema["root_xml"], "data-vault-model")
        self.assertTrue(len(schema["anti_patterns"]) > 0)
        self.assertEqual(schema["repository"], "hopper-edw")

    def test_model_schema_hsl_hem_hlv(self):
        hsl = main.get_model_schema(".hsl")
        self.assertEqual(hsl["root_xml"], "semantic-model")
        hem = main.get_model_schema("hem")
        self.assertEqual(hem["root_xml"], "hop-execution-map")
        hlv = main.get_model_schema(".hlv")
        self.assertEqual(hlv["root_xml"], "hop-lineage-view")

    def test_model_schema_unknown(self):
        schema = main.get_model_schema(".unknown")
        self.assertIn("error", schema)

    def test_search_knowledge_base(self):
        results = main.search_knowledge_base("catalog")
        self.assertTrue(len(results) > 0, "Expected search results for 'catalog'")
        for hit in results:
            self.assertIn("snippet", hit)
            self.assertNotIn("content", hit)
            self.assertEqual(hit["repository"], "hopper-edw")
            self.assertLessEqual(len(hit["snippet"]), 180)

    def test_search_category_and_body(self):
        hits = main.search_knowledge_base("Hop Lineage View", category="Governance")
        paths = [hit.get("path") for hit in hits]
        self.assertTrue(any(path and path.endswith("hop-lineage-view.adoc") for path in paths))
        excluded = main.search_knowledge_base("Hop Lineage View", category="Dimensional")
        excluded_paths = [hit.get("path") for hit in excluded]
        self.assertFalse(any(path and path.endswith("hop-lineage-view.adoc") for path in excluded_paths))

    def test_lookup_plugin(self):
        results = main.lookup_plugin_component("DataVault")
        self.assertTrue(len(results) > 0, "Expected matches for 'DataVault'")

    def test_lookup_plugin_id(self):
        results = main.lookup_plugin_component("DATA_VAULT_UPDATE")
        self.assertTrue(results, "Expected a match on the action plugin id")
        self.assertTrue(any(item.get("plugin_id") == "DATA_VAULT_UPDATE" for item in results))
        self.assertEqual(results[0]["repository"], "hopper-edw")

    def test_cypher_guardrail_rejection(self):
        mutations = [
            "CREATE (n:Person {name: 'Alice'})",
            "MATCH (n) DELETE n",
            "MATCH (n) DETACH DELETE n",
            "MERGE (n:Test)",
            "DROP INDEX idx",
            "CALL db.labels()",
            "CALL dbms.security.listUsers()",
            "MATCH (n) RETURN n; MATCH (m) DELETE m",
            "LOAD CSV FROM 'file:///etc/passwd' AS line RETURN line",
        ]
        for query in mutations:
            with self.subTest(query=query):
                with self.assertRaises(ValueError):
                    kb.query_cypher(query)

    def test_cypher_allowlist_accepts_match_shape(self):
        with self.assertRaises(RuntimeError):
            kb.query_cypher("MATCH (n:CodingRule) RETURN n LIMIT 5")
        with self.assertRaises(RuntimeError):
            kb.query_cypher("/* comment */ MATCH (n) RETURN n LIMIT 1")

    def test_cypher_file_is_one_statement_per_line(self):
        text = (OUTPUT / "init-graph.cypher").read_text(encoding="utf-8")
        self.assertNotIn("ON CREATE", text)
        self.assertIn("rule.repository", text)
        for line in text.splitlines():
            stripped = line.strip()
            if not stripped or stripped.startswith("//"):
                continue
            self.assertEqual(_unquoted_semicolons(stripped), 1, stripped[:160])
            self.assertTrue(stripped.endswith(";"))

    def test_streamable_http_initialize(self):
        port = _free_port()
        env = os.environ.copy()
        env["PORT"] = str(port)
        env["KNOWLEDGE_GRAPH_JSON"] = str(OUTPUT / "knowledge_graph.json")
        env.pop("NEO4J_URI", None)
        env.pop("NEO4J_PASSWORD", None)
        process = subprocess.Popen(
            [sys.executable, str(SERVER_DIR / "main.py"), "--host", "127.0.0.1", "--port", str(port)],
            cwd=str(SERVER_DIR),
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        try:
            deadline = time.time() + 20
            health = None
            while time.time() < deadline:
                if process.poll() is not None:
                    output = process.stdout.read() if process.stdout else ""
                    self.fail(f"server exited early: {output}")
                try:
                    with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=1) as response:
                        health = response.read().decode()
                        break
                except (urllib.error.URLError, ConnectionError, TimeoutError):
                    time.sleep(0.2)
            self.assertEqual(health, "ok")

            names = asyncio.run(_tool_names(port))
            self.assertIn("get_coding_rules", names)
            self.assertIn("get_model_schema", names)
            self.assertIn("search_knowledge_base", names)
            self.assertIn("lookup_plugin_component", names)
            self.assertIn("query_knowledge_graph", names)
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()


def _unquoted_semicolons(line: str) -> int:
    count = 0
    in_string = False
    escaped = False
    for char in line:
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
        elif char == '"':
            in_string = True
        elif char == ";":
            count += 1
    return count


async def _tool_names(port: int):
    from mcp import ClientSession
    from mcp.client.streamable_http import streamable_http_client

    async with streamable_http_client(f"http://127.0.0.1:{port}/mcp") as (read_stream, write_stream, _):
        async with ClientSession(read_stream, write_stream) as session:
            await session.initialize()
            listed = await session.list_tools()
            return {tool.name for tool in listed.tools}


if __name__ == "__main__":
    unittest.main()
