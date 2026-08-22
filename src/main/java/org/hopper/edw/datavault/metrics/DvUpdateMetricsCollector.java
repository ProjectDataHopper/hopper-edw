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
package org.hopper.edw.datavault.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.hop.core.Result;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Thread-safe in-memory collector for metrics gathered during one Data Vault update run. */
public final class DvUpdateMetricsCollector {

  private static final Class<?> PKG = DvUpdateMetricsCollector.class;

  private static final String COL_TYPE = "Type";
  private static final String COL_TABLE = "Table";
  private static final String COL_SOURCE = "Source";
  private static final String COL_SOURCE_READ = "Source Read";
  private static final String COL_TARGET_READ = "Target Read";
  private static final String COL_INSERTED = "Inserted";
  private static final String COL_ERRORS = "Errors";

  private static final ConcurrentMap<String, List<DvUpdateTableMetrics>> METRICS_BY_RUN =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, Date> RUN_STARTED_AT_BY_RUN_ID =
      new ConcurrentHashMap<>();

  private DvUpdateMetricsCollector() {}

  public static void markRunStarted(String runId) {
    if (Utils.isEmpty(runId)) {
      return;
    }
    RUN_STARTED_AT_BY_RUN_ID.put(runId, new Date());
  }

  static Date consumeRunStartedAt(String runId) {
    if (Utils.isEmpty(runId)) {
      return null;
    }
    return RUN_STARTED_AT_BY_RUN_ID.remove(runId);
  }

  public static void record(DvUpdateTableMetrics metrics) {
    if (metrics == null || Utils.isEmpty(metrics.getRunId())) {
      return;
    }
    METRICS_BY_RUN
        .computeIfAbsent(metrics.getRunId(), id -> Collections.synchronizedList(new ArrayList<>()))
        .add(metrics);
  }

  public static List<DvUpdateTableMetrics> removeRun(String runId) {
    if (Utils.isEmpty(runId)) {
      return List.of();
    }
    List<DvUpdateTableMetrics> metrics = METRICS_BY_RUN.remove(runId);
    if (metrics == null) {
      return List.of();
    }
    synchronized (metrics) {
      List<DvUpdateTableMetrics> copy = new ArrayList<>(metrics);
      copy.sort(
          Comparator.comparing(DvUpdateTableMetrics::getTableType)
              .thenComparing(DvUpdateTableMetrics::getTableName)
              .thenComparing(DvUpdateTableMetrics::getSourceName));
      return copy;
    }
  }

  /** Sums source reads, target inserts, and errors across all pipelines in a run. */
  public static DvUpdateRunTotals aggregateTotals(List<DvUpdateTableMetrics> metrics) {
    if (metrics == null || metrics.isEmpty()) {
      return DvUpdateRunTotals.EMPTY;
    }
    long sourceRowsRead = 0;
    long targetRowsInserted = 0;
    long errors = 0;
    for (DvUpdateTableMetrics entry : metrics) {
      sourceRowsRead += entry.getSourceRowsRead();
      targetRowsInserted += entry.getTargetRowsInserted();
      errors += entry.getErrors();
    }
    return new DvUpdateRunTotals(sourceRowsRead, targetRowsInserted, errors);
  }

  /**
   * Sets aggregated DV pipeline metrics on a Hop {@link Result}. Source table reads map to input,
   * target table inserts map to output; target table reads are not included.
   */
  public static void applyToResult(Result result, DvUpdateRunTotals totals) {
    applyToResult(result, new LoadRunPublishSummary(totals, null, 0, 0));
  }

  public static void applyToResult(Result result, LoadRunPublishSummary summary) {
    if (result == null || summary == null || summary.getTotals() == null) {
      return;
    }
    DvUpdateRunTotals totals = summary.getTotals();
    result.setNrLinesInput(totals.getSourceRowsRead());
    result.setNrLinesOutput(totals.getTargetRowsInserted());
    result.setNrLinesRead(0);
    result.setNrLinesWritten(0);
    long metricsErrors = totals.getErrors();
    if (metricsErrors > 0) {
      result.setNrErrors(result.getNrErrors() + metricsErrors);
      result.setResult(false);
    }
    if (summary.hasMetrics()) {
      result.setLogText(formatResultLogText(summary));
    }
  }

  static String formatResultLogText(LoadRunPublishSummary summary) {
    DvUpdateRunTotals totals = summary.getTotals();
    return BaseMessages.getString(
        PKG,
        "DvUpdateMetricsCollector.Log.ActionResultSummary",
        safe(summary.getRunId()),
        Integer.toString(summary.getPipelineCount()),
        Integer.toString(summary.getInsightCount()),
        Long.toString(totals.getSourceRowsRead()),
        Long.toString(totals.getTargetRowsInserted()),
        Long.toString(totals.getErrors()));
  }

