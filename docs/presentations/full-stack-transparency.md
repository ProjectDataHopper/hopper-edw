<!--
Copyright 2026 i-Bridge bv

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Full-Stack Transparency: If You Can't See It, You Can't Own It

**A 20-minute talk + 5-minute live demo on Enterprise Data Warehouse reality, architectural transparency, and the Data Hopper EDW solution.**  
Interactive Presentation: [full-stack-transparency.html](full-stack-transparency.html)  
Issue Reference: [Issue #190](https://github.com/ProjectDataHopper/hopper-edw/issues/190)

---

## Executive Summary & Narrative Arc

Most Enterprise Data Warehouse (EDW) presentations start with dry modeling theory: Inmon vs. Kimball, 3NF vs. Data Vault 2.0, or hub hash calculations. This talk deliberately discards that playbook.

Instead, the presentation follows a dramatic 4-act narrative:
1. **Act 1: The Staccato Firehose (~6 min)** — A rapid-fire, high-voltage onslaught of 10 real-world nightmares every data engineer endures: 2 AM schema drift, silent stalls, 14-tab context switching, read-only catalog islands, and brittle toolchain glue.
2. **Act 2: The Core Thesis (~4 min)** — Why "best-of-breed" toolchains broke the data engineer. The 5 pillars of unified object knowledge, the law of separating observation from mutation, and the absolute imperative of *editability* at the point of discovery.
3. **Act 3: Solution Tour (~5 min)** — A visual walkthrough with actual screenshots of how Data Hopper EDW solves these problems natively inside Apache Hop.
4. **Act 4: 5-Minute Live Demo (~5 min)** — A precise, timed live demonstration on the `retail-example` project tracing a single entity through every layer of the stack down to its generated pipelines and runtime telemetry.

---

## The Three Golden Axioms

Say these three lines out loud at key transitions. They anchor the entire message:

1. **"If you can't see it, you can't own it. Every layer, every object, every kind of metadata — one click away."**
2. **"Best tool for each job gives you the best parts. The engineer still needs the whole picture."**
3. **"Looking, deciding, and loading are three different acts. A deliberate write is not a side effect of looking."**

---

## Presentation Structure & Speaker Script

### Act 1: The Staccato Firehose (Slides 1–13, ~6:00)

*Pacing: Fast, energetic, visceral. Click rapidly through the 10 nightmares or let the automated Firehose Mode pulse on screen.*

#### Slide 1: Title — Full-Stack Transparency (0:30)
- **On screen:** Full-Stack Transparency: Why large enterprise data warehouses break down, and how to own every layer from source to dimensional mart.
- **Spoken:** *"Welcome everyone. Today we are talking about building enterprise data warehouses in the real world. Not the textbook fantasy where schemas never change and pipelines finish in 3 minutes. In the real world, enterprise data stacks are chaotic, distributed, and fragile. And the core principle of this talk is simple: If you can't see it, you can't own it."*

#### Slide 2: Act 1 Prelude — The EDW Reality (0:40)
- **On screen:** The EDW Staccato Firehose. Fragility, Blindness, Fragmentation.
- **Spoken:** *"I am not going to begin with academic definitions of Data Vault or 3NF. Instead, I want to give you a staccato firehose of the actual misery we live with when managing enterprise data stacks. Watch these 10 real-world scenarios. Tell me if any of these feel uncomfortably familiar."*

#### Slide 3: Firehose #01 — The 02:14 AM Schema Drift Ambush (0:35)
- **On screen:** *"Source CRM migrated to v3.2. Column customer_tier widened from VARCHAR(10) to VARCHAR(50). Nightly pipeline crashed with 1.8M half-loaded rows."* (Postgres Error 22001)
- **Spoken:** *"Scenario one: It's 2 AM. An upstream team deploys a minor release. They don't file a ticket with data engineering. A column widens by 40 characters. At 2:14 AM, your copy command throws an overflow error. Half the tables are loaded, half are corrupt. Morning reports are blocked. Why? Because detection happened by failure."*

#### Slide 4: Firehose #02 — Silence Is Not a Status (0:35)
- **On screen:** *"The batch load has been running for 4 hours and 18 minutes. Is it deadlocked? Is it processing 100M rows? Or did the remote worker freeze?"*
- **Spoken:** *"Scenario two: You trigger a pipeline. It runs on a remote agent. Four hours later, the spinner is still turning. Is it deadlocked on a Postgres lock? Is it quietly reading 100 million rows? Or did the JVM thread stall? Silence is not a status! Staring at a spinning circle while your executive SLA ticks away is pure anxiety."*

#### Slide 5: Firehose #03 — The 14-Tab Context Labyrinth (0:35)
- **On screen:** 24 tabs open: Modeler, Data Catalog, Lineage UI, Airflow, dbt Cloud, DBeaver, Great Expectations, 5 Slack channels.
- **Spoken:** *"Scenario three: A stakeholder asks why inventory turned negative in Bavaria. To answer, you open 14 browser tabs across 7 disconnected products. You are forced to act as a human network router, manually stitching together pieces of metadata from different tools. 35% of engineering velocity is wasted on toolchain context switching."*

#### Slide 6: Firehose #04 — The Read-Only Island Trap (0:35)
- **On screen:** Look, But Don't Touch: *"Our fancy data catalog spotted 12,000 orphan records and flagged the lineage node in bright red! Can you fix it here? Absolutely not."*
- **Spoken:** *"Scenario four: Read-only islands. Modern catalogs and lineage products are spectators. They show you a beautiful graph, and turn a box bright red when data is broken. Wonderful! But can you edit anything from that screen? No. You have to open another IDE, hunt for the repository, trace the job, and deploy. Seeing and fixing happen in two completely different worlds."*

#### Slide 7: Firehose #05 — The Triple Drift Schism (0:35)
- **On screen:** Model v4.0 (Aspirational) vs Database DDL v3.2 (Patched) vs ETL Code v2.5 (Hardcoded SELECTs).
- **Spoken:** *"Scenario five: The triple drift schism. The ERD diagram in your modeling tool was drawn in Q1. The DBA patched the live table DDL in Q2. The ETL job was patched last Tuesday. None of the three match. When someone asks 'What is our customer schema?', there are three conflicting answers."*

#### Slide 8: Firehose #06 — The Special Case Graveyard (0:35)
- **On screen:** Unmanaged bash scripts, Python cronjobs, and secret SQL files bypassing the model.
- **Spoken:** *"Scenario six: Every model-driven framework looks great on an employee table. Then reality hits: streaming Kafka feeds, semi-structured JSON payloads, files without headers or data types. Because the tool can't handle edge cases natively at the source, engineers write secret bash scripts and unmanaged Python side-pipelines. And that's where models go to die."*

#### Slide 9: Firehose #07 — The Data Vault Component Explosion (0:35)
- **On screen:** 1 Concept ('Customer') ➔ 10 Physical Artifacts (Hub, 2 Sats, Link, Link Sat, PIT, Bridge, Mart Dimension).
- **Spoken:** *"Scenario seven: Data Vault 2.0 is brilliant for auditability and scale. But it shreds a single business concept like 'Customer' across 10 physical tables: a hub, four satellites, two links, a PIT table, and dimensional marts. If the engineer cannot follow that object seamlessly across every layer, they drown in structural complexity."*

#### Slide 10: Firehose #08 — Performance Bottleneck Blindness (0:35)
- **On screen:** Grepping through 3.8 GB of raw text logs. High concurrency without execution Gantt timing.
- **Spoken:** *"Scenario eight: The nightly batch missed the 7 AM SLA by 90 minutes. You had 75 transforms executing in parallel across 3 workers. Which one was the bottleneck? Good luck finding out by grepping through 4 gigabytes of raw text log files."*

#### Slide 11: Firehose #09 — The Root-Cause Blame War (0:35)
- **On screen:** The blame loop: Executive ➔ BI Analyst ➔ EDW Engineer ➔ DBA ➔ Source Team.
- **Spoken:** *"Scenario nine: The dashboard shows -$2.4M inventory. BI blames the Mart. Mart blames Business Vault. Business Vault blames Raw Vault. Raw Vault blames the CSV. Teams spend four days in committee meetings trying to prove 'It wasn't my layer!' because nobody has the complete chain of custody next to the model."*

#### Slide 12: Firehose #10 — The Fragile Tool-Glue Tax (0:35)
- **On screen:** 58% of budget spent on Toolchain Integration & API Syncs vs 18% on Business Modeling.
- **Spoken:** *"Scenario ten: The tool-glue tax. You bought 8 'best-of-breed' licenses, and now spend 60% of your senior data engineering hours writing Python sync scripts and webhooks to keep tool A talking to tool B. Toolchain integration became the project, instead of delivering data value."*

#### Slide 13: The Firehose Verdict (0:40)
- **On screen:** Drift • Silence • Context Hell • Read-Only • Exceptions. *"The failure is NEVER the modeling technique itself. The failure is the loss of integrated visibility and editability."*
- **Spoken:** *"Look at the common thread across all ten scenarios. Data Vault didn't fail. Dimensional modeling didn't fail. What failed was fragmentation. The moment an engineer cannot see, examine, and edit the object in one coherent place, the architecture collapses into friction."*

---

### Act 2: The Core Thesis — Full-Stack Transparency (Slides 14–17, ~4:00)

#### Slide 14: The Fallacy of "Best of Breed" (1:00)
- **On screen:** *"Best tool for each job gives you the best parts. The engineer still needs the whole picture."*
- **Spoken:** *"When enterprise procurement buys tools, they evaluate each in isolation. They buy the deepest data modeler, the deepest catalog, the deepest orchestrator, the deepest lineage tool. That approach optimizes for purchasing categories and specialist consultants. But for the engineer on call at 2 AM, fragmented depth is a nightmare. You have the best parts, but nobody can drive the car without getting grease everywhere."*

#### Slide 15: The 5 Pillars of Unified Object Knowledge (1:00)
- **On screen:** 1. Schema, 2. ETL Logic, 3. Lineage, 4. Operations, 5. Quality — across Source Model ➔ Raw Vault ➔ Business Vault ➔ Dimensional Marts.
- **Spoken:** *"Here is the design principle of Issue #190: A data engineer must be able to view, examine, and edit everything about the object they are working on, in every layer of the stack. That means five kinds of knowledge must stay attached to the object: its schema contract, its load logic, bidirectional lineage, operational runtime telemetry, and data quality rules. Not in separate products. Right on the object."*

#### Slide 16: Three Different Acts (1:00)
- **On screen:** *"Looking, deciding, and loading are three different acts. A deliberate write is not a side effect of looking."* (Harvest ➔ Gates ➔ Update & Publish).
- **Spoken:** *"To maintain order, we establish three distinct acts: Harvest observes what live sources look like right now, building history without mutating the contract. The Schema Drift and Data Quality Gates decide whether this load wave is allowed to execute. And Update loads the targets and deliberately publishes back the target layouts. Looking, deciding, and loading are three separate acts."*

#### Slide 17: Seeing & Fixing in the Same Place (1:00)
- **On screen:** Disconnected World (2 days of tickets, git PRs, DDL approvals) vs Hopper EDW (30 seconds: 1-click remediation proposal applied to model and DDL).
- **Spoken:** *"Observability without editability is just an expensive siren. The word that always gets dropped in modern data catalogs is EDIT. In Hopper EDW, when a column width changes upstream, the schema gate flags it, calculates the exact remediation proposal, and in one click expands the model AND issues the ALTER TABLE DDL. Seeing and fixing happen on the exact same object."*

---

### Act 3: Hopper EDW Solution Tour (Slides 18–25, ~5:00)

#### Slide 18: What We Built in Hopper EDW (0:30)
- **On screen:** Solution Tour Intro: Journey, Source Modeler, Open Vault, Ops Canvas.
- **Spoken:** *"This is not theoretical. Everything I'm describing is built, shipping, and running in Apache Hop through our Hopper EDW plugin. Let's look at the actual screenshots of what we built."*

#### Slide 19: Solution 01 — The EDW Journey (0:40)
- **On screen:** `diagrams/d7-edw-journey.svg` — Single navigation tree in Apache Hop GUI.
- **Spoken:** *"First, the EDW Journey. In Hop GUI, you have one unified perspective. The tree walks from Sources to Controls to Raw Vault, Business Vault, Dimensional Marts, Workflows, and Reports. You don't switch tools to switch layers. One unified spine governs the entire lifecycle."*

#### Slide 20: Solution 02 — Source Modeler & JDBC (0:45)
- **On screen:** `source-modeler-retail-example-with-query-dialog-generated-sql.png`
- **Spoken:** *"Second, handling edge-case complexity at the source. Streaming, JSON documents, typing rules for typeless files, and custom query models are first-class citizens right in the Source Modeler. We even created a custom JDBC driver so external tools like DBeaver can query the source model directly with standard SQL."*

#### Slide 21: Solution 03 — Model-Driven Open Data Vault (0:45)
- **On screen:** `data-vault-model-retail-360.png` — Hubs, Links, Satellites, "Show update pipeline".
- **Spoken:** *"Third, automated Data Vault modeling. You visually model hubs, links, and satellites. We generate the load pipelines, hash keys, and DDL in one click. But critically: it is NOT a black box. You click 'Show update pipeline', and Hop opens the standard, transparent, editable pipeline. Generation is allowed only because the result stays inspectable."*

#### Slide 22: Solution 04 — Business Vault & Dimensional Marts (0:45)
- **On screen:** `business-vault-model-customer-360.png` — SCD2 timelines, PIT tables, conformed star marts.
- **Spoken:** *"Fourth, Business Vault and Dimensional modeling. Visual modeling of SCD2 timelines, Point-In-Time tables with automated unit tests, and conformed star schemas. You can even import existing dbt models directly into the Business Vault graph."*

#### Slide 23: Solution 05 — Operations on the Canvas (0:45)
- **On screen:** `data-vault-retail-360-model-with-duration-metrics.png` — In-canvas duration overlays and metrics panel.
- **Spoken:** *"Fifth, the canvas IS the operations surface. When a load completes, execution metrics and last-run duration bars appear directly on the model nodes! Plus active stall detection and Gantt execution maps. Silence is finally solved."*

#### Slide 24: Solution 06 — Schema Drift & Remediation (0:45)
- **On screen:** `resource-definition-group-validation-remediation-proposals-dialog.png`
- **Spoken:** *"Sixth, automated drift gates and remediation. Schema harvest tracks column history. If a source drifts, the validation gate stops the load before target corruption happens, displays the exact SQL remediation proposal, and updates the target DDL and model in one click."*

#### Slide 25: Solution 07 — Open Metadata Bus & AI Advisor (0:45)
- **On screen:** `ai-advisor-offering-performance-tuning-advice.png` — OpenLineage/Marquez + AI Copilot.
- **Spoken:** *"Seventh, open metadata and in-canvas intelligence. We export full job and dataset lineage to Marquez and OpenLineage. And we built in an AI Architecture Advisor that reviews your Data Vault models, explains complex link relationships, and recommends performance tuning for parallelism."*

---

### Act 4: The 5-Minute Live Demo (Slides 26–27, ~5:00)

#### Slide 26: 5-Minute Live Demo Blueprint (0:20 intro, then switch to Hop GUI)
- **On screen:** Interactive 5:00 countdown timer and 5-step click runbook.

#### Live Demo Clicks & Timing:
1. **00:00 – 00:30 (EDW Journey):**  
   - Open Hop GUI, project `retail-example`.
   - Click the sidebar icon **EDW Journey** (located next to Data Catalog).
   - Point out the clean sequential structure: **1. Sources**, **2. Controls**, **3. Raw Data Vault**, **4. Business Vault**, **5. Dimensional**.
   - *Line:* *"Notice how the engineer never leaves this tree. All layers share the same metadata spine."*
2. **00:30 – 01:15 (Source Modeler):**  
   - Open `models/source-tables-crm.hsm`.
   - Click the `customer_address` table card.
   - Show how keys, data types, and source mappings are defined.
   - *Line:* *"Here is where source contracts live. If this source was a stream or JSON feed, we would handle it right here."*
3. **01:15 – 02:30 (Data Vault Model & Generated Pipeline):**  
   - Return to Journey. Open `retail-360.hdv`.
   - Click the icon body of `sat_customer_address`.
   - Click **Show update pipeline**.
   - An ordinary, standard Apache Hop pipeline opens instantly on screen!
   - Hover over a transform: *"Notice this isn't proprietary bytecode or a black box. This is standard Hop transforms, 100% inspectable and debuggable."* Close the pipeline.
4. **02:30 – 03:30 (Lineage Tab):**  
   - Double-click the name `sat_customer_address`.
   - Switch to the **Lineage** tab.
   - Show parent hub `hub_customer` and source feed `E2E-customer-address`.
   - Point out that lineage continues seamlessly into Business Vault and Dimensional Marts. Close dialog.
5. **03:30 – 04:30 (Operations on the Canvas):**  
   - Direct attention to the right-hand pane of the model canvas.
   - Show the **duration bars and execution metrics** populated from the last load run.
   - Point out that execution timings and throughput sit right beside the architectural drawing.
6. **04:30 – 05:00 (Live Wrap-up):**  
   - Switch back to presentation slide 27.

#### Slide 27: Conclusion — The Three Axioms (0:30)
- **On screen:** The Three Lines:
  1. *"If you can't see it, you can't own it."*
  2. *"Best tool for each job gives you the best parts. The engineer still needs the whole picture."*
  3. *"Looking, deciding, and loading are three different acts."*
- **Spoken:** *"Thank you. Build your warehouses where you can see every layer, examine every metric, and edit every object in one place. I welcome your questions!"*

---

## Live Demo Fallback Procedure

If Hop GUI or the Postgres container encounters an unexpected local issue during a live presentation:
1. Stay on Slide 26 and click **[Open Fallback Stills]**.
2. Narrate the exact same five steps using the embedded high-resolution screenshots:
   - `docs/images/source-modeler-retail-example-with-query-dialog-generated-sql.png`
   - `docs/images/data-vault-model-retail-example.png`
   - `docs/images/data-vault-satellite-dialog-lineage-tab.png`
   - `docs/images/data-vault-retail-360-model-with-duration-metrics.png`
3. Resume Slide 27 for the closing axioms.
