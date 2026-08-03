<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Agent Guide for hop-data-vault

This file is for **AI coding assistants** and third-party contributors. Read it before changing Java, metadata, workflows, Docker test runners, or integration fixtures.

**Product docs** live under [`docs/`](docs/) (start at [`docs/README.md`](docs/README.md) and [`docs/feature-overview.md`](docs/feature-overview.md)). Do not restate them here.

## What this project is

**hop-datavault** is an Apache Hop plugin for **Data Vault 2.0**, **Business Vault**, and **dimensional** modeling: visual models (`.hsm` / `.hdv` / `.hbv` / `.hdm`), catalog-first sources (including multi-table composite feeds), model-driven DDL and load pipelines, lineage, schema validation gates, data quality, execution maps (`.hem`), and optional AI help.

- Maven artifact: `org.apache.hop:hop-datavault` (see `pom.xml` for current version)
- Install layout: `plugins/misc/datavault/` (from the assembly zip under `target/`)
- Upstream repo: https://github.com/mattcasters/hop-data-vault

## Hard version pins

Do **not** bump these without an explicit human decision.

| Constraint | Value |
|------------|--------|
| **JDK / Java** | **21** (`maven.compiler.source` / `target` / `release`) |
| **Apache Hop** | **2.19.0** required (`hop.version` is **2.19.0-SNAPSHOT** until GA) |
| **Lombok** | **1.18.42** (aligned with Hop) |
| **Base Docker image** | `apache/hop:2.19.0-SNAPSHOT` (or `2.19.0` after GA) → local `docker-hop:latest` |

Hop compile dependencies (`hop-core`, `hop-engine`, `hop-ui`) are **`provided`**. The plugin must match the Hop runtime (local install or Docker image).

