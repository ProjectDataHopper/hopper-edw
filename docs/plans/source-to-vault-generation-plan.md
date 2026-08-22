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

# Generate a Data Vault from a Source Model (Issue #125)

Internal copy of the approved implementation plan. Authoritative session plan lives with the coding session; this file is the in-repo design note.

**Issue:** https://github.com/ProjectDataHopper/hopper-edw/issues/125

## Summary

Classify selected `.hsm` source tables (PKs + relationships) and add hubs, satellites, and links to a new or existing `.hdv` after a single review screen.

v1 is **source-driven raw vault**, review-and-apply, same-PK cluster → one hub, junction/transaction tables → links + optional link satellites + dependent child keys.

Shipped follow-up: queries / JSON / pipelines, lookup **reference tables**, self-FK **hierarchy** aliases, n-ary leftover-FK links on feeds, coach-panel **Generate DV**, and AI Help classification JSON.

See the session plan for literature (Krneta 2014, Linstedt/Olschimke, VaultSpeed vs IRI), algorithm, GUI, tests, and PR slices.

## Packages

- `org.hopper.edw.datavault.metadata.sourcemodel.tovault` — classifier + apply
- `org.hopper.edw.datavault.hopgui.file.sourcemodel` / `...vault` — toolbar + review dialog
