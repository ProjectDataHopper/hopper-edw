#!/bin/sh
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
set -eu

. /opt/hop-datavault/entrypoint-init.sh

DB_TYPE="${DB_TYPE:-postgres}"
HOP_PROJECT_FOLDER="${HOP_PROJECT_FOLDER:-/workspace/integration-tests}"
PROFILE_DIR="${HOP_PROJECT_FOLDER}/metadata/rdbms/profiles/${DB_TYPE}"

if [ ! -d "${PROFILE_DIR}" ]; then
  echo "Missing RDBMS profile directory: ${PROFILE_DIR}" >&2
  exit 1
fi

rm -f "${HOP_PROJECT_FOLDER}/metadata/rdbms/CRM.json" "${HOP_PROJECT_FOLDER}/metadata/rdbms/Vault.json"
cp "${PROFILE_DIR}/CRM.json" "${HOP_PROJECT_FOLDER}/metadata/rdbms/CRM.json"
cp "${PROFILE_DIR}/Vault.json" "${HOP_PROJECT_FOLDER}/metadata/rdbms/Vault.json"

mkdir -p "${HOP_PROJECT_FOLDER}/metrics/${DB_TYPE}" "${HOP_PROJECT_FOLDER}/vault-catalog"

if [ -n "${HOST_UID:-}" ] && [ -n "${HOST_GID:-}" ]; then
  chown "${HOST_UID}:${HOST_GID}" "${HOP_PROJECT_FOLDER}/metadata/rdbms/CRM.json" "${HOP_PROJECT_FOLDER}/metadata/rdbms/Vault.json"
  chown "${HOST_UID}:${HOST_GID}" "${HOP_PROJECT_FOLDER}/metrics/${DB_TYPE}"
  chown -R "${HOST_UID}:${HOST_GID}" "${HOP_PROJECT_FOLDER}/vault-catalog"
fi

echo "Activated CRM/Vault RDBMS profiles for ${DB_TYPE} in ${HOP_PROJECT_FOLDER}"