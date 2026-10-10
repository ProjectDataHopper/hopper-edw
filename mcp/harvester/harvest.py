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

def run_harvest(repo_root: Path, output_dir: Path, neo4j_uri: str = None, neo4j_auth: tuple = None):
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"[*] Harvesting knowledge from {repo_root}...")
    rules = get_rules()
    print(f"    - Extracted {len(rules)} architectural and coding rules.")
    
    schemas = extract_schemas(repo_root)
    print(f"    - Extracted {len(schemas)} EDW model specifications (.hdv, .hbv, .hdm, .hsm).")
    
    docs = parse_docs(repo_root)
    print(f"    - Extracted {len(docs)} documentation articles and guides.")
    
    classes = parse_java_files(repo_root)
    print(f"    - Extracted {len(classes)} Java classes and Hop plugins.")

    payload = {
        "repository": "hopper-edw",
        "organization": "ProjectDataHopper",
        "rules": rules,
        "schemas": schemas,
        "docs": docs,
        "classes": classes
    }

    # Save knowledge_graph.json
    json_path = output_dir / "knowledge_graph.json"
    json_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(f"[+] Wrote standalone knowledge graph to {json_path}")

    # Generate Cypher script
    cypher_lines = [
        "// ProjectDataHopper Neo4j Knowledge Graph Initialization",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (r:Repository) REQUIRE r.name IS UNIQUE;",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (c:Class) REQUIRE c.qualifiedName IS UNIQUE;",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (rule:CodingRule) REQUIRE rule.ruleId IS UNIQUE;",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (f:FileType) REQUIRE f.extension IS UNIQUE;",
        "CREATE CONSTRAINT IF NOT EXISTS FOR (d:DocArticle) REQUIRE d.path IS UNIQUE;",
        "",
        "MERGE (repo:Repository {name: 'hopper-edw'})",
        "ON CREATE SET repo.org = 'ProjectDataHopper', repo.url = 'https://github.com/ProjectDataHopper/hopper-edw';",
        ""
    ]

    for rule in rules:
        cypher_lines.append(
            f"MERGE (rule:CodingRule {{ruleId: '{rule['id']}'}}) "
            f"ON CREATE SET rule.title = {json.dumps(rule['title'])}, "
            f"rule.category = {json.dumps(rule['category'])}, "
            f"rule.severity = {json.dumps(rule['severity'])}, "
            f"rule.description = {json.dumps(rule['description'])}, "
            f"rule.goodExample = {json.dumps(rule['good_example'])}, "
            f"rule.badExample = {json.dumps(rule['bad_example'])};"
        )
        cypher_lines.append(
            f"MATCH (repo:Repository {{name: 'hopper-edw'}}), (rule:CodingRule {{ruleId: '{rule['id']}'}}) "
            f"MERGE (repo)-[:ENFORCES_RULE]->(rule);"
        )

    for spec in schemas:
        cypher_lines.append(
            f"MERGE (f:FileType {{extension: '{spec['extension']}'}}) "
            f"ON CREATE SET f.name = {json.dumps(spec['name'])}, "
            f"f.rootXml = {json.dumps(spec['root_xml'])}, "
            f"f.summary = {json.dumps(spec['summary'])}, "
            f"f.antiPatterns = {json.dumps(spec['anti_patterns'])};"
        )
        cypher_lines.append(
            f"MATCH (repo:Repository {{name: 'hopper-edw'}}), (f:FileType {{extension: '{spec['extension']}'}}) "
            f"MERGE (repo)-[:SUPPORTS_FILE_TYPE]->(f);"
        )

    for doc in docs:
        cypher_lines.append(
            f"MERGE (d:DocArticle {{path: {json.dumps(doc['path'])}}}) "
            f"ON CREATE SET d.title = {json.dumps(doc['title'])}, "
            f"d.category = {json.dumps(doc['category'])}, "
            f"d.summary = {json.dumps(doc['summary'])};"
        )
        cypher_lines.append(
            f"MATCH (repo:Repository {{name: 'hopper-edw'}}), (d:DocArticle {{path: {json.dumps(doc['path'])}}}) "
            f"MERGE (repo)-[:HAS_DOCUMENTATION]->(d);"
        )

    for cls in classes:
        cypher_lines.append(
            f"MERGE (c:Class {{qualifiedName: {json.dumps(cls['qualified_name'])}}}) "
            f"ON CREATE SET c.name = {json.dumps(cls['name'])}, "
            f"c.package = {json.dumps(cls['package'])}, "
            f"c.kind = {json.dumps(cls['kind'])}, "
            f"c.path = {json.dumps(cls['path'])}, "
            f"c.isGuiPlugin = {str(cls['is_gui_plugin']).lower()}, "
            f"c.isMetadata = {str(cls['is_metadata']).lower()}, "
            f"c.isTransform = {str(cls['is_transform']).lower()}, "
            f"c.isAction = {str(cls['is_action']).lower()};"
        )
        cypher_lines.append(
            f"MATCH (repo:Repository {{name: 'hopper-edw'}}), (c:Class {{qualifiedName: {json.dumps(cls['qualified_name'])}}}) "
            f"MERGE (repo)-[:CONTAINS_CLASS]->(c);"
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
