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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsDecorations;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.EdwJourneyProblem;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.LoadOverviewSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.ModelLoadSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyPerspective;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.CatalogFeed;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.ModelRef;
import org.hopper.edw.datavault.hopgui.perspective.journey.link.EdwJourneyLink;
import org.hopper.edw.datavault.hopgui.perspective.journey.link.EdwJourneyModelLookup;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyHistory.FindingRollup;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelSpec.ChartSpec;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelSpec.TableSpec;
import org.hopper.edw.quality.history.DataQualityHistoryReader.QualityRunSummary;

/** Builds the twelve Journey dashboards from the snapshot, the last-run overlay, and history. */
public final class EdwJourneyPanelContent {

  private static final Class<?> PKG = EdwJourneyPerspective.class;

  /**
   * Tilt for horizontal category labels that would otherwise overlap. Negative so the text hangs
   * down-left from the category tick. Date stamps use {@code MM-dd HH:mm}.
   */
  private static final int CATEGORY_LABEL_ANGLE = -25;

  private EdwJourneyPanelContent() {}

  public record Inputs(
      EdwJourneySnapshot snapshot,
      EdwJourneyOpsOverlay overlay,
      EdwJourneyHistory history,
      boolean semanticLayerPresent,
      List<String> semanticFiles,
      List<JdbcService> jdbcServices) {

    public Inputs {
      semanticFiles = semanticFiles == null ? List.of() : List.copyOf(semanticFiles);
      jdbcServices = jdbcServices == null ? List.of() : List.copyOf(jdbcServices);
    }

    public static Inputs of(
        EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay, EdwJourneyHistory history) {
      return new Inputs(snapshot, overlay, history, false, List.of(), List.of());
    }
  }

  public record JdbcService(String name, String modelFile) {}

  public static EdwJourneyPanelSpec build(EdwJourneyPanelId id, Inputs inputs) {
    EdwJourneyPanelId panel = id == null ? EdwJourneyPanelId.PROJECT_PULSE : id;
    Inputs in = inputs == null ? Inputs.of(null, null, null) : inputs;
    EdwJourneySnapshot snapshot =
        in.snapshot() == null ? EdwJourneySnapshot.empty() : in.snapshot();
    EdwJourneyOpsOverlay overlay =
        in.overlay() == null ? EdwJourneyOpsOverlay.empty() : in.overlay();
    EdwJourneyHistory history = in.history() == null ? EdwJourneyHistory.empty(null) : in.history();
    return switch (panel) {
      case PROJECT_PULSE -> pulse(snapshot, overlay, history);
      case SOURCE_DRIFT -> drift(snapshot, overlay, history);
      case HARVEST_WATCH -> harvest(snapshot, history);
      case GATE_DESK -> gates(snapshot, overlay, history);
      case WAVE_BOARD -> waves(snapshot, overlay, history);
      case RAW_VAULT -> vault(snapshot, overlay, "dv", EdwJourneyPanelId.RAW_VAULT);
      case BUSINESS_VAULT -> business(snapshot, overlay);
      case DIMENSIONAL -> dimensional(snapshot, overlay, in);
      case CONTRACT -> contract(snapshot, overlay);
      case LINEAGE_OPS -> lineage(snapshot, overlay);
      case NEXT_MOVE -> next(snapshot, in.semanticLayerPresent());
      case CONSUMPTION -> consumption(snapshot, in);
    };
  }

