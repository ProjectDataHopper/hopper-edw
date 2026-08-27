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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport.ChangeKind;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport.FieldChange;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport.SchemaDiff;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.DiscoveryStatus;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestChange;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestSubjectResult;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.ValidationIssue;

/**
 * Converts a persisted {@link HarvestResult} into a {@link ValidationReport} so the schema gate can
 * apply severity policy without rediscovering live sources.
 */
public final class HarvestBackedValidationSupport {

  private HarvestBackedValidationSupport() {}

  public static ValidationReport toValidationReport(
      HarvestResult harvest, ValidationModels models, IVariables variables) {
    String groupName =
        models != null && models.group() != null
            ? models.group().getName()
            : harvest != null ? harvest.getResourceGroupName() : "?";
    ValidationReport report = new ValidationReport(groupName);
    if (harvest == null) {
      return report;
    }

    Map<RecordDefinitionKey, List<SourceUsage>> usageIndex =
        models != null ? SourceUsageIndexBuilder.build(models, variables) : Map.of();
    String defaultNamespace = DvCatalogNamespaces.projectSourcesNamespace(variables);

    Map<String, HarvestSubjectResult> bySubjectKey = indexSubjects(harvest);
    Map<String, Boolean> seen = new LinkedHashMap<>();

    // Prefer model-scoped subjects so usages/impact are populated.
    if (models != null && !usageIndex.isEmpty()) {
      for (Map.Entry<RecordDefinitionKey, List<SourceUsage>> entry : usageIndex.entrySet()) {
        RecordDefinitionKey templateKey = entry.getKey();
        List<SourceUsage> usages = entry.getValue();
        String catalogConnection = resolveCatalogConnection(usages, models);
        RecordDefinitionKey resolvedKey =
            SourceUsageIndexBuilder.resolveKey(
                templateKey, catalogConnection, variables, defaultNamespace);
        String subjectKey =
            Const.NVL(resolvedKey.getNamespace(), "") + "/" + Const.NVL(resolvedKey.getName(), "");
        HarvestSubjectResult subject = bySubjectKey.get(subjectKey);
        // Case-insensitive fallback on name within same namespace.
        if (subject == null) {
          subject = findSubjectLoose(bySubjectKey, subjectKey);
        }
        seen.put(subjectKey.toLowerCase(Locale.ROOT), true);
        report.addRecordValidation(
            buildValidation(
                resolvedKey, catalogConnection, subject, usages, harvest.getHarvestRunId()));
      }
    } else {
      for (HarvestSubjectResult subject : harvest.subjectsView()) {
        if (subject == null || Utils.isEmpty(subject.getSubjectKey())) {
          continue;
        }
        seen.put(subject.getSubjectKey().toLowerCase(Locale.ROOT), true);
        RecordDefinitionKey key = parseSubjectKey(subject.getSubjectKey());
        report.addRecordValidation(
            buildValidation(
                key,
                subject.getCatalogConnection(),
                subject,
                List.of(),
                harvest.getHarvestRunId()));
      }
    }

    // Harvest subjects not referenced by models (informational).
    for (HarvestSubjectResult subject : harvest.subjectsView()) {
      if (subject == null || Utils.isEmpty(subject.getSubjectKey())) {
        continue;
      }
      if (seen.containsKey(subject.getSubjectKey().toLowerCase(Locale.ROOT))) {
        continue;
      }
      RecordDefinitionKey key = parseSubjectKey(subject.getSubjectKey());
      report.addRecordValidation(
          buildValidation(
              key, subject.getCatalogConnection(), subject, List.of(), harvest.getHarvestRunId()));
    }

    return report;
  }

