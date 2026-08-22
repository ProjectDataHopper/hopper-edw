/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.hopgui.perspective.journey;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestSubjectSummary;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader.HistoryConnection;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.EdwJourneyProblem;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.LoadOverviewSummary;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.ModelLoadSummary;
import org.apache.hop.datavault.metrics.LoadRunMetricsCatalogPublisher;
import org.apache.hop.datavault.metrics.MetricsAiContextBuilder;
import org.apache.hop.datavault.metrics.WorkflowLoadOverviewDdlSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.quality.history.DataQualityHistoryReader;
import org.apache.hop.quality.history.DataQualityHistoryReader.FindingEntry;
import org.apache.hop.quality.history.DataQualityHistoryReader.QualityHistoryTablesMissingException;
import org.apache.hop.quality.history.DataQualityHistoryReader.QualityRunSummary;
import org.apache.hop.quality.model.QualityLifecycle;

/** Loads last-run harvest / quality / load facts from OPS. Never throws to the GUI. */
public final class EdwJourneyOpsOverlayLoader {

  static final int MAX_PROBLEMS = 25;
  static final int MAX_FINDINGS = 15;

  private EdwJourneyOpsOverlayLoader() {}

  public static EdwJourneyOpsOverlay load(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (group == null || Utils.isEmpty(group.getName()) || metadataProvider == null) {
      return EdwJourneyOpsOverlay.empty();
    }
    String catalog = Const.NVL(group.getDataCatalogConnection(), "");
    if (variables != null && !Utils.isEmpty(catalog)) {
      catalog = variables.resolve(catalog);
    }
    List<EdwJourneyProblem> problems = new ArrayList<>();
    HarvestRunSummary harvest =
        loadHarvest(group.getName(), catalog, variables, metadataProvider, problems);
    QualityRunSummary sourceQuality =
        loadQuality(
            QualityLifecycle.PRE_UPDATE.name(), catalog, variables, metadataProvider, problems);
    QualityRunSummary targetQuality =
        loadQuality(
            QualityLifecycle.POST_UPDATE.name(), catalog, variables, metadataProvider, problems);
    LoadBundle loadBundle = loadOverview(variables, metadataProvider, problems);
    boolean anyConfigured =
        harvest != null
            || sourceQuality != null
            || targetQuality != null
            || loadBundle.summary() != null
            || !problems.isEmpty();
    if (!anyConfigured
        && harvest == null
        && sourceQuality == null
        && loadBundle.summary() == null) {
      HistoryConnection harvestConn = harvestConnection(catalog, variables, metadataProvider);
      DataQualityHistoryReader.HistoryConnection qualityConn =
          qualityConnection(catalog, variables, metadataProvider);
      if (harvestConn == null && qualityConn == null && loadBundle.databaseMissing()) {
        return EdwJourneyOpsOverlay.unavailable(
            "No OPS harvest, quality, or load-metrics connection is configured.");
      }
    }
    return new EdwJourneyOpsOverlay(
        null,
        harvest,
        sourceQuality,
        targetQuality,
        loadBundle.summary(),
        loadBundle.models(),
        problems);
  }

  public static String opsModelType(String snapshotModelType) {
    if (EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT.equals(snapshotModelType)) {
      return "dv";
    }
    if (EdwJourneySnapshot.MODEL_TYPE_BUSINESS_VAULT.equals(snapshotModelType)) {
      return "bv";
    }
    if (EdwJourneySnapshot.MODEL_TYPE_DIMENSIONAL.equals(snapshotModelType)) {
      return "dm";
    }
    return null;
  }

  public static ModelLoadSummary modelLoad(
      EdwJourneyOpsOverlay overlay, String modelName, String opsType) {
    if (overlay == null || Utils.isEmpty(modelName)) {
      return null;
    }
    for (ModelLoadSummary summary : overlay.modelLoads()) {
      if (summary != null
          && modelName.equalsIgnoreCase(summary.modelName())
          && (opsType == null || opsType.equalsIgnoreCase(summary.opsModelType()))) {
        return summary;
      }
    }
    return null;
  }