  private static EdwJourneyPanelSpec pulse(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay, EdwJourneyHistory history) {
    LoadOverviewSummary load = overlay.load();
    List<Map<String, String>> numbers = new ArrayList<>();
    numbers.add(
        row(
            "duration",
            load == null ? "" : EdwJourneyOpsDecorations.formatDuration(load.durationMs()),
            "errors",
            load == null || load.errors() == null ? "0" : String.valueOf(load.errors()),
            "problems",
            String.valueOf(overlay.problems().size()),
            "models",
            String.valueOf(modelCount(snapshot))));
    List<Map<String, String>> waveRows = new ArrayList<>();
    List<LoadOverviewSummary> waves =
        history.waves().isEmpty() && load != null ? List.of(load) : history.waves();
    int n = waves.size();
    for (int i = n - 1; i >= 0; i--) {
      LoadOverviewSummary wave = waves.get(i);
      waveRows.add(
          row(
              "when",
              stamp(wave.finishedAt(), n - i),
              "duration",
              number(wave.durationMs()),
              "series",
              Boolean.FALSE.equals(wave.success()) ? "failed" : "ok"));
    }
    List<Map<String, String>> problems = new ArrayList<>();
    for (EdwJourneyProblem problem : overlay.problems()) {
      problems.add(
          row(
              "severity",
              problem.severity(),
              "source",
              problem.source(),
              "subject",
              problem.subject(),
              "message",
              problem.message()));
    }
    List<Map<String, String>> stages = new ArrayList<>();
    stages.add(stage("Sources", snapshot.sourceModels(), "hsm", EdwJourneyPanelId.SOURCE_DRIFT));
    stages.add(stage("Controls", List.of(), "", EdwJourneyPanelId.GATE_DESK));
    stages
        .get(stages.size() - 1)
        .put("state", overlay.sourceQuality() != null ? "present" : "empty");
    stages.add(stage("Raw vault", snapshot.dataVaultModels(), "dv", EdwJourneyPanelId.RAW_VAULT));
    stages.add(
        stage(
            "Business vault",
            snapshot.businessVaultModels(),
            "bv",
            EdwJourneyPanelId.BUSINESS_VAULT));
    stages.add(
        stage("Dimensional", snapshot.dimensionalModels(), "dm", EdwJourneyPanelId.DIMENSIONAL));
    return spec(
        EdwJourneyPanelId.PROJECT_PULSE,
        snapshot,
        history.reason(),
        List.of(chart("Wave duration", "when", "series", "duration", "", waveRows)),
        List.of(
            table("This group", List.of("duration", "errors", "problems", "models"), numbers),
            table("Problems", List.of("severity", "source", "subject", "message"), problems),
            table("Stages", List.of("stage", "state"), stages)));
  }

  private static EdwJourneyPanelSpec drift(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay, EdwJourneyHistory history) {
    List<Map<String, String>> bars = new ArrayList<>();
    List<HarvestRunSummary> runs = history.harvestRuns();
    int n = runs.size();
    for (int i = n - 1; i >= 0; i--) {
      HarvestRunSummary run = runs.get(i);
      bars.add(row("when", stamp(run.finishedAt(), n - i), "changes", number(run.changeCount())));
    }
    List<Map<String, String>> subjects = new ArrayList<>();
    for (EdwJourneyProblem problem : overlay.problems()) {
      if (problem == null || !"harvest".equalsIgnoreCase(problem.source())) {
        continue;
      }
      String element = problem.subject();
      String file = EdwJourneyModelLookup.file(snapshot, "hsm", element);
      subjects.add(
          row(
              "subject",
              element,
              "message",
              problem.message(),
              "link",
              file == null ? "" : EdwJourneyLink.open("hsm", file, element, "select"),
              "edit_link",
              file == null ? "" : EdwJourneyLink.open("hsm", file, element, "edit"),
              "browse_link",
              file == null ? "" : EdwJourneyLink.open("hsm", file, element, "browse")));
    }
    if (subjects.isEmpty() && overlay.harvest() != null) {
      subjects.add(
          row(
              "subject",
              overlay.harvest().resourceGroupName(),
              "message",
              EdwJourneyOpsDecorations.harvest(overlay.harvest()),
              "link",
              EdwJourneyLink.action("harvest-history")));
    }
    return spec(
        EdwJourneyPanelId.SOURCE_DRIFT,
        snapshot,
        runs.isEmpty() ? text("EdwJourneyPanel.Empty.Harvest") : null,
        List.of(chart("Changed subjects", "when", null, "changes", "", bars)),
        List.of(table("Harvest subjects", List.of("subject", "message"), subjects)));
  }

