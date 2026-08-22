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

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmSurrogateKeyPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void hybridDimensionPipelineUsesSourceFieldSurrogateStrategy() throws Exception {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setTableName("d_customer");
    dimension.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.USE_SOURCE_FIELD);
    dimension.setSurrogateKeyField("customer_hk");
    dimension.setSurrogateKeySourceField("customer_hk");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    dimension
        .getAttributes()
        .add(new DmDimensionAttribute("customer_name", DmScdUpdatePolicy.TYPE3_CURRENT));
    dimension
        .getSourceOrDefault()
        .setSourceSql("SELECT customer_id, customer_hk, customer_name FROM stg_customer");
    model.getTables().add(dimension);

    List<PipelineMeta> pipelines =
        dimension.generateUpdatePipelines(testMetadataProvider(), new Variables(), model, null);

    PipelineMeta pipelineMeta = pipelines.get(0);
    TransformMeta lookup =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof DimensionLookupMeta)
            .findFirst()
            .orElseThrow();
    DimensionLookupMeta lookupMeta = (DimensionLookupMeta) lookup.getTransform();
    assertEquals("customer_hk", lookupMeta.getFields().getReturns().getKeyField());
    assertEquals(
        DimensionLookupMeta.TechnicalKeyCreationMethod.FIELD,
        lookupMeta.getFields().getReturns().getCreationMethod());
    assertEquals("customer_hk", lookupMeta.getTkSourceField());
  }

  private static IHopMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("Vault");
    metadataProvider.getSerializer(DatabaseMeta.class).save(databaseMeta);
    return metadataProvider;
  }
}
