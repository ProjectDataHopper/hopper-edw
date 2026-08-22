#!/usr/bin/env python3
"""Generate input and golden CSV datasets for DvHashKey integration tests."""
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
#

from __future__ import annotations

import csv
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATASETS = ROOT / "datasets"
METADATA = ROOT / "metadata" / "dataset"
UNIT_TESTS = ROOT / "metadata" / "unit-test"
TEST_PIPELINES = Path(__file__).resolve().parent


def apply_casing(value: str | None, casing: str) -> str | None:
    if value is None:
        return None
    if casing == "UPPER":
        return value.upper()
    if casing == "LOWER":
        return value.lower()
    return value


def build_hash_input(row: dict[str, str | None], fields: list[str], cfg: dict) -> bytes | None:
    prefix = cfg.get("prefix") or ""
    suffix = cfg.get("suffix") or ""
    delimiter = cfg.get("delimiter", "||") or ""
    null_placeholder = cfg.get("null_placeholder", "^^")
    trim = cfg.get("trim", True)
    casing = cfg.get("casing", "UPPER")

    builder = prefix
    value_added = len(builder) > 0

    for field in fields:
        part = row.get(field)
        if part is None or part == "":
            if not null_placeholder:
                continue
            part = null_placeholder
        if trim and part is not None:
            part = part.strip()
        part = apply_casing(part, casing)
        if not part:
            continue
        if value_added and delimiter:
            builder += delimiter
        builder += part
        value_added = True

    if suffix:
        builder += suffix
        value_added = True

    if not value_added:
        return None
    return builder.encode("utf-8")


def format_hash(digest: bytes, data_type: str) -> str:
    if data_type == "HEX":
        return digest.hex()
    if data_type == "STRING":
        return "-".join(str(b) for b in digest)
    raise ValueError(f"Unsupported data type for CSV golden files: {data_type}")


def compute_hash(row: dict[str, str | None], fields: list[str], cfg: dict) -> str:
    payload = build_hash_input(row, fields, cfg)
    if payload is None:
        return ""
    algo_map = {
        "MD5": "md5",
        "SHA1": "sha1",
        "SHA256": "sha256",
        "SHA512": "sha512",
    }
    digest = hashlib.new(algo_map[cfg["algorithm"]], payload).digest()
    return format_hash(digest, cfg["data_type"])


