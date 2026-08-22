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
package org.hopper.edw.datavault.resourcedefinition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hopper.edw.catalog.discovery.RecordDefinitionDiscoveryService;
import org.hopper.edw.catalog.discovery.RecordDefinitionPhysicalRefSupport;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryPublisher;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HistoryConnection;
import org.hopper.edw.catalog.hopgui.preview.RecordDefinitionPreviewSupport;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.versioning.CatalogVersionService;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.catalog.DvSourceFieldSupport;
import org.hopper.edw.datavault.impact.ImpactGraph;
import org.hopper.edw.datavault.impact.ImpactGraphBuilder;
import org.hopper.edw.datavault.lineage.LineageDiffResult;
import org.hopper.edw.datavault.lineage.LineageDiffService;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Orchestrates schema gap analysis (catalog/version vs live or version) and optional downstream
 * impact enrichment for CI/CD gates and design-time dry-runs.
 */
public final class SchemaImpactSimulationService {

  private static final Class<?> PKG = SchemaImpactSimulationService.class;

  private SchemaImpactSimulationService() {}

  public static SchemaImpactSimulationResult run(
      SchemaImpactSimulationRequest request,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (request == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "SchemaImpactSimulationService.Error.MissingRequest"));
    }
    ResourceDefinitionGroupMeta group =
        ResourceDefinitionGroupResolver.loadGroup(
            request.resourceDefinitionGroup(), metadataProvider);
    return run(request, group, variables, metadataProvider);
  }

  public static SchemaImpactSimulationResult run(
      SchemaImpactSimulationRequest request,
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (group == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "SchemaImpactSimulationService.Error.MissingGroup"));
    }
    SchemaCompareMode mode =
        request != null && request.compareMode() != null
            ? request.compareMode()
            : SchemaCompareMode.LIVE_SOURCE;
    boolean includeImpact = request == null || request.includeImpact();
    boolean detailed =
        request == null ? group.isDetailedDataTypeChecking() : request.detailedDataTypeChecking();
    String catalogVersionTag = request != null ? trimToNull(request.catalogVersionTag()) : null;
    String baselineVersionTag = request != null ? trimToNull(request.baselineVersionTag()) : null;

    if (mode == SchemaCompareMode.WORKING_VS_VERSION && baselineVersionTag == null) {
      baselineVersionTag = catalogVersionTag;
    }
    if (mode == SchemaCompareMode.VERSION_VS_VERSION) {
      if (baselineVersionTag == null || catalogVersionTag == null) {
        throw new HopException(
            BaseMessages.getString(PKG, "SchemaImpactSimulationService.Error.VersionPairRequired"));
      }
    }

    int validationParallelism =
        request != null
            ? ParallelValidationSupport.resolveParallelism(request.validationParallelism())
            : ParallelValidationSupport.DEFAULT_PARALLELISM;

    ValidationModels models =
        ResourceDefinitionGroupResolver.resolve(group, variables, metadataProvider);
    String harvestRunIdUsed = null;
    ValidationReport report =
        switch (mode) {
          case LIVE_SOURCE ->
              simulateLive(
                  models,
                  catalogVersionTag,
                  detailed,
                  variables,
                  metadataProvider,
                  validationParallelism);
          case WORKING_VS_VERSION ->
              simulateWorkingVsVersion(
                  models,
                  baselineVersionTag,
                  detailed,
                  variables,
                  metadataProvider,
                  validationParallelism);
          case VERSION_VS_VERSION ->
              simulateVersionVsVersion(
                  models,
                  baselineVersionTag,
                  catalogVersionTag,
                  detailed,
                  variables,
                  metadataProvider,
                  validationParallelism);
          case HARVEST_RUN -> {
            HarvestLoad loaded =
                loadHarvestForGate(request, group, models, variables, metadataProvider);
            harvestRunIdUsed = loaded.runId();
            yield loaded.report();
          }
        };

    // Optional second axis: working catalog vs version baseline while also checking live sources.
    boolean alsoVersion =
        request != null
            && request.checkCatalogVsVersion()
            && mode == SchemaCompareMode.LIVE_SOURCE
            && baselineVersionTag != null;
    if (alsoVersion) {
      ValidationReport versionReport =
          simulateWorkingVsVersion(
              models,
              baselineVersionTag,
              detailed,
              variables,
              metadataProvider,
              validationParallelism);
      report = mergeReports(report, versionReport);
    }

    // Baseline for model/target axes: version tag when set, else working catalog (null tag).
    String contractBaselineTag =
        baselineVersionTag != null
            ? baselineVersionTag
            : mode == SchemaCompareMode.LIVE_SOURCE ? catalogVersionTag : null;

    if (request != null && request.checkTargetModels()) {
      report =
          ModelContractValidationSupport.enrich(
              report, models, contractBaselineTag, variables, metadataProvider);
    }
    if (request != null && request.checkTargetDatabases()) {
      report =
          TargetSchemaValidationSupport.enrich(
              report,
              models,
              variables,
              metadataProvider,
              request.expectAutomaticTargetTableCreation(),
              validationParallelism);
    }

    ImpactGraph graph = ImpactGraph.empty();
    if (includeImpact) {
      graph = ImpactGraphBuilder.build(models, variables);
      report = ValidationImpactEnricher.enrich(report, graph);
    }

    String catalogVersionUsed =
        mode == SchemaCompareMode.LIVE_SOURCE
            ? catalogVersionTag
            : mode == SchemaCompareMode.VERSION_VS_VERSION
                ? catalogVersionTag
                : mode == SchemaCompareMode.HARVEST_RUN ? harvestRunIdUsed : null;
    String baselineUsed =
        mode == SchemaCompareMode.LIVE_SOURCE
            ? (catalogVersionTag != null ? catalogVersionTag : baselineVersionTag)
            : mode == SchemaCompareMode.HARVEST_RUN
                ? (harvestRunIdUsed != null ? "HARVEST:" + harvestRunIdUsed : "HARVEST")
                : baselineVersionTag;

    List<LineageDiffResult> lineageDiffs = List.of();
    try {
      lineageDiffs = LineageDiffService.compareModelsToCatalog(models, variables, metadataProvider);
    } catch (Exception lineageEx) {
      // Lineage drift is best-effort: never fail schema simulation solely because lineage load
      // failed.
      lineageDiffs = List.of();
    }

    SimulationStatus status = SchemaImpactSimulationResult.statusOf(report, lineageDiffs);
    return new SchemaImpactSimulationResult(
        report, graph, catalogVersionUsed, baselineUsed, mode, Instant.now(), status, lineageDiffs);
  }

  private static ValidationReport mergeReports(
      ValidationReport primary, ValidationReport secondary) {
    if (primary == null) {
      return secondary;
    }
    if (secondary == null) {
      return primary;
    }
    ValidationReport merged = new ValidationReport(primary.getGroupName());
    for (ValidationReport.RecordDefinitionValidation left : primary.getRecordValidations()) {
      if (left == null) {
        continue;
      }
      ValidationReport.RecordDefinitionValidation right = findMatching(secondary, left);
      if (right == null) {
        merged.addRecordValidation(left);
        continue;
      }
      List<ValidationReport.ValidationIssue> all = new ArrayList<>(left.allIssues());
      all.addAll(right.allIssues());
      List<ValidationReport.ValidationIssue> visible = new ArrayList<>(left.issues());
      visible.addAll(right.issues());
      boolean inSync = left.inSync() && right.inSync() && visible.isEmpty();
      merged.addRecordValidation(
          new ValidationReport.RecordDefinitionValidation(
              left.key(),
              left.catalogConnection(),
              left.sourceType(),
              inSync,
              left.schemaDiff() != null ? left.schemaDiff() : right.schemaDiff(),
              left.usages() != null && !left.usages().isEmpty() ? left.usages() : right.usages(),
              all,
              visible,
              left.acknowledgedIssueCount() + right.acknowledgedIssueCount()));
    }
    for (ValidationReport.RecordDefinitionValidation right : secondary.getRecordValidations()) {
      if (right != null && findMatching(primary, right) == null) {
        merged.addRecordValidation(right);
      }
    }
    return merged;
  }

  private static ValidationReport.RecordDefinitionValidation findMatching(
      ValidationReport report, ValidationReport.RecordDefinitionValidation needle) {
    if (report == null || needle == null || needle.key() == null) {
      return null;
    }
    for (ValidationReport.RecordDefinitionValidation candidate : report.getRecordValidations()) {
      if (candidate == null || candidate.key() == null) {
        continue;
      }
      if (needle.key().getName() != null
          && needle.key().getName().equalsIgnoreCase(candidate.key().getName())
          && Objects.equals(needle.key().getNamespace(), candidate.key().getNamespace())) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * Convenience for existing callers: live discovery against the working-tree catalog with impact
   * enrichment.
   */
  public static SchemaImpactSimulationResult runLiveWithImpact(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    SchemaImpactSimulationRequest request =
        SchemaImpactSimulationRequest.builder()
            .resourceDefinitionGroup(group != null ? group.getName() : null)
            .compareMode(SchemaCompareMode.LIVE_SOURCE)
            .includeImpact(true)
            .detailedDataTypeChecking(group == null || group.isDetailedDataTypeChecking())
            .build();
    return run(request, group, variables, metadataProvider);
  }

  private record HarvestLoad(String runId, ValidationReport report) {}

  private static HarvestLoad loadHarvestForGate(
      SchemaImpactSimulationRequest request,
      ResourceDefinitionGroupMeta group,
      ValidationModels models,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String groupName = group != null ? group.getName() : request.resourceDefinitionGroup();
    String catalogConnection =
        !Utils.isEmpty(request.harvestCatalogConnection())
            ? request.harvestCatalogConnection()
            : group != null ? group.getDataCatalogConnection() : null;
    if (!Utils.isEmpty(catalogConnection) && variables != null) {
      catalogConnection = variables.resolve(catalogConnection);
    }

    HistoryConnection history =
        SchemaHarvestHistoryReader.resolveConnection(
            request.harvestHistoryDatabase(),
            request.harvestHistorySchema(),
            catalogConnection,
            variables,
            metadataProvider);
    if (history == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "SchemaImpactSimulationService.Error.HarvestHistoryDb"));
    }
    DatabaseMeta databaseMeta =
        SchemaHarvestHistoryReader.loadDatabaseMeta(history.databaseMetaName(), metadataProvider);
    if (databaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "SchemaImpactSimulationService.Error.HarvestHistoryDbMissing",
              history.databaseMetaName()));
    }

    String runId = trimToNull(request.harvestRunId());
    if (runId == null && variables != null) {
      String fromVar =
          variables.getVariable(SchemaHarvestHistoryPublisher.VAR_SCHEMA_HARVEST_RUN_ID);
      if (Utils.isEmpty(fromVar)) {
        String resolved =
            variables.resolve("${" + SchemaHarvestHistoryPublisher.VAR_SCHEMA_HARVEST_RUN_ID + "}");
        if (!Utils.isEmpty(resolved) && !resolved.contains("${")) {
          fromVar = resolved;
        }
      }
      runId = trimToNull(fromVar);
    }
    if (runId == null) {
      runId =
          SchemaHarvestHistoryReader.findLatestRunId(
              databaseMeta, history.schemaName(), groupName, variables);
    }
    if (Utils.isEmpty(runId)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "SchemaImpactSimulationService.Error.HarvestRunNotFound", groupName));
    }

    HarvestResult harvest =
        SchemaHarvestHistoryReader.loadHarvestResult(
            databaseMeta, history.schemaName(), runId, variables);
    ValidationReport report =
        HarvestBackedValidationSupport.toValidationReport(harvest, models, variables);
    return new HarvestLoad(runId, report);
  }

  private static ValidationReport simulateLive(
      ValidationModels models,
      String expectedVersionTag,
      boolean detailedDataTypeChecking,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int validationParallelism)
      throws HopException {
    int parallelism = ParallelValidationSupport.resolveParallelism(validationParallelism);
    if (Utils.isEmpty(expectedVersionTag)) {
      // Fast path: identical to classic SourceRecordValidationService behavior.
      return SourceRecordValidationService.validateModels(
          models, variables, metadataProvider, parallelism);
    }

    ValidationReport report = new ValidationReport(models.group().getName());
    Map<RecordDefinitionKey, List<SourceUsage>> usageIndex =
        SourceUsageIndexBuilder.build(models, variables);
    String defaultNamespace = DvCatalogNamespaces.projectSourcesNamespace(variables);
    int previewRowLimit = Math.max(1, models.group().getPreviewRowLimit());
    String defaultCatalog = resolveDefaultCatalog(models, variables, metadataProvider);
    String groupCatalog = models.group().getDataCatalogConnection();

    List<Map.Entry<RecordDefinitionKey, List<SourceUsage>>> entries =
        new ArrayList<>(usageIndex.entrySet());
    List<RecordDefinitionValidation> validations =
        ParallelValidationSupport.map(
            parallelism,
            entries,
            (entry, index) -> {
              List<SourceUsage> usages = entry.getValue();
              String catalogConnection =
                  firstNonEmpty(resolveCatalogConnection(usages), defaultCatalog, groupCatalog);
              RecordDefinitionKey resolvedKey =
                  SourceUsageIndexBuilder.resolveKey(
                      entry.getKey(), catalogConnection, variables, defaultNamespace);

              RecordDefinition expected =
                  loadFromVersion(
                      catalogConnection,
                      expectedVersionTag,
                      resolvedKey,
                      variables,
                      metadataProvider);
              // Physical discovery uses working-tree definition when available (current
              // connection/path), falling back to the versioned definition.
              RecordDefinition working =
                  loadWorking(catalogConnection, resolvedKey, variables, metadataProvider);
              RecordDefinition discoverySource = working != null ? working : expected;

              return validateAgainstLive(
                  expected,
                  discoverySource,
                  resolvedKey,
                  catalogConnection,
                  usages,
                  previewRowLimit,
                  detailedDataTypeChecking,
                  variables,
                  metadataProvider);
            });
    for (RecordDefinitionValidation validation : validations) {
      report.addRecordValidation(validation);
    }
    return report;
  }

  private static ValidationReport simulateWorkingVsVersion(
      ValidationModels models,
      String baselineVersionTag,
      boolean detailedDataTypeChecking,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int validationParallelism)
      throws HopException {
    if (Utils.isEmpty(baselineVersionTag)) {
      throw new HopException(
          BaseMessages.getString(PKG, "SchemaImpactSimulationService.Error.BaselineRequired"));
    }
    ValidationReport report = new ValidationReport(models.group().getName());
    Map<RecordDefinitionKey, List<SourceUsage>> usageIndex =
        SourceUsageIndexBuilder.build(models, variables);
    String defaultNamespace = DvCatalogNamespaces.projectSourcesNamespace(variables);
    String defaultCatalog = resolveDefaultCatalog(models, variables, metadataProvider);
    String groupCatalog = models.group().getDataCatalogConnection();
    int parallelism = ParallelValidationSupport.resolveParallelism(validationParallelism);

    List<Map.Entry<RecordDefinitionKey, List<SourceUsage>>> entries =
        new ArrayList<>(usageIndex.entrySet());
    List<RecordDefinitionValidation> validations =
        ParallelValidationSupport.map(
            parallelism,
            entries,
            (entry, index) -> {
              List<SourceUsage> usages = entry.getValue();
              String catalogConnection =
                  firstNonEmpty(resolveCatalogConnection(usages), defaultCatalog, groupCatalog);
              RecordDefinitionKey resolvedKey =
                  SourceUsageIndexBuilder.resolveKey(
                      entry.getKey(), catalogConnection, variables, defaultNamespace);

              RecordDefinition expected =
                  loadFromVersion(
                      catalogConnection,
                      baselineVersionTag,
                      resolvedKey,
                      variables,
                      metadataProvider);
              RecordDefinition actual =
                  loadWorking(catalogConnection, resolvedKey, variables, metadataProvider);
              return validateFieldContracts(
                  expected,
                  actual,
                  resolvedKey,
                  catalogConnection,
                  usages,
                  detailedDataTypeChecking,
                  true,
                  baselineVersionTag);
            });
    for (RecordDefinitionValidation validation : validations) {
      report.addRecordValidation(validation);
    }
    return report;
  }

  private static ValidationReport simulateVersionVsVersion(
      ValidationModels models,
      String baselineVersionTag,
      String actualVersionTag,
      boolean detailedDataTypeChecking,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int validationParallelism)
      throws HopException {
    ValidationReport report = new ValidationReport(models.group().getName());
    Map<RecordDefinitionKey, List<SourceUsage>> usageIndex =
        SourceUsageIndexBuilder.build(models, variables);
    String defaultNamespace = DvCatalogNamespaces.projectSourcesNamespace(variables);
    String defaultCatalog = resolveDefaultCatalog(models, variables, metadataProvider);
    String groupCatalog = models.group().getDataCatalogConnection();
    int parallelism = ParallelValidationSupport.resolveParallelism(validationParallelism);

    List<Map.Entry<RecordDefinitionKey, List<SourceUsage>>> entries =
        new ArrayList<>(usageIndex.entrySet());
    List<RecordDefinitionValidation> validations =
        ParallelValidationSupport.map(
            parallelism,
            entries,
            (entry, index) -> {
              List<SourceUsage> usages = entry.getValue();
              String catalogConnection =
                  firstNonEmpty(resolveCatalogConnection(usages), defaultCatalog, groupCatalog);
              RecordDefinitionKey resolvedKey =
                  SourceUsageIndexBuilder.resolveKey(
                      entry.getKey(), catalogConnection, variables, defaultNamespace);

              RecordDefinition expected =
                  loadFromVersion(
                      catalogConnection,
                      baselineVersionTag,
                      resolvedKey,
                      variables,
                      metadataProvider);
              RecordDefinition actual =
                  loadFromVersion(
                      catalogConnection,
                      actualVersionTag,
                      resolvedKey,
                      variables,
                      metadataProvider);
              return validateFieldContracts(
                  expected,
                  actual,
                  resolvedKey,
                  catalogConnection,
                  usages,
                  detailedDataTypeChecking,
                  true,
                  baselineVersionTag);
            });
    for (RecordDefinitionValidation validation : validations) {
      report.addRecordValidation(validation);
    }
    return report;
  }

  private static RecordDefinitionValidation validateAgainstLive(
      RecordDefinition expectedContract,
      RecordDefinition discoverySource,
      RecordDefinitionKey key,
      String catalogConnection,
      List<SourceUsage> usages,
      int previewRowLimit,
      boolean detailedDataTypeChecking,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    String recordKey = key != null ? key.getNamespace() + "/" + key.getName() : "?";
    if (expectedContract == null) {
      // LIVE_SOURCE with a version tag as expected contract: treat as baseline gap if working
      // discovery source exists, otherwise working/contract missing.
      boolean workingPresent = discoverySource != null;
      IssueKind kind =
          workingPresent ? IssueKind.BASELINE_CONTRACT_MISSING : IssueKind.WORKING_CONTRACT_MISSING;
      String finding =
          workingPresent
              ? ValidationFindingFormatter.baselineContractMissing(recordKey, null, true)
              : ValidationFindingFormatter.workingContractMissing(recordKey);
      List<ValidationIssue> issues =
          RemediationProposalSupport.buildIssues(null, usages, finding, kind, recordKey);
      return new RecordDefinitionValidation(
          key,
          catalogConnection,
          null,
          false,
          new RecordDefinitionSchemaDiffSupport.SchemaDiff(List.of()),
          usages,
          issues,
          issues,
          0);
    }

    RecordDefinition physical = discoverySource != null ? discoverySource : expectedContract;
    DvSourceType sourceType = RecordDefinitionPhysicalRefSupport.resolveSourceType(physical);
    String sourceTypeName = sourceType != null ? sourceType.name() : null;
    String unavailableMessage = null;
    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        new RecordDefinitionSchemaDiffSupport.SchemaDiff(new ArrayList<>());

    if (!RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(physical)) {
      unavailableMessage =
          ValidationFindingFormatter.liveSourceUnavailable(
              recordKey,
              BaseMessages.getString(PKG, "SourceRecordValidationService.Error.UnsupportedSource"));
    } else {
      try {
        unavailableMessage =
            verifyReadability(physical, sourceType, previewRowLimit, variables, metadataProvider);
        if (!Utils.isEmpty(unavailableMessage)) {
          unavailableMessage =
              ValidationFindingFormatter.liveSourceUnavailable(recordKey, unavailableMessage);
        }
        if (Utils.isEmpty(unavailableMessage)) {
          List<SourceField> expectedFields = extractFields(expectedContract);
          List<SourceField> actualFields =
              discoverFields(physical, sourceType, variables, metadataProvider);
          diff = diffFields(expectedFields, actualFields, sourceType, detailedDataTypeChecking);
        }
      } catch (HopException e) {
        unavailableMessage =
            ValidationFindingFormatter.liveSourceUnavailable(
                recordKey, Const.NVL(e.getMessage(), e.getClass().getSimpleName()));
      } catch (Exception e) {
        unavailableMessage =
            ValidationFindingFormatter.liveSourceUnavailable(
                recordKey,
                e.getMessage() != null
                    ? e.getMessage()
                    : BaseMessages.getString(
                        PKG, "SourceRecordValidationService.Error.DiscoveryFailed"));
      }
    }

    return toValidation(
        expectedContract,
        key,
        catalogConnection,
        sourceTypeName,
        usages,
        diff,
        unavailableMessage,
        IssueKind.SOURCE_UNAVAILABLE);
  }

  private static RecordDefinitionValidation validateFieldContracts(
      RecordDefinition expected,
      RecordDefinition actual,
      RecordDefinitionKey key,
      String catalogConnection,
      List<SourceUsage> usages,
      boolean detailedDataTypeChecking,
      boolean applyAcknowledgementsFromActual,
      String baselineVersionTag) {
    String recordKey = key != null ? key.getNamespace() + "/" + key.getName() : "?";
    if (expected == null && actual == null) {
      String finding =
          ValidationFindingFormatter.bothContractsMissing(recordKey, baselineVersionTag);
      List<ValidationIssue> issues =
          RemediationProposalSupport.buildIssues(
              null, usages, finding, IssueKind.BASELINE_CONTRACT_MISSING, recordKey);
      return new RecordDefinitionValidation(
          key,
          catalogConnection,
          null,
          false,
          new RecordDefinitionSchemaDiffSupport.SchemaDiff(List.of()),
          usages,
          issues,
          issues,
          0);
    }
    if (expected == null) {
      // Working catalog has the source; frozen baseline does not.
      String finding =
          ValidationFindingFormatter.baselineContractMissing(recordKey, baselineVersionTag, true);
      List<ValidationIssue> issues =
          RemediationProposalSupport.buildIssues(
              null, usages, finding, IssueKind.BASELINE_CONTRACT_MISSING, recordKey);
      return new RecordDefinitionValidation(
          key,
          catalogConnection,
          null,
          false,
          new RecordDefinitionSchemaDiffSupport.SchemaDiff(List.of()),
          usages,
          issues,
          issues,
          0);
    }
    if (actual == null) {
      String finding = ValidationFindingFormatter.workingContractMissing(recordKey);
      List<ValidationIssue> issues =
          RemediationProposalSupport.buildIssues(
              null, usages, finding, IssueKind.WORKING_CONTRACT_MISSING, recordKey);
      return new RecordDefinitionValidation(
          key,
          catalogConnection,
          null,
          false,
          new RecordDefinitionSchemaDiffSupport.SchemaDiff(List.of()),
          usages,
          issues,
          issues,
          0);
    }

    DvSourceType sourceType = RecordDefinitionPhysicalRefSupport.resolveSourceType(expected);
    String sourceTypeName = sourceType != null ? sourceType.name() : null;
    List<SourceField> expectedFields = extractFields(expected);
    List<SourceField> actualFields = extractFields(actual);
    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        diffFields(expectedFields, actualFields, sourceType, detailedDataTypeChecking);
    RecordDefinition ackSource = applyAcknowledgementsFromActual ? actual : expected;
    return toValidation(
        ackSource,
        key,
        catalogConnection,
        sourceTypeName,
        usages,
        diff,
        null,
        IssueKind.SOURCE_UNAVAILABLE);
  }

  private static RecordDefinitionValidation toValidation(
      RecordDefinition ackDefinition,
      RecordDefinitionKey key,
      String catalogConnection,
      String sourceTypeName,
      List<SourceUsage> usages,
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff,
      String unavailableMessage,
      IssueKind unavailableKind) {
    if (ackDefinition != null) {
      ValidationIssueSupport.pruneStaleAcknowledgements(ackDefinition, diff, unavailableMessage);
    }
    String recordKey = key != null ? key.getNamespace() + "/" + key.getName() : null;
    List<ValidationIssue> allIssues =
        RemediationProposalSupport.buildIssues(
            diff,
            usages,
            unavailableMessage,
            unavailableKind != null ? unavailableKind : IssueKind.SOURCE_UNAVAILABLE,
            recordKey);
    int acknowledgedIssueCount =
        ackDefinition != null
            ? ValidationIssueSupport.countAcknowledged(ackDefinition, allIssues)
            : 0;
    List<ValidationIssue> visibleIssues =
        ackDefinition != null
            ? ValidationIssueSupport.filterAcknowledged(ackDefinition, allIssues)
            : allIssues;
    boolean inSync = Utils.isEmpty(unavailableMessage) && visibleIssues.isEmpty();
    return new RecordDefinitionValidation(
        key,
        catalogConnection,
        sourceTypeName,
        inSync,
        diff,
        usages,
        allIssues,
        visibleIssues,
        acknowledgedIssueCount);
  }

  private static List<SourceField> extractFields(RecordDefinition definition) {
    try {
      return DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
    } catch (Exception e) {
      return List.of();
    }
  }

  private static List<SourceField> discoverFields(
      RecordDefinition definition,
      DvSourceType sourceType,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    var physicalRef = RecordDefinitionPhysicalRefSupport.toPhysicalSourceRef(definition);
    RecordDefinitionDiscoveryService.DiscoveryResult discovery =
        RecordDefinitionDiscoveryService.discover(
            sourceType, physicalRef, variables, metadataProvider);
    if (discovery.fields() == null || discovery.fields().isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceRecordValidationService.Error.NoDiscoveredFields"));
    }
    return discovery.fields();
  }

  private static RecordDefinitionSchemaDiffSupport.SchemaDiff diffFields(
      List<SourceField> expected,
      List<SourceField> actual,
      DvSourceType sourceType,
      boolean detailedDataTypeChecking) {
    if (sourceType == DvSourceType.ICEBERG || !detailedDataTypeChecking) {
      return RecordDefinitionSchemaDiffSupport.diffTypesOnly(expected, actual);
    }
    return RecordDefinitionSchemaDiffSupport.diff(expected, actual);
  }

  private static String verifyReadability(
      RecordDefinition definition,
      DvSourceType sourceType,
      int previewRowLimit,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (sourceType != DvSourceType.CSV && sourceType != DvSourceType.PARQUET) {
      return null;
    }
    if (!RecordDefinitionPreviewSupport.supportsPreview(definition)) {
      return BaseMessages.getString(PKG, "SourceRecordValidationService.Error.UnreadablePreview");
    }
    try {
      RecordDefinitionPreviewSupport.buildPreviewPipeline(
          definition, variables, metadataProvider, previewRowLimit);
      return null;
    } catch (HopException e) {
      return BaseMessages.getString(
          PKG, "SourceRecordValidationService.Error.SourceUnreadable", e.getMessage());
    }
  }

  private static RecordDefinition loadWorking(
      String catalogConnection,
      RecordDefinitionKey key,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(catalogConnection) || key == null) {
      return null;
    }
    return RecordDefinitionRegistry.getInstance()
        .read(catalogConnection, key, variables, metadataProvider);
  }

  private static RecordDefinition loadFromVersion(
      String catalogConnection,
      String tag,
      RecordDefinitionKey key,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(catalogConnection) || Utils.isEmpty(tag) || key == null) {
      return null;
    }
    return CatalogVersionService.readDefinition(
            catalogConnection, tag, key, variables, metadataProvider)
        .orElse(null);
  }

  private static String resolveDefaultCatalog(
      ValidationModels models, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (models.group() != null && !Utils.isEmpty(models.group().getDataCatalogConnection())) {
      return models.group().getDataCatalogConnection();
    }
    return DvSourceCatalogService.resolvePreferredCatalogConnection(
        null, variables, metadataProvider);
  }

  private static String resolveCatalogConnection(List<SourceUsage> usages) {
    if (usages == null) {
      return null;
    }
    for (SourceUsage usage : usages) {
      if (usage != null && !Utils.isEmpty(usage.catalogConnection())) {
        return usage.catalogConnection();
      }
    }
    return null;
  }

  private static String firstNonEmpty(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (!Utils.isEmpty(value)) {
        return value;
      }
    }
    return null;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