  private static EdwJourneyPanelSpec harvest(
      EdwJourneySnapshot snapshot, EdwJourneyHistory history) {
    List<Map<String, String>> rows = new ArrayList<>();
    List<Map<String, String>> bars = new ArrayList<>();
    List<HarvestRunSummary> runs = history.harvestRuns();
    int n = runs.size();
    for (int i = 0; i < n; i++) {
      HarvestRunSummary run = runs.get(i);
      rows.add(
          row(
              "when",
              stamp(run.finishedAt(), i + 1),
              "status",
              run.status(),
              "subjects",
              number(run.subjectCount()),
              "changes",
              number(run.changeCount()),
              "baseline",
              run.expectedBaseline(),
              "link",
              EdwJourneyLink.action("harvest-history")));
      bars.add(row("when", stamp(run.finishedAt(), n - i), "changes", number(run.changeCount())));
    }
    // bars were appended newest-first; reverse by rebuilding oldest-first for the axis
    List<Map<String, String>> ordered = new ArrayList<>();
    for (int i = n - 1; i >= 0; i--) {
      ordered.add(bars.get(i));
    }
    return spec(
        EdwJourneyPanelId.HARVEST_WATCH,
        snapshot,
        runs.isEmpty() ? text("EdwJourneyPanel.Empty.Harvest") : null,
        List.of(chart("Changes per harvest", "when", null, "changes", "", ordered)),
        List.of(
            table(
                "Harvest runs",
                List.of("when", "status", "subjects", "changes", "baseline"),
                rows)));
  }

  private static EdwJourneyPanelSpec gates(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay, EdwJourneyHistory history) {
    QualityRunSummary pre = overlay.sourceQuality();
    QualityRunSummary post = overlay.targetQuality();
    List<Map<String, String>> numbers =
        List.of(
            row(
                "blocking",
                number(sumBlocking(pre, post)),
                "findings",
                number(sumFindings(pre, post)),
                "link",
                EdwJourneyLink.action("validate-sources")));
    List<Map<String, String>> bars = new ArrayList<>();
    List<QualityRunSummary> runs = history.qualityRuns();
    for (int i = runs.size() - 1; i >= 0; i--) {
      QualityRunSummary run = runs.get(i);
      String when = stamp(run.measuredAt(), runs.size() - i);
      bars.add(row("when", when, "series", "blocking", "count", number(run.blockingCount())));
      long warnings =
          run.findingCount() == null
              ? 0
              : Math.max(
                  0, run.findingCount() - (run.blockingCount() == null ? 0 : run.blockingCount()));
      bars.add(row("when", when, "series", "other", "count", String.valueOf(warnings)));
    }
    List<Map<String, String>> findings = new ArrayList<>();
    for (FindingRollup rollup : history.findings()) {
      findings.add(
          row(
              "subject",
              rollup.subject(),
              "rule",
              rollup.rule(),
              "severity",
              rollup.severity(),
              "runs",
              String.valueOf(rollup.runs()),
              "chronic",
              rollup.chronic() ? "yes" : "no"));
    }
    for (EdwJourneyProblem problem : overlay.problems()) {
      if (problem != null && "quality".equalsIgnoreCase(problem.source()) && findings.isEmpty()) {
        findings.add(
            row(
                "subject",
                problem.subject(),
                "rule",
                "",
                "severity",
                problem.severity(),
                "runs",
                "1",
                "chronic",
                "no",
                "message",
                problem.message()));
      }
    }
    String note =
        runs.isEmpty() && findings.isEmpty() ? text("EdwJourneyPanel.Empty.Quality") : null;
    return spec(
        EdwJourneyPanelId.GATE_DESK,
        snapshot,
        note,
        List.of(chart("Findings by run", "when", "series", "count", "", bars)),
        List.of(
            table("Gate", List.of("blocking", "findings"), numbers),
            table(
                "Findings", List.of("subject", "rule", "severity", "runs", "chronic"), findings)));
  }

