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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvCatalogNamespaces;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ProposalType;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RemediationProposal;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Compares baseline catalog contracts to mapped DV model attributes. Baseline fields never come
 * from models or target databases.
 */
public final class ModelContractValidationSupport {

  private static final Class<?> PKG = ModelContractValidationSupport.class;

  private ModelContractValidationSupport() {}

  public static ValidationReport enrich(
      ValidationReport report,
      ValidationModels models,
      String baselineVersionTag,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (report == null || models == null) {
      return report;
    }
    Map<RecordDefinitionKey, List<SourceUsage>> usageIndex =
        SourceUsageIndexBuilder.build(models, variables);
    String defaultNamespace = DvCatalogNamespaces.projectSourcesNamespace(variables);

    ValidationReport enriched = new ValidationReport(report.getGroupName());
    for (RecordDefinitionValidation existing : report.getRecordValidations()) {
      if (existing == null) {
        continue;
      }
      List<ValidationIssue> extra =
          buildIssuesForValidation(
              existing,
              usageIndex,
              baselineVersionTag,
              defaultNamespace,
              variables,
              metadataProvider);
      enriched.addRecordValidation(mergeIssues(existing, extra));
    }

    // Also cover usages present in models but missing from the primary report (e.g. model-only
    // run).
    for (Map.Entry<RecordDefinitionKey, List<SourceUsage>> entry : usageIndex.entrySet()) {
      boolean present =
          report.getRecordValidations().stream()
              .anyMatch(
                  v ->
                      v != null
                          && v.key() != null
                          && entry.getKey() != null
                          && entry.getKey().getName() != null
                          && entry.getKey().getName().equalsIgnoreCase(v.key().getName()));
      if (present) {
        continue;
      }
      String catalogConnection = firstCatalog(entry.getValue());
      RecordDefinitionKey resolved =
          SourceUsageIndexBuilder.resolveKey(
              entry.getKey(), catalogConnection, variables, defaultNamespace);
      List<ValidationIssue> issues =
          buildIssuesForUsages(
              resolved,
              catalogConnection,
              entry.getValue(),
              baselineVersionTag,
              variables,
              metadataProvider);
      if (!issues.isEmpty()) {
        enriched.addRecordValidation(
            new RecordDefinitionValidation(
                resolved,
                catalogConnection,
                null,
                false,
                null,
                entry.getValue(),
                issues,
                issues,
                0));
      }
    }
    return enriched;
  }

  private static List<ValidationIssue> buildIssuesForValidation(
      RecordDefinitionValidation existing,
      Map<RecordDefinitionKey, List<SourceUsage>> usageIndex,
      String baselineVersionTag,
      String defaultNamespace,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<SourceUsage> usages = existing.usages();
    if (usages == null || usages.isEmpty()) {
      usages = usageIndex.getOrDefault(existing.key(), List.of());
    }
    return buildIssuesForUsages(
        existing.key(),
        existing.catalogConnection(),
        usages,
        baselineVersionTag,
        variables,
        metadataProvider);
  }

  private static List<ValidationIssue> buildIssuesForUsages(
      RecordDefinitionKey key,
      String catalogConnection,
      List<SourceUsage> usages,
      String baselineVersionTag,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<ValidationIssue> issues = new ArrayList<>();
    if (Utils.isEmpty(catalogConnection) || key == null || usages == null) {
      return issues;
    }
    Optional<RecordDefinition> baselineDef =
        BaselineContractSupport.loadBaselineDefinition(
            catalogConnection, key, baselineVersionTag, variables, metadataProvider);
    if (baselineDef.isEmpty()) {
      return issues;
    }
    List<SourceField> baselineFields = BaselineContractSupport.fieldsOf(baselineDef.get());

    for (SourceUsage usage : usages) {
      if (usage == null
          || !SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usage.modelType())
          || Utils.isEmpty(usage.modelFilename())) {
        continue;
      }
      DataVaultModel model;
      try {
        model =
            ResourceDefinitionGroupResolver.loadDataVaultModel(
                usage.modelFilename(), variables, metadataProvider);
      } catch (HopException e) {
        continue;
      }
      IDvTable table = model.findTable(usage.modelElementName());
      if (!(table instanceof DvSatellite satellite)) {
        continue;
      }
      for (String fieldName : usage.mappedFields()) {
        if (Utils.isEmpty(fieldName)) {
          continue;
        }
        SourceField baselineField = BaselineContractSupport.findField(baselineFields, fieldName);
        if (baselineField == null) {
          continue;
        }
        SatelliteAttribute attribute = findAttribute(satellite, fieldName);
        if (attribute == null) {
          continue;
        }
        int baselineLen = BaselineContractSupport.parsePositiveInt(baselineField.getLength());
        int modelLen = BaselineContractSupport.parsePositiveInt(attribute.getLength());
        if (baselineLen > 0 && (modelLen <= 0 || modelLen < baselineLen)) {
          String message =
              BaseMessages.getString(
                  PKG,
                  "ModelContractValidationSupport.Issue.AttributeNarrower",
                  fieldName,
                  satellite.getName(),
                  Const.NVL(usage.modelName(), usage.modelFilename()),
                  Const.NVL(attribute.getLength(), "?"),
                  Const.NVL(baselineField.getLength(), "?"),
                  Utils.isEmpty(baselineVersionTag)
                      ? "working catalog"
                      : "catalog version '" + baselineVersionTag + "'");
          issues.add(
              new ValidationIssue(
                  ValidationIssueSupport.buildIssueId(
                      IssueKind.MODEL_ATTRIBUTE_NARROWER, fieldName, "narrower"),
                  IssueKind.MODEL_ATTRIBUTE_NARROWER,
                  IssueSeverity.BLOCKING,
                  fieldName,
                  message,
                  List.of(
                      new RemediationProposal(
                          ProposalType.ALIGN_MODELS_TO_BASELINE,
                          BaseMessages.getString(
                              PKG, "ModelContractValidationSupport.Proposal.Align.Summary"),
                          BaseMessages.getString(
                              PKG,
                              "ModelContractValidationSupport.Proposal.Align.Details",
                              fieldName,
                              Const.NVL(baselineField.getLength(), "?"),
                              Utils.isEmpty(baselineVersionTag)
                                  ? "working catalog"
                                  : baselineVersionTag)))));
        }
      }
    }
    return issues;
  }

  private static SatelliteAttribute findAttribute(DvSatellite satellite, String fieldName) {
    if (satellite == null || satellite.getAttributes() == null || Utils.isEmpty(fieldName)) {
      return null;
    }
    for (SatelliteAttribute attribute : satellite.getAttributes()) {
      if (attribute != null
          && attribute.getName() != null
          && fieldName.equalsIgnoreCase(attribute.getName())) {
        return attribute;
      }
    }
    return null;
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
    boolean inSync = existing.inSync() && visible.isEmpty();
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

  private static String firstCatalog(List<SourceUsage> usages) {
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
}