  private static RecordDefinitionValidation buildValidation(
      RecordDefinitionKey key,
      String catalogConnection,
      HarvestSubjectResult subject,
      List<SourceUsage> usages,
      String harvestRunId) {
    if (subject == null) {
      String message =
          ValidationFindingFormatter.liveSourceUnavailable(
              key != null ? key.getNamespace() + "/" + key.getName() : "?",
              "Subject not present in harvest run "
                  + Const.NVL(harvestRunId, "?")
                  + " (re-run Harvest source metadata for the full group)");
      List<ValidationIssue> issues =
          RemediationProposalSupport.buildIssues(
              null, usages, message, IssueKind.SOURCE_UNAVAILABLE, subjectKey(key));
      return new RecordDefinitionValidation(
          key,
          catalogConnection,
          null,
          false,
          new SchemaDiff(List.of()),
          usages,
          issues,
          issues,
          0);
    }

    String sourceType = subject.getSourceType();
    DiscoveryStatus discovery = subject.getDiscoveryStatus();

    if (discovery == DiscoveryStatus.UNSUPPORTED) {
      return new RecordDefinitionValidation(
          key,
          catalogConnection != null ? catalogConnection : subject.getCatalogConnection(),
          sourceType,
          true,
          new SchemaDiff(List.of()),
          usages,
          List.of(),
          List.of(),
          0);
    }

    if (discovery == DiscoveryStatus.ERROR || discovery == DiscoveryStatus.UNAVAILABLE) {
      String message =
          !Utils.isEmpty(subject.getMessage())
              ? subject.getMessage()
              : "Source unavailable in harvest run " + Const.NVL(harvestRunId, "?");
      message = ValidationFindingFormatter.liveSourceUnavailable(subject.getSubjectKey(), message);
      List<ValidationIssue> issues =
          RemediationProposalSupport.buildIssues(
              null, usages, message, IssueKind.SOURCE_UNAVAILABLE, subject.getSubjectKey());
      return new RecordDefinitionValidation(
          key,
          catalogConnection != null ? catalogConnection : subject.getCatalogConnection(),
          sourceType,
          false,
          new SchemaDiff(List.of()),
          usages,
          issues,
          issues,
          0);
    }

    SchemaDiff diff = toSchemaDiff(subject.getChanges());
    List<ValidationIssue> allIssues =
        new ArrayList<>(RemediationProposalSupport.buildIssues(diff, usages, null));
    allIssues.addAll(foreignKeyIssues(subject.getChanges()));
    // Annotate messages with harvest provenance for gate logs.
    if (!Utils.isEmpty(harvestRunId) && !allIssues.isEmpty()) {
      List<ValidationIssue> annotated = new ArrayList<>(allIssues.size());
      for (ValidationIssue issue : allIssues) {
        annotated.add(annotateHarvest(issue, harvestRunId));
      }
      allIssues = annotated;
    }

    boolean gateIssues =
        allIssues.stream()
            .anyMatch(i -> i != null && i.severity() != null && i.severity() != IssueSeverity.INFO);
    boolean inSync = subject.isInSync() && !gateIssues;
    return new RecordDefinitionValidation(
        key,
        catalogConnection != null ? catalogConnection : subject.getCatalogConnection(),
        sourceType,
        inSync,
        diff,
        usages,
        allIssues,
        allIssues,
        0);
  }

  static List<ValidationIssue> foreignKeyIssues(List<HarvestChange> changes) {
    List<ValidationIssue> issues = new ArrayList<>();
    if (changes == null) {
      return issues;
    }
    for (HarvestChange change : changes) {
      if (change == null || Utils.isEmpty(change.getChangeKind())) {
        continue;
      }
      String kind = change.getChangeKind().trim().toUpperCase(Locale.ROOT);
      if (!kind.startsWith("FOREIGN_KEY")) {
        continue;
      }
      IssueKind issueKind =
          switch (kind) {
            case "FOREIGN_KEY_REMOVED" -> IssueKind.FOREIGN_KEY_REMOVED;
            case "FOREIGN_KEY_CHANGED" -> IssueKind.FOREIGN_KEY_CHANGED;
            default -> IssueKind.FOREIGN_KEY_ADDED;
          };
      IssueSeverity severity =
          switch (Const.NVL(change.getSeverity(), "WARNING").toUpperCase(Locale.ROOT)) {
            case "BLOCKING" -> IssueSeverity.BLOCKING;
            case "INFO" -> IssueSeverity.INFO;
            default -> IssueSeverity.WARNING;
          };
      String message =
          "Foreign key "
              + kind.replace("FOREIGN_KEY_", "").toLowerCase(Locale.ROOT)
              + ": expected=["
              + Const.NVL(change.getExpectedDetail(), "")
              + "] actual=["
              + Const.NVL(change.getActualDetail(), "")
              + "]";
      issues.add(
          new ValidationIssue(
              ValidationIssueSupport.buildIssueId(issueKind, change.getFieldName(), message),
              issueKind,
              severity,
              change.getFieldName(),
              message,
              List.of()));
    }
    return issues;
  }

  static SchemaDiff toSchemaDiff(List<HarvestChange> changes) {
    if (changes == null || changes.isEmpty()) {
      return new SchemaDiff(List.of());
    }
    List<FieldChange> fieldChanges = new ArrayList<>();
    for (HarvestChange change : changes) {
      if (change == null || Utils.isEmpty(change.getChangeKind())) {
        continue;
      }
      ChangeKind kind = parseChangeKind(change.getChangeKind());
      if (kind == null) {
        continue;
      }
      String details =
          !Utils.isEmpty(change.getActualDetail())
              ? change.getActualDetail()
              : change.getExpectedDetail();
      fieldChanges.add(new FieldChange(kind, change.getFieldName(), details));
    }
    return new SchemaDiff(fieldChanges);
  }

