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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.AggregatedFinding;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.FindingIssue;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.ModelFinding;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.Severity;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.ResourceGroupModelUpdatePlanner.ModelUpdateJob;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.ResourceGroupModelValidationSupport.ModelCheckOutcome;

/**
 * Collapses repeated environment-level model-check remarks (e.g. target Unicode/encoding) into
 * shared findings while keeping model-specific issues per model.
 */
public final class GroupModelValidationAggregator {

  private static final int SAMPLE_MODELS = 5;

  private GroupModelValidationAggregator() {}

  public static GroupModelValidationReport aggregate(
      String groupName,
      List<ModelCheckOutcome> outcomes,
      int parallelism,
      Instant startedAt,
      Instant finishedAt) {
    Instant start = startedAt != null ? startedAt : Instant.now();
    Instant end = finishedAt != null ? finishedAt : Instant.now();
    if (outcomes == null || outcomes.isEmpty()) {
      return new GroupModelValidationReport(
          Const.NVL(groupName, ""), start, end, parallelism, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    Map<String, FingerprintBucket> buckets = new LinkedHashMap<>();
    Map<String, List<PendingIssue>> pendingByModel = new LinkedHashMap<>();
    Map<String, ModelUpdateJob> jobByLabel = new LinkedHashMap<>();
    Map<String, String> loadFailures = new LinkedHashMap<>();

    int modelsChecked = 0;
    for (ModelCheckOutcome outcome : outcomes) {
      if (outcome == null || outcome.job() == null) {
        continue;
      }
      modelsChecked++;
      ModelUpdateJob job = outcome.job();
      String label = ResourceGroupModelValidationSupport.formatModelLabel(job);
      jobByLabel.put(label, job);
      pendingByModel.putIfAbsent(label, new ArrayList<>());

      if (outcome.failure() != null) {
        loadFailures.put(
            label,
            Const.NVL(
                outcome.failure().getMessage(), outcome.failure().getClass().getSimpleName()));
        continue;
      }
      if (outcome.remarks() == null) {
        continue;
      }
      for (ICheckResult remark : outcome.remarks()) {
        if (remark == null) {
          continue;
        }
        Severity severity = GroupModelValidationReport.severityOf(remark);
        if (severity == Severity.INFO) {
          continue;
        }
        String message = Const.NVL(remark.getText(), "").trim();
        if (Utils.isEmpty(message)) {
          continue;
        }
        boolean hasSource = remark.getSourceInfo() != null;
        String fingerprint = severity.name() + "|" + message;
        FingerprintBucket bucket =
            buckets.computeIfAbsent(fingerprint, k -> new FingerprintBucket(severity, message));
        bucket.models.add(label);
        pendingByModel.get(label).add(new PendingIssue(severity, message, fingerprint, hasSource));
      }
    }

    Set<String> sharedFingerprints = new LinkedHashSet<>();
    List<AggregatedFinding> sharedFindings = new ArrayList<>();
    for (Map.Entry<String, FingerprintBucket> entry : buckets.entrySet()) {
      FingerprintBucket bucket = entry.getValue();
      if (bucket.models.size() < 2) {
        continue;
      }
      sharedFingerprints.add(entry.getKey());
      List<String> samples = new ArrayList<>();
      int i = 0;
      for (String model : bucket.models) {
        if (i++ >= SAMPLE_MODELS) {
          break;
        }
        samples.add(model);
      }
      sharedFindings.add(
          new AggregatedFinding(
              bucket.severity, bucket.message, bucket.models.size(), samples, entry.getKey()));
    }

    List<ModelFinding> modelFindings = new ArrayList<>();
    int modelsWithErrors = 0;
    int totalErrors = 0;

    for (String label : pendingByModel.keySet()) {
      ModelUpdateJob job = jobByLabel.get(label);
      List<FindingIssue> modelSpecific = new ArrayList<>();
      for (PendingIssue pending : pendingByModel.get(label)) {
        if (sharedFingerprints.contains(pending.fingerprint) && !pending.hasSource) {
          continue;
        }
        modelSpecific.add(new FindingIssue(pending.severity, pending.message));
      }
      String loadFailure = loadFailures.get(label);
      if (loadFailure == null && modelSpecific.isEmpty()) {
        continue;
      }
      ModelFinding finding =
          new ModelFinding(
              job != null ? job.layer().name() : "?",
              job != null ? job.modelFile() : label,
              modelSpecific,
              loadFailure);
      modelFindings.add(finding);
      if (finding.hasError()) {
        modelsWithErrors++;
        if (loadFailure != null) {
          totalErrors++;
        }
        totalErrors +=
            (int) modelSpecific.stream().filter(i -> i.severity() == Severity.ERROR).count();
      }
    }

    int uniqueSharedWarnings =
        (int) sharedFindings.stream().filter(f -> f.severity() == Severity.WARNING).count();
    int uniqueSharedErrors =
        (int) sharedFindings.stream().filter(f -> f.severity() == Severity.ERROR).count();
    totalErrors += uniqueSharedErrors;

    return new GroupModelValidationReport(
        Const.NVL(groupName, ""),
        start,
        end,
        parallelism,
        modelsChecked,
        modelsWithErrors,
        totalErrors,
        uniqueSharedWarnings,
        uniqueSharedErrors,
        sharedFindings,
        modelFindings);
  }

  private static final class FingerprintBucket {
    final Severity severity;
    final String message;
    final LinkedHashSet<String> models = new LinkedHashSet<>();

    FingerprintBucket(Severity severity, String message) {
      this.severity = severity;
      this.message = message;
    }
  }

  private record PendingIssue(
      Severity severity, String message, String fingerprint, boolean hasSource) {}
}
