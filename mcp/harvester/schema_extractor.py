"""
Schema and Model specification extractor for Data Hopper EDW file formats (.hdv, .hbv, .hdm, .hsm).
"""

from pathlib import Path
from typing import Dict, Any, List

MODEL_SPECS = [
    {
        "extension": ".hdv",
        "name": "Data Vault 2.0 Model",
        "root_xml": "data-vault-model",
        "doc_file": "docs/ai-file-schemas/models/hdv.md",
        "xsd_file": "docs/ai-file-schemas/models/hdv.xsd",
        "summary": "Visual raw Data Vault model containing Hubs, Links, and Satellites.",
        "anti_patterns": [
            "Do not put foreign keys or transaction attributes directly on Hubs.",
            "Do not skip Links between Hubs; relationships must be modeled via Links.",
            "Always include hash keys, load dates, and record sources on Satellites."
        ],
        "sample_files": [
            "retail-example/models/retail-360.hdv",
            "integration-tests/tests/basic/vault1.hdv"
        ]
    },
    {
        "extension": ".hbv",
        "name": "Business Vault Model",
        "root_xml": "business-vault-model",
        "doc_file": "docs/ai-file-schemas/models/hbv.md",
        "xsd_file": "docs/ai-file-schemas/models/hbv.xsd",
        "summary": "Business Vault model containing Point-in-Time (PIT) tables, Bridges, SCD2 views, and calculated SQL views.",
        "anti_patterns": [
            "Do not duplicate raw satellites into Business Vault without transformations.",
            "PIT tables require snapshot date ranges and valid satellite reference links."
        ],
        "sample_files": [
            "retail-example/models/retail-360.hbv"
        ]
    },
    {
        "extension": ".hdm",
        "name": "Dimensional Model",
        "root_xml": "dimensional-model",
        "doc_file": "docs/ai-file-schemas/models/hdm.md",
        "xsd_file": "docs/ai-file-schemas/models/hdm.xsd",
        "summary": "Star and Snowflake dimensional model containing Fact tables, Dimensions, Date dimension lookups, and Dimension Joins.",
        "anti_patterns": [
            "Do not forget copies setting on fact dimension joins when scaling parallel lookups.",
            "Ensure surrogate keys and natural keys are clearly designated."
        ],
        "sample_files": [
            "retail-example/models/retail-f-orders.hdm"
        ]
    },
    {
        "extension": ".hsm",
        "name": "Source Model",
        "root_xml": "source-model",
        "doc_file": "docs/ai-file-schemas/models/hsm.md",
        "xsd_file": "docs/ai-file-schemas/models/hsm.xsd",
        "summary": "Catalog-first source definition capturing physical databases, tables, SQL queries, and composite multi-table feeds.",
        "anti_patterns": [
            "Do not invent source columns; harvest from database or read catalog definition JSON.",
            "Ensure catalog FILE store definitions match dvSource.fields or physicalTable.fields."
        ],
        "sample_files": [
            "retail-example/models/source-tables-crm.hsm"
        ]
    }
]

def extract_schemas(repo_root: Path) -> List[Dict[str, Any]]:
    schemas = []
    for spec in MODEL_SPECS:
        doc_path = repo_root / spec["doc_file"]
        xsd_path = repo_root / spec["xsd_file"]
        doc_content = doc_path.read_text(encoding="utf-8") if doc_path.exists() else ""
        xsd_content = xsd_path.read_text(encoding="utf-8") if xsd_path.exists() else ""

        schemas.append({
            "extension": spec["extension"],
            "name": spec["name"],
            "root_xml": spec["root_xml"],
            "summary": spec["summary"],
            "anti_patterns": spec["anti_patterns"],
            "sample_files": spec["sample_files"],
            "doc_markdown": doc_content,
            "xsd_content": xsd_content
        })
    return schemas
