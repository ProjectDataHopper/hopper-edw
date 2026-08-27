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
package org.hopper.edw.datavault.metadata.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.metainject.MetaInjectMeta;
import org.apache.hop.pipeline.transforms.metainject.MetaInjectOutputField;
import org.hopper.edw.datavault.metadata.SourceField;
import org.junit.jupiter.api.Test;

class DvPipelineSourceSupportTest {

  @Test
  void declaredFieldsDriveMetaInjectOutputFields() throws Exception {
    DvPipelineSource source = new DvPipelineSource();
    source.setPipelineFilename("${PROJECT_HOME}/pipelines/x.hpl");
    source.setOutputTransformName("output");
    SourceField id = new SourceField("id");
    id.setHopType(IValueMeta.TYPE_INTEGER);
    id.setLength("9");
    SourceField name = new SourceField("name");
    name.setHopType(IValueMeta.TYPE_STRING);
    name.setLength("100");
    source.setFields(List.of(id, name));

    MetaInjectMeta metaInjectMeta = DvPipelineSourceSupport.buildMetaInjectMeta(source, null, null);
    assertEquals("${PROJECT_HOME}/pipelines/x.hpl", metaInjectMeta.getTemplateFileName());
    assertEquals("output", metaInjectMeta.getSourceTransformName());
    List<MetaInjectOutputField> fields = metaInjectMeta.getSourceOutputFields();
    assertFalse(fields.isEmpty());
    assertEquals(2, fields.size());
    assertEquals("id", fields.get(0).getName());
    assertEquals(IValueMeta.TYPE_INTEGER, fields.get(0).getType());
  }

  @Test
  void activatePipelineParameterDefaultsUsesMetaDefaultsWithoutClobberingExisting()
      throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.addParameterDefinition("RETAIL_CSV_WAVE", "demo", "ASN / CSV wave label");
    pipelineMeta.addParameterDefinition("OTHER", "x", "");

    Variables variables = new Variables();
    variables.setVariable("OTHER", "already-set");
    variables.setVariable("PROJECT_HOME", "/tmp/retail-example");

    DvPipelineSourceSupport.activatePipelineParameterDefaults(pipelineMeta, variables);

    assertEquals("demo", variables.getVariable("RETAIL_CSV_WAVE"));
    assertEquals("already-set", variables.getVariable("OTHER"));
    assertEquals(
        "/tmp/retail-example/files/asn_demo.xml",
        variables.resolve("${PROJECT_HOME}/files/asn_${RETAIL_CSV_WAVE}.xml"));
  }

  @Test
  void activatePipelineParameterDefaultsNoopsOnNulls() {
    Variables variables = new Variables();
    DvPipelineSourceSupport.activatePipelineParameterDefaults(null, variables);
    DvPipelineSourceSupport.activatePipelineParameterDefaults(new PipelineMeta(), null);
    assertNull(variables.getVariable("RETAIL_CSV_WAVE"));
  }
}
