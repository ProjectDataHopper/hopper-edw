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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvReadOnlyExistingVaultSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void refuseUpdateThrowsWhenConfigurationIsReadOnly() {
    DataVaultModel model = documentationModel();
    HopException thrown =
        assertThrows(HopException.class, () -> DvReadOnlyExistingVaultSupport.refuseUpdate(model));
    assertTrue(thrown.getMessage().contains("read-only existing vault"));
  }

  @Test
  void refuseUpdateDoesNothingWhenNotReadOnly() throws HopException {
    DvReadOnlyExistingVaultSupport.refuseUpdate(new DataVaultModel());
  }

  @Test
  void skipResourceGroupJobOnlyForDataVaultLayer() {
    DataVaultModel model = documentationModel();
    assertTrue(DvReadOnlyExistingVaultSupport.skipDataVaultUpdateInResourceGroup(true, model));
    assertFalse(DvReadOnlyExistingVaultSupport.skipDataVaultUpdateInResourceGroup(false, model));
    assertFalse(
        DvReadOnlyExistingVaultSupport.skipDataVaultUpdateInResourceGroup(
            true, new DataVaultModel()));
  }

  @Test
  void relaxesSourceValidationWhenModelIsReadOnlyEvenIfTableIsHopManaged() {
    DataVaultModel model = documentationModel();
    DvHub hub = new DvHub("hub_customer");
    assertTrue(DvIntegrationSupport.isHopManaged(hub));
    assertFalse(DvIntegrationSupport.relaxesSourceValidation(hub));
    assertTrue(DvIntegrationSupport.relaxesSourceValidation(hub, model));
    assertEquals("doc", DvIntegrationSupport.integrationCanvasSuffix(hub, model));
  }

  @Test
  void checkAllowsDocumentationOnlyHubAndSatelliteWithoutRecordSources() throws Exception {
    DataVaultModel model = documentationModelWithHubAndSatellite("hub_customer");
    List<ICheckResult> remarks = model.check(metadataWithVault(), new Variables());
    assertFalse(
        hasError(remarks), () -> remarks.stream().map(ICheckResult::getText).toList().toString());
    assertTrue(
        remarks.stream()
            .anyMatch(r -> r.getText().contains("read-only existing vault configuration")));
  }

  @Test
  void checkStillErrorsWhenSatelliteParentHubIsMissing() throws Exception {
    DataVaultModel model = documentationModelWithHubAndSatellite("missing_hub");
    List<ICheckResult> remarks = model.check(metadataWithVault(), new Variables());
    assertTrue(hasError(remarks));
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("missing_hub")));
  }

  @Test
  void checkErrorsWhenReadOnlyHubIsMissingHashKeyFieldName() throws Exception {
    DataVaultModel model = documentationModelWithHubAndSatellite("hub_customer");
    DvHub hub = (DvHub) model.findTable("hub_customer");
    hub.setHashKeyFieldName(null);
    List<ICheckResult> remarks = model.check(metadataWithVault(), new Variables());
    assertTrue(hasError(remarks));
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("hash key field name")));
  }

  @Test
  void checkCommentsWhenHopManagedHubIsMissingHashKeyFieldName() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_customer");
    BusinessKey bk = new BusinessKey("customer_id");
    bk.setDataType("String");
    hub.setBusinessKeys(List.of(bk));
    model.getTables().add(hub);

    List<ICheckResult> remarks = new java.util.ArrayList<>();
    hub.check(remarks, null, new Variables(), DvModelCheckOptions.fastOnly(), model);
    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("hash key field name")));
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_COMMENT
                        && r.getText().contains("No hash key field name")));
  }

  @Test
  void generateUpdateDdlAndPipelinesThrowWhenReadOnly() {
    DataVaultModel model = documentationModelWithHubAndSatellite("hub_customer");
    DvHub hub = (DvHub) model.findTable("hub_customer");
    MemoryMetadataProvider provider = metadataWithVault();
    Variables variables = new Variables();

    HopException ddl =
        assertThrows(HopException.class, () -> hub.generateUpdateDdl(provider, variables, model));
    assertTrue(ddl.getMessage().contains("read-only existing vault"));

    HopException pipelines =
        assertThrows(
            HopException.class,
            () -> hub.generateUpdatePipelines(provider, variables, model, new Date(), null));
    assertTrue(pipelines.getMessage().contains("read-only existing vault"));
  }

  private static DataVaultModel documentationModel() {
    DataVaultConfiguration configuration = new DataVaultConfiguration();
    configuration.setReadOnlyExistingVault(true);
    configuration.setTargetDatabase("Vault");
    DataVaultModel model = new DataVaultModel();
    model.setConfiguration(configuration);
    return model;
  }

  private static DataVaultModel documentationModelWithHubAndSatellite(String satelliteHubName) {
    DataVaultModel model = documentationModel();
    DvHub hub = new DvHub("hub_customer");
    hub.setTableName("hub_customer");
    hub.setHashKeyFieldName("customer_hk");
    BusinessKey bk = new BusinessKey("customer_id");
    bk.setDataType("String");
    hub.setBusinessKeys(List.of(bk));

    DvSatellite satellite = new DvSatellite("sat_customer");
    satellite.setTableName("sat_customer");
    satellite.setHubName(satelliteHubName);
    SatelliteAttribute attr = new SatelliteAttribute();
    attr.setName("full_name");
    attr.setDataType("String");
    satellite.setAttributes(List.of(attr));

    model.setTables(List.of(hub, satellite));
    return model;
  }

  private static MemoryMetadataProvider metadataWithVault() {
    try {
      MemoryMetadataProvider provider = new MemoryMetadataProvider();
      DatabaseMeta databaseMeta = new DatabaseMeta();
      databaseMeta.setName("Vault");
      provider.getSerializer(DatabaseMeta.class).save(databaseMeta);
      return provider;
    } catch (HopException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean hasError(List<ICheckResult> remarks) {
    return remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
  }
}
