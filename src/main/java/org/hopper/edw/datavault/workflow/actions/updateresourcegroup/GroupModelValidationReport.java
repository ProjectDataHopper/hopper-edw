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
package org.hopper.edw.datavault.workflow.actions.updateresourcegroup;

import java.time.Instant;
import java.util.List;
import org.apache.hop.core.ICheckResult;

/**
 * Aggregated model-check outcome for a resource definition group wave: shared environment findings
 * collapsed once, model-specific findings kept per model.
 */
public record GroupModelValidationReport(
    String groupName,
    Instant startedAt,
    Instant finishedAt,
    int parallelism,
    int modelsChecked,
    int modelsWithErrors,
    int totalErrors,
    int uniqueSharedWarnings,
    int uniqueSharedErrors,
    List<AggregatedFinding> sharedFindings,
    List<ModelFinding> modelFindings) {

  public GroupModelValidationReport {
    sharedFindings = sharedFindings != null ? List.copyOf(sharedFindings) : List.of();
    modelFindings = modelFindings != null ? List.copyOf(modelFindings) : List.of();
  }

  public enum Severity {
    ERROR,
    WARNING,
    INFO
  }

  public record AggregatedFinding(
      Severity severity,
      String message,
      int modelCount,
      List<String> sampleModels,
      String fingerprint) {
    public AggregatedFinding {
      sampleModels = sampleModels != null ? List.copyOf(sampleModels) : List.of();
    }
  }

  public record FindingIssue(Severity severity, String message) {}

  public record ModelFinding(
      String layer, String modelFile, List<FindingIssue> issues, String loadFailure) {
    public ModelFinding {
      issues = issues != null ? List.copyOf(issues) : List.of();
    }

    public boolean hasError() {
      if (loadFailure != null && !loadFailure.isBlank()) {
        return true;
      }
      return issues.stream().anyMatch(i -> i != null && i.severity() == Severity.ERROR);
    }
  }

  public enum Status {
    PASS,
    WARNINGS,
    FAILED
  }

  public Status status() {
    if (totalErrors > 0 || modelsWithErrors > 0) {
      return Status.FAILED;
    }
    if (uniqueSharedWarnings > 0
        || modelFindings.stream()
            .flatMap(m -> m.issues().stream())
            .anyMatch(i -> i.severity() == Severity.WARNING)) {
      return Status.WARNINGS;
    }
    return Status.PASS;
  }

  public boolean hasErrors() {
    return totalErrors > 0 || modelsWithErrors > 0;
  }

  public static Severity severityOf(ICheckResult remark) {
    if (remark == null) {
      return Severity.INFO;
    }
    return switch (remark.getType()) {
      case ICheckResult.TYPE_RESULT_ERROR -> Severity.ERROR;
      case ICheckResult.TYPE_RESULT_WARNING -> Severity.WARNING;
      default -> Severity.INFO;
    };
  }
}
