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
package org.apache.hop.datavault.workflow.actions.updateresourcegroup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.AggregatedFinding;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.FindingIssue;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.ModelFinding;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.Severity;
import org.junit.jupiter.api.Test;

class GroupModelValidationReportFormatterTest {

  @Test
  void formatLog_dedupesSharedAndCanSuppressWarnings() {
    GroupModelValidationReport report = sampleReport();
    String withWarnings = GroupModelValidationReportFormatter.formatLog(report, true);
    assertTrue(withWarnings.contains("[shared, 465 models]"));
    assertTrue(withWarnings.contains("encoding"));
    assertTrue(withWarnings.contains("Missing hub"));

    String noWarnings = GroupModelValidationReportFormatter.formatLog(report, false);
    assertFalse(noWarnings.contains("encoding"));
    assertTrue(noWarnings.contains("Missing hub"));
    assertTrue(noWarnings.toLowerCase().contains("suppressed"));
  }

  @Test
  void formatMarkdown_hasSections() {
    String md = GroupModelValidationReportFormatter.formatMarkdown(sampleReport());
    assertTrue(md.contains("Shared environment findings"));
    assertTrue(md.contains("Model-specific findings"));
    assertTrue(md.contains("465"));
  }

  private static GroupModelValidationReport sampleReport() {
    return new GroupModelValidationReport(
        "retail-sources",
        Instant.now(),
        Instant.now(),
        8,
        465,
        1,
        1,
        1,
        0,
        List.of(
            new AggregatedFinding(
                Severity.WARNING,
                "Target database encoding is not UTF-8",
                465,
                List.of("DATA_VAULT models/a.hdv"),
                "WARNING|Target database encoding is not UTF-8")),
        List.of(
            new ModelFinding(
                "DATA_VAULT",
                "models/broken.hdv",
                List.of(new FindingIssue(Severity.ERROR, "Missing hub H_X")),
                null)));
  }
}
