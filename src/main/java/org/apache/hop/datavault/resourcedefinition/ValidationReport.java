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
import java.util.List;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.catalog.model.RecordDefinitionKey;

/** Aggregated validation outcome for a resource definition group. */
public final class ValidationReport {

  public enum IssueKind {
    /** Live physical source cannot be discovered or is not refreshable. */
    SOURCE_UNAVAILABLE,
    SOURCE_UNREADABLE,
    /**
     * Source is used by models and present in the working catalog, but missing from the chosen
     * catalog version baseline (WORKING_VS_VERSION / version expected side).
     */
    BASELINE_CONTRACT_MISSING,
    /**
     * Source is used by models but the working-tree catalog has no record definition for it
     * (publish/generate the source first).
     */
    WORKING_CONTRACT_MISSING,
    FIELD_ADDED,
    FIELD_REMOVED,
    FIELD_TYPE_CHANGED,
    PRIMARY_KEY_CHANGED,
    /** Live/catalog foreign-key constraint added, removed, or composition changed. */
    FOREIGN_KEY_ADDED,
    FOREIGN_KEY_REMOVED,
    FOREIGN_KEY_CHANGED,
    MAPPING_BROKEN,
    /** Model attribute length/type is narrower than the baseline catalog contract. */
    MODEL_ATTRIBUTE_NARROWER,
    /** Physical target table requires DDL to match the model layout. */
    TARGET_DDL_REQUIRED
  }

  public enum IssueSeverity {
    BLOCKING,
    WARNING,
    INFO
  }

  public enum ProposalType {
    /**
     * Legacy alias for expanding models/DDL from the catalog field length. Does not change the
     * catalog.
     */
    UPDATE_TARGET_COLUMN_LENGTH,
    /**
     * Expand mapped model attributes (and optional target DDL) using the catalog field length. The
     * catalog is never modified.
     */
    ALIGN_MODELS_TO_BASELINE,
    /**
     * Dangerous: refuse a longer live field and restore catalog values from a catalog version.
     * Never invent values from models or target tables. Can truncate data at load time.
     */
    IGNORE_SOURCE_DRIFT,
    REFRESH_CATALOG_CONTRACT,
    ADD_NEW_SATELLITE,
    EXTEND_EXISTING_SATELLITE,
    REVIEW_MAPPINGS,
    BLOCK_UPDATE_UNTIL_RESOLVED,
    /** Generate a reviewable per-table DDL workflow package without executing it. */
    GENERATE_TARGET_DDL_PACKAGE
  }

  public record RemediationProposal(ProposalType type, String summary, String details) {}

  public record ValidationIssue(
      String issueId,
      IssueKind kind,
      IssueSeverity severity,
      String fieldName,
      String message,
      List<RemediationProposal> proposals,
      String downstreamImpact) {

    public ValidationIssue {
      proposals = proposals != null ? List.copyOf(proposals) : List.of();
    }

    /** Compatibility constructor without downstream impact annotation. */
    public ValidationIssue(
        String issueId,
        IssueKind kind,
        IssueSeverity severity,
        String fieldName,
        String message,
        List<RemediationProposal> proposals) {
      this(issueId, kind, severity, fieldName, message, proposals, null);
    }

    public ValidationIssue withDownstreamImpact(String impact) {
      return new ValidationIssue(issueId, kind, severity, fieldName, message, proposals, impact);
    }
  }

  public record RecordDefinitionValidation(
      RecordDefinitionKey key,
      String catalogConnection,
      String sourceType,
      boolean inSync,
      RecordDefinitionSchemaDiffSupport.SchemaDiff schemaDiff,
      List<SourceUsage> usages,
      List<ValidationIssue> allIssues,
      List<ValidationIssue> issues,
      int acknowledgedIssueCount) {

    public RecordDefinitionValidation {
      usages = usages != null ? List.copyOf(usages) : List.of();
      allIssues = allIssues != null ? List.copyOf(allIssues) : List.of();
      issues = issues != null ? List.copyOf(issues) : List.of();
    }

    public RecordDefinitionValidation(
        RecordDefinitionKey key,
        String catalogConnection,
        String sourceType,
        boolean inSync,
        RecordDefinitionSchemaDiffSupport.SchemaDiff schemaDiff,
        List<SourceUsage> usages,
        List<ValidationIssue> issues) {
      this(key, catalogConnection, sourceType, inSync, schemaDiff, usages, issues, issues, 0);
    }

    public boolean hasBlockingIssues() {
      return issues.stream().anyMatch(issue -> issue.severity() == IssueSeverity.BLOCKING);
    }
  }

  private final String groupName;
  private final List<RecordDefinitionValidation> recordValidations = new ArrayList<>();

  public ValidationReport(String groupName) {
    this.groupName = groupName;
  }

  public String getGroupName() {
    return groupName;
  }

  public List<RecordDefinitionValidation> getRecordValidations() {
    return List.copyOf(recordValidations);
  }

  public void addRecordValidation(RecordDefinitionValidation validation) {
    if (validation != null) {
      recordValidations.add(validation);
    }
  }

  public int getTotalDefinitions() {
    return recordValidations.size();
  }

  public int getInSyncCount() {
    return (int) recordValidations.stream().filter(RecordDefinitionValidation::inSync).count();
  }

  public int getIssueCount() {
    return recordValidations.stream().mapToInt(v -> v.issues().size()).sum();
  }

  /** Issues with severity WARNING or BLOCKING (INFO does not count). */
  public int getGateRelevantIssueCount() {
    return (int)
        recordValidations.stream()
            .flatMap(v -> v.issues().stream())
            .filter(
                issue ->
                    issue != null
                        && (issue.severity() == IssueSeverity.WARNING
                            || issue.severity() == IssueSeverity.BLOCKING))
            .count();
  }

  public boolean hasWarningIssues() {
    return recordValidations.stream()
        .flatMap(v -> v.issues().stream())
        .anyMatch(issue -> issue != null && issue.severity() == IssueSeverity.WARNING);
  }

  public int getAcknowledgedIssueCount() {
    return recordValidations.stream()
        .mapToInt(RecordDefinitionValidation::acknowledgedIssueCount)
        .sum();
  }

  public boolean hasBlockingIssues() {
    return recordValidations.stream().anyMatch(RecordDefinitionValidation::hasBlockingIssues);
  }
}
