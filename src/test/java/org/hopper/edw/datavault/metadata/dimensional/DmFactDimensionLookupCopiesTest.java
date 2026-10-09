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
package org.hopper.edw.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Date;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmFactDimensionLookupCopiesTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void emptyCopiesMeansOneLookupCopy() {
    DmFactDimensionRole role = new DmFactDimensionRole("dim_order", "order_hk");
    assertEquals("1", role.resolveLookupCopies());
    role.setLookupCopies("   ");
    assertEquals("1", role.resolveLookupCopies());
  }

  @Test
  void variableExpressionIsKeptOnTheGeneratedLookup() throws Exception {
    DimensionalModel model = buildModel("${FACT_LOOKUP_COPIES}", false);
    PipelineMeta pipelineMeta = generate(model);
    TransformMeta lookup = findTransform(pipelineMeta, "lookup_dim_order");
    assertEquals("${FACT_LOOKUP_COPIES}", lookup.getCopiesString());
  }

  @Test
  void numericCopiesAreKeptOnTheGeneratedLookup() throws Exception {
    DimensionalModel model = buildModel("4", false);
    PipelineMeta pipelineMeta = generate(model);
    TransformMeta lookup = findTransform(pipelineMeta, "lookup_dim_order");
    assertEquals("4", lookup.getCopiesString());
  }

  @Test
  void skippedLookupDoesNotApplyCopies() throws Exception {
    DimensionalModel model = buildModel("4", true);
    PipelineMeta pipelineMeta = generate(model);
    assertFalse(
        pipelineMeta.getTransforms().stream().anyMatch(t -> "lookup_dim_order".equals(t.getName())));
    TransformMeta passthrough = findTransform(pipelineMeta, "map_surrogate_keys");
    assertEquals("1", passthrough.getCopiesString());
  }

  private static PipelineMeta generate(DimensionalModel model) throws HopException {
    DmFact fact = (DmFact) model.findTable("fact_orders");
    return fact.generateUpdatePipelines(testMetadataProvider(), new Variables(), model, new Date())
        .get(0);
  }

  private static TransformMeta findTransform(PipelineMeta pipelineMeta, String name) {
    return pipelineMeta.getTransforms().stream()
        .filter(transform -> name.equals(transform.getName()))
        .findFirst()
        .orElseThrow();
  }

  private static DimensionalModel buildModel(String lookupCopies, boolean skipLookup)
      throws HopException {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");

    DmDimension order = new DmDimension();
    order.setName("dim_order");
    order.setTableName("d_order");
    order.getNaturalKeys().add(new DmNaturalKeyField("order_id"));
    order.getSourceOrDefault().setSourceSql("SELECT order_id FROM staging.orders");
    model.getTables().add(order);

    DmFact fact = new DmFact();
    fact.setName("fact_orders");
    fact.setTableName("f_orders");
    fact.getSourceOrDefault().setSourceSql("SELECT order_id, amount FROM staging.orders");
    DmFactDimensionRole role = new DmFactDimensionRole("dim_order", "order_hk");
    role.setSourceFieldName("order_id");
    role.setLookupCopies(lookupCopies);
    role.setSkipDimensionLookup(skipLookup);
    if (skipLookup) {
      order.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.USE_SOURCE_FIELD);
      order.setSurrogateKeyField("order_hk");
      order.setSurrogateKeySourceField("order_hk");
      role.setSourceFieldName("order_hk");
      fact.getSourceOrDefault().setSourceSql("SELECT order_hk, amount FROM staging.orders");
    }
    fact.getDimensionRoles().add(role);
    fact.getMeasures().add(new DmFactMeasure("amount", true));
    model.getTables().add(fact);
    return model;
  }

  private static IHopMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("Vault");
    provider.getSerializer(DatabaseMeta.class).save(databaseMeta);
    return provider;
  }
}
