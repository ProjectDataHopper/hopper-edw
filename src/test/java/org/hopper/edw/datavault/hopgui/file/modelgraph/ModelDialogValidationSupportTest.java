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
package org.hopper.edw.datavault.hopgui.file.modelgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvModelCheckOptions;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.xp.RegisterModelConfigurationMetadataExtensionPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModelDialogValidationSupportTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    new RegisterModelConfigurationMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @Test
  void cloneDataVaultModelPreservesTables() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub();
    hub.setName("customer");
    hub.setTableName("h_customer");
    model.getTables().add(hub);

    DataVaultModel clone =
        ModelDialogValidationSupport.cloneDataVaultModel(model, new MemoryMetadataProvider());

    assertEquals(1, clone.getTables().size());
    assertEquals("customer", clone.getTables().getFirst().getName());
  }

  @Test
  void draftDuplicateNameProducesCheckError() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub first = new DvHub();
    first.setName("customer");
    first.setTableName("h_customer");
    DvHub second = new DvHub();
    second.setName("customer");
    second.setTableName("h_customer_2");
    model.getTables().add(first);
    model.getTables().add(second);

    DataVaultModel draft =
        ModelDialogValidationSupport.cloneDataVaultModel(model, new MemoryMetadataProvider());
    IDvTable draftSecond = draft.getTables().get(1);
    draftSecond.setName("customer");

    List<ICheckResult> remarks =
        draft.check(new MemoryMetadataProvider(), new Variables(), DvModelCheckOptions.defaults());

    assertTrue(
        remarks.stream()
            .anyMatch(
                remark ->
                    remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && remark.getText() != null
                        && remark.getText().contains("customer")));
  }

  @Test
  void tableDialogValidateChecksOnlySelectedTable() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub good = new DvHub();
    good.setName("good_hub");
    good.setTableName("h_good");
    DvHub broken = new DvHub();
    // empty name produces a check error only when this hub is validated
    broken.setName("");
    broken.setTableName("h_broken");
    model.getTables().add(good);
    model.getTables().add(broken);

    DataVaultModel draft =
        ModelDialogValidationSupport.cloneDataVaultModel(model, new MemoryMetadataProvider());
    IDvTable draftGood = draft.getTables().get(0);
    IDvTable draftBroken = draft.getTables().get(1);

    List<ICheckResult> tableOnly = new java.util.ArrayList<>();
    draftGood.check(
        tableOnly,
        new MemoryMetadataProvider(),
        new Variables(),
        DvModelCheckOptions.fastOnly(),
        draft);

    List<ICheckResult> wholeModel =
        draft.check(new MemoryMetadataProvider(), new Variables(), DvModelCheckOptions.fastOnly());

    // Single-table validate must not include remarks for other tables in the model.
    assertTrue(
        tableOnly.stream().allMatch(r -> r.getSourceInfo() == draftGood),
        () -> "Expected only good_hub remarks, got: " + tableOnly);
    assertTrue(
        wholeModel.stream().anyMatch(r -> r.getSourceInfo() == draftBroken),
        () -> "Expected whole-model check to include broken hub remarks, got: " + wholeModel);
    assertTrue(
        wholeModel.size() > tableOnly.size(),
        () ->
            "Expected whole-model check to produce more remarks than single-table check; whole="
                + wholeModel.size()
                + " tableOnly="
                + tableOnly.size());
  }

  @Test
  void cloneBusinessVaultModelResolvesNamedTargetDatabase() throws Exception {
    MemoryMetadataProvider metadata = namedConfigProvider();
    BusinessVaultModel model = new BusinessVaultModel();
    model.setConfigurationName("business-vault");
    model.setFilename("/tmp/customer.hbv");
    ModelConfigurationResolver.attach(model, metadata);

    assertEquals("Vault", model.getConfigurationOrDefault().getTargetDatabase());

    BusinessVaultModel clone =
        ModelDialogValidationSupport.cloneBusinessVaultModel(model, metadata);

    assertEquals("/tmp/customer.hbv", clone.getFilename());
    assertEquals("business-vault", clone.getConfigurationName());
    assertEquals("Vault", clone.getConfigurationOrDefault().getTargetDatabase());
  }

  @Test
  void cloneBusinessVaultModelFallsBackToSourceMetadataProvider() throws Exception {
    MemoryMetadataProvider metadata = namedConfigProvider();
    BusinessVaultModel model = new BusinessVaultModel();
    model.setConfigurationName("business-vault");
    ModelConfigurationResolver.attach(model, metadata);

    BusinessVaultModel clone = ModelDialogValidationSupport.cloneBusinessVaultModel(model, null);

    assertEquals("Vault", clone.getConfigurationOrDefault().getTargetDatabase());
  }

  @Test
  void clonedScd2TableCheckDoesNotReportMissingNamedBvTargetDatabase() throws Exception {
    MemoryMetadataProvider metadata = namedConfigProvider();

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.setConfigurationName("business-vault");
    BvScd2Table table = new BvScd2Table();
    table.setName("customer_bv");
    table.setTableName("customer_bv");
    table.setFunctionalTimestampField("x_load_ts");
    table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));
    bvModel.getTables().add(table);
    ModelConfigurationResolver.attach(bvModel, metadata);

    DataVaultModel dvModel = new DataVaultModel();
    dvModel.setConfigurationName("data-vault");
    DvSatellite satellite = new DvSatellite();
    satellite.setName("sat_customer");
    satellite.setTableName("sat_customer");
    dvModel.getTables().add(satellite);
    ModelConfigurationResolver.attach(dvModel, metadata);

    BusinessVaultModel draft =
        ModelDialogValidationSupport.cloneBusinessVaultModel(bvModel, metadata);
    BvScd2Table draftTable = (BvScd2Table) draft.getTables().getFirst();
    List<ICheckResult> remarks = new ArrayList<>();
    draftTable.check(remarks, metadata, new Variables(), draft, dvModel);

    assertFalse(
        remarks.stream()
            .anyMatch(
                remark ->
                    remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && remark.getText() != null
                        && remark.getText().contains("Business Vault target database")),
        () -> "Expected named BV target database to resolve on cloned model, got: " + remarks);
    assertFalse(
        remarks.stream()
            .anyMatch(
                remark ->
                    remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && remark.getText() != null
                        && remark.getText().contains("Data Vault target database")),
        () -> "Expected named DV target database to resolve, got: " + remarks);
  }

  private static MemoryMetadataProvider namedConfigProvider() throws Exception {
    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    BusinessVaultConfiguration bvConfig = new BusinessVaultConfiguration();
    bvConfig.setName("business-vault");
    bvConfig.setTargetDatabase("Vault");
    metadata.getSerializer(BusinessVaultConfiguration.class).save(bvConfig);

    DataVaultConfiguration dvConfig = new DataVaultConfiguration();
    dvConfig.setName("data-vault");
    dvConfig.setTargetDatabase("Vault");
    metadata.getSerializer(DataVaultConfiguration.class).save(dvConfig);

    DatabaseMeta vault = new DatabaseMeta();
    vault.setName("Vault");
    metadata.getSerializer(DatabaseMeta.class).save(vault);
    return metadata;
  }
}
