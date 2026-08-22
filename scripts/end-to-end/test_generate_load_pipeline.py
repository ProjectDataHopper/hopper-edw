#!/usr/bin/env python3
"""Tests for stable CRM load pipeline generation (variable wave filenames)."""
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

import importlib.util
import sys
import unittest
from pathlib import Path

_MODULE_PATH = Path(__file__).resolve().parent / "generate-load-pipeline.py"
_spec = importlib.util.spec_from_file_location("generate_load_pipeline", _MODULE_PATH)
_module = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
sys.modules[_spec.name] = _module
_spec.loader.exec_module(_module)


class GenerateLoadPipelineTest(unittest.TestCase):
    def test_stable_pipeline_uses_wave_variable(self) -> None:
        pipeline = _module.build_pipeline()
        self.assertIn("${RETAIL_CSV_WAVE}", pipeline)
        self.assertIn(
            "${PROJECT_HOME}/files/customer_hub_${RETAIL_CSV_WAVE}.csv", pipeline
        )
        # Customer satellites always upsert; other tables append (truncate=N).
        self.assertIn("<type>InsertUpdate</type>", pipeline)
        self.assertIn("<name>customer_demo_out</name>", pipeline)
        self.assertIn("<truncate>N</truncate>", pipeline)
        self.assertNotIn("<truncate>Y</truncate>", pipeline)
        # No baked-in wave labels.
        self.assertNotIn("customer_hub_initial.csv", pipeline)
        self.assertNotIn("customer_hub_2024-01.csv", pipeline)


if __name__ == "__main__":
    unittest.main()
