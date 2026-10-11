"""
Database and knowledge-graph access for the Data Hopper MCP server.

The four lookup tools read the harvested knowledge_graph.json. Cypher runs
only against Neo4j, and only after a read-only allowlist check. If Neo4j is
not configured, the lookup tools still answer from the JSON file.
"""

import os
import re
import json
from pathlib import Path
from typing import List, Dict, Any, Optional

NEO4J_URI = os.getenv("NEO4J_URI")
NEO4J_USER = os.getenv("NEO4J_USER", "neo4j_ro")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD")
CYPHER_TIMEOUT_SECONDS = float(os.getenv("MCP_CYPHER_TIMEOUT_SECONDS", "5"))
DEFAULT_REPOSITORY = "hopper-edw"

# Backstop. The allowlist below is the real check; this catches mutations
# hidden in an otherwise well-shaped statement.
FORBIDDEN_CYPHER_RE = re.compile(
    r"\b(CREATE|MERGE|DELETE|DETACH|SET|REMOVE|DROP|ALTER|GRANT|REVOKE|CALL|LOAD|USING|FOREACH)\b",
    re.IGNORECASE,
)
ALLOWED_START_RE = re.compile(
    r"(?is)^(EXPLAIN\s+|PROFILE\s+)?(MATCH|WITH|RETURN|SHOW)\b"
)
COMMENT_BLOCK_RE = re.compile(r"/\*.*?\*/", re.DOTALL)
COMMENT_LINE_RE = re.compile(r"//.*?$", re.MULTILINE)


def validate_read_only_cypher(cypher: str) -> str:
    """Return the normalized statement, or raise ValueError."""
    if cypher is None or not str(cypher).strip():
        raise ValueError("Query rejected: empty Cypher is not allowed.")

    text = COMMENT_BLOCK_RE.sub(" ", cypher)
    text = COMMENT_LINE_RE.sub(" ", text)
    text = text.strip()
    if text.endswith(";"):
        text = text[:-1].rstrip()
    if ";" in text:
        raise ValueError("Query rejected: only a single Cypher statement is allowed.")
    if not ALLOWED_START_RE.match(text):
        raise ValueError(
            "Query rejected: statement must start with MATCH, WITH, RETURN, SHOW, EXPLAIN, or PROFILE."
        )
    if FORBIDDEN_CYPHER_RE.search(text):
        raise ValueError(
            "Query rejected: only read-only Cypher is permitted "
            "(no CREATE, MERGE, DELETE, SET, CALL, LOAD, or USING)."
        )
    return text


def _snippet(text: str, query: str, width: int = 180) -> str:
    cleaned = re.sub(r"\s+", " ", text or "").strip()
    if not cleaned:
        return ""
    idx = cleaned.lower().find((query or "").lower())
    if idx < 0:
        return cleaned[:width]
    start = max(0, idx - 50)
    end = min(len(cleaned), idx + max(len(query), 1) + 120)
    return cleaned[start:end][:width]