  private static EdwJourneyPanelSpec waves(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay, EdwJourneyHistory history) {
    LoadOverviewSummary load = overlay.load();
    List<Map<String, String>> header = new ArrayList<>();
    if (load != null) {
      String workflow = load.rootWorkflowName();
      header.add(
          row(
              "workflow",
              workflow,
              "when",
              stamp(load.finishedAt(), 1),
              "duration",
              EdwJourneyOpsDecorations.formatDuration(load.durationMs()),
              "models",
              number(load.modelCount()),
              "errors",
              number(load.errors()),
              "success",
              String.valueOf(load.success()),
              "link",
              workflowLink(snapshot, workflow)));
    }
    List<Map<String, String>> bars = new ArrayList<>();
    List<LoadOverviewSummary> waves =
        history.waves().isEmpty() && load != null ? List.of(load) : history.waves();
    for (int i = waves.size() - 1; i >= 0; i--) {
      LoadOverviewSummary wave = waves.get(i);
      bars.add(
          row(
              "when",
              stamp(wave.finishedAt(), waves.size() - i),
              "duration",
              number(wave.durationMs()),
              "series",
              Boolean.FALSE.equals(wave.success()) ? "failed" : "ok"));
    }
    List<ModelLoadSummary> models = new ArrayList<>(overlay.modelLoads());
    models.sort((a, b) -> Long.compare(nz(b.durationMs()), nz(a.durationMs())));
    Map<String, Set<String>> layersByName = new LinkedHashMap<>();
    for (ModelLoadSummary model : models) {
      String name = model.modelName() == null ? "" : model.modelName();
      layersByName
          .computeIfAbsent(name, key -> new LinkedHashSet<>())
          .add(EdwJourneyModelLookup.layerForModelType(model.opsModelType()));
    }
    List<Map<String, String>> modelRows = new ArrayList<>();
    List<Map<String, String>> modelBars = new ArrayList<>();
    for (ModelLoadSummary model : models) {
      String layer = EdwJourneyModelLookup.layerForModelType(model.opsModelType());
      if (layer.isBlank()) {
        layer = "dv";
      }
      String name = model.modelName() == null ? "" : model.modelName();
      Set<String> nameLayers = layersByName.getOrDefault(name, Set.of());
      String axisName = name;
      if (nameLayers.size() > 1) {
        axisName = name + EdwJourneyModelLookup.LAYER_MARK + layer;
      }
      String file = EdwJourneyModelLookup.file(snapshot, layer, name);
      modelRows.add(
          row(
              "model",
              name,
              "layer",
              model.opsModelType(),
              "duration",
              EdwJourneyOpsDecorations.formatDuration(model.durationMs()),
              "errors",
              number(model.errors()),
              "link",
              file == null ? "" : EdwJourneyLink.open(layer, file, "", "select")));
      modelBars.add(
          row(
              "model",
              axisName,
              "duration",
              number(model.durationMs()),
              "series",
              Boolean.FALSE.equals(model.success()) ? "failed" : "ok"));
    }
    return spec(
        EdwJourneyPanelId.WAVE_BOARD,
        snapshot,
        waves.isEmpty() ? text("EdwJourneyPanel.Empty.Wave") : null,
        List.of(
            chart("Wave duration", "when", "series", "duration", "", bars),
            chart(
                "Models in the latest wave",
                "model",
                "series",
                "duration",
                "hop-edw://open?element={model}&verb=select",
                modelBars,
                CATEGORY_LABEL_ANGLE)),
        List.of(
            table(
                "Latest wave",
                List.of("workflow", "when", "duration", "models", "errors", "success"),
                header),
            table("Models", List.of("model", "layer", "duration", "errors"), modelRows)));
  }

  private static EdwJourneyPanelSpec vault(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay, String ops, EdwJourneyPanelId id) {
    List<ModelRef> models =
        "bv".equals(ops) ? snapshot.businessVaultModels() : snapshot.dataVaultModels();
    if ("dm".equals(ops)) {
      models = snapshot.dimensionalModels();
    }
    List<Map<String, String>> rows = modelTable(models, overlay, ops);
    String note = models.isEmpty() ? text("EdwJourneyPanel.Empty.Layer") : null;
    return spec(
        id,
        snapshot,
        note,
        List.of(),
        List.of(
            table("Models", List.of("model", "tables", "duration", "errors", "success"), rows)));
  }