SCENARIOS: list[dict] = [
    {
        "id": "md5-single-string-upper",
        "fields": ["bk1"],
        "algorithm": "MD5",
        "data_type": "STRING",
        "casing": "UPPER",
        "delimiter": "||",
        "null_placeholder": "^^",
        "trim": True,
        "prefix": "",
        "suffix": "",
        "rows": [
            {"row_id": "1", "bk1": "1001"},
            {"row_id": "2", "bk1": "1002"},
            {"row_id": "3", "bk1": "abc"},
        ],
    },
    {
        "id": "md5-single-hex-upper",
        "fields": ["bk1"],
        "algorithm": "MD5",
        "data_type": "HEX",
        "casing": "UPPER",
        "delimiter": "||",
        "null_placeholder": "^^",
        "trim": True,
        "rows": [
            {"row_id": "1", "bk1": "1001"},
            {"row_id": "2", "bk1": "product"},
        ],
    },
    {
        "id": "md5-composite-string",
        "fields": ["bk1", "bk2"],
        "algorithm": "MD5",
        "data_type": "STRING",
        "casing": "UPPER",
        "delimiter": "||",
        "null_placeholder": "^^",
        "trim": True,
        "rows": [
            {"row_id": "1", "bk1": "A", "bk2": "B"},
            {"row_id": "2", "bk1": "east", "bk2": "west"},
        ],
    },
    {
        "id": "md5-casing-lower-hex",
        "fields": ["bk1"],
        "algorithm": "MD5",
        "data_type": "HEX",
        "casing": "LOWER",
        "delimiter": "||",
        "null_placeholder": "^^",
        "trim": True,
        "rows": [
            {"row_id": "1", "bk1": "AbC"},
            {"row_id": "2", "bk1": "MiXeD"},
        ],
    },
    {
        "id": "md5-casing-none-hex",
        "fields": ["bk1"],
        "algorithm": "MD5",
        "data_type": "HEX",
        "casing": "NONE",
        "delimiter": "||",
        "null_placeholder": "^^",
        "trim": True,
        "rows": [
            {"row_id": "1", "bk1": "AbC"},
            {"row_id": "2", "bk1": "1001"},
        ],
    },
    {
        "id": "md5-no-trim-hex",
        "fields": ["bk1"],
        "algorithm": "MD5",
        "data_type": "HEX",
        "casing": "NONE",
        "delimiter": "||",
        "null_placeholder": "^^",
        "trim": False,
        "rows": [
            {"row_id": "1", "bk1": "  spaced  "},
            {"row_id": "2", "bk1": "x"},
        ],
    },
    {
        "id": "md5-null-placeholder",
        "fields": ["bk1", "bk2"],
        "algorithm": "MD5",
        "data_type": "HEX",
        "casing": "UPPER",
        "delimiter": "||",
        "null_placeholder": "^^",
        "trim": True,
        "rows": [
            {"row_id": "1", "bk1": "", "bk2": "B"},
            {"row_id": "2", "bk1": "A", "bk2": ""},
        ],
    },
    {
        "id": "md5-prefix-suffix-hex",
        "fields": ["bk1"],
        "algorithm": "MD5",
        "data_type": "HEX",
        "casing": "UPPER",
        "delimiter": "||",
        "null_placeholder": "^^",
        "trim": True,
        "prefix": "PRE:",
        "suffix": ":SUF",
        "rows": [
            {"row_id": "1", "bk1": "X"},
            {"row_id": "2", "bk1": "Y"},
        ],
    },
    {
        "id": "sha1-single-hex",
        "fields": ["bk1"],
        "algorithm": "SHA1",
        "data_type": "HEX",
        "casing": "UPPER",
        "delimiter": "||",
        "null_placeholder": "^^",
        "trim": True,
        "rows": [
            {"row_id": "1", "bk1": "1001"},
            {"row_id": "2", "bk1": "link"},
            {"row_id": "3", "bk1": "A||B"},
        ],
    },
    {
        "id": "sha256-single-hex",
        "fields": ["bk1"],
        "algorithm": "SHA256",
        "data_type": "HEX",
        "casing": "UPPER",
        "delimiter": "||",
        "null_placeholder": "^^",
        "trim": True,
        "rows": [
            {"row_id": "1", "bk1": "1001"},
            {"row_id": "2", "bk1": "link"},
            {"row_id": "3", "bk1": "A||B"},
        ],
    },
    {
        "id": "sha512-single-hex",
        "fields": ["bk1"],
        "algorithm": "SHA512",
        "data_type": "HEX",
        "casing": "UPPER",
        "delimiter": "||",
        "null_placeholder": "^^",
        "trim": True,
        "rows": [
            {"row_id": "1", "bk1": "1001"},
            {"row_id": "2", "bk1": "link"},
            {"row_id": "3", "bk1": "A||B"},
        ],
    },
    {
        "id": "md5-skip-null-no-placeholder",
        "fields": ["bk1"],
        "algorithm": "MD5",
        "data_type": "HEX",
        "casing": "UPPER",
        "delimiter": "||",
        "null_placeholder": "",
        "trim": True,
        "rows": [
            {"row_id": "1", "bk1": ""},
            {"row_id": "2", "bk1": "ok"},
        ],
    },
]


def field_defs(include_hash: bool, hash_length: int) -> list[dict]:
    fields = [
        {
            "field_comment": "row_id",
            "field_length": 9,
            "field_type": 5,
            "field_precision": 0,
            "field_name": "row_id",
            "field_format": "####0;-####0",
        },
        {
            "field_comment": "bk1",
            "field_length": 100,
            "field_type": 2,
            "field_precision": -1,
            "field_name": "bk1",
            "field_format": "",
        },
        {
            "field_comment": "bk2",
            "field_length": 100,
            "field_type": 2,
            "field_precision": -1,
            "field_name": "bk2",
            "field_format": "",
        },
    ]
    if include_hash:
        fields.append(
            {
                "field_comment": "hash_key",
                "field_length": hash_length,
                "field_type": 2,
                "field_precision": -1,
                "field_name": "hash_key",
                "field_format": "",
            }
        )
    return fields