  private static HarvestRunSummary loadHarvest(
      String groupName,
      String catalog,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      List<EdwJourneyProblem> problems) {
    try {
      HistoryConnection connection =
          SchemaHarvestHistoryReader.resolveConnection(
              null, null, catalog, variables, metadataProvider);
      if (connection == null) {
        return null;
      }
      DatabaseMeta databaseMeta =
          SchemaHarvestHistoryReader.loadDatabaseMeta(
              connection.databaseMetaName(), metadataProvider);
      if (databaseMeta == null) {
        return null;
      }
      List<HarvestRunSummary> runs =
          SchemaHarvestHistoryReader.listRuns(
              databaseMeta, connection.schemaName(), groupName, variables, 1);
      if (runs.isEmpty()) {
        return null;
      }
      HarvestRunSummary last = runs.get(0);
      if (last.changeCount() != null && last.changeCount() > 0) {
        problems.add(
            new EdwJourneyProblem(
                "WARNING",
                "harvest",
                groupName,
                last.changeCount() + " schema change(s) on last harvest"));
        try {
          List<HarvestSubjectSummary> changed =
              SchemaHarvestHistoryReader.listSubjectsForRun(
                  databaseMeta,
                  connection.schemaName(),
                  last.harvestRunId(),
                  null,
                  null,
                  true,
                  variables);
          int added = 0;
          for (HarvestSubjectSummary subject : changed) {
            if (added >= MAX_FINDINGS || problems.size() >= MAX_PROBLEMS) {
              break;
            }
            problems.add(
                new EdwJourneyProblem(
                    "WARNING",
                    "harvest",
                    Const.NVL(subject.tableName(), subject.subjectKey()),
                    Const.NVL(subject.message(), subject.discoveryStatus())
                        + " ("
                        + Const.NVL(String.valueOf(subject.changeCount()), "?")
                        + " changes)"));
            added++;
          }
        } catch (Exception ignored) {
          // Keep the harvest summary even if subject drill-down fails.
        }
      }
      if (last.errorCount() != null && last.errorCount() > 0) {
        problems.add(
            new EdwJourneyProblem(
                "ERROR", "harvest", groupName, last.errorCount() + " harvest subject error(s)"));
      }
      return last;
    } catch (HopException e) {
      if (e.getMessage() != null && e.getMessage().contains("missing")) {
        return null;
      }
      problems.add(new EdwJourneyProblem("INFO", "harvest", groupName, message(e)));
      return null;
    } catch (Exception e) {
      problems.add(new EdwJourneyProblem("INFO", "harvest", groupName, message(e)));
      return null;
    }
  }

  private static QualityRunSummary loadQuality(
      String lifecycle,
      String catalog,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      List<EdwJourneyProblem> problems) {
    try {
      DataQualityHistoryReader.HistoryConnection connection =
          DataQualityHistoryReader.resolveConnection(catalog, variables, metadataProvider);
      if (connection == null) {
        return null;
      }
      DatabaseMeta databaseMeta =
          DataQualityHistoryReader.loadDatabaseMeta(
              connection.databaseMetaName(), metadataProvider);
      if (databaseMeta == null) {
        return null;
      }
      List<QualityRunSummary> runs =
          DataQualityHistoryReader.listRecentRuns(
              databaseMeta, connection.schemaName(), lifecycle, variables, 1);
      if (runs.isEmpty()) {
        return null;
      }
      QualityRunSummary last = runs.get(0);
      if (last.blockingCount() != null && last.blockingCount() > 0) {
        problems.add(
            new EdwJourneyProblem(
                "ERROR",
                "quality",
                lifecycle,
                last.blockingCount() + " blocking quality finding(s)"));
        List<FindingEntry> findings =
            DataQualityHistoryReader.listFindings(
                databaseMeta, connection.schemaName(), last.qualityRunId(), null, variables);
        int added = 0;
        for (FindingEntry finding : findings) {
          if (added >= MAX_FINDINGS || problems.size() >= MAX_PROBLEMS) {
            break;
          }
          if (!"ERROR".equalsIgnoreCase(finding.severity())
              && !"BLOCKING".equalsIgnoreCase(finding.severity())) {
            continue;
          }
          problems.add(
              new EdwJourneyProblem(
                  "ERROR",
                  "quality",
                  Const.NVL(finding.subjectKey(), finding.fieldName()),
                  Const.NVL(finding.message(), finding.ruleName())));
          added++;
        }
      } else if (last.findingCount() != null && last.findingCount() > 0) {
        problems.add(
            new EdwJourneyProblem(
                "WARNING", "quality", lifecycle, last.findingCount() + " quality finding(s)"));
      }
      return last;
    } catch (QualityHistoryTablesMissingException e) {
      return null;
    } catch (Exception e) {
      problems.add(new EdwJourneyProblem("INFO", "quality", lifecycle, message(e)));
      return null;
    }
  }

