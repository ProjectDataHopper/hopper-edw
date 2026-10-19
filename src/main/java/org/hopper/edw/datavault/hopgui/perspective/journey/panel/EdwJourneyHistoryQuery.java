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
import java.util.Date;
import java.util.List;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.LoadOverviewSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyHistory.FindingRef;
import org.hopper.edw.datavault.metrics.LoadRunMetricsCatalogPublisher;
import org.hopper.edw.datavault.metrics.MetricsAiContextBuilder;
import org.hopper.edw.datavault.metrics.WorkflowLoadOverviewDdlSupport;
import org.hopper.edw.quality.history.DataQualityHistoryReader;
import org.hopper.edw.quality.history.DataQualityHistoryReader.FindingEntry;
import org.hopper.edw.quality.history.DataQualityHistoryReader.QualityHistoryTablesMissingException;
import org.hopper.edw.quality.history.DataQualityHistoryReader.QualityRunSummary;

/** Reads the last {@link EdwJourneyHistory#LIMIT} OPS runs. Never throws to the Journey. */
public final class EdwJourneyHistoryQuery {

  private EdwJourneyHistoryQuery() {}

  public static EdwJourneyHistory load(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (group == null || metadataProvider == null) {
      return EdwJourneyHistory.empty("No resource definition group is selected.");
    }
    String catalog = group.getDataCatalogConnection();
    if (variables != null && !Utils.isEmpty(catalog)) {
      catalog = variables.resolve(catalog);
    }
    List<HarvestRunSummary> harvest =
        harvest(group.getName(), catalog, variables, metadataProvider);
    List<QualityRunSummary> quality = quality(catalog, variables, metadataProvider);
    List<EdwJourneyHistory.FindingRollup> findings =
        EdwJourneyHistory.rollup(findingWindow(quality, catalog, variables, metadataProvider));
    List<LoadOverviewSummary> waves = waves(variables, metadataProvider);
    String reason = null;
    if (harvest.isEmpty() && quality.isEmpty() && waves.isEmpty()) {
      reason = "No harvest, quality, or load history for this group yet.";
    }
    return new EdwJourneyHistory(reason, harvest, quality, waves, findings);
  }

  private static List<HarvestRunSummary> harvest(
      String groupName,
      String catalog,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    try {
      SchemaHarvestHistoryReader.HistoryConnection connection =
          SchemaHarvestHistoryReader.resolveConnection(
              null, null, catalog, variables, metadataProvider);
      if (connection == null) {
        return List.of();
      }
      DatabaseMeta databaseMeta =
          SchemaHarvestHistoryReader.loadDatabaseMeta(
              connection.databaseMetaName(), metadataProvider);
      if (databaseMeta == null) {
        return List.of();
      }
      return SchemaHarvestHistoryReader.listRuns(
          databaseMeta, connection.schemaName(), groupName, variables, EdwJourneyHistory.LIMIT);
    } catch (Exception e) {
      return List.of();
    }
  }

  private static List<QualityRunSummary> quality(
      String catalog, IVariables variables, IHopMetadataProvider metadataProvider) {
    try {
      DataQualityHistoryReader.HistoryConnection connection =
          DataQualityHistoryReader.resolveConnection(catalog, variables, metadataProvider);
      if (connection == null) {
        return List.of();
      }
      DatabaseMeta databaseMeta =
          DataQualityHistoryReader.loadDatabaseMeta(
              connection.databaseMetaName(), metadataProvider);
      if (databaseMeta == null) {
        return List.of();
      }
      return DataQualityHistoryReader.listRecentRuns(
          databaseMeta, connection.schemaName(), null, variables, EdwJourneyHistory.LIMIT);
    } catch (QualityHistoryTablesMissingException e) {
      return List.of();
    } catch (Exception e) {
      return List.of();
    }
  }

