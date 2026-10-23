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
package org.hopper.edw.datavault.hopgui.perspective.journey.poster;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.EdwJourneyProblem;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.LoadOverviewSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.ModelLoadSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;
import org.hopper.edw.quality.history.DataQualityHistoryReader.QualityRunSummary;

/**
 * Status mark for each architecture block. The first matching severity wins: error, warning, ok.
 */
public final class EdwJourneyPosterStatus {

  public static final long STALE_MS = 7L * 24 * 60 * 60 * 1000;

  private static final Set<String> UNMARKED =
      Set.of(
          "documentation",
          "clients",
          "hop-jdbc",
          "jdbc-clients",
          "edw-journey",
          "presentations",
          "coach");

  private EdwJourneyPosterStatus() {}

  public enum Mark {
    ERROR,
    WARNING,
    OK,
    NONE
  }

  public record MarkedBlock(Mark mark, String code) {
    public static MarkedBlock none() {
      return new MarkedBlock(Mark.NONE, "");
    }
  }

  public static Map<String, MarkedBlock> marks(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay) {
    return marks(snapshot, overlay, new Date());
  }

  public static Map<String, MarkedBlock> marks(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay, Date now) {
    EdwJourneySnapshot picture = snapshot != null ? snapshot : EdwJourneySnapshot.empty();
    EdwJourneyOpsOverlay ops = overlay != null ? overlay : EdwJourneyOpsOverlay.empty();
    Date clock = now != null ? now : new Date();
    Map<String, MarkedBlock> marks = new LinkedHashMap<>();
    for (String id : EdwJourneyPosterHotspots.ids()) {
      marks.put(id, mark(id, picture, ops, clock));
    }
    return marks;
  }

  static MarkedBlock mark(
      String id, EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay, Date now) {
    if (UNMARKED.contains(id)) {
      return MarkedBlock.none();
    }
    return switch (id) {
      case "landing", "source-models" -> sources(snapshot, overlay, now, false);
      case "harvester" -> sources(snapshot, overlay, now, true);
      case "gates" -> gates(overlay, now);
      case "publish", "hop-engine" -> wave(overlay, now);
      case "dv-models", "dv-db" -> layer(snapshot.dataVaultModels(), overlay, "dv", now);
      case "bv-models", "bv-db" -> layer(snapshot.businessVaultModels(), overlay, "bv", now);
      case "dm-models", "dm-db" -> layer(snapshot.dimensionalModels(), overlay, "dm", now);
      case "catalog" -> catalog(snapshot, overlay);
      case "ops", "lineage-view" -> telemetry(id, snapshot, overlay);
      default -> MarkedBlock.none();
    };
  }

  private static MarkedBlock sources(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay, Date now, boolean requireHarvest) {
    HarvestRunSummary harvest = overlay.harvest();
    if (failed(harvest) || problem(overlay, "harvest", "ERROR")) {
      return new MarkedBlock(Mark.ERROR, "harvest-error");
    }
    if (changed(harvest) || problem(overlay, "harvest", "WARNING") || stale(harvest, now)) {
      return new MarkedBlock(Mark.WARNING, changed(harvest) ? "harvest-changes" : "stale");
    }
    boolean present = harvest != null || (!requireHarvest && !snapshot.sourceModels().isEmpty());
    if (present) {
      return new MarkedBlock(Mark.OK, "ok");
    }
    return MarkedBlock.none();
  }

  private static MarkedBlock gates(EdwJourneyOpsOverlay overlay, Date now) {
    QualityRunSummary pre = overlay.sourceQuality();
    QualityRunSummary post = overlay.targetQuality();
    if (blocking(pre) || blocking(post) || problem(overlay, "quality", "ERROR")) {
      return new MarkedBlock(Mark.ERROR, "quality-blocking");
    }
    if (findings(pre) || findings(post) || problem(overlay, "quality", "WARNING")) {
      return new MarkedBlock(Mark.WARNING, "quality-findings");
    }
    Date measured = newest(pre, post);
    if (measured != null && stale(measured, now)) {
      return new MarkedBlock(Mark.WARNING, "stale");
    }
    if (pre != null || post != null) {
      return new MarkedBlock(Mark.OK, "ok");
    }
    return MarkedBlock.none();
  }

  private static MarkedBlock wave(EdwJourneyOpsOverlay overlay, Date now) {
    LoadOverviewSummary load = overlay.load();
    if (load == null) {
      return MarkedBlock.none();
    }
    if ((load.errors() != null && load.errors() > 0) || Boolean.FALSE.equals(load.success())) {
      return new MarkedBlock(Mark.ERROR, "load-error");
    }
    if (stale(load.finishedAt(), now)) {
      return new MarkedBlock(Mark.WARNING, "stale");
    }
    return new MarkedBlock(Mark.OK, "ok");
  }

