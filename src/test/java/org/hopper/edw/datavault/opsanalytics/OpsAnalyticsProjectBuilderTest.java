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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyWorkflowScanner;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmModelLoadSupport;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.ActionUpdateResourceDefinitionGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpsAnalyticsProjectBuilderTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void writesConfigurationsGroupAndWorkflows(@TempDir Path home) throws Exception {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", home.toString());
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    OpsAnalyticsRequest request = OpsAnalyticsRequest.defaults();
    request.setOutputFolder(home.resolve("models/ops-analytics").toString());

    OpsAnalyticsProjectBuilder.OpsAnalyticsProject project =
        OpsAnalyticsProjectBuilder.write(request, variables, provider);

    assertEquals(
        "ops-analytics-dv",
        provider
            .getSerializer(DataVaultConfiguration.class)
            .load(OpsAnalyticsProjectBuilder.DV_CONFIGURATION)
            .getTargetDatabase());
    assertEquals(
        "ops-analytics-bv",
        provider
            .getSerializer(BusinessVaultConfiguration.class)
            .load(OpsAnalyticsProjectBuilder.BV_CONFIGURATION)
            .getTargetDatabase());
    assertEquals(
        "ops-analytics-dm",
        provider
            .getSerializer(DimensionalConfiguration.class)
            .load(OpsAnalyticsProjectBuilder.DM_CONFIGURATION)
            .getTargetDatabase());

    ResourceDefinitionGroupMeta group =
        provider
            .getSerializer(ResourceDefinitionGroupMeta.class)
            .load(OpsAnalyticsProjectBuilder.GROUP_NAME);
    assertHubsBeforeLinks(group.getDataVaultModelFiles());
    assertEquals(1, group.getBusinessVaultModelFiles().size());
    assertEquals(13, group.getDimensionalModelFiles().size());
    assertTrue(
        group
            .getDimensionalModelFiles()
            .get(0)
            .endsWith("/dm/" + OpsAnalyticsDimensionalBuilder.DIMENSIONS_FILE));
    assertEquals(OpsAnalyticsDimensionalBuilder.BUS_CATALOG, group.getBusinessProcessCatalog());
    assertEquals(group.getDimensionalModelFiles(), project.dimensionalModels());

    assertEquals(
        OpsAnalyticsProjectBuilder.DM_CONFIGURATION,
        DmModelLoadSupport.loadDimensionalModel(
                project.dimensionalModels().get(0), null, variables, provider)
            .getConfigurationName());
    DimensionalModel pipelineFact =
        DmModelLoadSupport.loadDimensionalModel(
            project.dimensionalModels().stream()
                .filter(file -> file.endsWith("ops-f-pipeline-load.hdm"))
                .findFirst()
                .orElseThrow(),
            null,
            variables,
            provider);
    assertEquals(OpsAnalyticsProjectBuilder.DM_CONFIGURATION, pipelineFact.getConfigurationName());
    assertTrue(
        pipelineFact.getTables().stream()
            .anyMatch(
                table ->
                    table
                        instanceof
                        org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias alias
                        && alias.getReferencedModelFilename() != null
                        && alias
                            .getReferencedModelFilename()
                            .endsWith(OpsAnalyticsDimensionalBuilder.DIMENSIONS_FILE)));

    ActionUpdateResourceDefinitionGroup initial = updateAction(project.initialWorkflow(), variables, provider);
    ActionUpdateResourceDefinitionGroup incremental =
        updateAction(project.incrementalWorkflow(), variables, provider);
    assertEquals(OpsAnalyticsProjectBuilder.GROUP_NAME, initial.getResourceDefinitionGroup());
    assertTrue(initial.isUpdateTargetDatabaseStructure());
    assertTrue(initial.isCreateMissingTargetTables());
    assertFalse(initial.isDoNotUpdateTargetDatabase());
    assertFalse(incremental.isUpdateTargetDatabaseStructure());
    assertTrue(incremental.isCreateMissingTargetTables());
    assertFalse(incremental.isDoNotUpdateTargetDatabase());
    assertFalse(initial.isManageVaultUpdateMetrics());
    assertTrue(initial.getSourceModelFilename().endsWith("/ops-analytics.hsm"));
    assertEquals(initial.getSourceModelFilename(), incremental.getSourceModelFilename());
    assertEquals(
        OpsAnalyticsRunConfigurationSupport.PIPELINE_RUN_CONFIGURATION,
        initial.getPipelineRunConfiguration());
    assertEquals(
        OpsAnalyticsRunConfigurationSupport.WORKFLOW_RUN_CONFIGURATION,
        initial.getWorkflowRunConfiguration());
    assertEquals(
        initial.getPipelineRunConfiguration(), incremental.getPipelineRunConfiguration());
    assertNull(
        provider
            .getSerializer(org.apache.hop.pipeline.config.PipelineRunConfiguration.class)
            .load(OpsAnalyticsRunConfigurationSupport.PIPELINE_RUN_CONFIGURATION)
            .getExecutionDataProfileName());

    assertEquals(
        2,
        EdwJourneyWorkflowScanner.scan(
                OpsAnalyticsProjectBuilder.GROUP_NAME, variables, home)
            .size());
  }

  private static ActionUpdateResourceDefinitionGroup updateAction(
      String storedPath, Variables variables, MemoryMetadataProvider provider) throws HopException {
    WorkflowMeta workflow = new WorkflowMeta();
    workflow.loadXml(variables, variables.resolve(storedPath), provider);
    ActionMeta actionMeta = workflow.findAction(OpsAnalyticsProjectBuilder.UPDATE_ACTION);
    assertNotNull(actionMeta);
    assertEquals("UPDATE_RESOURCE_DEFINITION_GROUP", actionMeta.getAction().getPluginId());
    return (ActionUpdateResourceDefinitionGroup) actionMeta.getAction();
  }

  private static void assertHubsBeforeLinks(List<String> files) {
    int lastHub = -1;
    int firstLink = Integer.MAX_VALUE;
    for (int i = 0; i < files.size(); i++) {
      String file = files.get(i);
      if (file.contains("ops_hub_")) {
        lastHub = i;
      }
      if (file.contains("ops_lnk_") && i < firstLink) {
        firstLink = i;
      }
    }
    assertTrue(lastHub >= 0 && firstLink < Integer.MAX_VALUE && lastHub < firstLink, files::toString);
  }
}