  private static LoadBundle loadOverview(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      List<EdwJourneyProblem> problems) {
    String databaseName =
        MetricsAiContextBuilder.resolveMetricsDatabaseName(metadataProvider, variables);
    if (Utils.isEmpty(databaseName)) {
      return LoadBundle.missingDatabase();
    }
    try {
      DatabaseMeta databaseMeta =
          metadataProvider.getSerializer(DatabaseMeta.class).load(variables.resolve(databaseName));
      if (databaseMeta == null) {
        return LoadBundle.missingDatabase();
      }
      String schema =
          LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(
              MetricsAiContextBuilder.resolveOperationsSchema(metadataProvider, variables),
              databaseMeta);
      LoggingObject loggingObject = new LoggingObject(EdwJourneyOpsOverlayLoader.class);
      Database db = new Database(loggingObject, variables, databaseMeta);
      db.connect();
      try {
        if (!db.checkTableExists(
            schema, WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW)) {
          return new LoadBundle(null, List.of(), false);
        }
        String overviewTable =
            databaseMeta.getQuotedSchemaTableCombination(
                db, schema, WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW);
        String sql =
            "SELECT overview_id, workflow_execution_id, root_workflow_name, finished_at,"
                + " duration_ms, model_count, total_errors, success FROM "
                + overviewTable
                + " ORDER BY finished_at DESC";
        db.setQueryLimit(1);
        List<Object[]> rows = db.getRows(sql, 1);
        IRowMeta rowMeta = db.getReturnRowMeta();
        if (rows == null || rows.isEmpty() || rowMeta == null) {
          return new LoadBundle(null, List.of(), false);
        }
        Object[] row = rows.get(0);
        String overviewId = stringVal(rowMeta, row, "overview_id");
        Long errors = longVal(rowMeta, row, "total_errors");
        Boolean success = boolVal(rowMeta, row, "success");
        LoadOverviewSummary summary =
            new LoadOverviewSummary(
                stringVal(rowMeta, row, "workflow_execution_id"),
                stringVal(rowMeta, row, "root_workflow_name"),
                dateVal(rowMeta, row, "finished_at"),
                longVal(rowMeta, row, "duration_ms"),
                longVal(rowMeta, row, "model_count"),
                errors,
                success);
        if (errors != null && errors > 0) {
          problems.add(
              new EdwJourneyProblem(
                  "ERROR", "load", summary.rootWorkflowName(), errors + " load error(s)"));
        } else if (Boolean.FALSE.equals(success)) {
          problems.add(
              new EdwJourneyProblem(
                  "ERROR", "load", summary.rootWorkflowName(), "Last load did not succeed"));
        }
        List<ModelLoadSummary> models =
            listModelLoads(db, databaseMeta, schema, overviewId, variables);
        return new LoadBundle(summary, models, false);
      } finally {
        db.disconnect();
      }
    } catch (Exception e) {
      problems.add(new EdwJourneyProblem("INFO", "load", "", message(e)));
      return new LoadBundle(null, List.of(), false);
    }
  }

