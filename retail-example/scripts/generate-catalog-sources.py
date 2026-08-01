#!/usr/bin/env python3
"""Create E2E-* catalog source JSON entries for the retail example project."""

#  Licensed to the Apache Software Foundation (ASF) under one or more
#  contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

from __future__ import annotations

import argparse
import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PROJECT_HOME = REPO_ROOT / "retail-example"

HOP_TYPE_NAMES = {
    5: "Integer",
    2: "String",
    1: "Number",
    9: "Timestamp",
}

def primary_key_positions(primary_keys: list[str]) -> dict[str, int]:
    return {name: index + 1 for index, name in enumerate(primary_keys)}


def library_binding(rule_id: str) -> dict:
    """Reference a rule in metadata/data-quality-rule-set/retail-source-quality.json."""
    return {
        "ruleSetName": "retail-source-quality",
        "ruleId": rule_id,
        "inlineRule": None,
        "severityOverride": None,
        "fieldNameOverride": None,
        "enabled": True,
    }


# Per-source quality rule bindings (survive regenerate of catalog source JSON).
QUALITY_BINDINGS: dict[str, list[dict]] = {
    "E2E-customer-hub": [
        library_binding("table-not-empty"),
        library_binding("customer-id-not-null"),
    ],
    "E2E-customer-demo": [
        library_binding("table-not-empty"),
        library_binding("customer-id-not-null"),
        library_binding("segment-allowed"),
        library_binding("loyalty-tier-allowed"),
        library_binding("demo-score-range"),
    ],
    "E2E-customer-contact": [
        library_binding("table-not-empty"),
        library_binding("customer-id-not-null"),
    ],
    "E2E-customer-address": [
        library_binding("table-not-empty"),
        library_binding("customer-id-not-null"),
    ],
    "E2E-customer-prefs": [
        library_binding("table-not-empty"),
        library_binding("customer-id-not-null"),
        library_binding("newsletter-opt-in-allowed"),
        library_binding("preferred-channel-allowed"),
        library_binding("language-code-allowed"),
    ],
    "E2E-product": [
        library_binding("table-not-empty"),
        library_binding("product-name-not-null"),
    ],
    "E2E-order-header": [
        library_binding("table-not-empty"),
        library_binding("customer-id-not-null"),
        library_binding("order-status-allowed"),
    ],
    "E2E-order-line": [
        library_binding("table-not-empty"),
        library_binding("quantity-range"),
    ],
    "E2E-warehouse": [
        library_binding("table-not-empty"),
    ],
    "E2E-warehouse-product": [
        library_binding("table-not-empty"),
    ],
    "E2E-sales-rep": [
        library_binding("table-not-empty"),
    ],
    "E2E-order-rep": [
        library_binding("table-not-empty"),
    ],
}


