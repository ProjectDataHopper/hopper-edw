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
package org.hopper.edw.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvUpdateWorkflowSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void postgresBulkLoadUsesPipelineActionWithCorrectCsvPath() throws Exception {
    if (!DvBulkLoadPluginSupport.isTransformPluginAvailable(
            DvBulkLoadPluginSupport.PG_BULK_LOADER_ID)
        || !DvBulkLoadPluginSupport.isActionPluginAvailable(
            DvUpdateWorkflowSupport.PIPELINE_ACTION_ID)) {
      return;
    }

    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setTargetLoadMode(DvTargetLoadMode.STAGING_FILE.getCode());
    config.setTargetTableParallelCopies("1");
    config.setBulkLoadStagingFolder("/tmp/dv2/bulk/");

    DatabaseMeta databaseMeta =
        new DatabaseMeta(
            "postgres-test", "PostgreSQL", "Native", "", "localhost", "test", "postgres", "");
    Variables variables = new Variables();

    PipelineMeta pipelineMeta =
        buildStagingPipeline(config, variables, databaseMeta, "dm-fact-f_orders");
    pipelineMeta.setName("0001-dm-fact-f_orders");

    List<DvUpdateWorkflowSupport.DvStagingLoadDescriptor> descriptors =
        DvUpdateWorkflowSupport.buildStagingDescriptors(
            config, variables, "retail-f-orders", databaseMeta, "target-db", List.of(pipelineMeta));

    assertEquals(1, descriptors.size());
    assertEquals(
        "/tmp/dv2/bulk/dm-fact-f_orders-${Internal.Transform.CopyNr}",
        descriptors.get(0).stagingFileBase());

    String stagedCsvPath =
        DvTargetLoadSupport.resolveStagedCsvFilePath(descriptors.get(0).stagingFileBase(), 0);
    assertEquals("/tmp/dv2/bulk/dm-fact-f_orders-0.csv", stagedCsvPath);
    assertFalse(stagedCsvPath.contains("0001-"));

    WorkflowMeta workflowMeta =
        DvUpdateWorkflowSupport.buildMasterWorkflow(
            descriptors, config, variables, "local", "retail-f-orders");

    long postgresBulkPipelineActions =
        workflowMeta.getActions().stream()
            .filter(action -> action.getAction() != null)
            .filter(
                action ->
                    DvUpdateWorkflowSupport.PIPELINE_ACTION_ID.equals(
                            action.getAction().getPluginId())
                        && action.getName().startsWith("bulk_load_"))
            .count();
    assertEquals(1, postgresBulkPipelineActions);
    assertTrue(
        workflowMeta.getActions().stream()
            .noneMatch(
                action ->
                    action.getAction() != null
                        && DvBulkLoadCommandSupport.SQL_ACTION_ID.equals(
                            action.getAction().getPluginId())));
  }

  @Test
  void masterWorkflowChainsPipelineAndBulkActionsPerShard() throws Exception {
    // PIPELINE is required for the staging pipeline hop; MYSQL bulk load for the bulk actions.
    // hop-databases-mysql alone is not enough — hop-action-pipeline must be on the test classpath.
    if (!DvBulkLoadPluginSupport.isActionPluginAvailable(
            DvBulkLoadCommandSupport.MYSQL_BULK_LOAD_ACTION_ID)
        || !DvBulkLoadPluginSupport.isActionPluginAvailable(
            DvUpdateWorkflowSupport.PIPELINE_ACTION_ID)) {
      return;
    }

    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setTargetLoadMode(DvTargetLoadMode.STAGING_FILE.getCode());
    config.setTargetTableParallelCopies("4");
    config.setBulkLoadStagingFolder("/tmp/dv2/bulk/");

    DatabaseMeta databaseMeta =
        new DatabaseMeta("mysql-test", "MySQL", "Native", "", "localhost", "test", "root", "");
    Variables variables = new Variables();

    PipelineMeta pipelineMeta =
        buildStagingPipeline(config, variables, databaseMeta, "hub-customer-src");

    List<DvUpdateWorkflowSupport.DvStagingLoadDescriptor> descriptors =
        DvUpdateWorkflowSupport.buildStagingDescriptors(
            config, variables, "vault1", databaseMeta, "target-db", List.of(pipelineMeta));

    assertEquals(1, descriptors.size());
    assertEquals(4, descriptors.get(0).parallelCopies());

    WorkflowMeta workflowMeta =
        DvUpdateWorkflowSupport.buildMasterWorkflow(
            descriptors, config, variables, "local", "vault1");

    long pipelineActions =
        workflowMeta.getActions().stream()
            .filter(action -> action.getAction() != null)
            .filter(
                action ->
                    DvUpdateWorkflowSupport.PIPELINE_ACTION_ID.equals(
                        action.getAction().getPluginId()))
            .count();
    long bulkActions =
        workflowMeta.getActions().stream()
            .filter(action -> action.getAction() != null)
            .filter(
                action ->
                    DvBulkLoadCommandSupport.MYSQL_BULK_LOAD_ACTION_ID.equals(
                        action.getAction().getPluginId()))
            .count();

    assertEquals(1, pipelineActions);
    assertEquals(4, bulkActions);
    assertEquals(5, workflowMeta.getWorkflowHops().size()); // Start -> pipeline -> 4 bulk actions
    assertTrue(
        workflowMeta.getActions().stream()
            .map(ActionMeta::getName)
            .anyMatch(name -> name.startsWith("bulk_load_hub_customer_")));
  }

  private static PipelineMeta buildStagingPipeline(
      DataVaultConfiguration config,
      Variables variables,
      DatabaseMeta databaseMeta,
      String pipelineName)
      throws Exception {
    DvTargetLoadSupport.TargetLoadContext ctx =
        new DvTargetLoadSupport.TargetLoadContext(
            config,
            variables,
            databaseMeta,
            "target-db",
            "hub_customer",
            pipelineName,
            "vault1",
            400,
            200);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(pipelineName);
    pipelineMeta.setFilename("/tmp/dv2/vault1/0001-" + pipelineName + ".hpl");
    TransformMeta predecessor = new TransformMeta("Constant", "add_LOAD_DATE", new ConstantMeta());

    IRowMeta layout = new RowMeta();
    layout.addValueMeta(new ValueMetaString("CUSTOMER_HK"));

    DvTargetLoadSupport.addTargetLoad(ctx, pipelineMeta, layout, predecessor, Set.of("flag"));
    return pipelineMeta;
  }
}
