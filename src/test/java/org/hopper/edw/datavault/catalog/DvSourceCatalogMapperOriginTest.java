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
package org.hopper.edw.datavault.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.pipeline.DvPipelineSource;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvSourceCatalogMapperOriginTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void pipelineSourceOriginUsesSourceModelProvenance() throws Exception {
    SourceField field = new SourceField("order_id");
    field.setHopType(IValueMeta.TYPE_STRING);

    DvPipelineSource pipeline = new DvPipelineSource();
    pipeline.setPipelineFilename("${PROJECT_HOME}/pipelines/parse-asn-xml.hpl");
    pipeline.setOutputTransformName("ASN lines");
    pipeline.setSourceModelFilename("${PROJECT_HOME}/models/source-tables-crm.hsm");
    pipeline.setSourcePipelineName("asn-package-lines");
    pipeline.setFields(List.of(field));

    DataVaultSource source = new DataVaultSource("asn-package-lines");
    source.setSource(pipeline);

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/tmp/retail-example");

    RecordDefinition definition =
        DvSourceCatalogMapper.toRecordDefinition(
            source,
            "hop/retail-example/sources",
            null,
            variables,
            new MemoryMetadataProvider(),
            new Date(),
            null);

    RecordOrigin origin = definition.getOrigin();
    assertNotNull(origin);
    assertEquals(DvSourceCatalogMapper.ORIGIN_MODEL_TYPE_SOURCE_MODEL, origin.getModelType());
    assertEquals("asn-package-lines", origin.getModelElementName());
    assertNotNull(origin.getModelFilename());
    assertTrue(
        origin.getModelFilename().contains("source-tables-crm.hsm"),
        "expected portable .hsm path, got: " + origin.getModelFilename());
  }
}
