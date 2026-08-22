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
package org.apache.hop.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmFactSurrogateKeyPassthroughPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void autoSkipUsesMapSurrogateKeysInsteadOfProductLookup() throws Exception {
    DimensionalModel model = buildHashKeyPassthroughModel(false, false);
    DmFact fact = (DmFact) model.findTable("fact_inventory");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    PipelineMeta pipelineMeta =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date()).get(0);

    assertEquals(
        1,
        pipelineMeta.getTransforms().stream()
            .filter(t -> "map_surrogate_keys".equals(t.getName()))
            .count());
    assertFalse(
        pipelineMeta.getTransforms().stream().anyMatch(t -> "lookup_Product".equals(t.getName())));
    assertTrue(
        pipelineMeta.getTransforms().stream().anyMatch(t -> "lookup_Customer".equals(t.getName())));

    SelectValuesMeta passthroughMeta =
        (SelectValuesMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "map_surrogate_keys".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(passthroughMeta.getSelectOption().isSelectingAndSortingUnspecifiedFields());
    assertEquals(1, passthroughMeta.getSelectOption().getSelectFields().size());
    assertEquals(
        "product_hk", passthroughMeta.getSelectOption().getSelectFields().get(0).getName());
    assertEquals(
        "product_key", passthroughMeta.getSelectOption().getSelectFields().get(0).getRename());
  }

  @Test
  void forceLookupGeneratesDimensionLookupDespiteMatchingHashKey() throws Exception {
    DimensionalModel model = buildHashKeyPassthroughModel(false, true);
    DmFact fact = (DmFact) model.findTable("fact_inventory");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    PipelineMeta pipelineMeta =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date()).get(0);

    assertFalse(
        pipelineMeta.getTransforms().stream()
            .anyMatch(t -> "map_surrogate_keys".equals(t.getName())));
    assertTrue(
        pipelineMeta.getTransforms().stream().anyMatch(t -> "lookup_Product".equals(t.getName())));
    assertTrue(
        pipelineMeta.getTransforms().stream().anyMatch(t -> "lookup_Customer".equals(t.getName())));

    DimensionLookupMeta productLookupMeta =
        (DimensionLookupMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "lookup_Product".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals("d_product", productLookupMeta.getTableName());
    assertEquals("product_hk", productLookupMeta.getFields().getReturns().getKeyField());
  }

  @Test
  void mixedFactUsesPassthroughForHashKeyAndLookupForNaturalKeyDimension() throws Exception {
    DimensionalModel model = buildHashKeyPassthroughModel(false, false);
    DmFact fact = (DmFact) model.findTable("fact_inventory");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    PipelineMeta pipelineMeta =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date()).get(0);

    long lookupCount =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof DimensionLookupMeta)
            .count();
    assertEquals(1, lookupCount);

    TransformMeta customerLookup =
        pipelineMeta.getTransforms().stream()
            .filter(t -> "lookup_Customer".equals(t.getName()))
            .findFirst()
            .orElseThrow();
    DimensionLookupMeta customerLookupMeta = (DimensionLookupMeta) customerLookup.getTransform();
    assertEquals("d_customer", customerLookupMeta.getTableName());
    assertEquals("customer_key", customerLookupMeta.getFields().getReturns().getKeyRename());
  }

  @Test
  void skipLookupDoesNotRequireDimensionLookupKeyOnTarget() throws HopException {
    DimensionalModel model = buildHashKeyPassthroughModel(false, false);
    DmFact fact = (DmFact) model.findTable("fact_inventory");
    DmDimension warehouse = (DmDimension) model.findTable("dim_product");
    warehouse.setName("dim_warehouse");
    warehouse.setTableName("d_warehouse");
    warehouse.setSurrogateKeyField("warehouse_hk");
    warehouse.setSurrogateKeySourceField("warehouse_hk");
    warehouse.getNaturalKeys().clear();
    warehouse.getNaturalKeys().add(new DmNaturalKeyField("warehouse_id"));

    DmFactDimensionRole warehouseRole =
        new DmFactDimensionRole("dim_warehouse", "Warehouse", "warehouse_hk");
    warehouseRole.setSourceFieldName("warehouse_hk");
    warehouseRole.setSkipDimensionLookup(true);
    fact.getDimensionRoles().add(warehouseRole);

    List<ICheckResult> remarks = new ArrayList<>();
    DmValidationSupport.validateFact(
        remarks, fact, model, new MemoryMetadataProvider(), new Variables());

    assertFalse(
        remarks.stream()
            .anyMatch(
                remark ->
                    remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && remark.getText() != null
                        && remark.getText().contains("DimensionLookupKeyMissingFromTarget")));
  }

  @Test
  void explicitSkipOnAutoIncrementDimensionFailsValidation() throws HopException {
    DimensionalModel model = buildHashKeyPassthroughModel(true, false);
    DmFact fact = (DmFact) model.findTable("fact_inventory");
    DmDimension customer = (DmDimension) model.findTable("dim_customer");
    customer.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.AUTO_INCREMENT);
    customer.setSurrogateKeyField("customer_key");
    fact.getDimensionRoles().get(0).setSkipDimensionLookup(true);

    List<ICheckResult> remarks = new ArrayList<>();
    DmValidationSupport.validateFact(
        remarks, fact, model, new MemoryMetadataProvider(), new Variables());

    assertTrue(
        remarks.stream()
            .anyMatch(
                remark ->
                    remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && remark.getText() != null
                        && remark.getText().contains("USE_SOURCE_FIELD")));
  }

  @Test
  void explicitSkipOnNaturalKeyType1DimensionPassesValidation() throws HopException {
    DimensionalModel model = buildNaturalKeyPassthroughModel(true);
    DmFact fact = (DmFact) model.findTable("fact_inventory");

    List<ICheckResult> remarks = new ArrayList<>();
    DmValidationSupport.validateFact(
        remarks, fact, model, new MemoryMetadataProvider(), new Variables());

    assertFalse(
        remarks.stream()
            .anyMatch(
                remark ->
                    remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && remark.getText() != null
                        && (remark.getText().contains("USE_SOURCE_FIELD")
                            || remark.getText().contains("natural key"))));
  }

  @Test
  void naturalKeyPassthroughSkipsWarehouseLookup() throws Exception {
    DimensionalModel model = buildNaturalKeyPassthroughModel(true);
    DmFact fact = (DmFact) model.findTable("fact_inventory");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    PipelineMeta pipelineMeta =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date()).get(0);

    assertTrue(
        pipelineMeta.getTransforms().stream()
            .anyMatch(t -> "map_surrogate_keys".equals(t.getName())));
    assertFalse(
        pipelineMeta.getTransforms().stream()
            .anyMatch(t -> "lookup_Warehouse".equals(t.getName())));

    SelectValuesMeta passthroughMeta =
        (SelectValuesMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "map_surrogate_keys".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals(1, passthroughMeta.getSelectOption().getSelectFields().size());
    assertEquals(
        "warehouse_id", passthroughMeta.getSelectOption().getSelectFields().get(0).getName());
  }

  private static DimensionalModel buildNaturalKeyPassthroughModel(boolean explicitWarehouseSkip) {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");

    DmDimension warehouse = new DmDimension();
    warehouse.setName("dim_warehouse");
    warehouse.setTableName("d_warehouse");
    warehouse.setScdType(DmDimensionScdType.TYPE1);
    warehouse.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.NONE);
    warehouse.getNaturalKeys().add(new DmNaturalKeyField("warehouse_id"));
    warehouse
        .getAttributes()
        .add(new DmDimensionAttribute("warehouse_name", DmScdUpdatePolicy.TYPE1));
    warehouse
        .getSourceOrDefault()
        .setSourceSql("SELECT warehouse_id, warehouse_name FROM stg_warehouse");
    model.getTables().add(warehouse);

    DmFact fact = new DmFact();
    fact.setName("fact_inventory");
    fact.setTableName("f_inventory");
    fact.getSourceOrDefault().setSourceSql("SELECT warehouse_id, stock_qty FROM stg_inventory");

    DmFactDimensionRole warehouseRole =
        new DmFactDimensionRole("dim_warehouse", "Warehouse", "warehouse_id");
    warehouseRole.setSourceFieldName("warehouse_id");
    warehouseRole.setSkipDimensionLookup(explicitWarehouseSkip);
    fact.getDimensionRoles().add(warehouseRole);

    fact.getMeasures().add(new DmFactMeasure("stock_qty", true));
    model.getTables().add(fact);
    return model;
  }

  private static DimensionalModel buildHashKeyPassthroughModel(
      boolean explicitProductSkip, boolean forceProductLookup) {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");

    DmDimension customer = new DmDimension();
    customer.setName("dim_customer");
    customer.setTableName("d_customer");
    customer.setScdType(DmDimensionScdType.TYPE1);
    customer.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    customer
        .getAttributes()
        .add(new DmDimensionAttribute("customer_name", DmScdUpdatePolicy.TYPE1));
    customer
        .getSourceOrDefault()
        .setSourceSql("SELECT customer_id, customer_name FROM stg_customer");
    model.getTables().add(customer);

    DmDimension product = new DmDimension();
    product.setName("dim_product");
    product.setTableName("d_product");
    product.setScdType(DmDimensionScdType.TYPE1);
    product.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.USE_SOURCE_FIELD);
    product.setSurrogateKeyField("product_hk");
    product.setSurrogateKeySourceField("product_hk");
    product.getNaturalKeys().add(new DmNaturalKeyField("product_id"));
    product.getAttributes().add(new DmDimensionAttribute("product_name", DmScdUpdatePolicy.TYPE1));
    product
        .getSourceOrDefault()
        .setSourceSql("SELECT product_id, product_hk, product_name FROM stg_product");
    model.getTables().add(product);

    DmFact fact = new DmFact();
    fact.setName("fact_inventory");
    fact.setTableName("f_inventory");
    fact.getSourceOrDefault()
        .setSourceSql("SELECT customer_id, product_hk, quantity FROM stg_inventory");

    DmFactDimensionRole customerRole =
        new DmFactDimensionRole("dim_customer", "Customer", "customer_key");
    customerRole.setSourceFieldName("customer_id");
    fact.getDimensionRoles().add(customerRole);

    DmFactDimensionRole productRole =
        new DmFactDimensionRole("dim_product", "Product", "product_key");
    productRole.setSourceFieldName("product_hk");
    productRole.setSkipDimensionLookup(explicitProductSkip);
    productRole.setForceDimensionLookup(forceProductLookup);
    fact.getDimensionRoles().add(productRole);

    fact.getMeasures().add(new DmFactMeasure("quantity", true));
    model.getTables().add(fact);
    return model;
  }

  private static IHopMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("Vault");
    metadataProvider.getSerializer(DatabaseMeta.class).save(databaseMeta);
    return metadataProvider;
  }
}
