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
package org.hopper.edw.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.addsequence.AddSequenceMeta;
import org.apache.hop.pipeline.transforms.pipelineexecutor.PipelineExecutorMeta;
import org.apache.hop.pipeline.transforms.rowgenerator.RowGeneratorMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.actions.sql.ActionSql;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvMultiSourceUpdateWorkflowSupport;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2PipelineSupport.Scd2BuildContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvScd2PartitionWorkflowSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void driverPipelineGeneratesPartitionRowsAndExecutesScd2() throws Exception {
    Scd2BuildContext ctx = partitionedContext();
    PipelineMeta scd2 = BvScd2PipelineSupport.generatePipeline(ctx);
    PipelineMeta driver = BvScd2PartitionWorkflowSupport.buildDriverPipeline(ctx, scd2);

    assertEquals(scd2.getName() + "-partitions", driver.getName());
    TransformMeta generate =
        driver.findTransform(BvScd2PartitionWorkflowSupport.GENERATE_PARTITIONS_TRANSFORM);
    RowGeneratorMeta generateMeta = (RowGeneratorMeta) generate.getTransform();
    assertEquals("4", generateMeta.getRowLimit());
    assertEquals(
        BvScd2HashPartitionSqlSupport.PARTITION_COUNT_VARIABLE,
        generateMeta.getFields().get(0).getName());

    TransformMeta sequence =
        driver.findTransform(BvScd2PartitionWorkflowSupport.PARTITION_NUMBER_TRANSFORM);
    AddSequenceMeta sequenceMeta = (AddSequenceMeta) sequence.getTransform();
    assertEquals(
        BvScd2HashPartitionSqlSupport.PARTITION_NUMBER_VARIABLE, sequenceMeta.getValueName());
    assertEquals("0", sequenceMeta.getStartAt());
    assertEquals("1", sequenceMeta.getIncrementBy());

    TransformMeta executor =
        driver.findTransform(BvScd2PartitionWorkflowSupport.EXECUTE_SCD2_TRANSFORM);
    PipelineExecutorMeta executorMeta = (PipelineExecutorMeta) executor.getTransform();
    assertEquals(scd2.getName() + PipelineMeta.PIPELINE_EXTENSION, executorMeta.getFilename());
    assertEquals("1", executorMeta.getGroupSize());
    assertEquals(2, executorMeta.getParameters().size());
    assertEquals(
        BvScd2HashPartitionSqlSupport.PARTITION_COUNT_VARIABLE,
        executorMeta.getParameters().get(0).getVariable());
    assertEquals(
        BvScd2HashPartitionSqlSupport.PARTITION_NUMBER_VARIABLE,
        executorMeta.getParameters().get(1).getField());
  }

  @Test
  void wrapperWorkflowTruncatesThenRunsDriver() throws Exception {
    Scd2BuildContext ctx = partitionedContext();
    PipelineMeta scd2 = BvScd2PipelineSupport.generatePipeline(ctx);
    PipelineMeta driver = BvScd2PartitionWorkflowSupport.buildDriverPipeline(ctx, scd2);
    WorkflowMeta workflow =
        BvScd2PartitionWorkflowSupport.buildWorkflow(ctx, driver, StubPipelineAction::new);

    assertEquals(ctx.pipelineName + "-partitioned", workflow.getName());
    List<ActionMeta> actions = workflow.getActions();
    assertTrue(actions.size() >= 3);

    ActionSql sqlAction =
        (ActionSql)
            actions.stream()
                .map(ActionMeta::getAction)
                .filter(a -> a instanceof ActionSql)
                .findFirst()
                .orElseThrow();
    assertEquals("Vault", sqlAction.getConnection());
    assertTrue(sqlAction.getSql().contains("bv_customer_scd2"), sqlAction.getSql());
    assertTrue(sqlAction.getSql().toUpperCase().contains("TRUNCATE"), sqlAction.getSql());
    assertTrue(sqlAction.isSendOneStatement());
    assertTrue(sqlAction.isUseVariableSubstitution());
  }

  @Test
  void resolvePartitionedStagingFileBaseSubstitutesPartitionNumber() {
    assertEquals(
        "/tmp/dv2/bulk/bv-scd2-sat-2-${Internal.Transform.CopyNr}",
        BvScd2PartitionWorkflowSupport.resolvePartitionedStagingFileBase(
            "/tmp/dv2/bulk/bv-scd2-sat-${PARTITION_NUMBER}-${Internal.Transform.CopyNr}", 2));
  }

  private static Scd2BuildContext partitionedContext() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");
    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault", "POSTGRESQL");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("bv_customer_scd2");
    scd2Table.setTableName("bv_customer_scd2");
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.setHashKeyPartitionCount(BvScd2HashPartitionCount.FOUR);
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    return new Scd2BuildContext(
        scd2Table,
        satellite,
        bvModel,
        dvModel,
        bvModel.getConfigurationOrDefault(),
        dvModel.getConfigurationOrDefault(),
        null,
        new Variables(),
        databaseMeta,
        "Vault",
        databaseMeta,
        "Vault",
        "sat_customer",
        "bv_customer_scd2",
        "bv-scd2-bv_customer_scd2-sat_customer",
        "customer_hk",
        null,
        BvScd2PipelineSupport.resolveAttributeFieldNames(satellite),
        "x_load_ts",
        "valid_from",
        "valid_to",
        "x_record_source",
        BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
        BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
        true);
  }

  private static DataVaultModel loadVault1Model() throws Exception {
    Path path = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(path.toFile());
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    return model;
  }

  public static final class StubPipelineAction extends ActionBase implements IAction {
    private String filename;

    StubPipelineAction(String name) {
      super(name, "");
      setPluginId(DvMultiSourceUpdateWorkflowSupport.PIPELINE_ACTION_ID);
    }

    public String getFilename() {
      return filename;
    }

    public void setFilename(String filename) {
      this.filename = filename;
    }

    @Override
    public org.apache.hop.core.Result execute(org.apache.hop.core.Result prevResult, int nr) {
      return prevResult != null ? prevResult : new org.apache.hop.core.Result();
    }
  }
}
