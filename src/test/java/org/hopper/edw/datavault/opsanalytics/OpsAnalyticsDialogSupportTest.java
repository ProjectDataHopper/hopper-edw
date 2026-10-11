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
package org.hopper.edw.datavault.opsanalytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.hopgui.opsanalytics.OpsAnalyticsDialogOptions;
import org.hopper.edw.datavault.opsanalytics.OpsAnalyticsDialogSupport.ReplaceDecision;
import org.junit.jupiter.api.Test;

class OpsAnalyticsDialogSupportTest {

  @Test
  void replaceRequiresACheckedBoxAndAConfirm() {
    assertEquals(ReplaceDecision.PROCEED, OpsAnalyticsDialogSupport.replaceDecision(false, false));
    assertEquals(ReplaceDecision.REFUSE, OpsAnalyticsDialogSupport.replaceDecision(true, false));
    assertEquals(ReplaceDecision.CONFIRM, OpsAnalyticsDialogSupport.replaceDecision(true, true));
  }

  @Test
  void missingTablesIgnoreCaseAndSkipExecutionsUnlessAsked() {
    Set<String> present = Set.of("LOAD_RUN", "quality_run");
    assertTrue(OpsAnalyticsDialogSupport.missingTables(present, false).contains("load_pipeline_metric"));
    assertFalse(OpsAnalyticsDialogSupport.missingTables(present, false).contains("load_run"));
    assertFalse(OpsAnalyticsDialogSupport.missingTables(present, false).contains("hop_executions"));
    assertTrue(OpsAnalyticsDialogSupport.missingTables(present, true).contains("hop_executions"));
  }

  @Test
  void blankTargetsReuseTheOpsConnection() throws Exception {
    OpsAnalyticsDialogOptions options = new OpsAnalyticsDialogOptions();
    options.setConnectionName("OPS");
    options.setSchemaName("dv_ops");
    options.setOutputFolder("${PROJECT_HOME}/models/ops-analytics");
    options.setGroupName("ops-analytics");
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/tmp/ops-project");

    assertNull(OpsAnalyticsDialogSupport.validate(options));
    OpsAnalyticsRequest request =
        OpsAnalyticsDialogSupport.toRequest(options, variables, new MemoryMetadataProvider());
    assertEquals("OPS", request.getDvTargetConnection());
    assertEquals("OPS", request.getBvTargetConnection());
    assertEquals("OPS", request.getDmTargetConnection());
    assertEquals(
        OpsAnalyticsRunConfigurationSupport.PIPELINE_RUN_CONFIGURATION,
        request.getPipelineRunConfiguration());
    assertEquals(
        OpsAnalyticsRunConfigurationSupport.WORKFLOW_RUN_CONFIGURATION,
        request.getWorkflowRunConfiguration());
    assertEquals("/tmp/ops-project/models/ops-analytics", request.getOutputFolder());
    assertEquals("OpsAnalyticsDialog.Validate.Connection", OpsAnalyticsDialogSupport.validate(new OpsAnalyticsDialogOptions()));
  }
}