class KnowledgeBase:
    def __init__(self):
        self._driver = None
        self._local_data: Optional[Dict[str, Any]] = None
        self._init_local_fallback()
        self._init_neo4j()

    def _repository(self) -> str:
        if self._local_data:
            return self._local_data.get("repository") or DEFAULT_REPOSITORY
        return DEFAULT_REPOSITORY

    def _tag(self, item: Dict[str, Any]) -> Dict[str, Any]:
        tagged = dict(item)
        tagged.setdefault("repository", self._repository())
        return tagged

    def _init_local_fallback(self):
        possible_paths = []
        env_path = os.getenv("KNOWLEDGE_GRAPH_JSON")
        if env_path:
            possible_paths.append(Path(env_path))
        possible_paths.extend(
            [
                Path(__file__).resolve().parents[1] / "harvester" / "output" / "knowledge_graph.json",
                Path("/app/output/knowledge_graph.json"),
                Path("./knowledge_graph.json"),
            ]
        )
        for path in possible_paths:
            if path.exists():
                try:
                    self._local_data = json.loads(path.read_text(encoding="utf-8"))
                    break
                except Exception:
                    pass

    def _init_neo4j(self):
        if NEO4J_URI and NEO4J_PASSWORD:
            try:
                from neo4j import GraphDatabase

                self._driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USER, NEO4J_PASSWORD))
            except Exception as exc:
                print(f"[!] Warning: Could not initialize Neo4j driver: {exc}")

    def query_cypher(self, cypher: str, params: Optional[Dict[str, Any]] = None, max_rows: int = 100) -> List[Dict[str, Any]]:
        """Execute one read-only Cypher query against Neo4j."""
        statement = validate_read_only_cypher(cypher)
        if not self._driver:
            raise RuntimeError("Neo4j live connection is not configured or unavailable.")

        from neo4j import READ_ACCESS

        with self._driver.session(default_access_mode=READ_ACCESS) as session:
            transaction = session.begin_transaction(timeout=CYPHER_TIMEOUT_SECONDS)
            try:
                result = transaction.run(statement, params or {})
                records = []
                for record in result:
                    records.append(record.data())
                    if len(records) >= max_rows:
                        break
                transaction.commit()
                return records
            except Exception:
                transaction.rollback()
                raise

    def get_rules(self, topic: Optional[str] = None) -> List[Dict[str, Any]]:
        if not self._local_data:
            return []
        rules = [self._tag(rule) for rule in self._local_data.get("rules", [])]
        if not topic:
            return rules
        needle = topic.lower()
        return [
            rule
            for rule in rules
            if needle in rule.get("title", "").lower()
            or needle in rule.get("category", "").lower()
            or needle in rule.get("id", "").lower()
        ]

    def get_schemas(self, file_type: Optional[str] = None) -> List[Dict[str, Any]]:
        if not self._local_data:
            return []
        schemas = [self._tag(schema) for schema in self._local_data.get("schemas", [])]
        if not file_type:
            return schemas
        extension = file_type if file_type.startswith(".") else f".{file_type}"
        return [schema for schema in schemas if schema.get("extension", "").lower() == extension.lower()]

    def _category_matches(self, category: Optional[str], value: Optional[str]) -> bool:
        if not category:
            return True
        return category.lower() in (value or "").lower()

    def search(self, query: str, category: Optional[str] = None, limit: int = 10) -> List[Dict[str, Any]]:
        if not self._local_data or not query:
            return []
        needle = query.lower()
        repo = self._repository()
        results = []

        for rule in self._local_data.get("rules", []):
            if not self._category_matches(category, rule.get("category")):
                continue
            blob = " ".join(
                [rule.get("title", ""), rule.get("description", ""), rule.get("id", ""), rule.get("category", "")]
            ).lower()
            if needle in blob:
                results.append(
                    {
                        "type": "rule",
                        "repository": repo,
                        "id": rule.get("id"),
                        "title": rule.get("title"),
                        "category": rule.get("category"),
                        "snippet": _snippet(rule.get("description", ""), query),
                    }
                )

        for schema in self._local_data.get("schemas", []):
            if not self._category_matches(category, "Model"):
                continue
            blob = " ".join(
                [schema.get("name", ""), schema.get("summary", ""), schema.get("extension", "")]
            ).lower()
            if needle in blob:
                results.append(
                    {
                        "type": "schema",
                        "repository": repo,
                        "id": schema.get("extension"),
                        "title": schema.get("name"),
                        "path": schema.get("extension"),
                        "category": "Model",
                        "snippet": _snippet(schema.get("summary", ""), query),
                    }
                )

        for doc in self._local_data.get("docs", []):
            if not self._category_matches(category, doc.get("category")):
                continue
            blob = " ".join(
                [doc.get("title", ""), doc.get("summary", ""), doc.get("content", ""), doc.get("path", "")]
            ).lower()
            if needle in blob:
                source = doc.get("content") or doc.get("summary") or ""
                results.append(
                    {
                        "type": "doc",
                        "repository": repo,
                        "title": doc.get("title"),
                        "path": doc.get("path"),
                        "category": doc.get("category"),
                        "snippet": _snippet(source, query),
                    }
                )

        for cls in self._local_data.get("classes", []):
            kind = _class_category(cls)
            if not self._category_matches(category, kind):
                continue
            blob = " ".join(
                [
                    cls.get("name", ""),
                    cls.get("qualified_name", ""),
                    cls.get("plugin_id") or "",
                    cls.get("plugin_name") or "",
                    cls.get("path", ""),
                ]
            ).lower()
            if needle in blob:
                results.append(
                    {
                        "type": "class",
                        "repository": repo,
                        "id": cls.get("qualified_name"),
                        "title": cls.get("name"),
                        "path": cls.get("path"),
                        "category": kind,
                        "snippet": cls.get("qualified_name") or cls.get("name") or "",
                    }
                )

        return results[:limit]

    def lookup_plugins(self, keyword: str, plugin_type: Optional[str] = None, limit: int = 20) -> List[Dict[str, Any]]:
        if not self._local_data or not keyword:
            return []
        needle = keyword.lower()
        matches = []
        for cls in self._local_data.get("classes", []):
            if not (
                cls.get("is_gui_plugin")
                or cls.get("is_metadata")
                or cls.get("is_transform")
                or cls.get("is_action")
                or cls.get("is_file_type")
            ):
                continue
            if plugin_type:
                kind = plugin_type.lower()
                if kind == "transform" and not cls.get("is_transform"):
                    continue
                if kind == "action" and not cls.get("is_action"):
                    continue
                if kind == "gui" and not cls.get("is_gui_plugin"):
                    continue
                if kind == "metadata" and not cls.get("is_metadata"):
                    continue
            blob = " ".join(
                [
                    cls.get("name", ""),
                    cls.get("qualified_name", ""),
                    cls.get("plugin_id") or "",
                    cls.get("plugin_name") or "",
                ]
            ).lower()
            if needle in blob:
                matches.append(self._tag(cls))
            if len(matches) >= limit:
                break
        return matches


def _class_category(cls: Dict[str, Any]) -> str:
    if cls.get("is_transform"):
        return "transform"
    if cls.get("is_action"):
        return "action"
    if cls.get("is_metadata"):
        return "metadata"
    if cls.get("is_gui_plugin"):
        return "gui"
    if cls.get("is_file_type"):
        return "file-type"
    return cls.get("kind") or "class"


kb = KnowledgeBase()
