#!/usr/bin/env python3
"""Tests for retail CSV data generation, including PK-unique warehouse_product pairs."""
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
from __future__ import annotations

import csv
import importlib.util
import random
import sys
import tempfile
import unittest
from pathlib import Path

_MODULE_PATH = Path(__file__).resolve().parent / "generate-retail-data.py"
_spec = importlib.util.spec_from_file_location("generate_retail_data", _MODULE_PATH)
_module = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
sys.modules[_spec.name] = _module
_spec.loader.exec_module(_module)


class SampleExistingIdsTest(unittest.TestCase):
    def test_samples_within_existing_range(self) -> None:
        rng = random.Random(11)
        ids = _module.sample_existing_ids(rng, existing_count=100, sample_size=20)
        self.assertEqual(20, len(ids))
        self.assertTrue(all(1 <= customer_id <= 100 for customer_id in ids))
        self.assertEqual(len(ids), len(set(ids)))


class UniqueRandomPairsTest(unittest.TestCase):
    def test_returns_unique_pairs_up_to_requested_count(self) -> None:
        rng = random.Random(42)
        pairs = _module.unique_random_pairs(rng, list(range(1, 6)), list(range(1, 11)), 20)
        self.assertEqual(20, len(pairs))
        self.assertEqual(20, len(set(pairs)))

    def test_caps_at_cartesian_product_size(self) -> None:
        rng = random.Random(7)
        pairs = _module.unique_random_pairs(rng, [1, 2], [10, 20], 100)
        self.assertEqual(4, len(pairs))
        self.assertEqual(4, len(set(pairs)))

    def test_empty_inputs_return_empty_list(self) -> None:
        rng = random.Random(1)
        self.assertEqual([], _module.unique_random_pairs(rng, [], [1, 2], 5))
        self.assertEqual([], _module.unique_random_pairs(rng, [1], [], 5))
        self.assertEqual([], _module.unique_random_pairs(rng, [1], [2], 0))


class UpdateWaveCustomerIdsTest(unittest.TestCase):
    def test_update_wave_uses_new_customer_ids_for_hub_and_mixed_ids_for_satellites(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project_home = Path(tmp)
            files_dir = project_home / "files"
            files_dir.mkdir()
            scale = _module.Scale(customers=100, products=50, orders=200, warehouses=10)
            context = _module.LoadContext(
                progress_date=_module.date(2024, 2, 1),
                period_months=1,
                wave_label="2024-02",
            )
            _module.generate_update(files_dir, scale, random.Random(42), context)

            with (files_dir / "customer_hub_2024-02.csv").open(encoding="utf-8") as handle:
                hub_ids = {int(row["customer_id"]) for row in csv.DictReader(handle)}
            with (files_dir / "customer_demo_2024-02.csv").open(encoding="utf-8") as handle:
                demo_ids = {int(row["customer_id"]) for row in csv.DictReader(handle)}

            self.assertTrue(all(customer_id > scale.customers for customer_id in hub_ids))
            self.assertTrue(any(1 <= customer_id <= scale.customers for customer_id in demo_ids))
            self.assertTrue(any(customer_id > scale.customers for customer_id in demo_ids))
            self.assertEqual(set(), hub_ids & {customer_id for customer_id in demo_ids if customer_id <= scale.customers})


class WarehouseProductCsvTest(unittest.TestCase):
    def test_initial_wave_csv_has_unique_primary_keys(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            files_dir = Path(tmp)
            scale = _module.Scale(customers=100, products=200, orders=500, warehouses=10)
            rng = random.Random(99)
            _module.generate_initial(files_dir, scale, rng)

            with (files_dir / "warehouse_product_initial.csv").open(encoding="utf-8") as handle:
                rows = list(csv.DictReader(handle))
            keys = [(row["warehouse_id"], row["product_id"]) for row in rows]
            self.assertGreater(len(keys), 0)
            self.assertEqual(len(keys), len(set(keys)), "duplicate warehouse_id/product_id in CSV")


if __name__ == "__main__":
    unittest.main()