  private static MarkedBlock layer(
      List<EdwJourneySnapshot.ModelRef> models,
      EdwJourneyOpsOverlay overlay,
      String opsType,
      Date now) {
    boolean failed = false;
    boolean ran = false;
    Date newest = null;
    for (ModelLoadSummary summary : overlay.modelLoads()) {
      if (!sameLayer(summary, opsType)) {
        continue;
      }
      ran = true;
      if ((summary.errors() != null && summary.errors() > 0)
          || Boolean.FALSE.equals(summary.success())) {
        failed = true;
      }
      if (summary.finishedAt() != null && (newest == null || summary.finishedAt().after(newest))) {
        newest = summary.finishedAt();
      }
    }
    if (failed) {
      return new MarkedBlock(Mark.ERROR, "load-error");
    }
    if (ran && stale(newest, now)) {
      return new MarkedBlock(Mark.WARNING, "stale");
    }
    if (ran || (models != null && !models.isEmpty())) {
      return new MarkedBlock(Mark.OK, "ok");
    }
    return MarkedBlock.none();
  }

  private static MarkedBlock catalog(EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay) {
    for (String warning : snapshot.warnings()) {
      if (warning != null && warning.toLowerCase(Locale.ROOT).contains("catalog")) {
        return new MarkedBlock(Mark.WARNING, "catalog-warning");
      }
    }
    if (!snapshot.catalogFeeds().isEmpty() || !snapshot.catalogVersionTags().isEmpty()) {
      return new MarkedBlock(Mark.OK, "ok");
    }
    return MarkedBlock.none();
  }

  private static MarkedBlock telemetry(
      String id, EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay) {
    LoadOverviewSummary load = overlay.load();
    boolean loadError =
        load != null
            && ((load.errors() != null && load.errors() > 0)
                || Boolean.FALSE.equals(load.success()));
    if (loadError) {
      return new MarkedBlock(Mark.ERROR, "load-error");
    }
    if ("lineage-view".equals(id)) {
      if (!snapshot.lineageViews().isEmpty()) {
        return new MarkedBlock(Mark.OK, "ok");
      }
      return MarkedBlock.none();
    }
    if (overlay.unavailableReason() != null && !overlay.hasAnyRun()) {
      return MarkedBlock.none();
    }
    if (overlay.hasAnyRun()) {
      return new MarkedBlock(Mark.OK, "ok");
    }
    return MarkedBlock.none();
  }

  private static boolean sameLayer(ModelLoadSummary summary, String opsType) {
    if (summary == null || summary.opsModelType() == null) {
      return false;
    }
    String type = summary.opsModelType().toLowerCase(Locale.ROOT);
    return switch (opsType) {
      case "dv" -> type.equals("dv") || type.contains("data_vault") || type.contains("datavault");
      case "bv" ->
          type.equals("bv") || type.contains("business_vault") || type.contains("businessvault");
      case "dm" -> type.equals("dm") || type.contains("dimensional");
      default -> type.equals(opsType);
    };
  }

  private static boolean problem(EdwJourneyOpsOverlay overlay, String source, String severity) {
    for (EdwJourneyProblem problem : overlay.problems()) {
      if (problem == null) {
        continue;
      }
      if (source.equalsIgnoreCase(problem.source())
          && severity.equalsIgnoreCase(problem.severity())) {
        return true;
      }
    }
    return false;
  }

  private static boolean failed(HarvestRunSummary harvest) {
    if (harvest == null) {
      return false;
    }
    if (harvest.errorCount() != null && harvest.errorCount() > 0) {
      return true;
    }
    String status = harvest.status() == null ? "" : harvest.status().toUpperCase(Locale.ROOT);
    return status.contains("FAIL") || status.contains("ERROR");
  }

  private static boolean changed(HarvestRunSummary harvest) {
    return harvest != null && harvest.changeCount() != null && harvest.changeCount() > 0;
  }

  private static boolean blocking(QualityRunSummary run) {
    return run != null && run.blockingCount() != null && run.blockingCount() > 0;
  }

  private static boolean findings(QualityRunSummary run) {
    if (run == null) {
      return false;
    }
    if (run.findingCount() != null && run.findingCount() > 0) {
      return true;
    }
    return run.warningCount() != null && run.warningCount() > 0;
  }

  private static Date newest(QualityRunSummary pre, QualityRunSummary post) {
    Date a = pre != null ? pre.measuredAt() : null;
    Date b = post != null ? post.measuredAt() : null;
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a.after(b) ? a : b;
  }

  private static boolean stale(HarvestRunSummary harvest, Date now) {
    return harvest != null && stale(harvest.finishedAt(), now);
  }

  static boolean stale(Date when, Date now) {
    if (when == null || now == null) {
      return false;
    }
    return now.getTime() - when.getTime() > STALE_MS;
  }
}
