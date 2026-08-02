#!/usr/bin/env python3
"""Generate retail example CSV source files for initial or incremental loads."""

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
import csv
import os
import random
import subprocess
import sys
from dataclasses import dataclass
from datetime import date, timedelta
from itertools import chain
from pathlib import Path
from typing import Iterable

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PROJECT_HOME = REPO_ROOT / "retail-example"
DEFAULT_FILES_DIR = DEFAULT_PROJECT_HOME / "files"
PERIOD_LABEL_FILE = ".period-label"
DV_LOAD_DATE_FILE = ".dv-load-date"
LOAD_CONTROL_FILE = ".load-control"

SEGMENTS = ("RETAIL", "WHOLESALE", "ONLINE")
LOYALTY_TIERS = ("BRONZE", "SILVER", "GOLD", "PLATINUM")
CHANNELS = ("EMAIL", "SMS", "PHONE")
LANGUAGES = ("en", "fr", "de", "es")
CATEGORIES = ("Electronics", "Apparel", "Home", "Sports", "Grocery")
REGIONS = ("North", "South", "East", "West")
SALES_REGIONS = ("NORTH", "SOUTH", "EAST", "WEST", "CENTRAL")
ORDER_STATUSES = ("NEW", "SHIPPED", "DELIVERED", "CANCELLED")

# Seed sales reps (rep_id, rep_name, region) — extended with generated names for scale.sales_reps.
SEED_SALES_REPS = (
    (101, "Ada Lovelace", "NORTH"),
    (102, "Grace Hopper", "SOUTH"),
    (103, "Alan Turing", "EAST"),
    (104, "Katherine Johnson", "WEST"),
    (105, "Donald Knuth", "CENTRAL"),
)


@dataclass(frozen=True)
class Scale:
    customers: int = 10_000
    products: int = 1_000
    orders: int = 100_000
    warehouses: int = 50
    sales_reps: int = 25


@dataclass(frozen=True)
class LoadContext:
    progress_date: date
    period_months: int
    wave_label: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("initial", "update"), required=True)
    parser.add_argument("--project-home", type=Path, default=DEFAULT_PROJECT_HOME)
    parser.add_argument("--customers", type=int, default=Scale.customers)
    parser.add_argument("--products", type=int, default=Scale.products)
    parser.add_argument("--orders", type=int, default=Scale.orders)
    parser.add_argument("--warehouses", type=int, default=Scale.warehouses)
    parser.add_argument("--sales-reps", type=int, default=Scale.sales_reps)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--progress-date",
        type=str,
        default="",
        help="Override progress_date for update mode (YYYY-MM-DD)",
    )
    return parser.parse_args()


def read_load_context_from_file(project_home: Path) -> LoadContext | None:
    path = project_home / "files" / LOAD_CONTROL_FILE
    if not path.is_file():
        return None
    line = path.read_text(encoding="utf-8").strip().splitlines()[-1]
    progress_raw, period_raw = line.split(",", 1)
    progress = date.fromisoformat(progress_raw)
    period_months = int(period_raw)
    return LoadContext(
        progress_date=progress,
        period_months=period_months,
        wave_label=_period_label(progress),
    )


def read_load_context(project_home: Path, progress_override: str) -> LoadContext:
    if progress_override:
        progress = date.fromisoformat(progress_override)
        return LoadContext(progress_date=progress, period_months=1, wave_label=_period_label(progress))

    env_date = os.environ.get("RETAIL_PROGRESS_DATE", "").strip()
    if env_date:
        progress = date.fromisoformat(env_date)
        return LoadContext(progress_date=progress, period_months=1, wave_label=_period_label(progress))

    file_context = read_load_context_from_file(project_home)
    if file_context is not None:
        return file_context

    sql_file = project_home / "sql" / "read-load-control.sql"
    query = sql_file.read_text(encoding="utf-8").strip()
    command = [
        "psql",
        "-h",
        os.environ.get("DB_HOST", "localhost"),
        "-p",
        os.environ.get("DB_PORT", "54320"),
        "-U",
        os.environ.get("DB_USER", "test"),
        "-d",
        os.environ.get("DB_TARGET_NAME", os.environ.get("DB_NAME", "test_edw")),
        "-At",
        "-F",
        ",",
        "-c",
        query,
    ]
    env = os.environ.copy()
    env.setdefault("PGPASSWORD", os.environ.get("DB_PASSWORD", "test"))

    try:
        result = subprocess.run(command, capture_output=True, text=True, check=True, env=env)
    except (OSError, subprocess.CalledProcessError):
        progress = date(2024, 2, 1)
        print("Warning: could not read load_control via psql; using default progress_date 2024-02-01")
        return LoadContext(progress_date=progress, period_months=1, wave_label=_period_label(progress))

    line = result.stdout.strip().splitlines()[-1]
    progress_raw, period_raw = line.split(",", 1)
    progress = date.fromisoformat(progress_raw)
    period_months = int(period_raw)
    return LoadContext(
        progress_date=progress,
        period_months=period_months,
        wave_label=_period_label(progress),
    )


