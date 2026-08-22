/*
 * Copyright 2026 i-Bridge bv
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hopper.edw.datavault.lineageview.backend;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Job or dataset vertex. */
@Value
@Builder(toBuilder = true)
public class LineageNode {
  /** Stable id: {@code job:ns:name} or {@code dataset:ns:name}. */
  String id;

  LineageNodeKind kind;
  String namespace;
  String name;

  LineageGraphLayer layer;

  HopExportFacet hopExport;
  HopLocationFacet hopLocation;

  /** From {@code hop_ops} only — never Marquez {@code latestRun.durationMs}. */
  HopOpsFacet hopOps;

  @Builder.Default List<String> schemaFieldNames = List.of();

  /** ISO-8601 export/event time for structure freshness. Not a load time. */
  String lastExportedAt;

  /** Marquez {@code latestRun.id} for follow-up. Not load telemetry. */
  String latestRunId;

  @Builder.Default List<LineageWarning> warnings = List.of();
}
