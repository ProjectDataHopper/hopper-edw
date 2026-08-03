/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;
import org.apache.hop.pipeline.transforms.numberrange.NumberRangeMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmRangeDimensionPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void factPipelineInsertsNumberRangeBeforeDimensionLookups() throws Exception {
    DimensionalModel model = buildModel();
    DmFact fact = (DmFact) model.findTable("fact_sales");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    List<PipelineMeta> pipelines =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date());

    assertEquals(1, pipelines.size());
    PipelineMeta pipelineMeta = pipelines.get(0);
    assertEquals(4, pipelineMeta.getTransforms().size());

    TransformMeta numberRange =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof NumberRangeMeta)
            .findFirst()
            .orElseThrow();
    NumberRangeMeta numberRangeMeta = (NumberRangeMeta) numberRange.getTransform();
    assertEquals("amount", numberRangeMeta.getInputField());
    assertEquals("amount_band", numberRangeMeta.getOutputField());
    assertEquals("unknown", numberRangeMeta.getFallBackValue());
    assertEquals(3, numberRangeMeta.getRules().size());
    assertEquals("small", numberRangeMeta.getRules().get(0).getValue());
    assertEquals("0", numberRangeMeta.getRules().get(0).getLowerBound());
    assertEquals("101", numberRangeMeta.getRules().get(0).getUpperBound());

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
    assertTrue(pipelineMeta.findPreviousTransforms(numberRange).contains(tableInput));
    assertTrue(pipelineMeta.findPreviousTransforms(firstLookup).contains(numberRange));
  }

  private static DimensionalModel buildModel() {
    DimensionalModel model = new DimensionalModel();
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    config.setTargetDatabase("Vault");

    DmDimension customer = new DmDimension();
    customer.setName("dim_customer");
    customer.setTableName("d_customer");
    customer.setScdType(DmDimensionScdType.TYPE1);
    customer.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    customer.getSourceOrDefault().setSourceSql("SELECT customer_id FROM stg_customer");
    model.getTables().add(customer);

    DmRangeDimension amountBand = new DmRangeDimension();
    amountBand.setName("dim_amount_band");
    amountBand.setTableName("d_amount_band");
    amountBand.setFallBackLabel("unknown");
    amountBand.getBands().add(new DmRangeBand("0", "101", "small"));
    amountBand.getBands().add(new DmRangeBand("101", "501", "medium"));
    amountBand.getBands().add(new DmRangeBand("501", "", "large"));
    model.getTables().add(amountBand);

    DmFact fact = new DmFact();
    fact.setName("fact_sales");
    fact.setTableName("f_sales");
    fact.getDimensionRoles().add(new DmFactDimensionRole("dim_customer", "customer_key"));
    fact.getMeasures().add(new DmFactMeasure("amount", true));
    fact.getRangeDimensionRoles()
        .add(new DmFactRangeDimensionRole("dim_amount_band", "amount", "amount_band"));
    fact.getSourceOrDefault().setSourceSql("SELECT customer_id, amount FROM stg_sales");
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