  private static List<ModelLoadSummary> listModelLoads(
      Database db,
      DatabaseMeta databaseMeta,
      String schema,
      String overviewId,
      IVariables variables)
      throws HopException {
    if (Utils.isEmpty(overviewId)
        || !db.checkTableExists(
            schema, WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL)) {
      return List.of();
    }
    String table =
        databaseMeta.getQuotedSchemaTableCombination(
            db, schema, WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL);
    String sql =
        "SELECT model_type, model_name, duration_ms, errors, success FROM "
            + table
            + " WHERE overview_id = '"
            + overviewId.replace("'", "''")
            + "' ORDER BY sequence_no";
    List<Object[]> rows = db.getRows(sql, 200);
    IRowMeta rowMeta = db.getReturnRowMeta();
    if (rows == null || rows.isEmpty() || rowMeta == null) {
      return List.of();
    }
    List<ModelLoadSummary> models = new ArrayList<>();
    for (Object[] row : rows) {
      models.add(
          new ModelLoadSummary(
              stringVal(rowMeta, row, "model_type"),
              stringVal(rowMeta, row, "model_name"),
              longVal(rowMeta, row, "duration_ms"),
              longVal(rowMeta, row, "errors"),
              boolVal(rowMeta, row, "success"),
              null));
    }
    return models;
  }

  private static HistoryConnection harvestConnection(
      String catalog, IVariables variables, IHopMetadataProvider metadataProvider) {
    try {
      return SchemaHarvestHistoryReader.resolveConnection(
          null, null, catalog, variables, metadataProvider);
    } catch (Exception e) {
      return null;
    }
  }

  private static DataQualityHistoryReader.HistoryConnection qualityConnection(
      String catalog, IVariables variables, IHopMetadataProvider metadataProvider) {
    try {
      return DataQualityHistoryReader.resolveConnection(catalog, variables, metadataProvider);
    } catch (Exception e) {
      return null;
    }
  }

  private static String message(Exception e) {
    if (e == null) {
      return "";
    }
    return Const.NVL(e.getMessage(), e.getClass().getSimpleName());
  }

  private static String stringVal(IRowMeta rowMeta, Object[] row, String field) {
    try {
      int index = rowMeta.indexOfValue(field);
      if (index < 0) {
        for (int i = 0; i < rowMeta.size(); i++) {
          if (rowMeta.getValueMeta(i).getName() != null
              && rowMeta.getValueMeta(i).getName().equalsIgnoreCase(field)) {
            index = i;
            break;
          }
        }
      }
      if (index < 0) {
        return null;
      }
      return rowMeta.getValueMeta(index).getString(row[index]);
    } catch (Exception e) {
      return null;
    }
  }

  private static Long longVal(IRowMeta rowMeta, Object[] row, String field) {
    try {
      String text = stringVal(rowMeta, row, field);
      if (Utils.isEmpty(text)) {
        int index = rowMeta.indexOfValue(field);
        if (index < 0) {
          return null;
        }
        Object value = row[index];
        if (value instanceof Number n) {
          return n.longValue();
        }
        return rowMeta.getValueMeta(index).getInteger(value);
      }
      return Long.parseLong(text);
    } catch (Exception e) {
      return null;
    }
  }

  private static Boolean boolVal(IRowMeta rowMeta, Object[] row, String field) {
    try {
      int index = rowMeta.indexOfValue(field);
      if (index < 0) {
        return null;
      }
      Object value = row[index];
      if (value instanceof Boolean b) {
        return b;
      }
      String text = rowMeta.getValueMeta(index).getString(value);
      return "Y".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text) || "1".equals(text);
    } catch (Exception e) {
      return null;
    }
  }

  private static Date dateVal(IRowMeta rowMeta, Object[] row, String field) {
    try {
      int index = rowMeta.indexOfValue(field);
      if (index < 0) {
        return null;
      }
      Object value = row[index];
      if (value instanceof Date date) {
        return date;
      }
      return rowMeta.getValueMeta(index).getDate(value);
    } catch (Exception e) {
      return null;
    }
  }

  private record LoadBundle(
      LoadOverviewSummary summary, List<ModelLoadSummary> models, boolean databaseMissing) {
    static LoadBundle missingDatabase() {
      return new LoadBundle(null, List.of(), true);
    }
  }
}
