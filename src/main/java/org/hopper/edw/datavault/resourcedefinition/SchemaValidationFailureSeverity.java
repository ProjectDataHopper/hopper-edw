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

/** When the Validate Resource Definitions workflow action should fail the hop. */
public enum SchemaValidationFailureSeverity {
  /** Fail only on blocking/critical issues (default). */
  FAIL_ON_BLOCKING,
  /** Fail when any visible issue exists (warnings or blocking). */
  FAIL_ON_WARNINGS,
  /** Always succeed; still write reports and log findings. */
  WARN_ONLY;

  public static SchemaValidationFailureSeverity parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return FAIL_ON_BLOCKING;
    }
    try {
      return SchemaValidationFailureSeverity.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return FAIL_ON_BLOCKING;
    }
  }

  public boolean shouldFail(ValidationReport report) {
    if (report == null) {
      return false;
    }
    return switch (this) {
      case WARN_ONLY -> false;
        // INFO findings do not fail the gate (WARNING and BLOCKING do).
      case FAIL_ON_WARNINGS -> report.getGateRelevantIssueCount() > 0;
      case FAIL_ON_BLOCKING -> report.hasBlockingIssues();
    };
  }
}
