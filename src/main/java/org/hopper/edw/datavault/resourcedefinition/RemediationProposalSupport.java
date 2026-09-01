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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.ProposalType;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.RemediationProposal;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.ValidationIssue;

/** Builds report-only remediation proposals for source schema drift. */
public final class RemediationProposalSupport {

  private static final Class<?> PKG = RemediationProposalSupport.class;

  private RemediationProposalSupport() {}

  public static List<ValidationIssue> buildIssues(
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff,
      List<SourceUsage> usages,
      String sourceUnavailableMessage) {
    return buildIssues(diff, usages, sourceUnavailableMessage, IssueKind.SOURCE_UNAVAILABLE, null);
  }

  /**
   * @param unavailableKind kind when {@code sourceUnavailableMessage} is set (live, baseline, …)
   * @param recordKey display key for remediation details (optional)
   */
  public static List<ValidationIssue> buildIssues(
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff,
      List<SourceUsage> usages,
      String sourceUnavailableMessage,
      IssueKind unavailableKind,
      String recordKey) {
    return buildIssues(diff, usages, sourceUnavailableMessage, unavailableKind, recordKey, null);
  }

  /**
   * @param sourceType catalog/harvest source kind ({@code PIPELINE}, {@code JSON}, {@code
   *     COMPOSITE}, …). When the actual side is a source-model projection, findings tell the
   *     operator to republish rather than treat it as live JDBC drift.
   */
  public static List<ValidationIssue> buildIssues(
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff,
      List<SourceUsage> usages,
      String sourceUnavailableMessage,
      IssueKind unavailableKind,
      String recordKey,
      String sourceType) {
    List<ValidationIssue> issues = new ArrayList<>();
    if (!Utils.isEmpty(sourceUnavailableMessage)) {
      IssueKind kind = unavailableKind != null ? unavailableKind : IssueKind.SOURCE_UNAVAILABLE;
      issues.add(
          new ValidationIssue(
              ValidationIssueSupport.buildIssueId(kind, null, null),
              kind,
              IssueSeverity.BLOCKING,
              null,
              sourceUnavailableMessage,
              proposalsForUnavailable(kind, recordKey, sourceUnavailableMessage)));
      return issues;
    }

    if (diff == null || !diff.hasChanges()) {
      return issues;
    }

    for (RecordDefinitionSchemaDiffSupport.FieldChange change : diff.changes()) {
      if (change == null) {
        continue;
      }
      issues.add(buildIssueForChange(change, usages, sourceType));
    }
    return issues;
  }