def _period_label(progress: date) -> str:
    return f"{progress.year:04d}-{progress.month:02d}"


def write_csv(path: Path, header: list[str], rows: list[list[object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(header)
        writer.writerows(rows)


def customer_hub_rows(
    scale: Scale, rng: random.Random, load_date: str, customer_ids: Iterable[int]
) -> list[list[object]]:
    return [
        [customer_id, load_date, "E2E-customer-hub"]
        for customer_id in customer_ids
    ]


def customer_demo_rows(
    scale: Scale, rng: random.Random, load_date: str, customer_ids: Iterable[int]
) -> list[list[object]]:
    return [
        [
            customer_id,
            rng.choice(SEGMENTS),
            rng.choice(LOYALTY_TIERS),
            rng.randint(40, 99),
            load_date,
            "E2E-customer-demo",
        ]
        for customer_id in customer_ids
    ]


def customer_contact_rows(
    scale: Scale, rng: random.Random, load_date: str, customer_ids: Iterable[int]
) -> list[list[object]]:
    return [
        [
            customer_id,
            f"customer{customer_id}@example.com",
            f"+1-555-{customer_id % 10000:04d}",
            load_date,
            "E2E-customer-contact",
        ]
        for customer_id in customer_ids
    ]


def customer_address_rows(
    scale: Scale, rng: random.Random, load_date: str, customer_ids: Iterable[int]
) -> list[list[object]]:
    cities = ("New York", "Chicago", "Phoenix", "Seattle", "Austin")
    return [
        [
            customer_id,
            f"{100 + (customer_id % 900)} Market Street",
            rng.choice(cities),
            f"{10000 + (customer_id % 900)}",
            load_date,
            "E2E-customer-address",
        ]
        for customer_id in customer_ids
    ]


def customer_prefs_rows(
    scale: Scale, rng: random.Random, load_date: str, customer_ids: Iterable[int]
) -> list[list[object]]:
    return [
        [
            customer_id,
            rng.choice(("Y", "N")),
            rng.choice(CHANNELS),
            rng.choice(LANGUAGES),
            load_date,
            "E2E-customer-prefs",
        ]
        for customer_id in customer_ids
    ]


def product_rows(
    scale: Scale, rng: random.Random, load_date: str, product_ids: Iterable[int]
) -> list[list[object]]:
    return [
        [
            f"P{product_id:06d}",
            f"Product {product_id}",
            rng.choice(CATEGORIES),
            round(rng.uniform(5.0, 499.99), 2),
            load_date,
            "E2E-product",
        ]
        for product_id in product_ids
    ]


def warehouse_rows(
    scale: Scale, rng: random.Random, load_date: str, warehouse_ids: Iterable[int]
) -> list[list[object]]:
    cities = ("New York", "Chicago", "Phoenix", "Seattle", "Austin")
    return [
        [
            warehouse_id,
            f"Warehouse {warehouse_id}",
            rng.choice(cities),
            rng.choice(REGIONS),
            rng.randint(10_000, 250_000),
            load_date,
            "E2E-warehouse",
        ]
        for warehouse_id in warehouse_ids
    ]


def order_header_rows(
    scale: Scale,
    rng: random.Random,
    load_date: str,
    order_ids: Iterable[int],
    anchor: date,
    max_customer_id: int | None = None,
) -> list[list[object]]:
    rows: list[list[object]] = []
    customer_ceiling = max_customer_id if max_customer_id is not None else scale.customers
    for order_id in order_ids:
        customer_id = rng.randint(1, customer_ceiling)
        order_day = anchor + timedelta(days=rng.randint(0, 27))
        ship_day = order_day + timedelta(days=rng.randint(1, 3))
        delivery_day = ship_day + timedelta(days=rng.randint(1, 5))
        rows.append(
            [
                f"O{order_id:06d}",
                customer_id,
                order_day.isoformat(),
                ship_day.isoformat(),
                delivery_day.isoformat(),
                rng.choice(ORDER_STATUSES),
                round(rng.uniform(25.0, 2500.0), 2),
                load_date,
                "E2E-order-header",
            ]
        )
    return rows


def order_line_rows(
    scale: Scale,
    rng: random.Random,
    load_date: str,
    order_ids: Iterable[int],
    anchor: date,
    max_product_id: int | None = None,
) -> list[list[object]]:
    """Generate order lines.

    Grain is (order_id, product_id, line_number). The same product may appear on
    more than one line of an order (transactional link dependent-child demo).
    The first order in the wave is forced to have at least two lines for the same
    product with different unit prices so the collapse problem is visible.
    """
    rows: list[list[object]] = []
    product_ceiling = max_product_id if max_product_id is not None else scale.products
    order_id_list = list(order_ids)
    for index, order_id in enumerate(order_id_list):
        if index == 0:
            # Demo: same product twice on one order (needs line_number on link hash)
            shared_product = rng.randint(1, product_ceiling)
            line_products = [shared_product, shared_product]
            if product_ceiling > 1:
                other = shared_product
                while other == shared_product:
                    other = rng.randint(1, product_ceiling)
                line_products.append(other)
        else:
            line_count = rng.randint(1, 4)
            line_products = [rng.randint(1, product_ceiling) for _ in range(line_count)]
            # Occasionally reuse a product already on this order
            if line_count >= 2 and rng.random() < 0.25:
                line_products[-1] = line_products[0]

        for line_number, product_id in enumerate(line_products, start=1):
            quantity = rng.randint(1, 5)
            if index == 0 and line_number <= 2:
                unit_price = round(4.0 + line_number * 0.5, 2)
                discount = round(0.05 * line_number, 2)
            else:
                unit_price = round(rng.uniform(5.0, 499.99), 2)
                discount = round(rng.uniform(0.0, 0.25), 2)
            rows.append(
                [
                    f"O{order_id:06d}",
                    f"P{product_id:06d}",
                    line_number,
                    quantity,
                    unit_price,
                    discount,
                    load_date,
                    "E2E-order-line",
                ]
            )
    return rows


def sample_existing_ids(
    rng: random.Random,
    existing_count: int,
    sample_size: int,
) -> list[int]:
    """Return a deterministic sample of existing entity ids in the range 1..existing_count."""
    if existing_count <= 0 or sample_size <= 0:
        return []
    target = min(sample_size, existing_count)
    return sorted(rng.sample(range(1, existing_count + 1), target))


def unique_random_pairs(
    rng: random.Random,
    warehouse_ids: list[int],
    product_ids: list[int],
    count: int,
) -> list[tuple[int, int]]:
    """Return up to count unique (warehouse_id, product_id) pairs."""
    if not warehouse_ids or not product_ids or count <= 0:
        return []
    max_pairs = len(warehouse_ids) * len(product_ids)
    target = min(count, max_pairs)
    pairs: set[tuple[int, int]] = set()
    while len(pairs) < target:
        pairs.add((rng.choice(warehouse_ids), rng.choice(product_ids)))
    return list(pairs)


def warehouse_product_rows(
    scale: Scale,
    rng: random.Random,
    load_date: str,
    pairs: list[tuple[int, int]],
) -> list[list[object]]:
    return [
        [
            warehouse_id,
            f"P{product_id:06d}",
            rng.randint(0, 5000),
            rng.randint(50, 500),
            load_date,
            "E2E-warehouse-product",
        ]
        for warehouse_id, product_id in pairs
    ]


def sales_rep_id_range(scale: Scale) -> range:
    """rep_id values: seed list starts at 101; remaining ids continue from 106."""
    count = max(len(SEED_SALES_REPS), scale.sales_reps)
    return range(101, 101 + count)


def sales_rep_rows(
    scale: Scale, rng: random.Random, load_date: str, rep_ids: Iterable[int]
) -> list[list[object]]:
    seed_by_id = {rep_id: (name, region) for rep_id, name, region in SEED_SALES_REPS}
    rows: list[list[object]] = []
    for rep_id in rep_ids:
        if rep_id in seed_by_id:
            name, region = seed_by_id[rep_id]
        else:
            name = f"Sales Rep {rep_id}"
            region = rng.choice(SALES_REGIONS)
        rows.append([rep_id, name, region, load_date, "E2E-sales-rep"])
    return rows


def order_rep_rows(
    scale: Scale,
    rng: random.Random,
    load_date: str,
    order_ids: Iterable[int],
    rep_ids: Iterable[int],
) -> list[list[object]]:
    """One primary + secondary sales rep assignment per order (secondary != primary)."""
    rep_list = list(rep_ids)
    if not rep_list:
        return []
    rows: list[list[object]] = []
    for order_id in order_ids:
        primary = rng.choice(rep_list)
        secondary = rng.choice(rep_list)
        if len(rep_list) > 1:
            while secondary == primary:
                secondary = rng.choice(rep_list)
        rows.append(
            [
                f"O{order_id:06d}",
                primary,
                secondary,
                load_date,
                "E2E-order-rep",
            ]
        )
    return rows


def subset_for_mode(mode: str, full_count: int, _rng: random.Random) -> range:
    if mode == "initial":
        return range(1, full_count + 1)
    sample = update_sample_size(full_count)
    return range(1, sample + 1)


def update_sample_size(full_count: int) -> int:
    return max(50, full_count // 100)


def wave_rng(base_seed: int, wave_label: str) -> random.Random:
    return random.Random(base_seed + sum(ord(character) for character in wave_label))


def hop_load_date(progress: date) -> str:
    return f"{progress.year:04d}/{progress.month:02d}/{progress.day:02d} 00:00:00.000"


def generate_initial(files_dir: Path, scale: Scale, rng: random.Random) -> str:
    wave = "initial"
    load_date = "2024-01-01"
    anchor = date(2024, 1, 1)

    customer_subset = subset_for_mode("initial", scale.customers, rng)
    product_subset = subset_for_mode("initial", scale.products, rng)
    order_subset = subset_for_mode("initial", scale.orders, rng)
    warehouse_subset = subset_for_mode("initial", scale.warehouses, rng)

    write_csv(
        files_dir / f"customer_hub_{wave}.csv",
        ["customer_id", "load_date", "record_source"],
        customer_hub_rows(scale, rng, load_date, customer_subset),
    )
    write_csv(
        files_dir / f"customer_demo_{wave}.csv",
        ["customer_id", "segment", "loyalty_tier", "demo_score", "load_date", "record_source"],
        customer_demo_rows(scale, rng, load_date, customer_subset),
    )
    write_csv(
        files_dir / f"customer_contact_{wave}.csv",
        ["customer_id", "email", "phone", "load_date", "record_source"],
        customer_contact_rows(scale, rng, load_date, customer_subset),
    )
    write_csv(
        files_dir / f"customer_address_{wave}.csv",
        ["customer_id", "address_line1", "city", "postal_code", "load_date", "record_source"],
        customer_address_rows(scale, rng, load_date, customer_subset),
    )
    write_csv(
        files_dir / f"customer_prefs_{wave}.csv",
        ["customer_id", "newsletter_opt_in", "preferred_channel", "language_code", "load_date", "record_source"],
        customer_prefs_rows(scale, rng, load_date, customer_subset),
    )
    write_csv(
        files_dir / f"product_{wave}.csv",
        ["product_id", "product_name", "category", "unit_price", "load_date", "record_source"],
        product_rows(scale, rng, load_date, product_subset),
    )
    write_csv(
        files_dir / f"warehouse_{wave}.csv",
        ["warehouse_id", "warehouse_name", "city", "region", "capacity", "load_date", "record_source"],
        warehouse_rows(scale, rng, load_date, warehouse_subset),
    )
    write_csv(
        files_dir / f"order_header_{wave}.csv",
        [
            "order_id",
            "customer_id",
            "order_date",
            "shipping_date",
            "delivery_date",
            "order_status",
            "total_amount",
            "load_date",
            "record_source",
        ],
        order_header_rows(scale, rng, load_date, order_subset, anchor),
    )
    write_csv(
        files_dir / f"order_line_{wave}.csv",
        [
            "order_id",
            "product_id",
            "line_number",
            "quantity",
            "unit_price",
            "discount_pct",
            "load_date",
            "record_source",
        ],
        order_line_rows(scale, rng, load_date, order_subset, anchor),
    )

    rep_subset = sales_rep_id_range(scale)
    write_csv(
        files_dir / f"sales_rep_{wave}.csv",
        ["rep_id", "rep_name", "region", "load_date", "record_source"],
        sales_rep_rows(scale, rng, load_date, rep_subset),
    )
    write_csv(
        files_dir / f"order_rep_{wave}.csv",
        ["order_id", "primary_rep_id", "secondary_rep_id", "load_date", "record_source"],
        order_rep_rows(scale, rng, load_date, order_subset, rep_subset),
    )

    pairs = unique_random_pairs(
        rng,
        list(warehouse_subset),
        list(product_subset),
        min(5_000, scale.warehouses * 20),
    )
    write_csv(
        files_dir / f"warehouse_product_{wave}.csv",
        ["warehouse_id", "product_id", "stock_qty", "reorder_point", "load_date", "record_source"],
        warehouse_product_rows(scale, rng, load_date, pairs),
    )
    return wave


def generate_update(files_dir: Path, scale: Scale, base_rng: random.Random, context: LoadContext) -> str:
    wave = context.wave_label
    load_date = context.progress_date.isoformat()
    satellite_rng = wave_rng(base_rng.randint(0, 2**31), wave)
    order_rng = wave_rng(base_rng.randint(0, 2**31), f"{wave}:orders")

    sample_customers = update_sample_size(scale.customers)
    sample_orders = update_sample_size(scale.orders)
    sample_products = update_sample_size(scale.products)
    sample_warehouses = update_sample_size(scale.warehouses)

    # Satellite CRM tables are upserted on update; include changed existing ids plus new ids.
    changed_customers = sample_existing_ids(satellite_rng, scale.customers, sample_customers)
    new_customers = range(scale.customers + 1, scale.customers + sample_customers + 1)
    satellite_customers = chain(changed_customers, new_customers)
    new_orders = range(scale.orders + 1, scale.orders + sample_orders + 1)
    new_products = range(scale.products + 1, scale.products + sample_products + 1)
    new_warehouses = range(scale.warehouses + 1, scale.warehouses + sample_warehouses + 1)
    max_customer_id = scale.customers + sample_customers
    max_product_id = scale.products + sample_products

    write_csv(
        files_dir / f"customer_hub_{wave}.csv",
        ["customer_id", "load_date", "record_source"],
        customer_hub_rows(scale, satellite_rng, load_date, new_customers),
    )
    write_csv(
        files_dir / f"customer_demo_{wave}.csv",
        ["customer_id", "segment", "loyalty_tier", "demo_score", "load_date", "record_source"],
        customer_demo_rows(scale, satellite_rng, load_date, satellite_customers),
    )
    write_csv(
        files_dir / f"customer_contact_{wave}.csv",
        ["customer_id", "email", "phone", "load_date", "record_source"],
        customer_contact_rows(scale, satellite_rng, load_date, satellite_customers),
    )
    write_csv(
        files_dir / f"customer_address_{wave}.csv",
        ["customer_id", "address_line1", "city", "postal_code", "load_date", "record_source"],
        customer_address_rows(scale, satellite_rng, load_date, satellite_customers),
    )
    write_csv(
        files_dir / f"customer_prefs_{wave}.csv",
        ["customer_id", "newsletter_opt_in", "preferred_channel", "language_code", "load_date", "record_source"],
        customer_prefs_rows(scale, satellite_rng, load_date, satellite_customers),
    )
    write_csv(
        files_dir / f"product_{wave}.csv",
        ["product_id", "product_name", "category", "unit_price", "load_date", "record_source"],
        product_rows(scale, satellite_rng, load_date, new_products),
    )
    write_csv(
        files_dir / f"warehouse_{wave}.csv",
        ["warehouse_id", "warehouse_name", "city", "region", "capacity", "load_date", "record_source"],
        warehouse_rows(scale, satellite_rng, load_date, new_warehouses),
    )
    write_csv(
        files_dir / f"order_header_{wave}.csv",
        [
            "order_id",
            "customer_id",
            "order_date",
            "shipping_date",
            "delivery_date",
            "order_status",
            "total_amount",
            "load_date",
            "record_source",
        ],
        order_header_rows(
            scale,
            order_rng,
            load_date,
            new_orders,
            context.progress_date,
            max_customer_id=max_customer_id,
        ),
    )
    write_csv(
        files_dir / f"order_line_{wave}.csv",
        [
            "order_id",
            "product_id",
            "line_number",
            "quantity",
            "unit_price",
            "discount_pct",
            "load_date",
            "record_source",
        ],
        order_line_rows(
            scale,
            order_rng,
            load_date,
            new_orders,
            context.progress_date,
            max_product_id=max_product_id,
        ),
    )

    # Sales rep master is loaded on initial; update waves only assign reps to new orders.
    write_csv(
        files_dir / f"sales_rep_{wave}.csv",
        ["rep_id", "rep_name", "region", "load_date", "record_source"],
        [],
    )
    write_csv(
        files_dir / f"order_rep_{wave}.csv",
        ["order_id", "primary_rep_id", "secondary_rep_id", "load_date", "record_source"],
        order_rep_rows(scale, order_rng, load_date, new_orders, sales_rep_id_range(scale)),
    )

    new_warehouse_list = list(new_warehouses)
    new_product_list = list(new_products)
    pairs = unique_random_pairs(
        order_rng,
        new_warehouse_list,
        new_product_list,
        min(500, sample_warehouses * sample_products),
    )
    write_csv(
        files_dir / f"warehouse_product_{wave}.csv",
        ["warehouse_id", "product_id", "stock_qty", "reorder_point", "load_date", "record_source"],
        warehouse_product_rows(scale, satellite_rng, load_date, pairs),
    )
    return wave


def main() -> None:
    args = parse_args()
    files_dir = args.project_home / "files"
    scale = Scale(
        customers=args.customers,
        products=args.products,
        orders=args.orders,
        warehouses=args.warehouses,
        sales_reps=args.sales_reps,
    )
    rng = random.Random(args.seed)

    if args.mode == "initial":
        wave = generate_initial(files_dir, scale, rng)
    else:
        context = read_load_context(args.project_home, args.progress_date)
        wave = generate_update(files_dir, scale, rng, context)
        (files_dir / PERIOD_LABEL_FILE).write_text(wave + "\n", encoding="utf-8")
        (files_dir / DV_LOAD_DATE_FILE).write_text(hop_load_date(context.progress_date) + "\n", encoding="utf-8")
        print(f"progress_date={context.progress_date.isoformat()}")
        print(f"period_months={context.period_months}")
        print(f"dv_load_date={hop_load_date(context.progress_date)}")

    print(f"Generated retail CSV wave '{wave}' in {files_dir}")

    # Expose wave to Hop; load pipeline uses ${RETAIL_CSV_WAVE} and is not rewritten.
    (files_dir / PERIOD_LABEL_FILE).write_text(wave + "\n", encoding="utf-8")
    work_dir = args.project_home / "work"
    work_dir.mkdir(parents=True, exist_ok=True)
    props_path = work_dir / "retail-csv-wave.properties"
    props_path.write_text(f"RETAIL_CSV_WAVE={wave}\n", encoding="utf-8")
    print(f"Wrote {props_path} (RETAIL_CSV_WAVE={wave})")


if __name__ == "__main__":
    main()