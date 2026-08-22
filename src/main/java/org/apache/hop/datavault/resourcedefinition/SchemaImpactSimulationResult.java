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
package org.apache.hop.datavault.resourcedefinition;

import java.time.Instant;
import java.util.List;
import org.apache.hop.datavault.impact.ImpactGraph;
import org.apache.hop.datavault.lineage.LineageDiffResult;
import org.apache.hop.datavault.lineage.LineageDiffService;

/** Outcome of a schema impact simulation run. */
public record SchemaImpactSimulationResult(
    ValidationReport validationReport,
    ImpactGraph impactGraph,
    String catalogVersionUsed,
    String baselineVersionUsed,
    SchemaCompareMode compareMode,
    Instant timestamp,
    SimulationStatus status,
    List<LineageDiffResult> lineageDiffs) {

  public SchemaImpactSimulationResult {
    lineageDiffs = lineageDiffs != null ? List.copyOf(lineageDiffs) : List.of();
  }

  /** Compatibility constructor without lineage diffs. */
  public SchemaImpactSimulationResult(
      ValidationReport validationReport,
      ImpactGraph impactGraph,
      String catalogVersionUsed,
      String baselineVersionUsed,
      SchemaCompareMode compareMode,
      Instant timestamp,
      SimulationStatus status) {
    this(
        validationReport,
        impactGraph,
        catalogVersionUsed,
        baselineVersionUsed,
        compareMode,
        timestamp,
        status,
        List.of());
  }

  public static SimulationStatus statusOf(ValidationReport report) {
    return statusOf(report, List.of());
  }

  public static SimulationStatus statusOf(
      ValidationReport report, List<LineageDiffResult> lineageDiffs) {
    SimulationStatus base;
    if (report == null) {
      base = SimulationStatus.PASS;
    } else if (report.hasBlockingIssues()) {
      base = SimulationStatus.CRITICAL_BLOCKED;
    } else if (report.hasWarningIssues()) {
      base = SimulationStatus.WARNING;
    } else {
      // INFO-only findings still PASS (do not elevate status to WARNING).
      base = SimulationStatus.PASS;
    }
    if (LineageDiffService.hasBlocking(lineageDiffs)) {
      return SimulationStatus.CRITICAL_BLOCKED;
    }
    if (LineageDiffService.hasWarnings(lineageDiffs) && base == SimulationStatus.PASS) {
      return SimulationStatus.WARNING;
    }
    return base;
  }
}
