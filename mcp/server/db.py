"""
Database and Knowledge Graph access layer for DataHopper MCP server.
Supports Neo4j Bolt connection with strict read-only query guardrails,
and transparent fallback to local knowledge_graph.json.
"""

import os
import re
import json
from pathlib import Path
from typing import List, Dict, Any, Optional

NEO4J_URI = os.getenv("NEO4J_URI")
NEO4J_USER = os.getenv("NEO4J_USER", "neo4j_ro")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD")

# Disallowed mutating Cypher keywords for security
FORBIDDEN_CYPHER_RE = re.compile(
    r"\b(CREATE|MERGE|DELETE|DETACH|SET|REMOVE|DROP|ALTER|GRANT|REVOKE|CALL\s+dbms)\b",
    re.IGNORECASE
)

class KnowledgeBase:
    def __init__(self):
        self._driver = None
        self._local_data: Optional[Dict[str, Any]] = None
        self._init_local_fallback()
        self._init_neo4j()

    def _init_local_fallback(self):
        # Look for harvested json
        possible_paths = [
            Path(__file__).resolve().parents[1] / "harvester" / "output" / "knowledge_graph.json",
            Path("/app/output/knowledge_graph.json"),
            Path("./knowledge_graph.json")
        ]
        for p in possible_paths:
            if p.exists():
                try:
                    self._local_data = json.loads(p.read_text(encoding="utf-8"))
                    break
                except Exception:
                    pass

    def _init_neo4j(self):
        if NEO4J_URI and NEO4J_PASSWORD:
            try:
                from neo4j import GraphDatabase
                self._driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USER, NEO4J_PASSWORD))
            except Exception as e:
                print(f"[!] Warning: Could not initialize Neo4j driver: {e}")

    def query_cypher(self, cypher: str, params: Optional[Dict[str, Any]] = None, max_rows: int = 100) -> List[Dict[str, Any]]:
        """Executes a strictly read-only Cypher query against Neo4j."""
        if FORBIDDEN_CYPHER_RE.search(cypher):
            raise ValueError("Query rejected: Only read-only Cypher queries (MATCH / RETURN) are permitted.")

        if not self._driver:
            raise RuntimeError("Neo4j live connection is not configured or unavailable.")

        with self._driver.session() as session:
            result = session.run(cypher, params or {})
            records = []
            for record in result:
                records.append(record.data())
                if len(records) >= max_rows:
                    break
            return records

    def get_rules(self, topic: Optional[str] = None) -> List[Dict[str, Any]]:
        if self._driver:
            try:
                where_clause = "WHERE toLower(r.title) CONTAINS toLower($topic) OR toLower(r.category) CONTAINS toLower($topic)" if topic else ""
                records = self.query_cypher(f"MATCH (r:CodingRule) {where_clause} RETURN r")
                if records:
                    return [rec["r"] for rec in records]
            except Exception:
                pass
        
        # Fallback to local data
        if self._local_data:
            rules = self._local_data.get("rules", [])
            if topic:
                t = topic.lower()
                return [r for r in rules if t in r["title"].lower() or t in r["category"].lower() or t in r["id"].lower()]
            return rules
        return []

    def get_schemas(self, file_type: Optional[str] = None) -> List[Dict[str, Any]]:
        if self._local_data:
            schemas = self._local_data.get("schemas", [])
            if file_type:
                ft = file_type if file_type.startswith(".") else f".{file_type}"
                return [s for s in schemas if s["extension"].lower() == ft.lower()]
            return schemas
        return []

    def search(self, query: str, category: Optional[str] = None, limit: int = 10) -> List[Dict[str, Any]]:
        q = query.lower()
        results = []
        if self._local_data:
            # Search rules
            for rule in self._local_data.get("rules", []):
                if q in rule["title"].lower() or q in rule["description"].lower():
                    results.append({"type": "rule", "item": rule})

            # Search schemas
            for schema in self._local_data.get("schemas", []):
                if q in schema["name"].lower() or q in schema["summary"].lower() or q in schema["extension"].lower():
                    results.append({"type": "schema", "item": schema})

            # Search docs
            for doc in self._local_data.get("docs", []):
                if q in doc["title"].lower() or q in doc["summary"].lower():
                    results.append({"type": "doc", "item": doc})

            # Search classes
            for cls in self._local_data.get("classes", []):
                if q in cls["name"].lower() or q in cls["qualified_name"].lower():
                    results.append({"type": "class", "item": cls})

        return results[:limit]

    def lookup_plugins(self, keyword: str, plugin_type: Optional[str] = None, limit: int = 20) -> List[Dict[str, Any]]:
        q = keyword.lower()
        matches = []
        if self._local_data:
            for cls in self._local_data.get("classes", []):
                if not (cls.get("is_gui_plugin") or cls.get("is_metadata") or cls.get("is_transform") or cls.get("is_action") or cls.get("is_file_type")):
                    continue
                if plugin_type:
                    pt = plugin_type.lower()
                    if pt == "transform" and not cls.get("is_transform"):
                        continue
                    if pt == "action" and not cls.get("is_action"):
                        continue
                    if pt == "gui" and not cls.get("is_gui_plugin"):
                        continue
                    if pt == "metadata" and not cls.get("is_metadata"):
                        continue
                if q in cls["name"].lower() or q in cls["qualified_name"].lower():
                    matches.append(cls)
                if len(matches) >= limit:
                    break
        return matches

kb = KnowledgeBase()
