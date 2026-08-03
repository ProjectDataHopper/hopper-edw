# Project scripts

Shell scripts in `project/` run Apache Hop workflows and supporting services in Docker. Shared helpers live in [`docker/hop-docker-lib.sh`](docker/hop-docker-lib.sh).

All scripts expect to be run from the `project/` directory unless noted otherwise.

## Test runners

### `run-postgres.sh`

Starts a local PostgreSQL 16 container for `run-tests.sh`.

| Command | What it does |
|---------|----------------|
| `up` (default) | Start `hop-data-vault-postgres-local` on port **54320** (`test` / `test` / `test`) and wait until healthy |
| `down` | Stop the container (keeps the data volume) |
| `reset` | Stop the container and remove the data volume |
| `status` | Show compose status and verify the port accepts connections |
| `logs` | Follow database logs |

Compose file: [`docker/compose.postgres-local.yml`](docker/compose.postgres-local.yml).

The `test` user is a superuser on the `test` database, which is enough for all DV test DDL and DML.

### `run-tests.sh`

Runs Hop test workflows in a short-lived container using **host networking**, so CRM and Vault connections reach `localhost:54320`.

**Prerequisite:** `./run-postgres.sh up`

```bash
./run-tests.sh                                          # full suite (tests/run-tests.hwf)
./run-tests.sh tests/basic/update-vault1.hwf            # one workflow
COLLECT_METRICS=N ./run-tests.sh                        # skip metrics overview
```

- Docker image: `docker-hop:latest` (built on first use from [`scripts/docker/Dockerfile`](../scripts/docker/Dockerfile); also rebuilt when `target/hop-datavault-*.zip` is newer than the image)
- Compose file: [`scripts/docker/compose.hop.yml`](../scripts/docker/compose.hop.yml)
- Environment: [`environments/local-docker-postgres.json`](environments/local-docker-postgres.json) — supplies `${DB_HOST}`, `${DB_PORT}`, `${DB_USER}`, `${DB_PASSWORD}`, `${DB_NAME}` to [`metadata/rdbms/CRM.json`](metadata/rdbms/CRM.json) and [`metadata/rdbms/Vault.json`](metadata/rdbms/Vault.json)
- Metrics: `metrics/local/` (gitignored)

Override the environment file if needed:

```bash
HOP_ENVIRONMENT_CONFIG_FILE_NAME_PATHS=/project/environments/local-postgres.json ./run-tests.sh
```

### `run-tests-all-databases.sh`

Runs the full test suite against containerised databases — no host database required. Each engine gets its own isolated compose stack (database + Hop on a Docker network).

```bash
./run-tests-all-databases.sh                  # all: postgres mysql singlestore sqlserver
./run-tests-all-databases.sh postgres         # one engine (PostgreSQL + French ICU collation suite)
./run-tests-all-databases.sh postgres mysql   # several engines
./run-tests-all-databases.sh sqlserver        # SQL Server + Unicode/collation suite
# Invalid names (e.g. postgresql) exit immediately with usage help.
```

- PostgreSQL uses port **54321** and hostname `db` ([`environments/docker-postgres.json`](environments/docker-postgres.json))
- SQL Server uses port **1433**, SA password from the environment file, and `tests/run-tests-sqlserver.hwf` (shared suite + Unicode/collation scenario). The `test` database is created with `Latin1_General_100_CI_AS_SC_UTF8`.
- PostgreSQL collation uses ICU name `fr-FR-x-icu` on CRM source columns; vault targets keep the database UTF-8 encoding
- Swaps RDBMS profiles from `metadata/rdbms/profiles/<engine>/` at container start, then restores your local CRM/Vault metadata afterward
- Metrics: `metrics/<engine>/`

## Other scripts

### `run-svg.sh`

Exports DV/BV/pipeline canvases to SVG via `hop svg` in Docker. Uses [`docker/compose.svg.yml`](docker/compose.svg.yml) and maps host paths to `/workspace/...`.

### `run-iceberg-stack.sh`

Manages the Iceberg test stack (MinIO, Iceberg REST, PostgreSQL seed DB).

| Command | What it does |
|---------|----------------|
| `up` | Start infrastructure services |
| `verify` | Check stack health |
| `test` | Run Iceberg test workflow |
| `down` | Tear down services |
| `logs` | Follow service logs |

## How the pieces fit together

```text
run-postgres.sh up
       │
       ▼
localhost:54320  ◄──── run-tests.sh ────►  Hop container (host network)
                      loads env vars         resolves CRM/Vault from
                      from local-docker-     metadata/rdbms/*.json
                      postgres.json
```

`run-tests.sh` and `run-tests-all-databases.sh` are independent:

| Script | Database | Port | Hop networking |
|--------|----------|------|----------------|
| `run-tests.sh` | Local Docker Postgres (`run-postgres.sh`) | 54320 | Host |
| `run-tests-all-databases.sh` | Per-engine compose `db` service | 54321 (postgres) | Bridge |

## Shared library (`scripts/hop-docker-lib.sh`)

Used by the test and SVG scripts. Provides:

- Hop image build (`ensure_hop_image`) — creates `docker-hop:latest` when missing, and rebuilds when a host plugin package (`target/hop-datavault-*.zip` from `mvn package`) is newer than the image. Force a rebuild anytime with `./scripts/rebuild-hop.sh`. Set `HOP_IMAGE_SKIP_FRESHNESS=1` to only build when the image is missing.
- Workflow path translation for containers
- Local Postgres readiness checks (`local_postgres_ready`, `wait_for_local_postgres`, `require_local_postgres`)
- Metrics and vault-catalog ownership fix-up after Docker runs (Hop container runs as root)