Hop **2.19.0** is required for OPS database execution info, BINARY hash key sorting ([apache/hop#7346](https://github.com/apache/hop/issues/7346)), and Marketplace install. Do not assume APIs beyond the **2.19.0** line without a deliberate pin bump.

## Apache Hop source and API

This plugin is built **against** Hop; it does not vendor Hop sources.

### Maven (compile classpath)

| Artifact | Role |
|----------|------|
| `org.apache.hop:hop-core` (`${hop.version}`) | Core utilities, metadata interfaces, types, **HopVfs** |
| `org.apache.hop:hop-engine` (`${hop.version}`) | Pipelines, workflows, execution |
| `org.apache.hop:hop-ui` (`${hop.version}`) | Hop GUI, dialogs, file types, canvas |

Many `hop-transform-*` / `hop-action-*` modules are also on the compile classpath so generated pipelines can reference Meta classes. They are **not** all packaged into this plugin jar (Hop loads plugins dynamically). See `src/main/resources/dependencies.xml` and `src/assembly/assembly.xml`.

### Reading Hop source

Prefer real Hop sources over inventing package or method names:

1. **Upstream:** https://github.com/apache/hop — use a tag/branch aligned with **2.19.0** / **2.19.0-SNAPSHOT** when inspecting APIs for this pin.
2. **Local Maven cache:** `~/.m2/repository/org/apache/hop/.../${hop.version}/` (binary and optional `-sources` jars).
3. **Local clone (maintainers):** a sibling Hop tree (e.g. `../hop`) is useful for navigation when available. Agents with filesystem access should read that tree instead of guessing APIs.

Useful Hop packages:

- `org.apache.hop.core.*` — `HopVfs`, `Const`, row meta, logging
- `org.apache.hop.metadata.*` — `IHopMetadata`, `@HopMetadata`
- `org.apache.hop.pipeline.*` / `org.apache.hop.workflow.*`
- `org.apache.hop.ui.core.*` / `org.apache.hop.ui.hopgui.*` — dialogs, file types, GUI plugins

**Plugin discovery:** the build generates a **Jandex** index (`META-INF/jandex.idx`) so Hop can find `@HopMetadata`, `@GuiPlugin`, `@HopFileTypePlugin`, transforms, and actions. Do not remove or bypass the Jandex Maven plugin.

Do **not** “fix” Hop core inside this repo. Core Hop changes belong in **apache/hop**, then a deliberate `hop.version` bump here.

## Repository map

| Path | Role |
|------|------|
| `src/main/java/org/apache/hop/datavault/` | Models, workflow actions, GUI, lineage, metrics, AI, transforms |
| `src/main/java/org/apache/hop/catalog/` | Data catalog (record definitions, validation, GUI) |
| `src/main/java/org/apache/hop/quality/` | Data quality rules, gates, profiling |
| `src/main/resources/` | i18n `messages_*.properties`, SVG icons, `version.xml`, `dependencies.xml`, AI prompts |
| `src/test/java/` | JUnit 5 unit tests |
| `integration-tests/` | Golden-dataset regression suites (Docker) |
| `retail-example/` | End-to-end tutorial (CSV → DV → BV → DM) |
| `scripts/` | Shared Docker runners and compose files |
| `docs/` | User-facing documentation |

**Model file types** (do not invent new extensions lightly):

| Extension | Meaning |
|-----------|---------|
| `.hsm` | Source model (tables, FKs, multi-table queries) |
| `.hdv` | Raw Data Vault model |
| `.hbv` | Business Vault model |
| `.hdm` | Dimensional model |
| `.hem` | Execution map |

## Build and unit tests

Requirements: **JDK 21**, **Maven 3.x**.

```bash
mvn clean package          # unit tests + jar + plugin zip
mvn test                   # unit tests only
mvn spotless:apply         # format before commit (Google Java Format)
mvn spotless:check         # CI-style format check
```

Artifacts:

- `target/hop-datavault-*-SNAPSHOT.jar`
- `target/hop-datavault-*-SNAPSHOT.zip` — unzip into `$HOP_HOME` (layout: `plugins/misc/datavault/`)

Notes:

- Surefire sets `HOP_AUDIT_FOLDER=/tmp/hop-data-vault-audit` — do not commit audit output.
- Unit tests alone are **not** enough for DDL, SQL dialect, collation, Unicode, or load-path changes (see below).
- After packaging, Docker-based tests need a Hop image that includes the plugin. Runners call `ensure_hop_image` (in `scripts/hop-docker-lib.sh`): build when `docker-hop:latest` is missing, or when `target/hop-datavault-*.zip` is newer than the image. Force rebuild with `./scripts/rebuild-hop.sh`.

## Integration tests (multi-database)

DDL generation, collation remediation, Unicode/VARCHAR handling, dialect-specific SQL, and load pipelines differ by engine. **A green Postgres-only run is not sufficient** for database-sensitive work.

### Full matrix (required for DB / SQL / DDL / load changes)

```bash
cd integration-tests
./run-tests-all-databases.sh                 # postgres mysql singlestore sqlserver
./run-tests-all-databases.sh postgres        # one engine
./run-tests-all-databases.sh postgres mysql  # several engines
```

Facts agents must not miss:

- Script: `integration-tests/run-tests-all-databases.sh`
- Compose stacks: `scripts/docker/compose.<engine>.yml`
- Shared helpers: `scripts/hop-docker-lib.sh`
- **Docker Compose v2** required; **no local Hop install** needed for CLI runs (image = `apache/hop` 2.19.x + this plugin + JDBC drivers)
- **Python 3** (stdlib only) prints the metrics overview table after a run
- Backs up and restores `integration-tests/metadata/rdbms/CRM.json` and `Vault.json` so local GUI connections are not left swapped
- SingleStore needs substantial RAM (~**6 GB** recommended for the dev image)
- Multi-satellite Business Vault golden suites currently run only when `DB_TYPE` is unset or `postgres` (other engines skip them in `tests/run-tests.hwf` — not a false “failure to run”)
- Postgres + SQL Server exercise French source collation vs UTF-8 vault remediation (see `integration-tests/PROJECT.md`)

Deeper reference: [`integration-tests/PROJECT.md`](integration-tests/PROJECT.md), [`integration-tests/SCRIPTS.md`](integration-tests/SCRIPTS.md).

### Fast local loop (Postgres only)

Useful while iterating; **not** a substitute for the full matrix on dialect/DDL work:

```bash
./scripts/run-postgres.sh up
cd integration-tests && ./run-tests.sh
# or from repo root:
./scripts/run-hop.sh integration-tests tests/run-tests.hwf
```

Local Postgres: port **54320**, user/db/password `test` / `test` / `test`.

### Suggested verification ladder

1. `mvn test` or `mvn clean package` after Java changes  
2. `integration-tests/run-tests.sh` for a quick Postgres smoke while iterating  
3. **`./run-tests-all-databases.sh` before treating DB/SQL/DDL/load changes as done**  
4. Optionally run `retail-example` workflows for end-to-end / tutorial-adjacent changes  

## Coding conventions

These are easy for agents to violate:

1. **GUI parity** — Do not add user-facing capability that exists only as a file, API, or config with no Hop GUI surface (dialog, toolbar, perspective, metadata editor, action/transform UI). Features must be operable in Hop GUI.
2. **Lombok** — Prefer `@Getter` / `@Setter` / related annotations over hand-written boilerplate. Keep the Lombok version aligned with Hop.
3. **I/O via Hop VFS** — Prefer `HopVfs.getInputStream()` / `HopVfs.getOutputStream()` (and related Hop VFS APIs) over raw `java.io.File` / NIO unless there is no alternative.
4. **i18n** — UI strings belong in package `messages/messages_*.properties` and must be **properly escaped** for Java Properties (`=`, `:`, spaces, newlines, unicode). Use the existing BaseMessages / key patterns for that package.
5. **ASF license headers** — Spotless injects the standard header on Java; Apache RAT enforces headers on most source/text. New `.java` / `.sh` (and many other text files) need headers. `docs/`, many `integration-tests/**` fixtures, models (`.hdv`/`.hbv`/`.hdm`), and JSON are largely excluded (see `pom.xml` RAT config). Header template: `asf-header.txt`.
6. **Formatting** — Google Java Format via Spotless (`mvn spotless:apply` before commit).
7. **Package placement** — Put new code under `org.apache.hop.datavault`, `.catalog`, or `.quality`, following existing layering (`metadata`, `hopgui`, `workflow/actions`, `transform`, services). Mirror Hop patterns: `*Meta` + dialog/editor; transforms often `*Meta` / `*Data` / `*`; actions under `.../workflow/actions/...`.
8. **Plugin isolation** — Treat classloaders carefully. The assembly deliberately bundles selected third-party libs (ELK, CommonMark, Iceberg, some Hop actions). Prefer `provided` for Hop itself.

## Anti-patterns (do not)

- Target Java 11/17 or a Hop version other than **2.19.0** without human approval.
- Ship SQL/DDL/dialect/collation/load changes with only unit tests (or only Postgres) green.
- Edit golden CSVs under `integration-tests/datasets/` (or other expected outputs) without understanding the intentional behavior change and re-running the relevant suites.
- Leave Docker-swapped `CRM.json` / `Vault.json` dirty after manual experiments.
- Add large binary or docs churn unless the task asks for it.
- Patch or reimplement Hop core APIs in this repository.
- Invent new model file extensions or parallel “file-only” configuration systems that bypass the GUI.

## Where to read next

| Need | Document |
|------|----------|
| Capabilities and maturity | [`docs/feature-overview.md`](docs/feature-overview.md) |
| Tutorial / retail flow | [`docs/getting-started-retail.adoc`](docs/getting-started-retail.adoc), [`retail-example/README.md`](retail-example/README.md) |
| Integration fixtures | [`docs/getting-started-integration-tests.adoc`](docs/getting-started-integration-tests.adoc), [`integration-tests/PROJECT.md`](integration-tests/PROJECT.md) |
| Shared scripts | [`scripts/README.md`](scripts/README.md), [`integration-tests/SCRIPTS.md`](integration-tests/SCRIPTS.md) |
| Docs index | [`docs/README.md`](docs/README.md) |
| Releases | [`CHANGELOG.md`](CHANGELOG.md) |
| User README | [`README.md`](README.md) |

## License

Apache License 2.0. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
