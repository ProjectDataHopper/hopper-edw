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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.discovery.RecordDefinitionDiscoveryService;
import org.hopper.edw.catalog.discovery.RecordDefinitionPhysicalRefSupport;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.hopper.edw.catalog.hopgui.preview.RecordDefinitionPreviewSupport;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.catalog.DvSourceFieldSupport;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.ValidationIssue;

/** Validates source record definitions referenced by a resource definition group. */
public final class SourceRecordValidationService {

  private static final Class<?> PKG = SourceRecordValidationService.class;

  private SourceRecordValidationService() {}

  public static ValidationReport validateGroup(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return validateGroup(
        group, variables, metadataProvider, ParallelValidationSupport.DEFAULT_PARALLELISM);
  }

  public static ValidationReport validateGroup(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int validationParallelism)
      throws HopException {
    ValidationModels models =
        ResourceDefinitionGroupResolver.resolve(group, variables, metadataProvider);
    return validateModels(models, variables, metadataProvider, validationParallelism);
  }

  public static ValidationReport validateGroupByName(
      String groupName, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    ResourceDefinitionGroupMeta group =
        ResourceDefinitionGroupResolver.loadGroup(groupName, metadataProvider);
    return validateGroup(group, variables, metadataProvider);
  }

  public static ValidationReport validateModels(
      ValidationModels models, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return validateModels(
        models, variables, metadataProvider, ParallelValidationSupport.DEFAULT_PARALLELISM);
  }

  public static ValidationReport validateModels(
      ValidationModels models,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int validationParallelism)
      throws HopException {
    if (models == null || models.group() == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceRecordValidationService.Error.MissingGroup"));
    }

    ValidationReport report = new ValidationReport(models.group().getName());
    Map<RecordDefinitionKey, List<SourceUsage>> usageIndex =
        SourceUsageIndexBuilder.build(models, variables);
    String defaultNamespace = DvCatalogNamespaces.projectSourcesNamespace(variables);
    int previewRowLimit = Math.max(1, models.group().getPreviewRowLimit());
    boolean detailedDataTypeChecking = models.group().isDetailedDataTypeChecking();
    int parallelism = ParallelValidationSupport.resolveParallelism(validationParallelism);

    List<Map.Entry<RecordDefinitionKey, List<SourceUsage>>> entries =
        new ArrayList<>(usageIndex.entrySet());
    List<RecordDefinitionValidation> validations =
        ParallelValidationSupport.map(
            parallelism,
            entries,
            (entry, index) -> {
              RecordDefinitionKey templateKey = entry.getKey();
              List<SourceUsage> usages = entry.getValue();
              String catalogConnection = resolveCatalogConnection(usages);
              RecordDefinitionKey resolvedKey =
                  SourceUsageIndexBuilder.resolveKey(
                      templateKey, catalogConnection, variables, defaultNamespace);

              RecordDefinition definition =
                  loadDefinition(catalogConnection, resolvedKey, variables, metadataProvider);
              return validateDefinition(
                  definition,
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

  private static String resolveCatalogConnection(List<SourceUsage> usages) {
    if (usages == null) {
      return null;
    }
    for (SourceUsage usage : usages) {
      if (!Utils.isEmpty(usage.catalogConnection())) {
        return usage.catalogConnection();
      }
    }
    return null;
  }

  private static RecordDefinition loadDefinition(
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

  private static RecordDefinitionValidation validateDefinition(
      RecordDefinition definition,
      RecordDefinitionKey key,
      String catalogConnection,
      List<SourceUsage> usages,
      int previewRowLimit,
      boolean detailedDataTypeChecking,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (definition == null) {
      List<ValidationIssue> issues =
          RemediationProposalSupport.buildIssues(
              null,
              usages,
              BaseMessages.getString(
                  PKG,
                  "SourceRecordValidationService.Error.DefinitionNotFound",
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

    DvSourceType sourceType = RecordDefinitionPhysicalRefSupport.resolveSourceType(definition);
    String sourceTypeName = sourceType != null ? sourceType.name() : null;
    String unavailableMessage = null;
    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        new RecordDefinitionSchemaDiffSupport.SchemaDiff(new ArrayList<>());

    String recordKey = key != null ? key.getNamespace() + "/" + key.getName() : "?";
    if (!RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(definition)) {
      unavailableMessage =
          ValidationFindingFormatter.liveSourceUnavailable(
              recordKey,
              BaseMessages.getString(PKG, "SourceRecordValidationService.Error.UnsupportedSource"));
    } else {
      try {
        unavailableMessage =
            verifyReadability(definition, sourceType, previewRowLimit, variables, metadataProvider);
        if (!Utils.isEmpty(unavailableMessage)) {
          unavailableMessage =
              ValidationFindingFormatter.liveSourceUnavailable(recordKey, unavailableMessage);
        }
        if (Utils.isEmpty(unavailableMessage)) {
          diff =
              discoverAndDiff(
                  definition, sourceType, detailedDataTypeChecking, variables, metadataProvider);
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

    ValidationIssueSupport.pruneStaleAcknowledgements(definition, diff, unavailableMessage);

    List<ValidationIssue> allIssues =
        RemediationProposalSupport.buildIssues(
            diff,
            usages,
            unavailableMessage,
            IssueKind.SOURCE_UNAVAILABLE,
            recordKey,
            sourceTypeName);
    int acknowledgedIssueCount = ValidationIssueSupport.countAcknowledged(definition, allIssues);
    List<ValidationIssue> visibleIssues =
        ValidationIssueSupport.filterAcknowledged(definition, allIssues);
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

  private static RecordDefinitionSchemaDiffSupport.SchemaDiff discoverAndDiff(
      RecordDefinition definition,
      DvSourceType sourceType,
      boolean detailedDataTypeChecking,
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

    // Same layout policy as catalog UI / hub-sat import (row meta types + nested PK/options).
    List<SourceField> storedFields = DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
    List<SourceField> discoveredFields = discovery.fields();
    if (sourceType == DvSourceType.ICEBERG || !detailedDataTypeChecking) {
      return RecordDefinitionSchemaDiffSupport.diffTypesOnly(storedFields, discoveredFields);
    }
    return RecordDefinitionSchemaDiffSupport.diff(storedFields, discoveredFields);
  }
}
