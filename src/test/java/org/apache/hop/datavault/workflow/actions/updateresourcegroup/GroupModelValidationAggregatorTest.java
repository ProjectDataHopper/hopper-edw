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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.AggregatedFinding;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.Status;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.ResourceGroupModelUpdatePlanner.ModelLayer;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.ResourceGroupModelUpdatePlanner.ModelUpdateJob;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.ResourceGroupModelValidationSupport.ModelCheckOutcome;
import org.junit.jupiter.api.Test;

class GroupModelValidationAggregatorTest {

  private static final String ENCODING_MSG =
      "Target database 'Vault' encoding is not UTF-8 (Latin1). Unicode may be corrupted.";

  @Test
  void collapsesIdenticalWarningsAcrossModels() {
    List<ModelCheckOutcome> outcomes = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      outcomes.add(
          outcome(
              ModelLayer.DATA_VAULT,
              "models/m" + i + ".hdv",
              warning(ENCODING_MSG),
              ok("Target database is set")));
    }
    GroupModelValidationReport report =
        GroupModelValidationAggregator.aggregate("g", outcomes, 8, Instant.now(), Instant.now());

    assertEquals(10, report.modelsChecked());
    assertEquals(1, report.sharedFindings().size());
    AggregatedFinding shared = report.sharedFindings().getFirst();
    assertEquals(10, shared.modelCount());
    assertTrue(shared.message().contains("encoding") || shared.message().contains("UTF"));
    assertEquals(0, report.modelFindings().size());
    assertEquals(Status.WARNINGS, report.status());
    assertFalse(report.hasErrors());
  }

  @Test
  void keepsUniqueModelErrorsModelSpecific() {
    List<ModelCheckOutcome> outcomes =
        List.of(
            outcome(
                ModelLayer.DATA_VAULT,
                "models/a.hdv",
                warning(ENCODING_MSG),
                error("Missing hub H_CUSTOMER")),
            outcome(
                ModelLayer.DATA_VAULT,
                "models/b.hdv",
                warning(ENCODING_MSG),
                error("Missing hub H_ORDER")));

    GroupModelValidationReport report =
        GroupModelValidationAggregator.aggregate("g", outcomes, 4, Instant.now(), Instant.now());

    assertEquals(1, report.sharedFindings().size());
    assertEquals(2, report.sharedFindings().getFirst().modelCount());
    assertEquals(2, report.modelFindings().size());
    assertTrue(report.hasErrors());
    assertEquals(Status.FAILED, report.status());
  }

  @Test
  void loadFailureCountsAsError() {
    ModelUpdateJob job = new ModelUpdateJob(ModelLayer.BUSINESS_VAULT, "broken.hbv");
    ModelCheckOutcome outcome =
        new ModelCheckOutcome(job, List.of(), new RuntimeException("cannot parse"));
    GroupModelValidationReport report =
        GroupModelValidationAggregator.aggregate(
            "g", List.of(outcome), 1, Instant.now(), Instant.now());
    assertEquals(1, report.modelsWithErrors());
    assertTrue(report.hasErrors());
    assertEquals(1, report.modelFindings().size());
    assertTrue(report.modelFindings().getFirst().loadFailure().contains("cannot parse"));
  }

  private static ModelCheckOutcome outcome(ModelLayer layer, String file, ICheckResult... remarks) {
    ModelUpdateJob job = new ModelUpdateJob(layer, file);
    return new ModelCheckOutcome(job, List.of(remarks), null);
  }

  private static ICheckResult warning(String text) {
    return new CheckResult(ICheckResult.TYPE_RESULT_WARNING, text, null);
  }

  private static ICheckResult error(String text) {
    return new CheckResult(ICheckResult.TYPE_RESULT_ERROR, text, null);
  }

  private static ICheckResult ok(String text) {
    return new CheckResult(ICheckResult.TYPE_RESULT_OK, text, null);
  }
}
