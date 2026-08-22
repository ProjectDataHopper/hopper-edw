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
# Smoke test: publish vault1.hdv target tables to the vault-catalog file catalog.
# Exercises the same catalog path as ActionDataVaultUpdate with "Update data catalog" enabled.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_HOME="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
PROJECT_HOME="${REPO_HOME}/integration-tests"
CP_FILE="${REPO_HOME}/target/catalog-publish-smoke-cp.txt"

echo "== Data Catalog publish smoke test (vault1) =="
echo "Project home: ${PROJECT_HOME}"

cd "${REPO_HOME}"
mvn -q compile dependency:build-classpath -Dmdep.outputFile="${CP_FILE}"
java -cp "target/classes:$(cat "${CP_FILE}")" \
  org.apache.hop.datavault.catalog.smoke.DvCatalogPublishSmoke "${PROJECT_HOME}"

echo "Publish smoke test completed successfully."