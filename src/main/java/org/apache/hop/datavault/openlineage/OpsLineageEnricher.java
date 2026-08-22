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
package org.apache.hop.datavault.openlineage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metrics.LoadRunMetricsCatalogPublisher;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Optionally attaches last-load operational facts from {@code load_pipeline_metric} onto
 * OpenLineage run facets.
 *
 * <p>Best-effort: missing tables or connections produce warnings, not hard failures.
 */
public final class OpsLineageEnricher {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private OpsLineageEnricher() {}

  public static void enrich(
      List<ObjectNode> events,
      OpenLineageExportOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log,
      OpenLineageExportResult result) {
    if (events == null || events.isEmpty() || options == null) {
      return;
    }
    if (Utils.isEmpty(options.getOpsDatabase()) || metadataProvider == null) {
      result.addWarning(
          "Operational metrics requested but ops database connection is not configured; skipping enrichment");
      return;
    }
    try {
      DatabaseMeta databaseMeta =
          metadataProvider.getSerializer(DatabaseMeta.class).load(options.getOpsDatabase());
      if (databaseMeta == null) {
        result.addWarning("Ops database connection not found: " + options.getOpsDatabase());
        return;
      }
      IVariables vars = variables != null ? variables : new Variables();
      Map<String, PipelineMetricRow> byPipeline =
          loadLatestMetrics(databaseMeta, options, vars, log);
      if (byPipeline.isEmpty()) {
        result.addWarning("No load_pipeline_metric rows found for operational enrichment");
        return;
      }
      int enriched = 0;
      for (ObjectNode event : events) {
        String jobName = event.path("job").path("name").asText("");
        String tableSegment = tableSegment(jobName);
        PipelineMetricRow metric = findMetric(byPipeline, tableSegment, jobName);
        if (metric == null) {
          continue;
        }
        ObjectNode run = (ObjectNode) event.get("run");
        if (run == null) {
          continue;
        }
        ObjectNode facets =
            run.has("facets") ? (ObjectNode) run.get("facets") : MAPPER.createObjectNode();
        ObjectNode ops = MAPPER.createObjectNode();
        ops.put("_producer", OpenLineageConstants.PRODUCER);
        ops.put("_schemaURL", "https://github.com/ProjectDataHopper/hopper-edw#hop-ops-facet");
        if (metric.lastSuccessAt != null) {
          ops.put("lastSuccessAt", metric.lastSuccessAt);
        }
        if (metric.runId != null) {
          ops.put("loadRunId", metric.runId);
        }
        if (metric.pipelineName != null) {
          ops.put("pipelineName", metric.pipelineName);
        }
        if (metric.durationMs != null) {
          ops.put("durationMs", metric.durationMs);
        }
        facets.set("hop_ops", ops);
        run.set("facets", facets);
        enriched++;
      }
      if (log != null) {
        log.logBasic("OpenLineage ops enrichment applied to " + enriched + " event(s)");
      }
    } catch (Exception e) {
      result.addWarning("Operational metrics enrichment failed: " + e.getMessage());
    }
  }

  private static Map<String, PipelineMetricRow> loadLatestMetrics(
      DatabaseMeta databaseMeta,
      OpenLineageExportOptions options,
      IVariables variables,
      ILogChannel log)
      throws Exception {
    Map<String, PipelineMetricRow> map = new HashMap<>();
    String schema =
        Utils.isEmpty(options.getOpsSchema())
            ? LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME
            : options.getOpsSchema();
    String table = LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC;
    String qualified = databaseMeta.getQuotedSchemaTableCombination(variables, schema, table);

    Database db =
        new Database(new LoggingObject("OpenLineageOpsEnricher"), variables, databaseMeta);
    try {
      db.connect();
      String sql =
          "SELECT pipeline_name, run_id, duration_ms, execution_end_date FROM "
              + qualified
              + " ORDER BY execution_end_date DESC";
      try (Statement st = db.getConnection().createStatement();
          ResultSet rs = st.executeQuery(sql)) {
        while (rs.next()) {
          String pipelineName = rs.getString(1);
          if (Utils.isEmpty(pipelineName) || map.containsKey(pipelineName.toLowerCase())) {
            continue;
          }
          PipelineMetricRow row = new PipelineMetricRow();
          row.pipelineName = pipelineName;
          row.runId = rs.getString(2);
          long duration = rs.getLong(3);
          if (!rs.wasNull()) {
            row.durationMs = duration;
          }
          java.sql.Timestamp finished = rs.getTimestamp(4);
          if (finished != null) {
            row.lastSuccessAt = finished.toInstant().toString();
          }
          map.put(pipelineName.toLowerCase(), row);
        }
      } catch (Exception primary) {
        String fallback = "SELECT pipeline_name, run_id FROM " + qualified;
        try (Statement st = db.getConnection().createStatement();
            ResultSet rs = st.executeQuery(fallback)) {
          while (rs.next()) {
            String pipelineName = rs.getString(1);
            if (Utils.isEmpty(pipelineName) || map.containsKey(pipelineName.toLowerCase())) {
              continue;
            }
            PipelineMetricRow row = new PipelineMetricRow();
            row.pipelineName = pipelineName;
            row.runId = rs.getString(2);
            map.put(pipelineName.toLowerCase(), row);
          }
        } catch (Exception secondary) {
          if (log != null) {
            log.logBasic(
                "Could not query load_pipeline_metric ("
                    + secondary.getMessage()
                    + "); primary error: "
                    + primary.getMessage());
          }
        }
      }
    } finally {
      db.disconnect();
    }
    return map;
  }

  private static String tableSegment(String jobName) {
    if (Utils.isEmpty(jobName)) {
      return "";
    }
    int slash = jobName.lastIndexOf('/');
    return slash >= 0 ? jobName.substring(slash + 1) : jobName;
  }

  private static PipelineMetricRow findMetric(
      Map<String, PipelineMetricRow> byPipeline, String tableSegment, String jobName) {
    if (!Utils.isEmpty(tableSegment)) {
      for (Map.Entry<String, PipelineMetricRow> e : byPipeline.entrySet()) {
        if (e.getKey().contains(tableSegment.toLowerCase())) {
          return e.getValue();
        }
      }
    }
    if (!Utils.isEmpty(jobName) && byPipeline.containsKey(jobName.toLowerCase())) {
      return byPipeline.get(jobName.toLowerCase());
    }
    return null;
  }

  private static final class PipelineMetricRow {
    private String pipelineName;
    private String runId;
    private String lastSuccessAt;
    private Long durationMs;
  }
}