  private static List<RemediationProposal> proposalsForUnavailable(
      IssueKind kind, String recordKey, String message) {
    List<RemediationProposal> proposals = new ArrayList<>();
    if (kind == IssueKind.BASELINE_CONTRACT_MISSING) {
      proposals.add(
          new RemediationProposal(
              ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
              BaseMessages.getString(PKG, "RemediationProposalSupport.TagBaseline.Summary"),
              BaseMessages.getString(PKG, "RemediationProposalSupport.TagBaseline.Details")));
    } else if (kind == IssueKind.WORKING_CONTRACT_MISSING) {
      proposals.add(
          new RemediationProposal(
              ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
              BaseMessages.getString(PKG, "RemediationProposalSupport.PublishWorking.Summary"),
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.PublishWorking.Details",
                  Const.NVL(recordKey, "?"))));
    } else {
      proposals.add(
          new RemediationProposal(
              ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
              BaseMessages.getString(PKG, "RemediationProposalSupport.LiveUnavailable.Summary"),
              Const.NVL(message, "")));
    }
    proposals.add(
        new RemediationProposal(
            ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
            BaseMessages.getString(
                PKG, "RemediationProposalSupport.BlockUpdateUntilResolved.Summary"),
            Const.NVL(message, "")));
    return proposals;
  }

  private static ValidationIssue buildIssueForChange(
      RecordDefinitionSchemaDiffSupport.FieldChange change,
      List<SourceUsage> usages,
      String sourceType) {
    String fieldName = change.fieldName();
    boolean mapped = isFieldMapped(fieldName, usages);
    return switch (change.kind()) {
      case ADDED ->
          new ValidationIssue(
              ValidationIssueSupport.buildIssueId(IssueKind.FIELD_ADDED, change),
              IssueKind.FIELD_ADDED,
              IssueSeverity.WARNING,
              fieldName,
              BaseMessages.getString(PKG, "RemediationProposalSupport.Issue.FieldAdded", fieldName),
              proposalsForAddedField(fieldName, usages));
      case REMOVED -> {
        IssueKind kind = mapped ? IssueKind.MAPPING_BROKEN : IssueKind.FIELD_REMOVED;
        yield new ValidationIssue(
            ValidationIssueSupport.buildIssueId(kind, change),
            kind,
            mapped ? IssueSeverity.BLOCKING : IssueSeverity.WARNING,
            fieldName,
            mapped
                ? BaseMessages.getString(
                    PKG, "RemediationProposalSupport.Issue.FieldRemovedMapped", fieldName)
                : BaseMessages.getString(
                    PKG, "RemediationProposalSupport.Issue.FieldRemoved", fieldName),
            proposalsForRemovedField(fieldName, mapped, usages));
      }
      case CHANGED ->
          new ValidationIssue(
              ValidationIssueSupport.buildIssueId(IssueKind.FIELD_TYPE_CHANGED, change),
              IssueKind.FIELD_TYPE_CHANGED,
              mapped ? IssueSeverity.BLOCKING : IssueSeverity.WARNING,
              fieldName,
              BaseMessages.getString(
                  PKG,
                  isSourceModelBacked(sourceType)
                      ? "RemediationProposalSupport.Issue.FieldChangedSourceModel"
                      : "RemediationProposalSupport.Issue.FieldChanged",
                  fieldName,
                  Utils.isEmpty(change.details()) ? "" : change.details()),
              proposalsForChangedField(fieldName, change.details(), mapped, usages, sourceType));
      case PRIMARY_KEY_CHANGED ->
          new ValidationIssue(
              ValidationIssueSupport.buildIssueId(IssueKind.PRIMARY_KEY_CHANGED, change),
              IssueKind.PRIMARY_KEY_CHANGED,
              IssueSeverity.BLOCKING,
              null,
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.Issue.PrimaryKeyChanged",
                  Utils.isEmpty(change.details()) ? "" : change.details()),
              List.of(
                  new RemediationProposal(
                      ProposalType.REFRESH_CATALOG_CONTRACT,
                      BaseMessages.getString(
                          PKG, "RemediationProposalSupport.RefreshCatalogContract.Summary"),
                      BaseMessages.getString(
                          PKG,
                          "RemediationProposalSupport.RefreshCatalogContract.PrimaryKeyDetails",
                          Utils.isEmpty(change.details()) ? "" : change.details()))));
    };
  }

  private static List<RemediationProposal> proposalsForAddedField(
      String fieldName, List<SourceUsage> usages) {
    List<RemediationProposal> proposals = new ArrayList<>();
    proposals.add(
        new RemediationProposal(
            ProposalType.REFRESH_CATALOG_CONTRACT,
            BaseMessages.getString(
                PKG, "RemediationProposalSupport.RefreshCatalogContract.Summary"),
            BaseMessages.getString(
                PKG, "RemediationProposalSupport.RefreshCatalogContract.AddedDetails", fieldName)));

    Set<String> hubElements = hubElementsForUsages(usages);
    if (!hubElements.isEmpty()) {
      proposals.add(
          new RemediationProposal(
              ProposalType.ADD_NEW_SATELLITE,
              BaseMessages.getString(PKG, "RemediationProposalSupport.AddNewSatellite.Summary"),
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.AddNewSatellite.Details",
                  fieldName,
                  String.join(", ", hubElements))));
    }

    Set<String> satelliteElements = satelliteElementsForUsages(usages);
    if (!satelliteElements.isEmpty()) {
      proposals.add(
          new RemediationProposal(
              ProposalType.EXTEND_EXISTING_SATELLITE,
              BaseMessages.getString(PKG, "RemediationProposalSupport.ExtendSatellite.Summary"),
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.ExtendSatellite.Details",
                  fieldName,
                  String.join(", ", satelliteElements))));
    }
    return proposals;
  }

  private static List<RemediationProposal> proposalsForRemovedField(
      String fieldName, boolean mapped, List<SourceUsage> usages) {
    List<RemediationProposal> proposals = new ArrayList<>();
    proposals.add(
        new RemediationProposal(
            ProposalType.REFRESH_CATALOG_CONTRACT,
            BaseMessages.getString(
                PKG, "RemediationProposalSupport.RefreshCatalogContract.Summary"),
            BaseMessages.getString(
                PKG,
                "RemediationProposalSupport.RefreshCatalogContract.RemovedDetails",
                fieldName)));
    if (mapped) {
      proposals.add(
          new RemediationProposal(
              ProposalType.REVIEW_MAPPINGS,
              BaseMessages.getString(PKG, "RemediationProposalSupport.ReviewMappings.Summary"),
              formatUsageDetails(fieldName, usages)));
      proposals.add(
          new RemediationProposal(
              ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
              BaseMessages.getString(
                  PKG, "RemediationProposalSupport.BlockUpdateUntilResolved.Summary"),
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.BlockUpdateUntilResolved.RemovedField",
                  fieldName)));
    }
    return proposals;
  }

  private static List<RemediationProposal> proposalsForChangedField(
      String fieldName,
      String details,
      boolean mapped,
      List<SourceUsage> usages,
      String sourceType) {
    List<RemediationProposal> proposals = new ArrayList<>();
    String change = Utils.isEmpty(details) ? "" : details;
    if (isSourceModelBacked(sourceType)) {
      proposals.add(
          new RemediationProposal(
              ProposalType.REFRESH_CATALOG_CONTRACT,
              BaseMessages.getString(
                  PKG, "RemediationProposalSupport.RepublishSourceModel.Summary"),
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.RepublishSourceModel.Details",
                  fieldName,
                  change)));
      if (mapped) {
        proposals.add(
            new RemediationProposal(
                ProposalType.REVIEW_MAPPINGS,
                BaseMessages.getString(PKG, "RemediationProposalSupport.ReviewMappings.Summary"),
                formatUsageDetails(fieldName, usages)));
      }
      return proposals;
    }
    LengthDirection direction = lengthDirection(change);

    // Catalog is never modified by remediation. The catalog length is the value used to expand
    // models and target DDL only.
    if (mapped) {
      proposals.add(
          new RemediationProposal(
              ProposalType.ALIGN_MODELS_TO_BASELINE,
              BaseMessages.getString(
                  PKG, "RemediationProposalSupport.ExpandModelsFromCatalog.Summary"),
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.ExpandModelsFromCatalog.Details",
                  fieldName,
                  change,
                  formatUsageDetails(fieldName, usages))));
      if (direction == LengthDirection.ACTUAL_LONGER) {
        proposals.add(
            new RemediationProposal(
                ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
                BaseMessages.getString(
                    PKG, "RemediationProposalSupport.LiveLongerThanCatalog.Summary"),
                BaseMessages.getString(
                    PKG,
                    "RemediationProposalSupport.LiveLongerThanCatalog.Details",
                    fieldName,
                    change)));
      } else if (direction == LengthDirection.ACTUAL_SHORTER) {
        proposals.add(
            new RemediationProposal(
                ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
                BaseMessages.getString(PKG, "RemediationProposalSupport.LiveNarrower.Summary"),
                BaseMessages.getString(
                    PKG, "RemediationProposalSupport.LiveNarrower.Details", fieldName, change)));
      }
      proposals.add(
          new RemediationProposal(
              ProposalType.REVIEW_MAPPINGS,
              BaseMessages.getString(PKG, "RemediationProposalSupport.ReviewMappings.Summary"),
              formatUsageDetails(fieldName, usages)));
    } else {
      // Unmapped length/type drift: do not rewrite the catalog from live here.
      proposals.add(
          new RemediationProposal(
              ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
              BaseMessages.getString(
                  PKG, "RemediationProposalSupport.UnmappedLengthChange.Summary"),
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.UnmappedLengthChange.Details",
                  fieldName,
                  change)));
      proposals.add(
          new RemediationProposal(
              ProposalType.REVIEW_MAPPINGS,
              BaseMessages.getString(PKG, "RemediationProposalSupport.ReviewMappings.Summary"),
              formatUsageDetails(fieldName, usages)));
    }
    return proposals;
  }

  static boolean isSourceModelBacked(String sourceType) {
    if (Utils.isEmpty(sourceType)) {
      return false;
    }
    String kind = sourceType.trim().toUpperCase(Locale.ROOT);
    return "PIPELINE".equals(kind) || "JSON".equals(kind) || "COMPOSITE".equals(kind);
  }

  enum LengthDirection {
    ACTUAL_LONGER,
    ACTUAL_SHORTER,
    UNKNOWN
  }

  /**
   * Diff details use {@code expected … → actual …}. Returns whether actual length is greater than
   * expected (growth), shorter (would-be shrink), or not a comparable length change.
   */
  static LengthDirection lengthDirection(String details) {
    String expected = RemediationProposalApplySupport.parseLengthSide(details, true);
    String actual = RemediationProposalApplySupport.parseLengthSide(details, false);
    int e = BaselineContractSupport.parsePositiveInt(expected);
    int a = BaselineContractSupport.parsePositiveInt(actual);
    if (e <= 0 || a <= 0) {
      return LengthDirection.UNKNOWN;
    }
    if (a > e) {
      return LengthDirection.ACTUAL_LONGER;
    }
    if (a < e) {
      return LengthDirection.ACTUAL_SHORTER;
    }
    return LengthDirection.UNKNOWN;
  }

  /**
   * @deprecated use {@link #lengthDirection(String)}
   */
  static boolean isActualLengthLonger(String details) {
    return lengthDirection(details) == LengthDirection.ACTUAL_LONGER;
  }

  private static boolean isFieldMapped(String fieldName, List<SourceUsage> usages) {
    if (Utils.isEmpty(fieldName) || usages == null) {
      return false;
    }
    for (SourceUsage usage : usages) {
      if (usage.mappedFields().contains(fieldName)) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> hubElementsForUsages(List<SourceUsage> usages) {
    return dataVaultElementsForUsages(usages);
  }

  private static Set<String> satelliteElementsForUsages(List<SourceUsage> usages) {
    return dataVaultElementsForUsages(usages);
  }

  private static Set<String> dataVaultElementsForUsages(List<SourceUsage> usages) {
    Set<String> elements = new LinkedHashSet<>();
    if (usages == null) {
      return elements;
    }
    for (SourceUsage usage : usages) {
      if (SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usage.modelType())
          && !Utils.isEmpty(usage.modelElementName())) {
        elements.add(usage.modelElementName());
      }
    }
    return elements;
  }

  private static String formatUsageDetails(String fieldName, List<SourceUsage> usages) {
    if (usages == null || usages.isEmpty()) {
      return BaseMessages.getString(PKG, "RemediationProposalSupport.NoUsages");
    }
    StringBuilder builder = new StringBuilder();
    for (SourceUsage usage : usages) {
      if (!usage.mappedFields().contains(fieldName)) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append('\n');
      }
      builder.append(
          BaseMessages.getString(
              PKG,
              "RemediationProposalSupport.UsageLine",
              usage.modelType(),
              usage.modelName(),
              usage.modelElementName()));
    }
    if (builder.length() == 0) {
      return BaseMessages.getString(PKG, "RemediationProposalSupport.NoMappedUsages");
    }
    return builder.toString();
  }
}
