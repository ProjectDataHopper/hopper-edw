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
package org.hopper.edw.quality.service;

import java.util.ArrayList;
import java.util.List;
import org.hopper.edw.catalog.model.PhysicalTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.quality.CatalogQualitySubjectSupport;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.quality.engine.DataQualityRuleEvaluatorRegistry;
import org.hopper.edw.quality.engine.QualityEvaluationContext;
import org.hopper.edw.quality.model.DataQualityFinding;
import org.hopper.edw.quality.model.DataQualityReport;
import org.hopper.edw.quality.model.DataQualityRule;
import org.hopper.edw.quality.model.DataQualityRuleType;
import org.hopper.edw.quality.model.QualityEvaluationMode;
import org.hopper.edw.quality.model.QualityLifecycle;
import org.hopper.edw.quality.model.RecordQualityRuleBinding;
import org.hopper.edw.quality.profile.DataProfileSnapshot;
import org.hopper.edw.quality.profile.DatabaseProfileCollector;
import org.hopper.edw.quality.profile.SqlAssertionRunner;
import org.hopper.edw.quality.resolve.QualityRuleResolver;

/**
 * Measures data quality against catalog subjects. Never fails for rule findings — only records
 * infrastructure errors on the report.
 */
public final class DataQualityMeasureService {

  private DataQualityMeasureService() {}

  public static DataQualityReport measureDefinitions(
      List<RecordDefinition> definitions,
      QualityEvaluationMode mode,
      QualityLifecycle lifecycle,
      int sampleLimit,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log)
      throws HopException {
    return measureDefinitions(
        definitions, mode, lifecycle, sampleLimit, variables, metadataProvider, log, null);
  }

  /**
   * @param additionalRuleSetName optional Hop metadata name of a {@code DataQualityRuleSetMeta}
   *     selected on Measure / Evaluate (applied to every subject in addition to catalog bindings)
   */
  public static DataQualityReport measureDefinitions(
      List<RecordDefinition> definitions,
      QualityEvaluationMode mode,
      QualityLifecycle lifecycle,
      int sampleLimit,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log,
      String additionalRuleSetName)
      throws HopException {
    DataQualityReport report =
        new DataQualityReport(lifecycle != null ? lifecycle : QualityLifecycle.AD_HOC);
    if (definitions == null || definitions.isEmpty()) {
      return report;
    }
    ILogChannel logger = log != null ? log : LogChannel.GENERAL;
    QualityEvaluationMode effectiveMode = mode != null ? mode : QualityEvaluationMode.AUTO;

    for (RecordDefinition definition : definitions) {
      if (definition == null) {
        continue;
      }
      String subjectKey = CatalogQualitySubjectSupport.subjectKey(definition);
      report.addSubjectKey(subjectKey);
      try {
        List<RecordQualityRuleBinding> bindings = definition.getQualityRules();
        List<DataQualityRule> rules =
            QualityRuleResolver.resolve(bindings, metadataProvider, additionalRuleSetName);
        if (rules.isEmpty()) {
          logger.logDetailed("No quality rules bound for " + subjectKey);
          continue;
        }
        DataProfileSnapshot profile =
            collectProfile(
                definition,
                rules,
                effectiveMode,
                lifecycle,
                sampleLimit,
                variables,
                metadataProvider,
                logger);
        report.putProfile(subjectKey, profile);
        QualityEvaluationContext context =
            QualityEvaluationContext.builder()
                .subjectKey(subjectKey)
                .profile(profile)
                .mode(profile.getEvaluationMode())
                .lifecycle(lifecycle)
                .variables(variables)
                .metadataProvider(metadataProvider)
                .log(logger)
                .build();
        PhysicalTableRef table = definition.getPhysicalTable();
        for (DataQualityRule rule : rules) {
          List<DataQualityFinding> findings;
          if (rule != null && rule.getType() == DataQualityRuleType.SQL_ASSERTION) {
            findings =
                SqlAssertionRunner.evaluate(
                    rule,
                    subjectKey,
                    table != null ? table.getDatabaseMetaName() : null,
                    table != null ? table.getSchemaName() : null,
                    table != null ? table.getTableName() : null,
                    variables,
                    metadataProvider);
          } else {
            findings = DataQualityRuleEvaluatorRegistry.getInstance().evaluate(rule, context);
          }
          for (DataQualityFinding finding : findings) {
            report.addFinding(finding);
          }
        }
      } catch (Exception e) {
        String message =
            "Failed to measure quality for "
                + subjectKey
                + ": "
                + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        report.addInfraError(message);
        logger.logError(message, e);
      }
    }
    return report;
  }