SOURCE_DEFINITIONS = {
    "E2E-customer-hub": {
        "prefix": "customer_hub",
        "description": "Retail customer hub database source",
        "primary_keys": ["customer_id"],
        "fields": [
            ("customer_id", "Integer", "9", "0", 5),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "30", "", 2),
        ],
    },
    "E2E-customer-demo": {
        "prefix": "customer_demo",
        "description": "Retail customer demographics satellite database source",
        "primary_keys": ["customer_id"],
        "fields": [
            ("customer_id", "Integer", "9", "0", 5),
            ("segment", "String", "20", "", 2),
            ("loyalty_tier", "String", "20", "", 2),
            ("demo_score", "Integer", "4", "0", 5),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "30", "", 2),
        ],
    },
    "E2E-customer-contact": {
        "prefix": "customer_contact",
        "description": "Retail customer contact satellite database source",
        "primary_keys": ["customer_id"],
        "fields": [
            ("customer_id", "Integer", "9", "0", 5),
            ("email", "String", "50", "", 2),
            ("phone", "String", "20", "", 2),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "30", "", 2),
        ],
    },
    "E2E-customer-address": {
        "prefix": "customer_address",
        "description": "Retail customer address satellite database source",
        "primary_keys": ["customer_id"],
        "fields": [
            ("customer_id", "Integer", "9", "0", 5),
            ("address_line1", "String", "50", "", 2),
            ("city", "String", "50", "", 2),
            ("postal_code", "String", "10", "", 2),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "30", "", 2),
        ],
    },
    "E2E-customer-prefs": {
        "prefix": "customer_prefs",
        "description": "Retail customer preference satellite database source",
        "primary_keys": ["customer_id"],
        "fields": [
            ("customer_id", "Integer", "9", "0", 5),
            ("newsletter_opt_in", "String", "1", "", 2),
            ("preferred_channel", "String", "10", "", 2),
            ("language_code", "String", "5", "", 2),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "30", "", 2),
        ],
    },
    "E2E-product": {
        "prefix": "product",
        "description": "Retail product hub/satellite database source",
        "primary_keys": ["product_id"],
        "fields": [
            ("product_id", "String", "7", "", 2),
            ("product_name", "String", "50", "", 2),
            ("category", "String", "50", "", 2),
            ("unit_price", "Number", "9", "2", 1),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "30", "", 2),
        ],
    },
    "E2E-order-header": {
        "prefix": "order_header",
        "description": "Retail order header satellite database source",
        "primary_keys": ["order_id"],
        "fields": [
            ("order_id", "String", "7", "", 2),
            ("customer_id", "Integer", "9", "0", 5),
            ("order_date", "Timestamp", "", "", 9),
            ("shipping_date", "Timestamp", "", "", 9),
            ("delivery_date", "Timestamp", "", "", 9),
            ("order_status", "String", "20", "", 2),
            ("total_amount", "Number", "12", "2", 1),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "30", "", 2),
        ],
    },
    "E2E-order-line": {
        "prefix": "order_line",
        "description": "Retail order line link satellite database source",
        "primary_keys": ["order_id", "product_id", "line_number"],
        "fields": [
            ("order_id", "String", "7", "", 2),
            ("product_id", "String", "7", "", 2),
            ("line_number", "Integer", "9", "0", 5),
            ("quantity", "Integer", "9", "0", 5),
            ("unit_price", "Number", "9", "2", 1),
            ("discount_pct", "Number", "5", "2", 1),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "30", "", 2),
        ],
    },
    "E2E-warehouse": {
        "prefix": "warehouse",
        "description": "Retail warehouse hub/satellite database source",
        "primary_keys": ["warehouse_id"],
        "fields": [
            ("warehouse_id", "Integer", "9", "0", 5),
            ("warehouse_name", "String", "50", "", 2),
            ("city", "String", "50", "", 2),
            ("region", "String", "20", "", 2),
            ("capacity", "Integer", "9", "0", 5),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "30", "", 2),
        ],
    },
    "E2E-warehouse-product": {
        "prefix": "warehouse_product",
        "description": "Retail warehouse-product link satellite database source",
        "primary_keys": ["warehouse_id", "product_id"],
        "fields": [
            ("warehouse_id", "Integer", "9", "0", 5),
            ("product_id", "String", "7", "", 2),
            ("stock_qty", "Integer", "9", "0", 5),
            ("reorder_point", "Integer", "9", "0", 5),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "30", "", 2),
        ],
    },
    "E2E-sales-rep": {
        "prefix": "sales_rep",
        "description": "Sales representative master (role-played on orders)",
        "primary_keys": ["rep_id"],
        "fields": [
            ("rep_id", "Integer", "9", "0", 5),
            ("rep_name", "String", "50", "", 2),
            ("region", "String", "20", "", 2),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "30", "", 2),
        ],
    },
    "E2E-order-rep": {
        "prefix": "order_rep",
        "description": "Order to primary/secondary sales rep assignment (same hub twice on link)",
        "primary_keys": ["order_id"],
        "fields": [
            # order_id length must match hub_order / E2E-order-header (7), not CSV display width
            ("order_id", "String", "7", "", 2),
            ("primary_rep_id", "Integer", "9", "0", 5),
            ("secondary_rep_id", "Integer", "9", "0", 5),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "30", "", 2),
        ],
    },
}