  private static List<List<FindingRef>> findingWindow(
      List<QualityRunSummary> runs,
      String catalog,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    List<List<FindingRef>> window = new ArrayList<>();
    if (runs == null || runs.isEmpty()) {
      return window;
    }
    try {
      DataQualityHistoryReader.HistoryConnection connection =
          DataQualityHistoryReader.resolveConnection(catalog, variables, metadataProvider);
      if (connection == null) {
        return window;
      }
      DatabaseMeta databaseMeta =
          DataQualityHistoryReader.loadDatabaseMeta(
              connection.databaseMetaName(), metadataProvider);
      if (databaseMeta == null) {
        return window;
      }
      int limit = Math.min(EdwJourneyHistory.CHRONIC_WINDOW, runs.size());
      for (int i = 0; i < limit; i++) {
        List<FindingEntry> entries =
            DataQualityHistoryReader.listFindings(
                databaseMeta, connection.schemaName(), runs.get(i).qualityRunId(), null, variables);
        List<FindingRef> refs = new ArrayList<>();
        for (FindingEntry entry : entries) {
          refs.add(new FindingRef(entry.subjectKey(), entry.ruleName(), entry.severity()));
        }
        window.add(refs);
      }
    } catch (Exception e) {
      return window;
    }
    return window;
  }

  private static List<LoadOverviewSummary> waves(
      IVariables variables, IHopMetadataProvider metadataProvider) {
    try {
      String databaseName =
          MetricsAiContextBuilder.resolveMetricsDatabaseName(metadataProvider, variables);
      if (Utils.isEmpty(databaseName)) {
        return List.of();
      }
      DatabaseMeta databaseMeta =
          metadataProvider.getSerializer(DatabaseMeta.class).load(variables.resolve(databaseName));
      if (databaseMeta == null) {
        return List.of();
      }
      String schema =
          LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(
              MetricsAiContextBuilder.resolveOperationsSchema(metadataProvider, variables),
              databaseMeta);
      Database db =
          new Database(new LoggingObject(EdwJourneyHistoryQuery.class), variables, databaseMeta);
      db.connect();
      try {
        if (!db.checkTableExists(
            schema, WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW)) {
          return List.of();
        }
        String table =
            databaseMeta.getQuotedSchemaTableCombination(
                db, schema, WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW);
        String sql =
            "SELECT workflow_execution_id, root_workflow_name, finished_at, duration_ms,"
                + " model_count, total_errors, success FROM "
                + table
                + " ORDER BY finished_at DESC";
        db.setQueryLimit(EdwJourneyHistory.LIMIT);
        List<Object[]> rows = db.getRows(sql, EdwJourneyHistory.LIMIT);
        IRowMeta rowMeta = db.getReturnRowMeta();
        if (rows == null || rowMeta == null) {
          return List.of();
        }
        List<LoadOverviewSummary> waves = new ArrayList<>();
        for (Object[] row : rows) {
          waves.add(
              new LoadOverviewSummary(
                  stringVal(rowMeta, row, "workflow_execution_id"),
                  stringVal(rowMeta, row, "root_workflow_name"),
                  dateVal(rowMeta, row, "finished_at"),
                  longVal(rowMeta, row, "duration_ms"),
                  longVal(rowMeta, row, "model_count"),
                  longVal(rowMeta, row, "total_errors"),
                  boolVal(rowMeta, row, "success")));
        }
        return waves;
      } finally {
        db.disconnect();
      }
    } catch (Exception e) {
      return List.of();
    }
  }

  private static String stringVal(IRowMeta rowMeta, Object[] row, String column) {
    int index = rowMeta.indexOfValue(column);
    if (index < 0 || row[index] == null) {
      return null;
    }
    return row[index].toString();
  }

  private static Long longVal(IRowMeta rowMeta, Object[] row, String column) {
    int index = rowMeta.indexOfValue(column);
    if (index < 0 || row[index] == null) {
      return null;
    }
    if (row[index] instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.valueOf(row[index].toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Date dateVal(IRowMeta rowMeta, Object[] row, String column) {
    int index = rowMeta.indexOfValue(column);
    if (index < 0 || !(row[index] instanceof Date date)) {
      return null;
    }
    return date;
  }

  private static Boolean boolVal(IRowMeta rowMeta, Object[] row, String column) {
    int index = rowMeta.indexOfValue(column);
    if (index < 0 || row[index] == null) {
      return null;
    }
    if (row[index] instanceof Boolean value) {
      return value;
    }
    String text = row[index].toString();
    if ("Y".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text) || "1".equals(text)) {
      return true;
    }
    if ("N".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text) || "0".equals(text)) {
      return false;
    }
    return null;
  }
}