  /**
   * Evaluate profile-based rules against an injected snapshot (unit tests / library use).
   *
   * <p>{@link DataQualityRuleType#SQL_ASSERTION} is not supported here — it requires a live DB via
   * {@link #measureDefinitions}. Enabled SQL_ASSERTION rules are recorded as infra errors (fail
   * closed) rather than silently passing with zero findings.
   */
  public static DataQualityReport measureAgainstProfiles(
      String subjectKey,
      DataProfileSnapshot profile,
      List<DataQualityRule> rules,
      QualityLifecycle lifecycle) {
    DataQualityReport report =
        new DataQualityReport(lifecycle != null ? lifecycle : QualityLifecycle.AD_HOC);
    report.addSubjectKey(subjectKey);
    if (profile != null) {
      report.putProfile(subjectKey, profile);
    }
    if (rules == null) {
      return report;
    }
    QualityEvaluationContext context =
        QualityEvaluationContext.builder()
            .subjectKey(subjectKey)
            .profile(profile)
            .mode(profile != null ? profile.getEvaluationMode() : QualityEvaluationMode.SAMPLE)
            .lifecycle(lifecycle)
            .build();
    for (DataQualityRule rule : rules) {
      if (rule != null && rule.isEnabled() && rule.getType() == DataQualityRuleType.SQL_ASSERTION) {
        report.addInfraError(
            "SQL_ASSERTION rule '"
                + (rule.getName() != null && !rule.getName().isBlank()
                    ? rule.getName()
                    : rule.getId())
                + "' requires database execution via measureDefinitions;"
                + " measureAgainstProfiles does not support SQL_ASSERTION");
        continue;
      }
      for (DataQualityFinding finding :
          DataQualityRuleEvaluatorRegistry.getInstance().evaluate(rule, context)) {
        report.addFinding(finding);
      }
    }
    return report;
  }

  private static DataProfileSnapshot collectProfile(
      RecordDefinition definition,
      List<DataQualityRule> rules,
      QualityEvaluationMode mode,
      QualityLifecycle lifecycle,
      int sampleLimit,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log)
      throws HopException {
    String subjectKey = CatalogQualitySubjectSupport.subjectKey(definition);
    PhysicalTableRef table = definition.getPhysicalTable();
    boolean hasDb =
        table != null
            && !Utils.isEmpty(table.getDatabaseMetaName())
            && !Utils.isEmpty(table.getTableName());

    QualityEvaluationMode resolved = resolveMode(mode, hasDb);
    if (resolved == QualityEvaluationMode.SQL_PUSHDOWN && hasDb) {
      return DatabaseProfileCollector.collect(
          subjectKey,
          table.getDatabaseMetaName(),
          table.getSchemaName(),
          table.getTableName(),
          rules,
          lifecycle,
          variables,
          metadataProvider);
    }

    // SAMPLE / FULL_SCAN: use empty profile with rowCount 0 when no scanner available headless.
    // Catalog preview is GUI-oriented; Phase 1 relies on SQL for databases and unit-injected
    // profiles for pure logic tests. File SAMPLE path records infra guidance when unsupported.
    if (!hasDb) {
      throw new HopException(
          "Phase 1 measure supports database physical tables via SQL pushdown; "
              + "file/Iceberg sample scanning is planned. Subject: "
              + subjectKey);
    }

    // Fallback: still SQL for DB when SAMPLE requested but pushdown preferred for accuracy on
    // counts
    if (hasDb) {
      log.logBasic(
          "Using SQL pushdown for database subject "
              + subjectKey
              + " (requested mode "
              + mode
              + ")");
      return DatabaseProfileCollector.collect(
          subjectKey,
          table.getDatabaseMetaName(),
          table.getSchemaName(),
          table.getTableName(),
          rules,
          lifecycle,
          variables,
          metadataProvider);
    }

    DataProfileSnapshot empty = new DataProfileSnapshot();
    empty.setSubjectKey(subjectKey);
    empty.setLifecycle(lifecycle);
    empty.setEvaluationMode(QualityEvaluationMode.SAMPLE);
    empty.setRowCount(0);
    empty.setRowCountExact(true);
    return empty;
  }

  private static QualityEvaluationMode resolveMode(QualityEvaluationMode mode, boolean hasDb) {
    if (mode == null || mode == QualityEvaluationMode.AUTO) {
      return hasDb ? QualityEvaluationMode.SQL_PUSHDOWN : QualityEvaluationMode.SAMPLE;
    }
    if (mode == QualityEvaluationMode.FULL_SCAN && hasDb) {
      return QualityEvaluationMode.SQL_PUSHDOWN;
    }
    if (mode == QualityEvaluationMode.SAMPLE && hasDb) {
      return QualityEvaluationMode.SQL_PUSHDOWN;
    }
    return mode;
  }

  /** Convenience for unit tests and library rule evaluation without catalog I/O. */
  public static List<DataQualityRule> rulesOf(DataQualityRule... rules) {
    List<DataQualityRule> list = new ArrayList<>();
    if (rules != null) {
      for (DataQualityRule rule : rules) {
        if (rule != null) {
          rule.ensureId();
          list.add(rule);
        }
      }
    }
    return list;
  }
}
