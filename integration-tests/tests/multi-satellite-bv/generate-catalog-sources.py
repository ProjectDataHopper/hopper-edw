#!/usr/bin/env python3
"""Generate catalog source JSON files for customer-360 CSV integration tests."""
#
# Copyright 2026 i-Bridge bv
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

from __future__ import annotations

import json
from pathlib import Path

CATALOG_DIR = (
    Path(__file__).resolve().parents[2] / "catalog-data" / "hop" / "integration-tests" / "sources"
)

def primary_key_positions(primary_keys: list[str]) -> dict[str, int]:
    return {name: index + 1 for index, name in enumerate(primary_keys)}


FIELD_SPECS = {
    "CRM-c360-hub": {
        "prefix": "hub",
        "primary_keys": ["customer_id"],
        "fields": [
            ("customer_id", "Integer", "9", "0", 5),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "20", "", 2),
        ],
    },
    "CRM-c360-demo": {
        "prefix": "demo",
        "primary_keys": ["customer_id"],
        "fields": [
            ("customer_id", "Integer", "9", "0", 5),
            ("segment", "String", "20", "", 2),
            ("loyalty_tier", "String", "20", "", 2),
            ("demo_score", "Integer", "4", "0", 5),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "20", "", 2),
        ],
    },
    "CRM-c360-contact": {
        "prefix": "contact",
        "primary_keys": ["customer_id"],
        "fields": [
            ("customer_id", "Integer", "9", "0", 5),
            ("email", "String", "50", "", 2),
            ("phone", "String", "20", "", 2),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "20", "", 2),
        ],
    },
    "CRM-c360-address": {
        "prefix": "address",
        "primary_keys": ["customer_id"],
        "fields": [
            ("customer_id", "Integer", "9", "0", 5),
            ("address_line1", "String", "50", "", 2),
            ("city", "String", "50", "", 2),
            ("postal_code", "String", "10", "", 2),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "20", "", 2),
        ],
    },
    "CRM-c360-prefs": {
        "prefix": "prefs",
        "primary_keys": ["customer_id"],
        "fields": [
            ("customer_id", "Integer", "9", "0", 5),
            ("newsletter_opt_in", "String", "1", "", 2),
            ("preferred_channel", "String", "10", "", 2),
            ("language_code", "String", "5", "", 2),
            ("load_date", "Timestamp", "", "", 9),
            ("record_source", "String", "20", "", 2),
        ],
    },
}


def build_source(name: str, spec: dict) -> dict:
    prefix = spec["prefix"]
    fields = spec["fields"]
    pk_positions = primary_key_positions(spec.get("primary_keys", []))
    dv_fields = []
    for field_name, data_type, length, precision, hop_type in fields:
        entry = {
            "name": field_name,
            "description": None,
            "sourceDataType": data_type,
            "length": length,
            "precision": precision,
            "hopType": hop_type,
        }
        pk_position = pk_positions.get(field_name, 0)
        if pk_position > 0:
            entry["primaryKeyPosition"] = pk_position
        if field_name == "load_date":
            entry["inputOptions"] = {
                "csv": {
                    "format": "yyyy-MM-dd",
                    "decimalSymbol": "",
                    "groupingSymbol": "",
                    "currencySymbol": None,
                }
            }
        else:
            entry["inputOptions"] = None
        dv_fields.append(entry)

    return {
        "namespace": "hop/integration-tests/sources",
        "name": name,
        "type": "DV_SOURCE",
        "description": f"Customer 360 CSV source for {prefix} satellite integration tests",
        "rowMetaXml": "",
        "origin": {
            "modelType": "DATA_VAULT_SOURCE",
            "modelName": "customer-360",
            "modelFilename": "/integration-tests/tests//multi-satellite-bv/customer-360.hdv",
            "modelElementName": name,
            "hopProject": "multi-satellite-bv",
            "createdAt": 1782300000000,
            "updatedAt": 1782300000000,
            "updatedBy": None,
            "lastWorkflow": None,
            "lastPipeline": None,
        },
        "physicalTable": None,
        "physicalFile": {
            "folder": "${PROJECT_HOME}/files/multi-satellite-bv",
            "includeFileMask": f"{prefix}_load1\\.csv",
            "excludeFileMask": ".*\\.txt",
            "includeSubfolders": False,
            "required": True,
        },
        "tags": ["DV Source", "FULL_SNAPSHOT", "CSV", "CUSTOMER_360"],
        "glossaryTerms": [],
        "customProperties": {},
        "dvSource": {
            "sourceType": "CSV",
            "sourceIndicator": "",
            "sourceIndicatorField": "record_source",
            "group": None,
            "deliveryType": "CHANGES_ONLY" if name != "CRM-c360-hub" else "FULL_SNAPSHOT",
            "fields": dv_fields,
            "csvFormat": {
                "delimiter": ",",
                "enclosure": '"',
                "escapeCharacter": "",
                "encoding": "",
                "headerPresent": True,
                "headerLines": 1,
                "fileFormat": "CSV",
                "inputTransform": "TEXT_FILE_INPUT",
                "singleFilename": "",
            },
        },
    }


def main() -> None:
    CATALOG_DIR.mkdir(parents=True, exist_ok=True)
    for name, spec in FIELD_SPECS.items():
        path = CATALOG_DIR / f"{name}.json"
        payload = build_source(name, spec)
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        print(f"Wrote {path}")


if __name__ == "__main__":
    main()