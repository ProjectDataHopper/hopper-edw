#!/usr/bin/env bash
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
# One-time migration of legacy data-vault-source Hop metadata into catalog-data.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_HOME="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
PROJECT_HOME="${REPO_HOME}/integration-tests"
CP_FILE="${REPO_HOME}/target/migrate-sources-cp.txt"

cd "${REPO_HOME}"
mvn -q compile dependency:build-classpath -Dmdep.outputFile="${CP_FILE}"
java -cp "target/classes:$(cat "${CP_FILE}")" \
  org.hopper.edw.datavault.catalog.DvLegacySourceMigrator "${PROJECT_HOME}" local-catalog