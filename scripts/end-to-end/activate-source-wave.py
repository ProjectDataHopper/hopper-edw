#!/usr/bin/env python3
"""Point retail E2E catalog CSV sources at the requested load wave file mask."""
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

import argparse
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PROJECT_HOME = REPO_ROOT / "retail-example"
PERIOD_LABEL_FILE = ".period-label"

SOURCE_PREFIXES = {
    "E2E-customer-hub": "customer_hub",
    "E2E-customer-demo": "customer_demo",
    "E2E-customer-contact": "customer_contact",
    "E2E-customer-address": "customer_address",
    "E2E-customer-prefs": "customer_prefs",
    "E2E-product": "product",
    "E2E-order-header": "order_header",
    "E2E-order-line": "order_line",
    "E2E-warehouse": "warehouse",
    "E2E-warehouse-product": "warehouse_product",
    "E2E-sales-rep": "sales_rep",
    "E2E-order-rep": "order_rep",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "wave",
        nargs="?",
        default="",
        help="initial or period label such as 2024-02; defaults to files/.period-label for updates",
    )
    parser.add_argument("--project-home", type=Path, default=DEFAULT_PROJECT_HOME)
    return parser.parse_args()


def resolve_wave(args: argparse.Namespace) -> str:
    if args.wave:
        return args.wave
    label_file = args.project_home / "files" / PERIOD_LABEL_FILE
    if label_file.is_file():
        return label_file.read_text(encoding="utf-8").strip()
    raise SystemExit("Usage: activate-source-wave.py <initial|period-label>")


def main() -> None:
    args = parse_args()
    wave = resolve_wave(args)
    if wave not in {"initial"} and len(wave) != 7:
        raise SystemExit(f"Unsupported wave label: {wave}")

    catalog_dir = (
        args.project_home.expanduser().resolve()
        / "work"
        / "edw-catalog"
        / "hop"
        / "retail-example"
        / "sources"
    )
    for source_name, prefix in SOURCE_PREFIXES.items():
        path = catalog_dir / f"{source_name}.json"
        if not path.is_file():
            raise SystemExit(f"Missing catalog source: {path}")
        payload = json.loads(path.read_text(encoding="utf-8"))
        effective_wave = "initial" if source_name == "E2E-customer-hub" and wave != "initial" else wave
        payload["physicalFile"]["includeFileMask"] = f"{prefix}_{effective_wave}\\.csv"
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        print(f"Updated {path.name} -> {prefix}_{effective_wave}.csv")


if __name__ == "__main__":
    main()