  private static EdwJourneyPanelSpec business(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay) {
    EdwJourneyPanelSpec base = vault(snapshot, overlay, "bv", EdwJourneyPanelId.BUSINESS_VAULT);
    Date newestRaw = null;
    for (ModelLoadSummary summary : overlay.modelLoads()) {
      if (EdwJourneyModelLookup.layerForModelType(summary.opsModelType()).equals("dv")
          && summary.finishedAt() != null
          && (newestRaw == null || summary.finishedAt().after(newestRaw))) {
        newestRaw = summary.finishedAt();
      }
    }
    List<Map<String, String>> fresh = new ArrayList<>();
    for (ModelLoadSummary summary : overlay.modelLoads()) {
      if (!EdwJourneyModelLookup.layerForModelType(summary.opsModelType()).equals("bv")) {
        continue;
      }
      String behind;
      if (summary.finishedAt() == null || newestRaw == null) {
        behind = "not in this wave";
      } else {
        long hours =
            Math.max(0, (newestRaw.getTime() - summary.finishedAt().getTime()) / 3_600_000L);
        behind = String.valueOf(hours);
      }
      fresh.add(row("model", summary.modelName(), "hours_behind", behind));
    }
    List<TableSpec> tables = new ArrayList<>(base.tables());
    tables.add(table("Hours behind the newest raw load", List.of("model", "hours_behind"), fresh));
    return new EdwJourneyPanelSpec(base.id(), base.title(), base.note(), base.charts(), tables);
  }

  private static EdwJourneyPanelSpec dimensional(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay, Inputs inputs) {
    EdwJourneyPanelSpec base = vault(snapshot, overlay, "dm", EdwJourneyPanelId.DIMENSIONAL);
    String note =
        snapshot.dimensionalModels().isEmpty()
            ? text("EdwJourneyPanel.Empty.Layer")
            : text("EdwJourneyPanel.Note.FactsInsert");
    List<Map<String, String>> facts = new ArrayList<>();
    for (ModelRef model : snapshot.dimensionalModels()) {
      for (String table : model.tableNames()) {
        if (table == null || !(table.startsWith("f_") || table.contains("fact"))) {
          continue;
        }
        String hsl = semanticFor(inputs.semanticFiles(), model.storedPath());
        facts.add(
            row(
                "fact",
                table,
                "model",
                model.displayName(),
                "link",
                EdwJourneyLink.open("dm", model.storedPath(), table, "select"),
                "edit_link",
                EdwJourneyLink.open("dm", model.storedPath(), table, "edit"),
                "pipeline_link",
                EdwJourneyLink.open("dm", model.storedPath(), table, "pipeline"),
                "presentation_link",
                hsl == null ? "" : EdwJourneyLink.open("hsl", hsl, table, "presentation")));
      }
    }
    List<TableSpec> tables = new ArrayList<>(base.tables());
    tables.add(table("Facts", List.of("fact", "model"), facts));
    return new EdwJourneyPanelSpec(base.id(), base.title(), note, base.charts(), tables);
  }

  private static EdwJourneyPanelSpec contract(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay) {
    List<Map<String, String>> counts =
        List.of(
            row(
                "feeds",
                String.valueOf(snapshot.catalogFeeds().size()),
                "versions",
                String.valueOf(snapshot.catalogVersionTags().size()),
                "link",
                EdwJourneyLink.action("list-versions")));
    List<Map<String, String>> feeds = new ArrayList<>();
    for (CatalogFeed feed : snapshot.catalogFeeds()) {
      String element = feed.key() == null ? "" : feed.key().toString();
      String originLayer = EdwJourneyModelLookup.layerForModelType(feed.originModelType());
      feeds.add(
          row(
              "feed",
              feed.key() == null ? "" : feed.key().getName(),
              "namespace",
              feed.key() == null ? "" : feed.key().getNamespace(),
              "link",
              EdwJourneyLink.open("catalog", feed.catalogConnection(), element, "select"),
              "edit_link",
              originLayer.isBlank() || feed.originFilename() == null
                  ? ""
                  : EdwJourneyLink.open(
                      originLayer, feed.originFilename(), feed.key().getName(), "select")));
    }
    String tag =
        snapshot.catalogVersionTags().isEmpty()
            ? text("EdwJourneyPanel.Empty.Version")
            : snapshot.catalogVersionTags().get(0);
    return spec(
        EdwJourneyPanelId.CONTRACT,
        snapshot,
        tag,
        List.of(),
        List.of(
            table("Catalog", List.of("feeds", "versions"), counts),
            table("Feeds", List.of("feed", "namespace"), feeds)));
  }

