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
import java.util.Iterator;
import java.util.List;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionValidationAcknowledgement;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;

/** Stable issue identifiers and acknowledgement lifecycle helpers. */
public final class ValidationIssueSupport {

  private static final String SEPARATOR = "|";

  private ValidationIssueSupport() {}

  public static String buildIssueId(IssueKind kind, String fieldName, String changeSignature) {
    return (kind != null ? kind.name() : "")
        + SEPARATOR
        + Const.NVL(fieldName, "")
        + SEPARATOR
        + Const.NVL(changeSignature, "");
  }

  public static String buildIssueId(
      IssueKind kind, RecordDefinitionSchemaDiffSupport.FieldChange change) {
    if (change == null) {
      return buildIssueId(kind, null, null);
    }
    String signature =
        change.kind() == RecordDefinitionSchemaDiffSupport.ChangeKind.CHANGED
                || change.kind() == RecordDefinitionSchemaDiffSupport.ChangeKind.PRIMARY_KEY_CHANGED
            ? change.details()
            : null;
    return buildIssueId(kind, change.fieldName(), signature);
  }

  public static boolean matchesAcknowledgement(
      RecordDefinitionValidationAcknowledgement acknowledgement, String issueId) {
    return acknowledgement != null
        && !Utils.isEmpty(acknowledgement.getIssueId())
        && acknowledgement.getIssueId().equals(issueId);
  }

  public static void pruneStaleAcknowledgements(
      RecordDefinition definition,
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff,
      String unavailableMessage) {
    if (definition == null || definition.getValidationAcknowledgements() == null) {
      return;
    }
    List<RecordDefinitionValidationAcknowledgement> acknowledgements =
        definition.getValidationAcknowledgements();
    if (acknowledgements.isEmpty()) {
      return;
    }

    Iterator<RecordDefinitionValidationAcknowledgement> iterator = acknowledgements.iterator();
    while (iterator.hasNext()) {
      RecordDefinitionValidationAcknowledgement acknowledgement = iterator.next();
      if (acknowledgement == null || Utils.isEmpty(acknowledgement.getIssueId())) {
        iterator.remove();
        continue;
      }
      if (!isAcknowledgementStillRelevant(acknowledgement.getIssueId(), diff, unavailableMessage)) {
        iterator.remove();
      }
    }
  }

  private static boolean isAcknowledgementStillRelevant(
      String issueId,
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff,
      String unavailableMessage) {
    ParsedIssueId parsed = parseIssueId(issueId);
    if (parsed == null) {
      return false;
    }

    return switch (parsed.kind()) {
      case SOURCE_UNAVAILABLE, SOURCE_UNREADABLE -> !Utils.isEmpty(unavailableMessage);
      case BASELINE_CONTRACT_MISSING, WORKING_CONTRACT_MISSING ->
          !Utils.isEmpty(unavailableMessage);
      case FIELD_ADDED ->
          hasFieldChange(
              diff,
              RecordDefinitionSchemaDiffSupport.ChangeKind.ADDED,
              parsed.fieldName(),
              parsed.changeSignature());
      case FIELD_REMOVED, MAPPING_BROKEN ->
          hasFieldChange(
              diff,
              RecordDefinitionSchemaDiffSupport.ChangeKind.REMOVED,
              parsed.fieldName(),
              parsed.changeSignature());
      case FIELD_TYPE_CHANGED ->
          hasFieldChange(
              diff,
              RecordDefinitionSchemaDiffSupport.ChangeKind.CHANGED,
              parsed.fieldName(),
              parsed.changeSignature());
      case PRIMARY_KEY_CHANGED -> hasPrimaryKeyChange(diff, parsed.changeSignature());
        // FK issues come from harvest (not SchemaDiff); keep acknowledgements until re-harvest
        // clears them.
      case FOREIGN_KEY_ADDED, FOREIGN_KEY_REMOVED, FOREIGN_KEY_CHANGED -> true;
        // Model/target axes are re-evaluated each run; drop acknowledgements when re-run no longer
        // emits them.
      case MODEL_ATTRIBUTE_NARROWER, TARGET_DDL_REQUIRED -> true;
    };
  }

  private static boolean hasPrimaryKeyChange(
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff, String changeSignature) {
    if (diff == null || !diff.hasChanges()) {
      return false;
    }
    for (RecordDefinitionSchemaDiffSupport.FieldChange change : diff.changes()) {
      if (change == null
          || change.kind() != RecordDefinitionSchemaDiffSupport.ChangeKind.PRIMARY_KEY_CHANGED) {
        continue;
      }
      return Const.NVL(change.details(), "").equals(Const.NVL(changeSignature, ""));
    }
    return false;
  }

  private static boolean hasFieldChange(
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff,
      RecordDefinitionSchemaDiffSupport.ChangeKind expectedKind,
      String fieldName,
      String changeSignature) {
    if (diff == null || !diff.hasChanges() || Utils.isEmpty(fieldName)) {
      return false;
    }
    for (RecordDefinitionSchemaDiffSupport.FieldChange change : diff.changes()) {
      if (change == null || !fieldName.equals(change.fieldName())) {
        continue;
      }
      if (change.kind() != expectedKind) {
        continue;
      }
      if (expectedKind == RecordDefinitionSchemaDiffSupport.ChangeKind.CHANGED) {
        return Const.NVL(change.details(), "").equals(Const.NVL(changeSignature, ""));
      }
      return true;
    }
    return false;
  }

  private static ParsedIssueId parseIssueId(String issueId) {
    if (Utils.isEmpty(issueId)) {
      return null;
    }
    String[] parts = issueId.split("\\|", -1);
    if (parts.length < 3) {
      return null;
    }
    try {
      IssueKind kind = IssueKind.valueOf(parts[0]);
      return new ParsedIssueId(kind, parts[1], parts[2]);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static List<ValidationReport.ValidationIssue> filterAcknowledged(
      RecordDefinition definition, List<ValidationReport.ValidationIssue> issues) {
    if (issues == null || issues.isEmpty()) {
      return List.of();
    }
    if (definition == null || definition.getValidationAcknowledgements() == null) {
      return List.copyOf(issues);
    }
    List<ValidationReport.ValidationIssue> visible = new ArrayList<>();
    for (ValidationReport.ValidationIssue issue : issues) {
      if (issue == null) {
        continue;
      }
      if (!ValidationAcknowledgementSupport.isAcknowledged(definition, issue.issueId())) {
        visible.add(issue);
      }
    }
    return List.copyOf(visible);
  }

  public static int countAcknowledged(
      RecordDefinition definition, List<ValidationReport.ValidationIssue> issues) {
    if (issues == null || issues.isEmpty() || definition == null) {
      return 0;
    }
    int count = 0;
    for (ValidationReport.ValidationIssue issue : issues) {
      if (issue != null
          && ValidationAcknowledgementSupport.isAcknowledged(definition, issue.issueId())) {
        count++;
      }
    }
    return count;
  }

  private record ParsedIssueId(IssueKind kind, String fieldName, String changeSignature) {}
}
