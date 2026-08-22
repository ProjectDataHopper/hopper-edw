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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvSpecialRecordSupport;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ProposalType;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RemediationProposal;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Detects DV target tables whose physical layout lags the model (DDL required). Does not invent
 * field lengths from the database for catalog remediation.
 */
public final class TargetSchemaValidationSupport {

  private static final Class<?> PKG = TargetSchemaValidationSupport.class;

  private TargetSchemaValidationSupport() {}

  public static ValidationReport enrich(
      ValidationReport report,
      ValidationModels models,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    return enrich(
        report,
        models,
        variables,
        metadataProvider,
        false,
        ParallelValidationSupport.DEFAULT_PARALLELISM);
  }

  public static ValidationReport enrich(
      ValidationReport report,
      ValidationModels models,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean expectAutomaticTargetTableCreation) {
    return enrich(
        report,
        models,
        variables,
        metadataProvider,
        expectAutomaticTargetTableCreation,
        ParallelValidationSupport.DEFAULT_PARALLELISM);
  }

  public static ValidationReport enrich(
      ValidationReport report,
      ValidationModels models,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean expectAutomaticTargetTableCreation,
      int validationParallelism) {
    if (report == null || models == null) {
      return report;
    }

    List<RecordDefinitionValidation> owners = new ArrayList<>();
    List<TargetCheckJob> jobs = new ArrayList<>();
    Set<String> tablesChecked = new LinkedHashSet<>();

    for (RecordDefinitionValidation existing : report.getRecordValidations()) {
      if (existing == null) {
        continue;
      }
      int ownerIndex = owners.size();
      owners.add(existing);
      if (existing.usages() == null) {
        continue;
      }
      for (SourceUsage usage : existing.usages()) {
        if (usage == null
            || !SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usage.modelType())
            || Utils.isEmpty(usage.modelFilename())
            || Utils.isEmpty(usage.modelElementName())) {
          continue;
        }
        String tableKey = usage.modelFilename() + "#" + usage.modelElementName();
        if (!tablesChecked.add(tableKey)) {
          continue;
        }
        jobs.add(new TargetCheckJob(ownerIndex, usage));
      }
    }

    List<List<ValidationIssue>> perOwnerExtras = new ArrayList<>(owners.size());
    for (int i = 0; i < owners.size(); i++) {
      perOwnerExtras.add(new ArrayList<>());
    }

    int parallelism = ParallelValidationSupport.resolveParallelism(validationParallelism);
    try {
      List<TargetCheckResult> results =
          ParallelValidationSupport.map(
              parallelism,
              jobs,
              (job, index) ->
                  checkTargetTable(
                      job.usage(),
                      expectAutomaticTargetTableCreation,
                      variables,
                      metadataProvider));
      for (int i = 0; i < jobs.size(); i++) {
        TargetCheckResult result = results.get(i);
        if (result == null || result.issue() == null) {
          continue;
        }
        perOwnerExtras.get(jobs.get(i).ownerIndex()).add(result.issue());
      }
    } catch (HopException e) {
      // Unexpected pool failure: attach a single warning to the first owner so the gate still
      // surfaces a problem instead of aborting silently.
      if (!owners.isEmpty()) {
        perOwnerExtras
            .get(0)
            .add(
                new ValidationIssue(
                    ValidationIssueSupport.buildIssueId(
                        IssueKind.TARGET_DDL_REQUIRED, "target-check", "pool-failed"),
                    IssueKind.TARGET_DDL_REQUIRED,
                    IssueSeverity.WARNING,
                    null,
                    ValidationFindingFormatter.targetDdlCheckFailed(
                        "?", Const.NVL(e.getMessage(), e.getClass().getSimpleName())),
                    List.of(
                        new RemediationProposal(
                            ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
                            BaseMessages.getString(
                                PKG, "TargetSchemaValidationSupport.Proposal.CheckFailed.Summary"),
                            Const.NVL(e.getMessage(), "")))));
      }
    }