  private static EdwJourneyPanelSpec lineage(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay) {
    String note = overlay.unavailableReason();
    List<Map<String, String>> actions = new ArrayList<>();
    actions.add(row("action", "Browse lineage", "link", EdwJourneyLink.action("browse-lineage")));
    if (note != null) {
      actions.add(
          row(
              "action",
              "Create operational analytics",
              "link",
              EdwJourneyLink.action("create-ops-analytics")));
    }
    List<Map<String, String>> views = new ArrayList<>();
    for (EdwJourneySnapshot.OutputRef view : snapshot.lineageViews()) {
      views.add(
          row(
              "view",
              view.displayName(),
              "link",
              EdwJourneyLink.open("hlv", view.storedPath(), "", "select")));
    }
    LoadOverviewSummary load = overlay.load();
    QualityRunSummary quality =
        overlay.targetQuality() != null ? overlay.targetQuality() : overlay.sourceQuality();
    List<Map<String, String>> side =
        List.of(
            row(
                "wave",
                load == null ? "" : load.rootWorkflowName(),
                "wave_errors",
                load == null ? "" : number(load.errors()),
                "quality_findings",
                quality == null ? "" : number(quality.findingCount())));
    return spec(
        EdwJourneyPanelId.LINEAGE_OPS,
        snapshot,
        note,
        List.of(),
        List.of(
            table("Latest", List.of("wave", "wave_errors", "quality_findings"), side),
            table("Lineage views", List.of("view"), views),
            table("Actions", List.of("action"), actions)));
  }

  private static EdwJourneyPanelSpec next(
      EdwJourneySnapshot snapshot, boolean semanticLayerPresent) {
    List<Map<String, String>> gaps = new ArrayList<>();
    if (!snapshot.hasGroup()) {
      gaps.add(gap("Create a resource definition group", "create-group"));
    } else {
      if (snapshot.sourceModels().isEmpty()) {
        gaps.add(gap("Create a source model", "create-source-model"));
      }
      if (!snapshot.sourceModels().isEmpty() && snapshot.dataVaultModels().isEmpty()) {
        gaps.add(gap("Generate Data Vault from the source model", "generate-dv"));
      }
      if (!snapshot.dataVaultModels().isEmpty() && snapshot.businessVaultModels().isEmpty()) {
        ModelRef raw = snapshot.dataVaultModels().get(0);
        gaps.add(
            row(
                "gap",
                "Open the raw vault and coach it to the Business Vault",
                "link",
                EdwJourneyLink.open("dv", raw.storedPath(), "", "select")));
      }
      if (snapshot.dimensionalModels().isEmpty()
          && (!snapshot.businessVaultModels().isEmpty() || !snapshot.dataVaultModels().isEmpty())) {
        ModelRef from =
            !snapshot.businessVaultModels().isEmpty()
                ? snapshot.businessVaultModels().get(0)
                : snapshot.dataVaultModels().get(0);
        String layer = snapshot.businessVaultModels().isEmpty() ? "dv" : "bv";
        gaps.add(
            row(
                "gap",
                "Open the model and coach it to the dimensional layer",
                "link",
                EdwJourneyLink.open(layer, from.storedPath(), "", "select")));
      }
      if (!snapshot.dimensionalModels().isEmpty() && !semanticLayerPresent) {
        ModelRef fact = snapshot.dimensionalModels().get(0);
        gaps.add(
            row(
                "gap",
                "Open the dimensional model and create a presentation",
                "link",
                EdwJourneyLink.open("dm", fact.storedPath(), "", "select")));
      }
      if (hasWarehouse(snapshot) && !hasOpsAnalytics(snapshot)) {
        gaps.add(gap("Create operational analytics", "create-ops-analytics"));
      }
      if (snapshot.workflows().isEmpty()) {
        gaps.add(gap("Open the group and add a load workflow", "edit-group"));
      }
    }
    String note = gaps.isEmpty() ? text("EdwJourneyPanel.Empty.Next") : null;
    return spec(
        EdwJourneyPanelId.NEXT_MOVE,
        snapshot,
        note,
        List.of(),
        List.of(table("Next step", List.of("gap"), gaps)));
  }

