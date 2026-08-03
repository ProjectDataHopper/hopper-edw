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
package org.apache.hop.datavault.hopgui.resourcedefinition;

import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;

/** One row in the validation results issue table. */
public record ValidationIssueRow(
    RecordDefinitionValidation validation, ValidationIssue issue, boolean acknowledged) {

  public int severityRank() {
    if (issue == null || issue.severity() == null) {
      return 99;
    }
    return switch (issue.severity()) {
      case BLOCKING -> 0;
      case WARNING -> 1;
      case INFO -> 2;
    };
  }

  public static boolean isAcknowledged(
      RecordDefinitionValidation validation, ValidationIssue issue, boolean showAcknowledged) {
    if (!showAcknowledged || validation == null || issue == null) {
      return false;
    }
    return validation.issues().stream()
            .noneMatch(visible -> visible.issueId().equals(issue.issueId()))
        && validation.allIssues().stream().anyMatch(all -> all.issueId().equals(issue.issueId()));
  }
}
