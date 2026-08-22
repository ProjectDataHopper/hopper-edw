#!/usr/bin/env python3
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
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""Seed Iceberg tables for hop-data-vault local integration tests."""

from __future__ import annotations

import fnmatch
import glob
import os
import sys
import time

import pyarrow.parquet as pq
from pyiceberg.catalog import load_catalog
from pyiceberg.exceptions import NoSuchTableError


def env(name: str, default: str | None = None) -> str:
    value = os.environ.get(name, default)
    if value is None or value == "":
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def as_bool(name: str, default: bool = False) -> bool:
    raw = os.environ.get(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "y"}


def wait_for_catalog(catalog, attempts: int = 60, delay_seconds: float = 2.0) -> None:
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            catalog.list_namespaces()
            print("Iceberg REST catalog is ready")
            return
        except Exception as exc:  # noqa: BLE001 - retry until REST catalog is up
            last_error = exc
            print(f"Waiting for Iceberg REST catalog ({attempt}/{attempts}): {exc}")
            time.sleep(delay_seconds)
    raise RuntimeError("Iceberg REST catalog did not become ready") from last_error


def namespace_exists(catalog, namespace: str) -> bool:
    return any(item == (namespace,) for item in catalog.list_namespaces())


def main() -> int:
    catalog_uri = env("ICEBERG_CATALOG_URI")
    warehouse = env("ICEBERG_WAREHOUSE", "s3://warehouse/")
    s3_endpoint = env("S3_ENDPOINT")
    s3_access_key = env("S3_ACCESS_KEY", "admin")
    s3_secret_key = env("S3_SECRET_KEY", "password")
    parquet_dir = env("PARQUET_SOURCE_DIR", "/data/parquet")
    parquet_include_mask = os.environ.get("PARQUET_INCLUDE_MASK", "*.parquet")
    namespace = env("ICEBERG_NAMESPACE", "crm")
    table_name = env("ICEBERG_TABLE", "customer")
    reset = as_bool("SEED_RESET", True)

    catalog = load_catalog(
        "hop-data-vault",
        **{
            "type": "rest",
            "uri": catalog_uri,
            "warehouse": warehouse,
            "s3.endpoint": s3_endpoint,
            "s3.access-key-id": s3_access_key,
            "s3.secret-access-key": s3_secret_key,
            "s3.region": "us-east-1",
            "s3.force-virtual-addressing": "false",
        },
    )

    wait_for_catalog(catalog)

    parquet_files = sorted(
        path
        for path in glob.glob(os.path.join(parquet_dir, "*.parquet"))
        if fnmatch.fnmatch(os.path.basename(path), parquet_include_mask)
    )
    if not parquet_files:
        print(
            f"No parquet files matching {parquet_include_mask!r} found in {parquet_dir}",
            file=sys.stderr,
        )
        return 1

    identifier = f"{namespace}.{table_name}"

    if reset:
        try:
            catalog.drop_table(identifier)
            print(f"Dropped existing table {identifier}")
        except NoSuchTableError:
            pass

    if not namespace_exists(catalog, namespace):
        catalog.create_namespace(namespace)
        print(f"Created namespace {namespace}")

    first_batch = pq.read_table(parquet_files[0])
    table = catalog.create_table(identifier, schema=first_batch.schema)
    print(f"Created table {identifier}")

    total_rows = 0
    for parquet_file in parquet_files:
        batch = pq.read_table(parquet_file)
        table.append(batch)
        total_rows += batch.num_rows
        print(f"Appended {parquet_file} ({batch.num_rows} rows)")

    loaded_rows = len(table.scan().to_arrow())
    print(f"Verified table {identifier}: {loaded_rows} rows readable via Iceberg scan")

    if loaded_rows != total_rows:
        print(
            f"Row count mismatch after seeding: appended={total_rows}, scanned={loaded_rows}",
            file=sys.stderr,
        )
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())