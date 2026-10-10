"""
Load harvested Cypher into Neo4j and ensure the reader user exists.

The graph is derived. This script drops every node, then applies init-graph.cypher.
Neo4j Community Edition does not enforce GRANT, so neo4j_ro is a separate password,
not a read-only role. Keep Bolt off the public and shared Docker networks.
"""

import os
import sys
import time
from pathlib import Path


def _quote_password(password: str) -> str:
    if not password or any(char in password for char in "'\\\n\r"):
        raise SystemExit("Neo4j password must not contain quotes, backslashes, or newlines.")
    return "'" + password + "'"


def _connect():
    from neo4j import GraphDatabase
    from neo4j.exceptions import ServiceUnavailable, AuthError, SessionExpired

    uri = os.environ["NEO4J_URI"]
    user = os.getenv("NEO4J_USER", "neo4j")
    password = os.environ["NEO4J_PASSWORD"]
    driver = GraphDatabase.driver(uri, auth=(user, password))
    last_error = None
    for attempt in range(1, 31):
        try:
            driver.verify_connectivity()
            return driver
        except (ServiceUnavailable, AuthError, SessionExpired, OSError) as exc:
            last_error = exc
            print(f"[*] Neo4j not ready (attempt {attempt}/30): {exc}")
            time.sleep(2)
    driver.close()
    raise SystemExit(f"Could not connect to Neo4j at {uri}: {last_error}")


def _consume(session, statement: str, **parameters):
    result = session.run(statement, parameters)
    result.consume()


def _ensure_reader(session, reader_user: str, reader_password: str):
    quoted = _quote_password(reader_password)
    _consume(
        session,
        f"CREATE USER {reader_user} IF NOT EXISTS SET PASSWORD {quoted} CHANGE NOT REQUIRED",
    )
    try:
        _consume(session, f"ALTER USER {reader_user} SET PASSWORD {quoted}")
    except Exception as exc:
        if "cannot be the same" not in str(exc):
            raise
        print("[*] Reader password already matches")
    for grant in (
        f"GRANT ACCESS ON DATABASE neo4j TO {reader_user}",
        f"GRANT MATCH {{*}} ON GRAPH neo4j TO {reader_user}",
    ):
        try:
            _consume(session, grant)
        except Exception as exc:
            print(f"[!] Skipping privilege statement ({exc.__class__.__name__}: {exc})")
            print("[!] Community Edition authenticates this user but does not enforce read-only grants.")
            return


def _unquoted_semicolons(line: str) -> int:
    """Count semicolons outside double-quoted Cypher strings."""
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


def _statements(path: Path):
    statements = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("//"):
            continue
        if _unquoted_semicolons(line) != 1 or not line.endswith(";"):
            raise SystemExit(f"Refusing to load a line that is not one statement: {line[:160]}")
        statements.append(line)
    return statements


def main() -> None:
    cypher_path = Path(os.getenv("GRAPH_CYPHER", "/app/output/init-graph.cypher"))
    if not cypher_path.exists():
        raise SystemExit(f"Cypher file not found: {cypher_path}")

    reader_user = os.getenv("MCP_NEO4J_READER_USER", "neo4j_ro")
    reader_password = os.getenv("MCP_NEO4J_READER_PASSWORD") or os.getenv("NEO4J_READER_PASSWORD")
    if not reader_password:
        raise SystemExit("MCP_NEO4J_READER_PASSWORD is required.")
    if not reader_user.isidentifier():
        raise SystemExit(f"Refusing reader user name: {reader_user}")

    statements = _statements(cypher_path)
    constraints = [line for line in statements if line.upper().startswith("CREATE CONSTRAINT")]
    rest = [line for line in statements if line not in constraints]

    driver = _connect()
    try:
        with driver.session() as session:
            print(f"[*] Ensuring reader user {reader_user}")
            _ensure_reader(session, reader_user, reader_password)
            print(f"[*] Applying {len(constraints)} constraints")
            for statement in constraints:
                _consume(session, statement)
            print("[*] Wiping derived graph")
            _consume(session, "MATCH (n) DETACH DELETE n")
            print(f"[*] Loading {len(rest)} statements from {cypher_path}")
            for index, statement in enumerate(rest, start=1):
                try:
                    _consume(session, statement)
                except Exception:
                    print(f"[!] Failed at statement {index}: {statement[:240]}", file=sys.stderr)
                    raise
                if index % 250 == 0:
                    print(f"    {index}/{len(rest)}")
        print("[+] Graph load complete")
    finally:
        driver.close()


if __name__ == "__main__":
    main()