def write_csv(path: Path, fieldnames: list[str], rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def write_dataset_json(name: str, filename: str, fields: list[dict]) -> None:
    import json

    payload = {
        "base_filename": filename,
        "name": name,
        "description": "",
        "dataset_fields": fields,
        "folder_name": "",
    }
    METADATA.mkdir(parents=True, exist_ok=True)
    (METADATA / f"{name}.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def hash_length_for(cfg: dict) -> int:
    digest = {"MD5": 16, "SHA1": 20, "SHA256": 32, "SHA512": 64}[cfg["algorithm"]]
    if cfg["data_type"] == "HEX":
        return digest * 2
    if cfg["data_type"] == "STRING":
        return digest * 3 + (digest - 1 if digest else 0)
    return digest


def yn(value: bool) -> str:
    return "Y" if value else "N"


def field_xml(names: list[str]) -> str:
    return "\n".join(f"      <field>\n        <name>{name}</name>\n      </field>" for name in names)


def pipeline_xml(scenario: dict) -> str:
    sid = scenario["id"]
    input_name = f"hash-key-{sid}-input"
    fields = scenario["fields"]
    prefix = scenario.get("prefix") or ""
    suffix = scenario.get("suffix") or ""
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<pipeline>
  <info>
    <capture_transform_performance>N</capture_transform_performance>
    <transform_performance_capturing_delay>1000</transform_performance_capturing_delay>
    <transform_performance_capturing_size_limit>100</transform_performance_capturing_size_limit>
    <pipeline_type>Normal</pipeline_type>
    <pipeline_status>-1</pipeline_status>
    <parameters/>
    <name>validate-hash-key-{sid}</name>
    <name_sync_with_filename>Y</name_sync_with_filename>
    <created_user>-</created_user>
    <modified_user>-</modified_user>
    <created_date>2026/06/19 12:00:00.000</created_date>
    <modified_date>2026/06/19 12:00:00.000</modified_date>
  </info>
  <transform>
    <type>DataSetInput</type>
    <name>input data</name>
    <dataSetName>{input_name}</dataSetName>
    <distribute>Y</distribute>
    <copies>1</copies>
    <GUI>
      <xloc>128</xloc>
      <yloc>96</yloc>
    </GUI>
    <partitioning>
      <method>none</method>
      <schema_name/>
    </partitioning>
    <attributes/>
  </transform>
  <transform>
    <type>DvHashKey</type>
    <name>calc hash</name>
    <fields>
{field_xml(fields)}
    </fields>
    <resultfieldName>hash_key</resultfieldName>
    <hashAlgorithm>{scenario["algorithm"]}</hashAlgorithm>
    <hashKeyDataType>{scenario["data_type"]}</hashKeyDataType>
    <hashContentCasing>{scenario["casing"]}</hashContentCasing>
    <businessKeyDelimiter>{scenario["delimiter"]}</businessKeyDelimiter>
    <hashContentPrefix>{prefix}</hashContentPrefix>
    <hashContentSuffix>{suffix}</hashContentSuffix>
    <nullPlaceholder>{scenario["null_placeholder"]}</nullPlaceholder>
    <trimBusinessKeys>{yn(scenario["trim"])}</trimBusinessKeys>
    <distribute>Y</distribute>
    <copies>1</copies>
    <GUI>
      <xloc>288</xloc>
      <yloc>96</yloc>
    </GUI>
    <partitioning>
      <method>none</method>
      <schema_name/>
    </partitioning>
    <attributes/>
  </transform>
  <transform>
    <type>SelectValues</type>
    <name>select result</name>
    <fields>
      <field>
        <name>row_id</name>
        <length>-2</length>
        <precision>-2</precision>
      </field>
      <field>
        <name>hash_key</name>
        <length>-2</length>
        <precision>-2</precision>
      </field>
      <select_unspecified>N</select_unspecified>
    </fields>
    <distribute>Y</distribute>
    <copies>1</copies>
    <GUI>
      <xloc>448</xloc>
      <yloc>96</yloc>
    </GUI>
    <partitioning>
      <method>none</method>
      <schema_name/>
    </partitioning>
    <attributes/>
  </transform>
  <transform>
    <type>Dummy</type>
    <name>Validate</name>
    <distribute>Y</distribute>
    <copies>1</copies>
    <GUI>
      <xloc>608</xloc>
      <yloc>96</yloc>
    </GUI>
    <partitioning>
      <method>none</method>
      <schema_name/>
    </partitioning>
    <attributes/>
  </transform>
  <order>
    <hop>
      <from>input data</from>
      <to>calc hash</to>
      <enabled>Y</enabled>
    </hop>
    <hop>
      <from>calc hash</from>
      <to>select result</to>
      <enabled>Y</enabled>
    </hop>
    <hop>
      <from>select result</from>
      <to>Validate</to>
      <enabled>Y</enabled>
    </hop>
  </order>
  <notepads/>
  <attributes/>
  <transform_error_handling/>
</pipeline>
"""


def unit_test_json(scenario: dict) -> str:
    import json

    sid = scenario["id"]
    golden_name = f"hash-key-{sid}-golden"
    payload = {
        "database_replacements": [],
        "autoOpening": True,
        "description": f"DvHashKey integration test: {sid}",
        "persist_filename": "",
        "test_type": "UNIT_TEST",
        "variableValues": [],
        "basePath": "${HOP_UNIT_TESTS_FOLDER}",
        "golden_data_sets": [
            {
                "field_mappings": [
                    {"transform_field": "row_id", "data_set_field": "row_id"},
                    {"transform_field": "hash_key", "data_set_field": "hash_key"},
                ],
                "field_order": ["row_id"],
                "data_set_name": golden_name,
                "transform_name": "Validate",
            }
        ],
        "input_data_sets": [],
        "name": f"validate-hash-key-{sid} UNIT",
        "trans_test_tweaks": [],
        "pipeline_filename": f"./tests/hash-key/validate-hash-key-{sid}.hpl",
    }
    return json.dumps(payload, indent=2) + "\n"


def workflow_xml() -> str:
    test_names = "\n".join(
        f"        <test_name><name>validate-hash-key-{s['id']} UNIT</name></test_name>"
        for s in SCENARIOS
    )
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<workflow>
  <name>run-hash-key-tests</name>
  <name_sync_with_filename>Y</name_sync_with_filename>
  <created_user>-</created_user>
  <modified_user>-</modified_user>
  <created_date>2026/06/19 12:00:00.000</created_date>
  <modified_date>2026/06/19 12:00:00.000</modified_date>
  <parameters/>
  <actions>
    <action>
      <repeat>N</repeat>
      <schedulerType>0</schedulerType>
      <intervalSeconds>0</intervalSeconds>
      <intervalMinutes>60</intervalMinutes>
      <DayOfMonth>1</DayOfMonth>
      <weekDay>1</weekDay>
      <minutes>0</minutes>
      <hour>12</hour>
      <doNotWaitOnFirstExecution>N</doNotWaitOnFirstExecution>
      <name>Start</name>
      <description/>
      <type>SPECIAL</type>
      <attributes/>
      <xloc>96</xloc>
      <yloc>64</yloc>
      <parallel>N</parallel>
      <attributes_hac/>
    </action>
    <action>
      <test_names>
{test_names}
      </test_names>
      <name>Run DvHashKey tests</name>
      <description>Integration tests for Data Vault Hash Key transform options</description>
      <type>RunPipelineTests</type>
      <attributes/>
      <xloc>320</xloc>
      <yloc>64</yloc>
      <parallel>N</parallel>
      <attributes_hac/>
    </action>
    <action>
      <name>Success</name>
      <description/>
      <type>SUCCESS</type>
      <attributes/>
      <xloc>544</xloc>
      <yloc>64</yloc>
      <parallel>N</parallel>
      <attributes_hac/>
    </action>
  </actions>
  <hops>
    <hop>
      <from>Start</from>
      <to>Run DvHashKey tests</to>
      <evaluation>Y</evaluation>
      <unconditional>Y</unconditional>
      <enabled>Y</enabled>
    </hop>
    <hop>
      <from>Run DvHashKey tests</from>
      <to>Success</to>
      <evaluation>Y</evaluation>
      <unconditional>N</unconditional>
      <enabled>Y</enabled>
    </hop>
  </hops>
  <notepads/>
  <attributes/>
</workflow>
"""


def main() -> None:
    DATASETS.mkdir(parents=True, exist_ok=True)
    for scenario in SCENARIOS:
        sid = scenario["id"]
        fields = scenario["fields"]
        input_name = f"hash-key-{sid}-input"
        golden_name = f"hash-key-{sid}-golden"
        input_file = f"{input_name}.csv"
        golden_file = f"{golden_name}.csv"

        input_rows = []
        golden_rows = []
        for row in scenario["rows"]:
            normalized = {
                "row_id": row["row_id"],
                "bk1": row.get("bk1", ""),
                "bk2": row.get("bk2", ""),
            }
            input_rows.append(normalized)
            golden_rows.append(
                {
                    "row_id": row["row_id"],
                    "hash_key": compute_hash(normalized, fields, scenario),
                }
            )

        write_csv(DATASETS / input_file, ["row_id", "bk1", "bk2"], input_rows)
        write_csv(DATASETS / golden_file, ["row_id", "hash_key"], golden_rows)

        write_dataset_json(
            input_name,
            input_file,
            field_defs(include_hash=False, hash_length=0),
        )
        write_dataset_json(
            golden_name,
            golden_file,
            [
                field_defs(include_hash=False, hash_length=0)[0],
                field_defs(include_hash=True, hash_length=hash_length_for(scenario))[-1],
            ],
        )

        pipeline_path = TEST_PIPELINES / f"validate-hash-key-{sid}.hpl"
        pipeline_path.write_text(pipeline_xml(scenario), encoding="utf-8")
        unit_test_path = UNIT_TESTS / f"validate-hash-key-{sid} UNIT.json"
        unit_test_path.write_text(unit_test_json(scenario), encoding="utf-8")

        print(f"generated {sid}")

    workflow_path = TEST_PIPELINES / "run-hash-key-tests.hwf"
    workflow_path.write_text(workflow_xml(), encoding="utf-8")
    print(f"generated workflow ({len(SCENARIOS)} tests)")


if __name__ == "__main__":
    main()