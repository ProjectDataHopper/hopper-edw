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

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmHybridDimensionPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void hybridBirthdatePoliciesUseDimensionLookupWithRenameAndCurrentFlag() throws Exception {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    DmDimension dimension = hybridCustomerDimension();
    model.getTables().add(dimension);

    PipelineMeta pipeline =
        dimension
            .generateUpdatePipelines(testMetadataProvider(), new Variables(), model, null)
            .get(0);

    TransformMeta lookup =
        pipeline.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof DimensionLookupMeta)
            .findFirst()
            .orElseThrow();
    DimensionLookupMeta lookupMeta = (DimensionLookupMeta) lookup.getTransform();
    assertTrue(lookupMeta.isUpdate());

    DimensionLookupMeta.DLField punchThrough =
        lookupMeta.getFields().getFields().stream()
            .filter(f -> "birthdate".equals(f.getLookup()))
            .findFirst()
            .orElseThrow();
    assertEquals("birthdate", punchThrough.getName());
    assertEquals(
        DimensionLookupMeta.DimensionUpdateType.PUNCH_THROUGH, punchThrough.getUpdateType());

    DimensionLookupMeta.DLField historized =
        lookupMeta.getFields().getFields().stream()
            .filter(f -> "birthdate_hist".equals(f.getLookup()))
            .findFirst()
            .orElseThrow();
    assertEquals("birthdate", historized.getName());
    assertEquals(DimensionLookupMeta.DimensionUpdateType.INSERT, historized.getUpdateType());

    DimensionLookupMeta.DLField currentFlag =
        lookupMeta.getFields().getFields().stream()
            .filter(f -> "is_current".equals(f.getLookup()))
            .findFirst()
            .orElseThrow();
    assertEquals(DimensionLookupMeta.DimensionUpdateType.LAST_VERSION, currentFlag.getUpdateType());

    IRowMeta layout =
        dimension.getTargetTableLayout(testMetadataProvider(), new Variables(), model);
    assertTrue(layout.indexOfValue("birthdate") >= 0);
    assertTrue(layout.indexOfValue("birthdate_hist") >= 0);
    assertTrue(layout.indexOfValue("is_current") >= 0);
  }

  private static DmDimension hybridCustomerDimension() {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setTableName("d_customer");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    DmDimensionAttribute punchThrough =
        new DmDimensionAttribute("birthdate", DmScdUpdatePolicy.TYPE1_PUNCH_THROUGH);
    punchThrough.setSourceFieldName("birthdate");
    dimension.getAttributes().add(punchThrough);
    DmDimensionAttribute historized =
        new DmDimensionAttribute("birthdate_hist", DmScdUpdatePolicy.TYPE2);
    historized.setSourceFieldName("birthdate");
    dimension.getAttributes().add(historized);
    dimension
        .getAttributes()
        .add(new DmDimensionAttribute("customer_name", DmScdUpdatePolicy.TYPE1));
    dimension
        .getSourceOrDefault()
        .setSourceSql("SELECT customer_id, birthdate, customer_name FROM stg_customer");
    return dimension;
  }

  private static IHopMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("Vault");
    metadataProvider.getSerializer(DatabaseMeta.class).save(databaseMeta);
    return metadataProvider;
  }
}