def build_row_meta_xml(fields: list[tuple]) -> str:
    value_metas = []
    for name, _data_type, length, precision, hop_type in fields:
        type_name = HOP_TYPE_NAMES[hop_type]
        length_val = length if length else "-1"
        precision_val = precision if precision not in ("", None) else "-1"
        if hop_type == 5:
            precision_val = precision or "0"
            conversion_mask = "####0;-####0"
        else:
            conversion_mask = ""
        value_metas.append(
            f"<value-meta><type>{type_name}</type>\n"
            f"<storagetype>normal</storagetype>\n"
            f"<name>{name}</name>\n"
            f"<length>{length_val}</length>\n"
            f"<precision>{precision_val}</precision>\n"
            f"<origin/>\n"
            f"<comments/>\n"
            f"<conversion_Mask>{conversion_mask}</conversion_Mask>\n"
            f"<decimal_symbol>.</decimal_symbol>\n"
            f"<grouping_symbol>,</grouping_symbol>\n"
            f"<currency_symbol>$</currency_symbol>\n"
            f"<trim_type>none</trim_type>\n"
            f"<case_insensitive>N</case_insensitive>\n"
            f"<collator_disabled>Y</collator_disabled>\n"
            f"<collator_strength>0</collator_strength>\n"
            f"<sort_descending>N</sort_descending>\n"
            f"<output_padding>N</output_padding>\n"
            f"<date_format_lenient>N</date_format_lenient>\n"
            f"<date_format_locale>en_US</date_format_locale>\n"
            f"<date_format_timezone>GMT</date_format_timezone>\n"
            f"<lenient_string_to_number>N</lenient_string_to_number>\n"
            f"</value-meta>"
        )
    return "<row-meta>" + "".join(value_metas) + "</row-meta>"


def field_entry(
    name: str,
    data_type: str,
    length: str,
    precision: str,
    hop_type: int,
    primary_key_position: int = 0,
) -> dict:
    entry = {
        "name": name,
        "description": None,
        "sourceDataType": data_type,
        "length": length,
        "precision": precision,
        "hopType": hop_type,
        "inputOptions": None,
    }
    if primary_key_position > 0:
        entry["primaryKeyPosition"] = primary_key_position
    if name == "load_date":
        entry["inputOptions"] = {
            "csv": {
                "format": "yyyy-MM-dd",
                "decimalSymbol": "",
                "groupingSymbol": "",
                "currencySymbol": None,
            }
        }
    if data_type == "Timestamp" and name != "load_date":
        entry["inputOptions"] = {
            "csv": {
                "format": "yyyy-MM-dd",
                "decimalSymbol": "",
                "groupingSymbol": "",
                "currencySymbol": None,
            }
        }
    return entry


def project_sources_namespace(project_home: Path) -> str:
    # Resolve so `--project-home .` uses the real directory name, not Path('.').name == ''.
    name = project_home.expanduser().resolve().name
    return f"hop/{name}/sources"


def edw_catalog_root(project_home: Path) -> Path:
    """Runtime FILE catalog root (gitignored under work/)."""
    return project_home.expanduser().resolve() / "work" / "edw-catalog"


def build_source(name: str, definition: dict, namespace: str) -> dict:
    table_name = definition["prefix"]
    pk_positions = primary_key_positions(definition.get("primary_keys", []))
    return {
        "namespace": namespace,
        "name": name,
        "type": "DV_SOURCE",
        "description": definition["description"],
        "rowMetaXml": build_row_meta_xml(definition["fields"]),
        "origin": {
            "modelType": "DATA_VAULT_SOURCE",
            "modelName": "retail-360",
            "modelFilename": "${PROJECT_HOME}/models/retail-360.hdv",
            "modelElementName": name,
            "hopProject": "retail-example",
            "createdAt": 1782400000000,
            "updatedAt": 1782400000000,
            "updatedBy": None,
            "lastWorkflow": None,
            "lastPipeline": None,
        },
        "physicalTable": {
            "databaseMetaName": "CRM",
            "schemaName": "",
            "tableName": table_name,
        },
        "physicalFile": None,
        "tags": ["DV Source", "FULL_SNAPSHOT", "DATABASE", "RETAIL_E2E"],
        "glossaryTerms": [],
        "customProperties": {},
        "validationAcknowledgements": [],
        "qualityRules": QUALITY_BINDINGS.get(name, []),
        "dvSource": {
            "sourceType": "DATABASE",
            "sourceIndicator": "",
            "sourceIndicatorField": "record_source",
            "group": None,
            "deliveryType": "FULL_SNAPSHOT",
            "fields": [
                field_entry(*field, primary_key_position=pk_positions.get(field[0], 0))
                for field in definition["fields"]
            ],
        },
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-home", type=Path, default=DEFAULT_PROJECT_HOME)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    namespace = project_sources_namespace(args.project_home)
    catalog_dir = edw_catalog_root(args.project_home) / Path(*namespace.split("/"))
    catalog_dir.mkdir(parents=True, exist_ok=True)

    for name, definition in SOURCE_DEFINITIONS.items():
        path = catalog_dir / f"{name}.json"
        payload = build_source(name, definition, namespace)
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        print(f"Wrote {path}")


if __name__ == "__main__":
    main()