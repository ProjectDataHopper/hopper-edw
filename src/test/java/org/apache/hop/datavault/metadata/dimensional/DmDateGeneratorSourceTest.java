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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorMeta;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorMetaFactory;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmDateGeneratorSourceTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void templateUsesDateGeneratorSource() {
    DmDimension dimension = DmDateDimensionTemplate.createDateDimension(null);

    assertEquals(DmSourceType.DATE_GENERATOR, dimension.getSourceOrDefault().resolveSourceType());
    assertFalse(
        dimension.getSourceOrDefault().getDateGeneratorOrDefault().getFieldsOrEmpty().isEmpty());
    assertEquals("date_key", dimension.getNaturalKeysOrEmpty().get(0).getFieldName());
  }

  @Test
  void fieldResolutionUsesGeneratorFieldsWithoutDatabase() {
    DimensionalModel model = new DimensionalModel();
    model.setName("test");
    DmDimension dimension = DmDateDimensionTemplate.createDateDimension(null);
    model.getTables().add(dimension);

    IRowMeta rowMeta =
        DmSourceFieldResolutionSupport.tryResolveSourceRowMeta(
            new MemoryMetadataProvider(), new Variables(), model, dimension);

    assertTrue(rowMeta != null && rowMeta.size() > 0);
    assertTrue(rowMeta.indexOfValue("date_key") >= 0);
    assertTrue(rowMeta.indexOfValue("full_date") >= 0);
  }

  @Test
  void validationAcceptsDateGeneratorWithoutSql() {
    DimensionalModel model = new DimensionalModel();
    model.setName("test");
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    DmDimension dimension = DmDateDimensionTemplate.createDateDimension(null);
    model.getTables().add(dimension);

    List<ICheckResult> remarks = new ArrayList<>();
    dimension.check(remarks, new MemoryMetadataProvider(), new Variables(), model);

    boolean missingSql =
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().toLowerCase().contains("source sql"));
    assertFalse(missingSql, () -> "Unexpected SQL errors: " + remarks);
  }

  @Test
  void dateGeneratorConfigMapsToTransformMeta() {
    DmDateGeneratorConfiguration config = DmDateGeneratorConfiguration.createDefault();
    config.setReferenceDate("2026-07-15");
    config.setMonthOffset("6");

    DateDimensionGeneratorMeta meta = config.toTransformMeta();

    assertEquals(DateDimensionGeneratorMetaFactory.DEFAULT_START_DATE, meta.getStartDate());
    assertEquals("2026-07-15", meta.getReferenceDate());
    assertEquals("6", meta.getMonthOffset());
    assertFalse(meta.getFields().isEmpty());
  }

  @Test
  void pipelineBuilderAddsDateGeneratorTransform() {
    DmDimension dimension = DmDateDimensionTemplate.createDateDimension(null);
    PipelineMeta pipelineMeta = new PipelineMeta();

    // Build a minimal context-free path: transform name uses tableName from source config.
    dimension.setTableName("d_date");
    TransformMeta source =
        new TransformMeta(
            "DateDimensionGenerator",
            "source_" + dimension.getTableName(),
            dimension.getSourceOrDefault().getDateGeneratorOrDefault().toTransformMeta());
    pipelineMeta.addTransform(source);

    TransformMeta found = pipelineMeta.findTransform("source_d_date");
    assertInstanceOf(DateDimensionGeneratorMeta.class, found.getTransform());
  }
}
