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
package org.apache.hop.datavault.hopgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.workflow.actions.dimensionalupdate.ActionDimensionalUpdate;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.junit.jupiter.api.Test;

class ModelUpdateWorkflowClipboardSupportTest {

  @Test
  void resolvesBaseFilenameWithoutExtension() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/home/project");

    assertEquals(
        "retail-example",
        ModelUpdateWorkflowClipboardSupport.resolveActionNameFromModelFilename(
            variables, "${PROJECT_HOME}/models/retail-example.hdm"));
    assertEquals(
        "customer_vault",
        ModelUpdateWorkflowClipboardSupport.resolveActionNameFromModelFilename(
            variables, "C:\\data\\customer_vault.hdv"));
    assertEquals(
        "sales_bv",
        ModelUpdateWorkflowClipboardSupport.resolveActionNameFromModelFilename(
            null, "/tmp/sales_bv.hbv"));
  }

  @Test
  void appliesPipelineRunConfigurationToCopiedAction() {
    ActionDimensionalUpdate action = new ActionDimensionalUpdate();
    ModelUpdateWorkflowClipboardSupport.applyPipelineRunConfiguration(action, "local");
    assertEquals("local", action.getPipelineRunConfiguration());
  }

  @Test
  void listsPipelineRunConfigurationNamesSorted() throws Exception {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    metadataProvider.getSerializer(PipelineRunConfiguration.class).save(newRunConfig("zebra"));
    metadataProvider.getSerializer(PipelineRunConfiguration.class).save(newRunConfig("alpha"));

    assertEquals(
        List.of("alpha", "zebra"),
        ModelUpdateWorkflowClipboardSupport.listPipelineRunConfigurationNames(metadataProvider));
  }

  private static PipelineRunConfiguration newRunConfig(String name) {
    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    runConfiguration.setName(name);
    return runConfiguration;
  }

  @Test
  void returnsNullForBlankFilename() {
    assertNull(
        ModelUpdateWorkflowClipboardSupport.resolveActionNameFromModelFilename(
            new Variables(), null));
    assertNull(
        ModelUpdateWorkflowClipboardSupport.resolveActionNameFromModelFilename(
            new Variables(), "   "));
  }
}