  /**
   * Logs all collected metrics for a run as a table after the orchestrator pipeline has finished.
   * Optionally writes the same metrics as JSON when an output folder is configured.
   *
   * @param log log channel (typically the orchestrator engine log channel)
   * @param runId metrics run id set on the orchestrator
   * @param modelName data vault model name
   * @param logLevel log level from the Data Vault Update action
   * @param metricsOutputFolder optional folder for JSON output; skipped when empty
   * @param logChannelId orchestrator pipeline log channel id used in the JSON filename
   * @param variables variables for folder resolution
   * @return aggregated metrics for the run, or {@link LoadRunPublishSummary#EMPTY} when none
   *     collected
   */
  public static LoadRunPublishSummary publishRunSummary(
      ILogChannel log,
      String runId,
      String modelName,
      LogLevel logLevel,
      String metricsOutputFolder,
      String logChannelId,
      IVariables variables)
      throws HopException {
    return publishRunSummary(
        log, runId, modelName, logLevel, metricsOutputFolder, logChannelId, variables, null, null);
  }

  public static LoadRunPublishSummary publishRunSummary(
      ILogChannel log,
      String runId,
      String modelName,
      LogLevel logLevel,
      String metricsOutputFolder,
      String logChannelId,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      LoadRunPublishContext publishContext)
      throws HopException {
    if (log == null || Utils.isEmpty(runId)) {
      return LoadRunPublishSummary.EMPTY;
    }
    List<DvUpdateTableMetrics> metrics = removeRun(runId);
    if (metrics.isEmpty()) {
      consumeRunStartedAt(runId);
      return LoadRunPublishSummary.EMPTY;
    }

    Date startedAt = consumeRunStartedAt(runId);
    DvUpdateRunTotals totals = aggregateTotals(metrics);

    LogLevel effectiveLevel = logLevel != null ? logLevel : LogLevel.BASIC;

    logAtLevel(
        log,
        effectiveLevel,
        BaseMessages.getString(
            PKG,
            "DvUpdateMetricsCollector.Log.RunSummaryHeader",
            safe(runId),
            safe(modelName),
            Integer.toString(metrics.size())));

    for (String line : formatMetricsTable(metrics)) {
      logAtLevel(log, effectiveLevel, line);
    }

    List<LoadRunInsight> insights =
        publishContext != null
            ? LoadRunInsightEngine.evaluate(
                metrics,
                publishContext.dimLookupPreloadRatioThreshold(),
                publishContext.targetReadRatioThreshold(),
                publishContext.sortRowsRiskThreshold(),
                publishContext.highTransformDurationMs())
            : LoadRunInsightEngine.evaluate(metrics);
    List<LoadRunInsight> reportableInsights = LoadRunInsightSupport.reportableInsights(insights);
    for (LoadRunInsight insight : insights) {
      logAtLevel(
          log, effectiveLevel, "Load insight [" + insight.getCode() + "]: " + insight.getMessage());
    }

    boolean metricsEnabled = !Utils.isEmpty(metricsOutputFolder);
    if (metricsEnabled) {
      DvUpdateMetricsReport report =
          DvUpdateMetricsReport.builder()
              .runId(runId)
              .modelName(modelName)
              .logChannelId(logChannelId)
              .pipelineCount(metrics.size())
              .pipelines(metrics)
              .insights(reportableInsights)
              .build();
      String jsonPath =
          DvUpdateMetricsJsonWriter.writeReport(
              metricsOutputFolder, modelName, logChannelId, report, variables);
      if (!Utils.isEmpty(jsonPath)) {
        logAtLevel(
            log,
            effectiveLevel,
            BaseMessages.getString(PKG, "DvUpdateMetricsCollector.Log.MetricsWritten", jsonPath));
      }

      if (publishContext != null && metadataProvider != null) {
        String modelType =
            resolvePublishValue(
                publishContext.modelType(), variables, DvUpdateMetricsConstants.VAR_MODEL_TYPE);
        String workflowName =
            resolvePublishValue(
                publishContext.workflowName(),
                variables,
                DvUpdateMetricsConstants.VAR_WORKFLOW_NAME);
        String catalogConnection =
            resolvePublishValue(
                publishContext.catalogConnectionName(),
                variables,
                DvUpdateMetricsConstants.VAR_METRICS_CATALOG_CONNECTION);
        String targetDatabase =
            resolvePublishValue(
                publishContext.targetDatabaseName(),
                variables,
                DvUpdateMetricsConstants.VAR_METRICS_DATABASE);
        boolean runSuccess = totals.getErrors() == 0;
        try {
          LoadRunMetricsCatalogPublisher.publish(
              log,
              publishContext,
              runId,
              modelName,
              modelType,
              workflowName,
              logChannelId,
              runSuccess,
              totals.getErrors(),
              startedAt,
              metrics,
              insights,
              variables,
              metadataProvider);
        } catch (HopException e) {
          logAtLevel(
              log,
              effectiveLevel,
              "Load-run metrics catalog/database publish failed; update result unchanged: "
                  + e.getMessage());
          if (log != null) {
            log.logError("Load-run metrics catalog/database publish failed", e);
          }
        }
      }
    }

    return new LoadRunPublishSummary(totals, runId, metrics.size(), reportableInsights.size());
  }

