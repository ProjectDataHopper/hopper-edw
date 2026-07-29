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

package org.apache.hop.datavault.resourcedefinition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.hop.catalog.discovery.RecordDefinitionDiscoveryService;
import org.apache.hop.catalog.discovery.RecordDefinitionPhysicalRefSupport;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.catalog.hopgui.preview.RecordDefinitionPreviewSupport;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.versioning.CatalogVersionService;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvCatalogNamespaces;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.datavault.impact.ImpactGraph;
import org.apache.hop.datavault.impact.ImpactGraphBuilder;
import org.apache.hop.datavault.lineage.LineageDiffResult;
import org.apache.hop.datavault.lineage.LineageDiffService;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
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
        request == null
            ? group.isDetailedDataTypeChecking()
            : request.detailedDataTypeChecking();
    String catalogVersionTag = request != null ? trimToNull(request.catalogVersionTag()) : null;
    String baselineVersionTag = request != null ? trimToNull(request.baselineVersionTag()) : null;

    if (mode == SchemaCompareMode.WORKING_VS_VERSION && baselineVersionTag == null) {
      baselineVersionTag = catalogVersionTag;
    }
    if (mode == SchemaCompareMode.VERSION_VS_VERSION) {
      if (baselineVersionTag == null || catalogVersionTag == null) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "SchemaImpactSimulationService.Error.VersionPairRequired"));
      }
    }

    ValidationModels models =
        ResourceDefinitionGroupResolver.resolve(group, variables, metadataProvider);
    ValidationReport report =
        switch (mode) {
          case LIVE_SOURCE ->
              simulateLive(models, catalogVersionTag, detailed, variables, metadataProvider);
          case WORKING_VS_VERSION ->
              simulateWorkingVsVersion(
                  models, baselineVersionTag, detailed, variables, metadataProvider);
          case VERSION_VS_VERSION ->
              simulateVersionVsVersion(
                  models,
                  baselineVersionTag,
                  catalogVersionTag,
                  detailed,
                  variables,
                  metadataProvider);
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
              models, baselineVersionTag, detailed, variables, metadataProvider);
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
          TargetSchemaValidationSupport.enrich(report, models, variables, metadataProvider);
    }

    ImpactGraph graph = ImpactGraph.empty();
    if (includeImpact) {
      graph = ImpactGraphBuilder.build(models, variables);
      report = ValidationImpactEnricher.enrich(report, graph);
    }

    String catalogVersionUsed =
        mode == SchemaCompareMode.LIVE_SOURCE
            ? catalogVersionTag
            : mode == SchemaCompareMode.VERSION_VS_VERSION ? catalogVersionTag : null;
    String baselineUsed =
        mode == SchemaCompareMode.LIVE_SOURCE
            ? (catalogVersionTag != null ? catalogVersionTag : baselineVersionTag)
            : baselineVersionTag;

    List<LineageDiffResult> lineageDiffs = List.of();
    try {
      lineageDiffs = LineageDiffService.compareModelsToCatalog(models, variables, metadataProvider);
    } catch (Exception lineageEx) {
      // Lineage drift is best-effort: never fail schema simulation solely because lineage load failed.
      lineageDiffs = List.of();
    }

    SimulationStatus status = SchemaImpactSimulationResult.statusOf(report, lineageDiffs);
    return new SchemaImpactSimulationResult(
        report,
        graph,
        catalogVersionUsed,
        baselineUsed,
        mode,
        Instant.now(),
        status,
        lineageDiffs);
  }

  private static ValidationReport mergeReports(ValidationReport primary, ValidationReport secondary) {
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
      ResourceDefinitionGroupMeta group, IVariables variables, IHopMetadataProvider metadataProvider)
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

  private static ValidationReport simulateLive(
      ValidationModels models,
      String expectedVersionTag,
      boolean detailedDataTypeChecking,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(expectedVersionTag)) {
      // Fast path: identical to classic SourceRecordValidationService behavior.
      return SourceRecordValidationService.validateModels(models, variables, metadataProvider);
    }

    ValidationReport report = new ValidationReport(models.group().getName());
    Map<RecordDefinitionKey, List<SourceUsage>> usageIndex =
        SourceUsageIndexBuilder.build(models, variables);
    String defaultNamespace = DvCatalogNamespaces.projectSourcesNamespace(variables);
    int previewRowLimit = Math.max(1, models.group().getPreviewRowLimit());
    String defaultCatalog = resolveDefaultCatalog(models, variables, metadataProvider);

    for (Map.Entry<RecordDefinitionKey, List<SourceUsage>> entry : usageIndex.entrySet()) {
      List<SourceUsage> usages = entry.getValue();
      String catalogConnection =
          firstNonEmpty(resolveCatalogConnection(usages), defaultCatalog, models.group().getDataCatalogConnection());
      RecordDefinitionKey resolvedKey =
          SourceUsageIndexBuilder.resolveKey(
              entry.getKey(), catalogConnection, variables, defaultNamespace);

      RecordDefinition expected =
          loadFromVersion(catalogConnection, expectedVersionTag, resolvedKey, variables, metadataProvider);
      // Physical discovery uses working-tree definition when available (current connection/path),
      // falling back to the versioned definition.
      RecordDefinition working =
          loadWorking(catalogConnection, resolvedKey, variables, metadataProvider);
      RecordDefinition discoverySource = working != null ? working : expected;

      report.addRecordValidation(
          validateAgainstLive(
              expected,
              discoverySource,
              resolvedKey,
              catalogConnection,
              usages,
              previewRowLimit,
              detailedDataTypeChecking,
              variables,
              metadataProvider));
    }
    return report;
  }

  private static ValidationReport simulateWorkingVsVersion(
      ValidationModels models,
      String baselineVersionTag,
      boolean detailedDataTypeChecking,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
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

    for (Map.Entry<RecordDefinitionKey, List<SourceUsage>> entry : usageIndex.entrySet()) {
      List<SourceUsage> usages = entry.getValue();
      String catalogConnection =
          firstNonEmpty(resolveCatalogConnection(usages), defaultCatalog, models.group().getDataCatalogConnection());
      RecordDefinitionKey resolvedKey =
          SourceUsageIndexBuilder.resolveKey(
              entry.getKey(), catalogConnection, variables, defaultNamespace);

      RecordDefinition expected =
          loadFromVersion(
              catalogConnection, baselineVersionTag, resolvedKey, variables, metadataProvider);
      RecordDefinition actual =
          loadWorking(catalogConnection, resolvedKey, variables, metadataProvider);
      report.addRecordValidation(
          validateFieldContracts(
              expected,
              actual,
              resolvedKey,
              catalogConnection,
              usages,
              detailedDataTypeChecking,
              true));
    }
    return report;
  }

  private static ValidationReport simulateVersionVsVersion(
      ValidationModels models,
      String baselineVersionTag,
      String actualVersionTag,
      boolean detailedDataTypeChecking,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    ValidationReport report = new ValidationReport(models.group().getName());
    Map<RecordDefinitionKey, List<SourceUsage>> usageIndex =
        SourceUsageIndexBuilder.build(models, variables);
    String defaultNamespace = DvCatalogNamespaces.projectSourcesNamespace(variables);
    String defaultCatalog = resolveDefaultCatalog(models, variables, metadataProvider);

    for (Map.Entry<RecordDefinitionKey, List<SourceUsage>> entry : usageIndex.entrySet()) {
      List<SourceUsage> usages = entry.getValue();
      String catalogConnection =
          firstNonEmpty(resolveCatalogConnection(usages), defaultCatalog, models.group().getDataCatalogConnection());
      RecordDefinitionKey resolvedKey =
          SourceUsageIndexBuilder.resolveKey(
              entry.getKey(), catalogConnection, variables, defaultNamespace);

      RecordDefinition expected =
          loadFromVersion(
              catalogConnection, baselineVersionTag, resolvedKey, variables, metadataProvider);
      RecordDefinition actual =
          loadFromVersion(
              catalogConnection, actualVersionTag, resolvedKey, variables, metadataProvider);
      report.addRecordValidation(
          validateFieldContracts(
              expected,
              actual,
              resolvedKey,
              catalogConnection,
              usages,
              detailedDataTypeChecking,
              true));
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
    if (expectedContract == null) {
      List<ValidationIssue> issues =
          RemediationProposalSupport.buildIssues(
              null,
              usages,
              BaseMessages.getString(
                  PKG,
                  "SchemaImpactSimulationService.Error.ExpectedNotFound",
                  key != null ? key.getNamespace() + "/" + key.getName() : "?"));
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
          BaseMessages.getString(PKG, "SourceRecordValidationService.Error.UnsupportedSource");
    } else {
      try {
        unavailableMessage =
            verifyReadability(physical, sourceType, previewRowLimit, variables, metadataProvider);
        if (Utils.isEmpty(unavailableMessage)) {
          List<SourceField> expectedFields = extractFields(expectedContract);
          List<SourceField> actualFields =
              discoverFields(physical, sourceType, variables, metadataProvider);
          diff = diffFields(expectedFields, actualFields, sourceType, detailedDataTypeChecking);
        }
      } catch (HopException e) {
        unavailableMessage = e.getMessage();
      } catch (Exception e) {
        unavailableMessage =
            e.getMessage() != null
                ? e.getMessage()
                : BaseMessages.getString(PKG, "SourceRecordValidationService.Error.DiscoveryFailed");
      }
    }

    return toValidation(
        expectedContract,
        key,
        catalogConnection,
        sourceTypeName,
        usages,
        diff,
        unavailableMessage);
  }

  private static RecordDefinitionValidation validateFieldContracts(
      RecordDefinition expected,
      RecordDefinition actual,
      RecordDefinitionKey key,
      String catalogConnection,
      List<SourceUsage> usages,
      boolean detailedDataTypeChecking,
      boolean applyAcknowledgementsFromActual) {
    if (expected == null && actual == null) {
      List<ValidationIssue> issues =
          RemediationProposalSupport.buildIssues(
              null,
              usages,
              BaseMessages.getString(
                  PKG,
                  "SchemaImpactSimulationService.Error.BothMissing",
                  key != null ? key.getNamespace() + "/" + key.getName() : "?"));
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
      List<ValidationIssue> issues =
          RemediationProposalSupport.buildIssues(
              null,
              usages,
              BaseMessages.getString(
                  PKG,
                  "SchemaImpactSimulationService.Error.ExpectedNotFound",
                  key != null ? key.getNamespace() + "/" + key.getName() : "?"));
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
      List<ValidationIssue> issues =
          RemediationProposalSupport.buildIssues(
              null,
              usages,
              BaseMessages.getString(
                  PKG,
                  "SchemaImpactSimulationService.Error.ActualNotFound",
                  key != null ? key.getNamespace() + "/" + key.getName() : "?"));
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
        ackSource, key, catalogConnection, sourceTypeName, usages, diff, null);
  }

  private static RecordDefinitionValidation toValidation(
      RecordDefinition ackDefinition,
      RecordDefinitionKey key,
      String catalogConnection,
      String sourceTypeName,
      List<SourceUsage> usages,
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff,
      String unavailableMessage) {
    if (ackDefinition != null) {
      ValidationIssueSupport.pruneStaleAcknowledgements(ackDefinition, diff, unavailableMessage);
    }
    List<ValidationIssue> allIssues =
        RemediationProposalSupport.buildIssues(diff, usages, unavailableMessage);
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
    if (definition == null || definition.getDvSource() == null) {
      return List.of();
    }
    return DvSourceFieldSupport.fromCatalogFields(definition.getDvSource().getFields());
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
    return DvSourceCatalogService.resolvePreferredCatalogConnection(null, variables, metadataProvider);
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
