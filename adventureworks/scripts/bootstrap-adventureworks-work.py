#!/usr/bin/env python3
"""Prepare gitignored work/ tree for the AdventureWorks sample."""
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
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PROJECT_HOME = REPO_ROOT / "adventureworks"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-home", type=Path, default=DEFAULT_PROJECT_HOME)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    project_home = args.project_home.expanduser().resolve()
    work = project_home / "work"
    for rel in (
        "edw-catalog",
        "reports",
        "execution-maps",
        "metrics",
        "documentation",
    ):
        (work / rel).mkdir(parents=True, exist_ok=True)
        print(f"Ensured {work / rel}")


if __name__ == "__main__":
    main()