  private static EdwJourneyPanelSpec consumption(EdwJourneySnapshot snapshot, Inputs inputs) {
    List<Map<String, String>> semantic = new ArrayList<>();
    for (String file : inputs.semanticFiles()) {
      semantic.add(
          row(
              "semantic_layer",
              EdwJourneyModelLookup.basename(file),
              "link",
              EdwJourneyLink.open("hsl", file, "", "presentation")));
    }
    List<Map<String, String>> jdbc = new ArrayList<>();
    for (JdbcService service : inputs.jdbcServices()) {
      jdbc.add(
          row(
              "service",
              service.name(),
              "model",
              EdwJourneyModelLookup.basename(service.modelFile()),
              "link",
              EdwJourneyLink.open("hsm", service.modelFile(), "", "select")));
    }
    List<Map<String, String>> docs =
        List.of(
            row(
                "audience",
                "SQL tools, BI, Hop GUI, Hop Web",
                "link",
                EdwJourneyLink.action("open-docs")));
    String note = semantic.isEmpty() ? text("EdwJourneyPanel.Empty.Consumption") : null;
    return spec(
        EdwJourneyPanelId.CONSUMPTION,
        snapshot,
        note,
        List.of(),
        List.of(
            table("Semantic layers", List.of("semantic_layer"), semantic),
            table("Hop JDBC services", List.of("service", "model"), jdbc),
            table("Clients", List.of("audience"), docs)));
  }

  private static List<Map<String, String>> modelTable(
      List<ModelRef> models, EdwJourneyOpsOverlay overlay, String ops) {
    List<Map<String, String>> rows = new ArrayList<>();
    for (ModelRef model : models) {
      ModelLoadSummary load = findLoad(overlay, model.displayName());
      String verbPipeline = "dm".equals(ops) ? "pipeline" : "select";
      rows.add(
          row(
              "model",
              model.displayName(),
              "tables",
              String.valueOf(model.tableNames().size()),
              "duration",
              load == null ? "" : EdwJourneyOpsDecorations.formatDuration(load.durationMs()),
              "errors",
              load == null ? "" : number(load.errors()),
              "success",
              load == null || load.success() == null ? "" : String.valueOf(load.success()),
              "link",
              EdwJourneyLink.open(ops, model.storedPath(), "", "select"),
              "edit_link",
              "dm".equals(ops) ? "" : EdwJourneyLink.open(ops, model.storedPath(), "", "edit"),
              "pipeline_link",
              "dm".equals(ops)
                  ? EdwJourneyLink.open(ops, model.storedPath(), "", verbPipeline)
                  : ""));
    }
    return rows;
  }

  private static ModelLoadSummary findLoad(EdwJourneyOpsOverlay overlay, String name) {
    for (ModelLoadSummary summary : overlay.modelLoads()) {
      if (summary != null && name != null && name.equals(summary.modelName())) {
        return summary;
      }
    }
    return null;
  }

  private static Map<String, String> stage(
      String name, List<ModelRef> models, String layer, EdwJourneyPanelId panel) {
    String state = models == null || models.isEmpty() ? "empty" : "present";
    Map<String, String> row =
        row("stage", name, "state", state, "link", EdwJourneyLink.panel(panel));
    if (!models.isEmpty() && !layer.isBlank()) {
      row.put("edit_link", EdwJourneyLink.open(layer, models.get(0).storedPath(), "", "select"));
    }
    return row;
  }

  private static Map<String, String> gap(String text, String action) {
    return row("gap", text, "link", EdwJourneyLink.action(action));
  }