    ValidationReport enriched = new ValidationReport(report.getGroupName());
    for (int i = 0; i < owners.size(); i++) {
      enriched.addRecordValidation(mergeIssues(owners.get(i), perOwnerExtras.get(i)));
    }
    return enriched;
  }

  private static TargetCheckResult checkTargetTable(
      SourceUsage usage,
      boolean expectAutomaticTargetTableCreation,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    try {
      DataVaultModel model =
          ResourceDefinitionGroupResolver.loadDataVaultModel(
              usage.modelFilename(), variables, metadataProvider);
      IDvTable table = model.findTable(usage.modelElementName());
      if (!(table instanceof DvSatellite satellite)) {
        return TargetCheckResult.empty();
      }
      List<String> ddl = satellite.generateUpdateDdl(metadataProvider, variables, model);
      if (ddl == null || ddl.isEmpty()) {
        return TargetCheckResult.empty();
      }
      String preview = String.join("; ", ddl.subList(0, Math.min(3, ddl.size())));
      if (ddl.size() > 3) {
        preview = preview + "; ...";
      }
      String tableName = Const.NVL(satellite.getName(), usage.modelElementName());
      String physicalName =
          !Utils.isEmpty(satellite.getTableName()) ? satellite.getTableName() : satellite.getName();
      String modelName = Const.NVL(usage.modelName(), usage.modelFilename());
      boolean pendingCreate =
          isPendingCreate(satellite, model, physicalName, ddl, variables, metadataProvider);
      // User opted into automatic table creation (initial vault load): omit missing-table
      // CREATE findings entirely — do not flood reports / results dialog with INFO noise.
      // Layout drift on tables that already exist still reports as WARNING.
      if (pendingCreate && expectAutomaticTargetTableCreation) {
        return TargetCheckResult.empty();
      }
      String message =
          ValidationFindingFormatter.targetDdlRequired(tableName, modelName, ddl.size(), preview);
      return new TargetCheckResult(
          new ValidationIssue(
              ValidationIssueSupport.buildIssueId(
                  IssueKind.TARGET_DDL_REQUIRED, satellite.getName(), "ddl"),
              IssueKind.TARGET_DDL_REQUIRED,
              IssueSeverity.WARNING,
              null,
              message,
              List.of(
                  new RemediationProposal(
                      ProposalType.GENERATE_TARGET_DDL_PACKAGE,
                      BaseMessages.getString(PKG, "TargetSchemaValidationSupport.Proposal.Summary"),
                      BaseMessages.getString(
                          PKG,
                          "TargetSchemaValidationSupport.Proposal.Details",
                          satellite.getName(),
                          Integer.toString(ddl.size()))))));
    } catch (HopException e) {
      String tableName = Const.NVL(usage.modelElementName(), "?");
      String message =
          ValidationFindingFormatter.targetDdlCheckFailed(
              tableName, Const.NVL(e.getMessage(), e.getClass().getSimpleName()));
      return new TargetCheckResult(
          new ValidationIssue(
              ValidationIssueSupport.buildIssueId(
                  IssueKind.TARGET_DDL_REQUIRED, usage.modelElementName(), "ddl-check-failed"),
              IssueKind.TARGET_DDL_REQUIRED,
              IssueSeverity.WARNING,
              null,
              message,
              List.of(
                  new RemediationProposal(
                      ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
                      BaseMessages.getString(
                          PKG, "TargetSchemaValidationSupport.Proposal.CheckFailed.Summary"),
                      Const.NVL(e.getMessage(), "")))));
    }
  }

  /** True when the target table is missing (or DDL is create-only). Package-visible for tests. */
  static boolean isPendingCreate(
      DvSatellite satellite,
      DataVaultModel model,
      String physicalTableName,
      List<String> ddl,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    Boolean exists =
        targetTableExists(satellite, model, physicalTableName, variables, metadataProvider);
    if (exists != null) {
      return !exists;
    }
    return looksLikeCreateOnly(ddl);
  }

  private static Boolean targetTableExists(
      DvSatellite satellite,
      DataVaultModel model,
      String physicalTableName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (satellite == null || model == null || metadataProvider == null) {
      return null;
    }
    try {
      DataVaultConfiguration config = model.getConfigurationOrDefault();
      DatabaseMeta targetDatabaseMeta =
          DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, config);
      if (targetDatabaseMeta == null || Utils.isEmpty(physicalTableName)) {
        return null;
      }
      String table = variables != null ? variables.resolve(physicalTableName) : physicalTableName;
      ILoggingObject loggingObject =
          new SimpleLoggingObject(
              TargetSchemaValidationSupport.class.getSimpleName() + ".tableExists",
              LoggingObjectType.GENERAL,
              null);
      try (Database db = new Database(loggingObject, variables, targetDatabaseMeta)) {
        db.connect();
        return db.checkTableExists(null, table);
      }
    } catch (Exception e) {
      return null;
    }
  }

  static boolean looksLikeCreateOnly(List<String> ddl) {
    if (ddl == null || ddl.isEmpty()) {
      return false;
    }
    for (String statement : ddl) {
      if (Utils.isEmpty(statement)) {
        continue;
      }
      String normalized = statement.trim().toUpperCase(Locale.ROOT);
      if (normalized.startsWith("ALTER")
          || normalized.contains(" ALTER ")
          || normalized.contains("ADD COLUMN")
          || normalized.contains("DROP COLUMN")
          || normalized.contains("MODIFY ")) {
        return false;
      }
      if (!(normalized.startsWith("CREATE") || normalized.contains("CREATE TABLE"))) {
        return false;
      }
    }
    return true;
  }

  private static RecordDefinitionValidation mergeIssues(
      RecordDefinitionValidation existing, List<ValidationIssue> extra) {
    if (extra == null || extra.isEmpty()) {
      return existing;
    }
    List<ValidationIssue> all = new ArrayList<>(existing.allIssues());
    all.addAll(extra);
    List<ValidationIssue> visible = new ArrayList<>(existing.issues());
    visible.addAll(extra);
    boolean gateIssues =
        visible.stream()
            .anyMatch(
                i ->
                    i != null
                        && (i.severity() == IssueSeverity.WARNING
                            || i.severity() == IssueSeverity.BLOCKING));
    boolean inSync = existing.inSync() && !gateIssues;
    return new RecordDefinitionValidation(
        existing.key(),
        existing.catalogConnection(),
        existing.sourceType(),
        inSync,
        existing.schemaDiff(),
        existing.usages(),
        all,
        visible,
        existing.acknowledgedIssueCount());
  }

  private record TargetCheckJob(int ownerIndex, SourceUsage usage) {}

  private record TargetCheckResult(ValidationIssue issue) {
    static TargetCheckResult empty() {
      return new TargetCheckResult(null);
    }
  }
}
