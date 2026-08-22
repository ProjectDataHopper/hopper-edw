#!/usr/bin/env python3
"""Generate golden CSV for the customer_360_bv current-state integration test."""
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

import csv
import hashlib
import importlib.util
import sys
from pathlib import Path

_DATA_MODULE_PATH = Path(__file__).resolve().parent / "generate-customer-360-data.py"
_SPEC = importlib.util.spec_from_file_location("generate_customer_360_data", _DATA_MODULE_PATH)
_DATA = importlib.util.module_from_spec(_SPEC)
assert _SPEC.loader is not None
sys.modules[_SPEC.name] = _DATA
_SPEC.loader.exec_module(_DATA)

changed_customer_ids = _DATA.changed_customer_ids
customer_ids = _DATA.customer_ids
build_seed = _DATA.build_seed
mutate_seed = _DATA.mutate_seed

OUTPUT_DIR = Path(__file__).resolve().parents[2] / "datasets" / "multi-satellite-bv"
GOLDEN_FILE = OUTPUT_DIR / "customer-360-bv-current-golden.csv"

HEADER = [
    "customer_hk",
    "cust_segment",
    "cust_loyalty_tier",
    "cust_demo_score",
    "cust_email",
    "cust_phone",
    "cust_address",
    "cust_city",
    "cust_postal_code",
    "cust_newsletter",
    "cust_channel",
    "cust_language",
]


def customer_hash_key(customer_id: int) -> str:
    digest = hashlib.md5(str(customer_id).encode("utf-8")).digest()
    return "-".join(str(byte) for byte in digest)


def latest_seed(customer_id: int, seeds: dict) -> object:
    latest_wave = 0
    for wave_index in range(1, 5):
        if customer_id in changed_customer_ids(wave_index):
            latest_wave = wave_index
    seed = seeds[customer_id]
    if latest_wave == 0:
        return seed
    return mutate_seed(seed, latest_wave)


def main() -> None:
    seeds = {customer_id: build_seed(customer_id) for customer_id in customer_ids()}
    rows = []
    for customer_id in customer_ids():
        seed = latest_seed(customer_id, seeds)
        rows.append(
            [
                customer_hash_key(customer_id),
                seed.segment,
                seed.loyalty_tier,
                seed.demo_score,
                seed.email,
                seed.phone,
                seed.address_line1,
                seed.city,
                seed.postal_code,
                seed.newsletter_opt_in,
                seed.preferred_channel,
                seed.language_code,
            ]
        )

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    with GOLDEN_FILE.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(HEADER)
        writer.writerows(rows)
    print(f"Wrote {GOLDEN_FILE} ({len(rows)} rows)")


if __name__ == "__main__":
    main()