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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.transform.junkdimension.JunkDimensionMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmJunkDimensionPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void factPipelineInsertsJunkDimensionBeforeDimensionLookups() throws Exception {
    DimensionalModel model = buildModel();
    DmFact fact = (DmFact) model.findTable("fact_sales");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    List<PipelineMeta> pipelines =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date());

    assertEquals(1, pipelines.size());
    PipelineMeta pipelineMeta = pipelines.get(0);
    assertEquals(4, pipelineMeta.getTransforms().size());

    TransformMeta junkTransform =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof JunkDimensionMeta)
            .findFirst()
            .orElseThrow();
    JunkDimensionMeta junkMeta = (JunkDimensionMeta) junkTransform.getTransform();
    assertEquals("junk_junk_sales_flags_key", junkTransform.getName());
    assertTrue(junkMeta.isReplaceFields());
    assertEquals("junk_sales_flags_key", junkMeta.resolveTechnicalKeyOutputField());

    TransformMeta tableInput =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof TableInputMeta)
            .findFirst()
            .orElseThrow();
    TransformMeta firstLookup =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof DimensionLookupMeta)
            .findFirst()
            .orElseThrow();
    assertTrue(pipelineMeta.findPreviousTransforms(firstLookup).contains(junkTransform));
    assertTrue(pipelineMeta.findPreviousTransforms(junkTransform).contains(tableInput));
  }

  @Test
  void sharedHashFieldUsesSurrogateKeyColumnInJunkTransform() throws Exception {
    DimensionalModel model = buildModel();
    DmJunkDimension junk = (DmJunkDimension) model.findTable("junk_sales_flags");
    junk.setSurrogateKeyField("junk_sales_hk");
    junk.setSurrogateKeyStrategy(DmJunkSurrogateKeyStrategy.COMPUTE_HASH_KEY);
    junk.setHashCodeStrategy(DmJunkHashCodeStrategy.MD5);
    junk.setUseSurrogateKeyAsHashCodeField(true);

    DmFact fact = (DmFact) model.findTable("fact_sales");
    IHopMetadataProvider metadataProvider = testMetadataProvider();
    List<PipelineMeta> pipelines =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date());

    JunkDimensionMeta junkMeta =
        (JunkDimensionMeta)
            pipelines.get(0).getTransforms().stream()
                .filter(t -> t.getTransform() instanceof JunkDimensionMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals("junk_sales_hk", junkMeta.getHashField());
    assertTrue(junkMeta.isUseSurrogateKeyAsHashCodeField());
    assertTrue(junkMeta.hashFieldSameAsTechnicalKey());
  }

  @Test
  void factEmbeddedJunkDimensionSkipsStandaloneCatalogPipeline() throws Exception {
    DimensionalModel model = buildModel();
    DmJunkDimension junk = (DmJunkDimension) model.findTable("junk_sales_flags");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    List<PipelineMeta> pipelines =
        junk.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date());

    assertTrue(pipelines.isEmpty());
  }

  private static DimensionalModel buildModel() throws HopException {
    DimensionalModel model = new DimensionalModel();
    model.setName("test-model");

    DimensionalConfiguration config = model.getConfigurationOrDefault();
    config.setTargetDatabase("Vault");

    DmDimension customer = new DmDimension();
    customer.setName("dim_customer");
    customer.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    customer.getSourceOrDefault().setSourceType(DmSourceType.SQL);
    customer.getSourceOrDefault().setSourceSql("SELECT customer_id FROM staging.customer");
    model.getTables().add(customer);

    DmJunkDimension junk = new DmJunkDimension();
    junk.setName("junk_sales_flags");
    DmJunkDimensionSupport.applyFactTableSource(junk, "fact_sales");
    junk.getKeyFields().add(new DmNaturalKeyField("is_promo"));
    junk.getKeyFields().add(new DmNaturalKeyField("is_clearance"));
    model.getTables().add(junk);

    DmFact fact = new DmFact();
    fact.setName("fact_sales");
    fact.getSourceOrDefault().setSourceType(DmSourceType.SQL);
    fact.getSourceOrDefault()
        .setSourceSql("SELECT customer_id, is_promo, is_clearance, amount FROM staging.sales");
    fact.getDimensionRoles()
        .add(new DmFactDimensionRole("dim_customer", "customer_id", "customer_key"));
    fact.getJunkDimensionRoles()
        .add(new DmFactJunkDimensionRole("junk_sales_flags", "junk_sales_flags_key"));
    fact.getMeasures().add(new DmFactMeasure("amount"));
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
