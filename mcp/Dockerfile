FROM python:3.12-slim

# Build context is the hopper-edw repository root, not this directory.
# See .dockerignore for what is left out of the context.

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 --shell /usr/sbin/nologin mcp

WORKDIR /app

COPY mcp/requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt

COPY CLAUDE.md README.md CHANGELOG.md /src/
COPY docs /src/docs
COPY src/main/java /src/src/main/java
COPY mcp /app/mcp

RUN python /app/mcp/harvester/harvest.py --repo-root /src --output-dir /app/output \
    && chown -R mcp:mcp /app /src

USER mcp

ENV PORT=8000 \
    PYTHONUNBUFFERED=1 \
    KNOWLEDGE_GRAPH_JSON=/app/output/knowledge_graph.json \
    GRAPH_CYPHER=/app/output/init-graph.cypher

EXPOSE 8000

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 \
    CMD curl -sf http://127.0.0.1:8000/health >/dev/null || exit 1

CMD ["python", "/app/mcp/server/main.py"]
