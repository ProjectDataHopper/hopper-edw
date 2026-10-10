"""
Documentation parser for AsciiDoc (.adoc) and Markdown (.md) in hopper-edw.
Extracts articles, sections, and concept summaries for the Neo4j knowledge graph.
"""

from pathlib import Path
from typing import List, Dict, Any
import re

def parse_docs(repo_root: Path) -> List[Dict[str, Any]]:
    articles = []
    
    # 1. Parse top-level CLAUDE.md and README.md
    for filename in ["CLAUDE.md", "README.md", "CHANGELOG.md"]:
        path = repo_root / filename
        if path.exists():
            text = path.read_text(encoding="utf-8", errors="replace")
            articles.append({
                "path": str(path.relative_to(repo_root)),
                "title": filename.replace(".md", "").replace("_", " "),
                "category": "Guide",
                "summary": text[:300].strip(),
                "content": text
            })

    # 2. Parse docs/ directory
    docs_dir = repo_root / "docs"
    if docs_dir.exists():
        for doc_file in docs_dir.glob("**/*"):
            if doc_file.suffix in [".adoc", ".md"] and doc_file.is_file():
                rel_path = str(doc_file.relative_to(repo_root))
                text = doc_file.read_text(encoding="utf-8", errors="replace")
                
                # Extract title from first header
                title_match = re.search(r"^#+\s+(.+)$", text, re.MULTILINE)
                if not title_match:
                    title_match = re.search(r"^=\s+(.+)$", text, re.MULTILINE)
                title = title_match.group(1).strip() if title_match else doc_file.stem
                
                # Categorize based on path or name
                category = "General"
                if "datavault" in rel_path or "dv-" in rel_path:
                    category = "Data Vault"
                elif "business-vault" in rel_path:
                    category = "Business Vault"
                elif "dimensional" in rel_path:
                    category = "Dimensional"
                elif "catalog" in rel_path:
                    category = "Data Catalog"
                elif "lineage" in rel_path or "quality" in rel_path:
                    category = "Governance & Quality"

                articles.append({
                    "path": rel_path,
                    "title": title,
                    "category": category,
                    "summary": text[:300].strip(),
                    "content": text
                })

    return articles
