"""
Main harvester CLI for ProjectDataHopper / hopper-edw.
Extracts code, rules, documentation, and model schemas, and generates:
1. Direct Cypher execution against Neo4j (if --neo4j-uri provided)
2. Standalone knowledge_graph.json (for offline/standalone FastMCP operation)
3. init-graph.cypher (for Neo4j initialization scripts)
"""

import os
import sys
import json
import argparse
from pathlib import Path

# Local extractors
from rules_extractor import get_rules
from schema_extractor import extract_schemas
from docs_parser import parse_docs
from java_parser import parse_java_files

REPOSITORY = "hopper-edw"
ORGANIZATION = "ProjectDataHopper"


def _tag(items):
    for item in items:
        item["repository"] = REPOSITORY
    return items


def _literal(value):
    """One-line Cypher literal. Strings use JSON quoting, which Cypher accepts."""
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return str(value)
    if isinstance(value, list):
        return "[" + ", ".join(_literal(item) for item in value) + "]"
    return json.dumps(value, ensure_ascii=False)


def _set_clause(alias: str, props: dict) -> str:
    return ", ".join(f"{alias}.{key} = {_literal(value)}" for key, value in props.items())


def run_harvest(repo_root: Path, output_dir: Path, neo4j_uri: str = None, neo4j_auth: tuple = None):
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"[*] Harvesting knowledge from {repo_root}...")
    rules = _tag(get_rules())
    print(f"    - Extracted {len(rules)} architectural and coding rules.")

    schemas = _tag(extract_schemas(repo_root))
    print(f"    - Extracted {len(schemas)} EDW model specifications.")

    docs = _tag(parse_docs(repo_root))
    print(f"    - Extracted {len(docs)} documentation articles and guides.")

    classes = _tag(parse_java_files(repo_root))
    print(f"    - Extracted {len(classes)} Java classes and Hop plugins.")

    payload = {
        "repository": REPOSITORY,
        "organization": ORGANIZATION,
        "rules": rules,
        "schemas": schemas,
        "docs": docs,
        "classes": classes,
    }

    # Save knowledge_graph.json
    json_path = output_dir / "knowledge_graph.json"
    json_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(f"[+] Wrote standalone knowledge graph to {json_path}")

    # One Cypher statement per line. SET (not ON CREATE) so a reload refreshes properties.
    # The production loader wipes the derived graph before applying this file.
    cypher_lines = [
        "// ProjectDataHopper Neo4j knowledge graph. Derived from hopper-edw; safe to rebuild.",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (r:Repository) REQUIRE r.name IS UNIQUE;",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (c:Class) REQUIRE c.qualifiedName IS UNIQUE;",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (rule:CodingRule) REQUIRE rule.ruleId IS UNIQUE;",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (f:FileType) REQUIRE f.extension IS UNIQUE;",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (d:DocArticle) REQUIRE d.path IS UNIQUE;",
        (
            "MERGE (repo:Repository {name: 'hopper-edw'}) SET "
            "repo.org = 'ProjectDataHopper', "
            "repo.url = 'https://github.com/ProjectDataHopper/hopper-edw';"
        ),
    ]

    for rule in rules:
        cypher_lines.append(
            "MERGE (rule:CodingRule {ruleId: "
            + _literal(rule["id"])
            + "}) SET "
            + _set_clause(
                "rule",
                {
                    "repository": REPOSITORY,
                    "title": rule["title"],
                    "category": rule["category"],
                    "severity": rule["severity"],
                    "description": rule["description"],
                    "goodExample": rule["good_example"],
                    "badExample": rule["bad_example"],
                },
            )
            + ";"
        )
        cypher_lines.append(
            "MATCH (repo:Repository {name: 'hopper-edw'}), (rule:CodingRule {ruleId: "
            + _literal(rule["id"])
            + "}) MERGE (repo)-[:ENFORCES_RULE]->(rule);"
        )

    for spec in schemas:
        cypher_lines.append(
            "MERGE (f:FileType {extension: "
            + _literal(spec["extension"])
            + "}) SET "
            + _set_clause(
                "f",
                {
                    "repository": REPOSITORY,
                    "name": spec["name"],
                    "rootXml": spec["root_xml"],
                    "summary": spec["summary"],
                    "antiPatterns": spec["anti_patterns"],
                },
            )
            + ";"
        )
        cypher_lines.append(
            "MATCH (repo:Repository {name: 'hopper-edw'}), (f:FileType {extension: "
            + _literal(spec["extension"])
            + "}) MERGE (repo)-[:SUPPORTS_FILE_TYPE]->(f);"
        )

    for doc in docs:
        cypher_lines.append(
            "MERGE (d:DocArticle {path: "
            + _literal(doc["path"])
            + "}) SET "
            + _set_clause(
                "d",
                {
                    "repository": REPOSITORY,
                    "title": doc["title"],
                    "category": doc["category"],
                    "summary": doc["summary"],
                },
            )
            + ";"
        )
        cypher_lines.append(
            "MATCH (repo:Repository {name: 'hopper-edw'}), (d:DocArticle {path: "
            + _literal(doc["path"])
            + "}) MERGE (repo)-[:HAS_DOCUMENTATION]->(d);"
        )

    for cls in classes:
        cypher_lines.append(
            "MERGE (c:Class {qualifiedName: "
            + _literal(cls["qualified_name"])
            + "}) SET "
            + _set_clause(
                "c",
                {
                    "repository": REPOSITORY,
                    "name": cls["name"],
                    "package": cls["package"],
                    "kind": cls["kind"],
                    "path": cls["path"],
                    "isGuiPlugin": cls["is_gui_plugin"],
                    "isMetadata": cls["is_metadata"],
                    "isTransform": cls["is_transform"],
                    "isAction": cls["is_action"],
                    "isFileType": cls["is_file_type"],
                    "pluginId": cls.get("plugin_id"),
                    "pluginName": cls.get("plugin_name"),
                },
            )
            + ";"
        )
        cypher_lines.append(
            "MATCH (repo:Repository {name: 'hopper-edw'}), (c:Class {qualifiedName: "
            + _literal(cls["qualified_name"])
            + "}) MERGE (repo)-[:CONTAINS_CLASS]->(c);"
        )

    cypher_path = output_dir / "init-graph.cypher"
    cypher_path.write_text("\n".join(cypher_lines), encoding="utf-8")
    print(f"[+] Wrote Neo4j Cypher initialization script to {cypher_path}")

    # Optional: direct ingestion into Neo4j
    if neo4j_uri and neo4j_auth:
        try:
            from neo4j import GraphDatabase
            print(f"[*] Connecting to Neo4j at {neo4j_uri}...")
            driver = GraphDatabase.driver(neo4j_uri, auth=neo4j_auth)
            with driver.session() as session:
                for line in cypher_lines:
                    line = line.strip()
                    if line and not line.startswith("//"):
                        session.run(line)
            driver.close()
            print("[+] Successfully ingested graph into live Neo4j database!")
        except Exception as e:
            print(f"[!] Warning: Could not connect to live Neo4j: {e}", file=sys.stderr)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Data Hopper Knowledge Harvester")
    parser.add_argument("--repo-root", default=str(Path(__file__).resolve().parents[2]), help="Path to repository root")
    parser.add_argument("--output-dir", default=str(Path(__file__).resolve().parent / "output"), help="Output directory")
    parser.add_argument("--neo4j-uri", default=os.getenv("NEO4J_URI"), help="Neo4j Bolt URI")
    parser.add_argument("--neo4j-user", default=os.getenv("NEO4J_USER", "neo4j"), help="Neo4j Username")
    parser.add_argument("--neo4j-password", default=os.getenv("NEO4J_PASSWORD"), help="Neo4j Password")

    args = parser.parse_args()
    auth = (args.neo4j_user, args.neo4j_password) if args.neo4j_password else None
    run_harvest(Path(args.repo_root), Path(args.output_dir), args.neo4j_uri, auth)
