#!/usr/bin/env python3
"""Point customer-360 catalog CSV sources at the requested load wave file mask."""
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
import sys
from pathlib import Path

CATALOG_DIR = (
    Path(__file__).resolve().parents[2] / "catalog-data" / "hop" / "integration-tests" / "sources"
)

SOURCE_PREFIXES = {
    "CRM-c360-hub": "hub",
    "CRM-c360-demo": "demo",
    "CRM-c360-contact": "contact",
    "CRM-c360-address": "address",
    "CRM-c360-prefs": "prefs",
}


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: activate-source-wave.py <load1|update1|update2|update3|update4>")

    wave = sys.argv[1]
    if wave not in {"load1", "update1", "update2", "update3", "update4"}:
        raise SystemExit(f"Unsupported wave: {wave}")

    for source_name, prefix in SOURCE_PREFIXES.items():
        path = CATALOG_DIR / f"{source_name}.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        effective_wave = "load1" if source_name == "CRM-c360-hub" and wave != "load1" else wave
        payload["physicalFile"]["includeFileMask"] = f"{prefix}_{effective_wave}\\.csv"
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        print(f"Updated {path.name} -> {prefix}_{effective_wave}.csv")


if __name__ == "__main__":
    main()