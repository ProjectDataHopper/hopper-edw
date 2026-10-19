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
package org.hopper.edw.datavault.hopgui.perspective.journey.panel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.LoadOverviewSummary;
import org.hopper.edw.quality.history.DataQualityHistoryReader.QualityRunSummary;

/** Bounded OPS history for Journey panels. Missing history is an empty list plus a reason. */
public record EdwJourneyHistory(
    String reason,
    List<HarvestRunSummary> harvestRuns,
    List<QualityRunSummary> qualityRuns,
    List<LoadOverviewSummary> waves,
    List<FindingRollup> findings) {

  public static final int LIMIT = 14;
  public static final int CHRONIC_WINDOW = 5;
  public static final int CHRONIC_COUNT = 3;

  public EdwJourneyHistory {
    harvestRuns = harvestRuns == null ? List.of() : List.copyOf(cap(harvestRuns));
    qualityRuns = qualityRuns == null ? List.of() : List.copyOf(cap(qualityRuns));
    waves = waves == null ? List.of() : List.copyOf(cap(waves));
    findings = findings == null ? List.of() : List.copyOf(findings);
  }

  public static EdwJourneyHistory empty(String reason) {
    return new EdwJourneyHistory(reason, List.of(), List.of(), List.of(), List.of());
  }

  public static <T> List<T> cap(List<T> rows) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    if (rows.size() <= LIMIT) {
      return List.copyOf(rows);
    }
    return List.copyOf(rows.subList(0, LIMIT));
  }

  public static boolean chronic(int runsInWindow) {
    return runsInWindow >= CHRONIC_COUNT;
  }

  /**
   * Newest-first runs. A finding in {@link #CHRONIC_COUNT} of the first {@link #CHRONIC_WINDOW} is
   * chronic.
   */
  public static List<FindingRollup> rollup(List<List<FindingRef>> runsNewestFirst) {
    List<List<FindingRef>> window = runsNewestFirst == null ? List.of() : runsNewestFirst;
    if (window.size() > CHRONIC_WINDOW) {
      window = window.subList(0, CHRONIC_WINDOW);
    }
    Map<String, int[]> counts = new LinkedHashMap<>();
    Map<String, FindingRef> sample = new LinkedHashMap<>();
    for (List<FindingRef> run : window) {
      if (run == null) {
        continue;
      }
      Map<String, FindingRef> once = new LinkedHashMap<>();
      for (FindingRef finding : run) {
        if (finding == null) {
          continue;
        }
        once.putIfAbsent(finding.key(), finding);
      }
      for (Map.Entry<String, FindingRef> entry : once.entrySet()) {
        counts.computeIfAbsent(entry.getKey(), k -> new int[1])[0]++;
        sample.putIfAbsent(entry.getKey(), entry.getValue());
      }
    }
    List<FindingRollup> rollups = new ArrayList<>();
    for (Map.Entry<String, int[]> entry : counts.entrySet()) {
      FindingRef finding = sample.get(entry.getKey());
      int runs = entry.getValue()[0];
      rollups.add(
          new FindingRollup(
              finding.subject(), finding.rule(), finding.severity(), runs, chronic(runs)));
    }
    rollups.sort((a, b) -> Integer.compare(b.runs(), a.runs()));
    return rollups;
  }

  public record FindingRef(String subject, String rule, String severity) {
    public String key() {
      return (subject == null ? "" : subject) + "\n" + (rule == null ? "" : rule);
    }
  }

  public record FindingRollup(
      String subject, String rule, String severity, int runs, boolean chronic) {}
}
