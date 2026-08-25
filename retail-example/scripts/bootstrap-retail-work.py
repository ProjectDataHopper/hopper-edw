#!/usr/bin/env python3
"""Prepare gitignored work/ tree for the retail example.

Creates work/{edw-catalog,reports,execution-maps,metrics}, writes E2E catalog
source contracts plus COMPOSITE/JSON/PIPELINE model feeds under work/edw-catalog,
and copies the schema-gate baseline fixture (v1.0.0) into
work/edw-catalog/catalog-versions when missing.
"""
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

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PROJECT_HOME = REPO_ROOT / "retail-example"
SCRIPT_DIR = Path(__file__).resolve().parent


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-home", type=Path, default=DEFAULT_PROJECT_HOME)
    parser.add_argument(
        "--force-baseline",
        action="store_true",
        help="Overwrite work/edw-catalog/catalog-versions from fixtures even if present",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    project_home = args.project_home.expanduser().resolve()
    work = project_home / "work"
    edw_catalog = work / "edw-catalog"

    for rel in ("edw-catalog", "reports", "execution-maps", "metrics"):
        (work / rel).mkdir(parents=True, exist_ok=True)
        print(f"Ensured {work / rel}")

    # E2E source contracts plus model feeds from the schema-gate baseline snapshot.
    gen = SCRIPT_DIR / "generate-catalog-sources.py"
    subprocess.check_call(
        [sys.executable, str(gen), "--project-home", str(project_home)],
        cwd=str(project_home),
    )

    fixture = project_home / "fixtures" / "schema-gate-baseline"
    target_versions = edw_catalog / "catalog-versions"
    if not fixture.is_dir():
        print(f"WARNING: missing schema-gate baseline fixture at {fixture}", file=sys.stderr)
        return

    if target_versions.exists() and not args.force_baseline:
        print(f"Keeping existing catalog versions at {target_versions}")
    else:
        if target_versions.exists():
            shutil.rmtree(target_versions)
        shutil.copytree(fixture, target_versions)
        print(f"Copied schema-gate baseline -> {target_versions}")


if __name__ == "__main__":
    main()