  static ChangeKind parseChangeKind(String raw) {
    if (Utils.isEmpty(raw)) {
      return null;
    }
    try {
      return ChangeKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      // Map harvest severity-oriented aliases if any appear later.
      return switch (raw.trim().toUpperCase(Locale.ROOT)) {
        case "FIELD_ADDED", "ADDED" -> ChangeKind.ADDED;
        case "FIELD_REMOVED", "REMOVED" -> ChangeKind.REMOVED;
        case "TYPE_CHANGED", "LENGTH_CHANGED", "FIELD_TYPE_CHANGED", "CHANGED" ->
            ChangeKind.CHANGED;
        case "PK_CHANGED", "PRIMARY_KEY_CHANGED" -> ChangeKind.PRIMARY_KEY_CHANGED;
        default -> null;
      };
    }
  }

  /**
   * Severity mapping used by harvest storage and documented for operators. Remapped through {@link
   * RemediationProposalSupport} for gate issues; this helper is for unit tests and reporting.
   */
  public static IssueSeverity severityForChangeKind(ChangeKind kind) {
    if (kind == null) {
      return IssueSeverity.WARNING;
    }
    return switch (kind) {
      case REMOVED, PRIMARY_KEY_CHANGED -> IssueSeverity.BLOCKING;
      case ADDED, CHANGED -> IssueSeverity.WARNING;
    };
  }

  public static IssueKind issueKindForChangeKind(ChangeKind kind) {
    if (kind == null) {
      return IssueKind.FIELD_TYPE_CHANGED;
    }
    return switch (kind) {
      case ADDED -> IssueKind.FIELD_ADDED;
      case REMOVED -> IssueKind.FIELD_REMOVED;
      case CHANGED -> IssueKind.FIELD_TYPE_CHANGED;
      case PRIMARY_KEY_CHANGED -> IssueKind.PRIMARY_KEY_CHANGED;
    };
  }

  private static ValidationIssue annotateHarvest(ValidationIssue issue, String harvestRunId) {
    if (issue == null) {
      return null;
    }
    String message = issue.message();
    if (Utils.isEmpty(message) || message.contains("harvest run")) {
      return issue;
    }
    String annotated = message + " [harvest run " + harvestRunId + "]";
    return new ValidationIssue(
        issue.issueId(),
        issue.kind(),
        issue.severity(),
        issue.fieldName(),
        annotated,
        issue.proposals(),
        issue.downstreamImpact());
  }

  private static Map<String, HarvestSubjectResult> indexSubjects(HarvestResult harvest) {
    Map<String, HarvestSubjectResult> map = new LinkedHashMap<>();
    for (HarvestSubjectResult subject : harvest.subjectsView()) {
      if (subject != null && !Utils.isEmpty(subject.getSubjectKey())) {
        map.put(subject.getSubjectKey(), subject);
      }
    }
    return map;
  }

  private static HarvestSubjectResult findSubjectLoose(
      Map<String, HarvestSubjectResult> bySubjectKey, String subjectKey) {
    if (Utils.isEmpty(subjectKey)) {
      return null;
    }
    HarvestSubjectResult direct = bySubjectKey.get(subjectKey);
    if (direct != null) {
      return direct;
    }
    for (Map.Entry<String, HarvestSubjectResult> entry : bySubjectKey.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(subjectKey)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static String resolveCatalogConnection(
      List<SourceUsage> usages, ValidationModels models) {
    if (usages != null) {
      for (SourceUsage usage : usages) {
        if (usage != null && !Utils.isEmpty(usage.catalogConnection())) {
          return usage.catalogConnection();
        }
      }
    }
    if (models != null
        && models.group() != null
        && !Utils.isEmpty(models.group().getDataCatalogConnection())) {
      return models.group().getDataCatalogConnection();
    }
    return null;
  }

  private static RecordDefinitionKey parseSubjectKey(String subjectKey) {
    if (Utils.isEmpty(subjectKey)) {
      return new RecordDefinitionKey("", "?");
    }
    int slash = subjectKey.lastIndexOf('/');
    if (slash <= 0) {
      return new RecordDefinitionKey("", subjectKey);
    }
    return new RecordDefinitionKey(subjectKey.substring(0, slash), subjectKey.substring(slash + 1));
  }

  private static String subjectKey(RecordDefinitionKey key) {
    if (key == null) {
      return "?";
    }
    return Const.NVL(key.getNamespace(), "") + "/" + Const.NVL(key.getName(), "");
  }
}