  private static List<String> formatMetricsTable(List<DvUpdateTableMetrics> metrics) {
    List<String[]> rows = new ArrayList<>();
    for (DvUpdateTableMetrics entry : metrics) {
      rows.add(
          new String[] {
            safe(entry.getTableType()),
            safe(entry.getTableName()),
            safe(entry.getSourceName()),
            Long.toString(entry.getSourceRowsRead()),
            Long.toString(entry.getTargetRowsRead()),
            Long.toString(entry.getTargetRowsInserted()),
            Long.toString(entry.getErrors())
          });
    }

    String[] headers = {
      COL_TYPE, COL_TABLE, COL_SOURCE, COL_SOURCE_READ, COL_TARGET_READ, COL_INSERTED, COL_ERRORS
    };

    int[] widths = new int[headers.length];
    for (int i = 0; i < headers.length; i++) {
      widths[i] = headers[i].length();
    }
    for (String[] row : rows) {
      for (int i = 0; i < row.length; i++) {
        widths[i] = Math.max(widths[i], row[i].length());
      }
    }

    List<String> lines = new ArrayList<>();
    lines.add(formatTableRow(headers, widths));
    lines.add(formatTableSeparator(widths));
    for (String[] row : rows) {
      lines.add(formatTableRow(row, widths));
    }
    return lines;
  }

  private static String formatTableRow(String[] cells, int[] widths) {
    StringBuilder row = new StringBuilder("|");
    for (int i = 0; i < cells.length; i++) {
      row.append(' ').append(padRight(cells[i], widths[i])).append(" |");
    }
    return row.toString();
  }

  private static String formatTableSeparator(int[] widths) {
    StringBuilder separator = new StringBuilder("+");
    for (int width : widths) {
      separator.append("-".repeat(width + 2)).append('+');
    }
    return separator.toString();
  }

  private static String padRight(String value, int width) {
    if (value.length() >= width) {
      return value;
    }
    return value + " ".repeat(width - value.length());
  }

  private static void logAtLevel(ILogChannel log, LogLevel level, String message) {
    if (level == null) {
      log.logBasic(message);
      return;
    }
    switch (level) {
      case ERROR -> log.logError(message);
      case MINIMAL -> log.logMinimal(message);
      case BASIC -> log.logBasic(message);
      case DETAILED -> log.logDetailed(message);
      case DEBUG -> log.logDebug(message);
      case ROWLEVEL -> log.logRowlevel(message);
      case NOTHING -> {
        /* suppressed */
      }
      default -> log.logBasic(message);
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private static String resolvePublishValue(
      String configured, IVariables variables, String variableName) {
    if (!Utils.isEmpty(configured)) {
      return configured;
    }
    if (variables != null && !Utils.isEmpty(variables.getVariable(variableName))) {
      return variables.getVariable(variableName);
    }
    return null;
  }

  /** Optional catalog/database targets when metrics collection is enabled. */
  public record LoadRunPublishContext(
      String catalogConnectionName,
      String targetDatabaseName,
      String operationsSchema,
      String workflowName,
      String modelType,
      boolean publishCatalogDefinitions,
      boolean publishDatabaseRows,
      boolean autoCreateTables,
      long dimLookupPreloadRatioThreshold,
      long targetReadRatioThreshold,
      long sortRowsRiskThreshold,
      long highTransformDurationMs,
      String pipelineRunConfiguration) {

    public LoadRunPublishContext withPipelineRunConfiguration(String pipelineRunConfiguration) {
      if (Utils.isEmpty(pipelineRunConfiguration)
          || pipelineRunConfiguration.equals(this.pipelineRunConfiguration)) {
        return this;
      }
      return new LoadRunPublishContext(
          catalogConnectionName,
          targetDatabaseName,
          operationsSchema,
          workflowName,
          modelType,
          publishCatalogDefinitions,
          publishDatabaseRows,
          autoCreateTables,
          dimLookupPreloadRatioThreshold,
          targetReadRatioThreshold,
          sortRowsRiskThreshold,
          highTransformDurationMs,
          pipelineRunConfiguration);
    }

    public static LoadRunPublishContext withDefaults(
        String catalogConnectionName,
        String targetDatabaseName,
        String workflowName,
        String modelType) {
      return new LoadRunPublishContext(
          catalogConnectionName,
          targetDatabaseName,
          LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME,
          workflowName,
          modelType,
          true,
          true,
          true,
          LoadRunInsightEngine.DEFAULT_LOOKUP_RATIO_THRESHOLD,
          LoadRunInsightEngine.DEFAULT_TARGET_READ_RATIO_THRESHOLD,
          LoadRunInsightEngine.DEFAULT_SORT_ROWS_RISK_THRESHOLD,
          LoadRunInsightEngine.DEFAULT_HIGH_TRANSFORM_DURATION_MS,
          null);
    }
  }
}
