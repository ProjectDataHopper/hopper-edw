#!/usr/bin/env python3
"""Generate CSV source files for the customer-360 multi-satellite BV integration test."""
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
from dataclasses import dataclass
from pathlib import Path

CUSTOMER_COUNT = 300
CUSTOMER_ID_START = 1001
OUTPUT_DIR = Path(__file__).resolve().parents[2] / "files" / "multi-satellite-bv"

LOAD_DATES = {
    "load1": "2024-01-01",
    "update1": "2024-02-01",
    "update2": "2024-03-01",
    "update3": "2024-04-01",
    "update4": "2024-05-01",
}

SEGMENTS = ["RETAIL", "WHOLESALE", "ENTERPRISE", "SMB"]
TIERS = ["BRONZE", "SILVER", "GOLD", "PLATINUM"]
CHANNELS = ["EMAIL", "SMS", "PHONE", "PORTAL"]
LANGUAGES = ["en", "fr", "de", "es"]
CITIES = ["New York", "Los Angeles", "Chicago", "Houston", "Phoenix", "Philadelphia"]


@dataclass(frozen=True)
class CustomerSeed:
    customer_id: int
    segment: str
    loyalty_tier: str
    demo_score: int
    email: str
    phone: str
    address_line1: str
    city: str
    postal_code: str
    newsletter_opt_in: str
    preferred_channel: str
    language_code: str


def customer_ids() -> list[int]:
    return list(range(CUSTOMER_ID_START, CUSTOMER_ID_START + CUSTOMER_COUNT))


def build_seed(customer_id: int) -> CustomerSeed:
    idx = customer_id - CUSTOMER_ID_START
    return CustomerSeed(
        customer_id=customer_id,
        segment=SEGMENTS[idx % len(SEGMENTS)],
        loyalty_tier=TIERS[idx % len(TIERS)],
        demo_score=50 + (idx % 50),
        email=f"customer{customer_id}@example.com",
        phone=f"+1-555-{customer_id:04d}",
        address_line1=f"{100 + (idx % 900)} Market Street",
        city=CITIES[idx % len(CITIES)],
        postal_code=f"{10000 + idx:05d}",
        newsletter_opt_in="Y" if idx % 2 == 0 else "N",
        preferred_channel=CHANNELS[idx % len(CHANNELS)],
        language_code=LANGUAGES[idx % len(LANGUAGES)],
    )


def write_csv(path: Path, header: list[str], rows: list[list[object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(header)
        writer.writerows(rows)


def changed_customer_ids(wave_index: int) -> list[int]:
    ids = customer_ids()
    offset = (wave_index * 37) % CUSTOMER_COUNT
    step = 5
    count = 60
    return [ids[(offset + i * step) % CUSTOMER_COUNT] for i in range(count)]


def mutate_seed(seed: CustomerSeed, wave_index: int) -> CustomerSeed:
    idx = seed.customer_id - CUSTOMER_ID_START
    bump = wave_index
    return CustomerSeed(
        customer_id=seed.customer_id,
        segment=SEGMENTS[(idx + bump) % len(SEGMENTS)],
        loyalty_tier=TIERS[(idx + bump) % len(TIERS)],
        demo_score=min(99, seed.demo_score + 3 + wave_index),
        email=f"customer{seed.customer_id}.v{wave_index}@example.com",
        phone=f"+1-555-{seed.customer_id:04d}-{wave_index}",
        address_line1=f"{100 + ((idx + bump * 11) % 900)} Updated Avenue",
        city=CITIES[(idx + bump) % len(CITIES)],
        postal_code=f"{10000 + idx + wave_index:05d}",
        newsletter_opt_in="N" if seed.newsletter_opt_in == "Y" else "Y",
        preferred_channel=CHANNELS[(idx + bump) % len(CHANNELS)],
        language_code=LANGUAGES[(idx + bump) % len(LANGUAGES)],
    )


def demo_row(seed: CustomerSeed, load_date: str) -> list[object]:
    return [
        seed.customer_id,
        seed.segment,
        seed.loyalty_tier,
        seed.demo_score,
        load_date,
        "CRM-c360-demo",
    ]


def contact_row(seed: CustomerSeed, load_date: str) -> list[object]:
    return [seed.customer_id, seed.email, seed.phone, load_date, "CRM-c360-contact"]


def address_row(seed: CustomerSeed, load_date: str) -> list[object]:
    return [
        seed.customer_id,
        seed.address_line1,
        seed.city,
        seed.postal_code,
        load_date,
        "CRM-c360-address",
    ]


def prefs_row(seed: CustomerSeed, load_date: str) -> list[object]:
    return [
        seed.customer_id,
        seed.newsletter_opt_in,
        seed.preferred_channel,
        seed.language_code,
        load_date,
        "CRM-c360-prefs",
    ]


SATELLITE_WRITERS = {
    "demo": (
        ["customer_id", "segment", "loyalty_tier", "demo_score", "load_date", "record_source"],
        demo_row,
    ),
    "contact": (
        ["customer_id", "email", "phone", "load_date", "record_source"],
        contact_row,
    ),
    "address": (
        ["customer_id", "address_line1", "city", "postal_code", "load_date", "record_source"],
        address_row,
    ),
    "prefs": (
        [
            "customer_id",
            "newsletter_opt_in",
            "preferred_channel",
            "language_code",
            "load_date",
            "record_source",
        ],
        prefs_row,
    ),
}


def main() -> None:
    seeds = {customer_id: build_seed(customer_id) for customer_id in customer_ids()}

    write_csv(
        OUTPUT_DIR / "hub_load1.csv",
        ["customer_id", "load_date", "record_source"],
        [[customer_id, LOAD_DATES["load1"], "CRM-c360-hub"] for customer_id in customer_ids()],
    )

    for sat_name, (header, row_builder) in SATELLITE_WRITERS.items():
        write_csv(
            OUTPUT_DIR / f"{sat_name}_load1.csv",
            header,
            [row_builder(seed, LOAD_DATES["load1"]) for seed in seeds.values()],
        )

        for wave_index, wave_name in enumerate(
            ["update1", "update2", "update3", "update4"], start=1
        ):
            rows = [
                row_builder(mutate_seed(seeds[customer_id], wave_index), LOAD_DATES[wave_name])
                for customer_id in changed_customer_ids(wave_index)
            ]
            write_csv(OUTPUT_DIR / f"{sat_name}_{wave_name}.csv", header, rows)

    print(f"Generated customer-360 CSV files in {OUTPUT_DIR}")


if __name__ == "__main__":
    main()