  private static boolean hasWarehouse(EdwJourneySnapshot snapshot) {
    return !snapshot.dataVaultModels().isEmpty()
        || !snapshot.businessVaultModels().isEmpty()
        || !snapshot.dimensionalModels().isEmpty();
  }

  private static boolean hasOpsAnalytics(EdwJourneySnapshot snapshot) {
    return pathHas(snapshot.sourceModels(), "ops-analytics")
        || pathHas(snapshot.dataVaultModels(), "ops-analytics")
        || pathHas(snapshot.dimensionalModels(), "ops-analytics");
  }

  private static boolean pathHas(List<ModelRef> models, String token) {
    for (ModelRef model : models) {
      if (model.storedPath() != null && model.storedPath().contains(token)) {
        return true;
      }
    }
    return false;
  }

  private static String workflowLink(EdwJourneySnapshot snapshot, String workflow) {
    if (workflow == null) {
      return "";
    }
    for (EdwJourneySnapshot.WorkflowRef ref : snapshot.workflows()) {
      if (workflow.equals(ref.workflowName())
          || workflow.equals(EdwJourneyModelLookup.basename(ref.storedPath()))) {
        return EdwJourneyLink.open("workflow", ref.storedPath(), "", "select");
      }
    }
    return "";
  }

  private static String semanticFor(List<String> files, String hdm) {
    if (hdm == null) {
      return null;
    }
    String stem = hdm.replaceAll("(?i)\\.hdm$", "");
    for (String file : files) {
      if (file != null
          && file.replaceAll("(?i)\\.hsl$", "").endsWith(EdwJourneyModelLookup.basename(stem))) {
        return file;
      }
    }
    return files.isEmpty() ? null : null;
  }

  private static int modelCount(EdwJourneySnapshot snapshot) {
    return snapshot.sourceModels().size()
        + snapshot.dataVaultModels().size()
        + snapshot.businessVaultModels().size()
        + snapshot.dimensionalModels().size();
  }

  private static long sumBlocking(QualityRunSummary pre, QualityRunSummary post) {
    return nz(pre == null ? null : pre.blockingCount())
        + nz(post == null ? null : post.blockingCount());
  }

  private static long sumFindings(QualityRunSummary pre, QualityRunSummary post) {
    return nz(pre == null ? null : pre.findingCount())
        + nz(post == null ? null : post.findingCount());
  }

  private static long nz(Long value) {
    return value == null ? 0L : value;
  }

  private static String number(Long value) {
    return value == null ? "0" : String.valueOf(value);
  }

  private static String stamp(Date when, int index) {
    if (when == null) {
      return "run " + index;
    }
    return new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(when);
  }

  private static EdwJourneyPanelSpec spec(
      EdwJourneyPanelId id,
      EdwJourneySnapshot snapshot,
      String note,
      List<ChartSpec> charts,
      List<TableSpec> tables) {
    String group = snapshot.groupName() == null ? "" : snapshot.groupName();
    String title = text("EdwJourneyPanel." + id.name());
    if (!group.isBlank()) {
      title = title + " — " + group;
    }
    return new EdwJourneyPanelSpec(id, title, note, charts, tables);
  }

  private static ChartSpec chart(
      String title,
      String category,
      String series,
      String value,
      String linkTemplate,
      List<Map<String, String>> rows) {
    Integer angle = "when".equals(category) ? CATEGORY_LABEL_ANGLE : null;
    return chart(title, category, series, value, linkTemplate, rows, angle);
  }

  private static ChartSpec chart(
      String title,
      String category,
      String series,
      String value,
      String linkTemplate,
      List<Map<String, String>> rows,
      Integer labelAngle) {
    return new ChartSpec(title, category, series, value, linkTemplate, rows, labelAngle);
  }

  private static TableSpec table(
      String title, List<String> columns, List<Map<String, String>> rows) {
    return new TableSpec(title, columns, rows);
  }

  private static Map<String, String> row(String... pairs) {
    Map<String, String> row = new LinkedHashMap<>();
    for (int i = 0; i + 1 < pairs.length; i += 2) {
      row.put(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
    }
    return row;
  }

  private static String text(String key) {
    return BaseMessages.getString(PKG, key);
  }
}
