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
package org.hopper.edw.datavault.metadata.sourcemodel.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingMeta;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingRule;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipeline;
import org.junit.jupiter.api.Test;

class SourcePipelineCatalogPublisherTest {

  @Test
  void projectionWithoutMetadataLeavesStringLengthAbsent() throws Exception {
    SourcePipeline pipeline = pipelineWithUnboundString("asn_id");

    List<SourceField> fields =
        SourcePipelineCatalogPublisher.buildFieldsFromProjection(pipeline, null);

    assertEquals(1, fields.size());
    assertTrue(Utils.isEmpty(fields.get(0).getLength()), fields.get(0).getLength());
  }

  @Test
  void projectionAppliesAttachedStringDefaultLength() throws Exception {
    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    metadata.getSerializer(DataTypeMappingMeta.class).save(stringDefaultLengthProfile());

    SourcePipeline pipeline = pipelineWithUnboundString("asn_id");
    pipeline.getDataTypeMappingNames().add("premodel-defaults");

    List<SourceField> fields =
        SourcePipelineCatalogPublisher.buildFieldsFromProjection(pipeline, metadata);

    assertEquals(1, fields.size());
    assertEquals("asn_id", fields.get(0).getName());
    assertEquals(IValueMeta.TYPE_STRING, fields.get(0).getHopType());
    assertEquals("2000", fields.get(0).getLength());
  }

  private static SourcePipeline pipelineWithUnboundString(String fieldName) {
    SourcePipeline pipeline = new SourcePipeline("asn-package-lines");
    SourceColumn column = new SourceColumn(fieldName);
    column.setHopType(IValueMeta.TYPE_STRING);
    pipeline.getFields().add(column);
    return pipeline;
  }

  private static DataTypeMappingMeta stringDefaultLengthProfile() {
    DataTypeMappingRule rule = new DataTypeMappingRule();
    rule.setId("string-default-length");
    rule.setMatchHopType("String");
    rule.setMatchLengthAbsent(true);
    rule.setTargetHopType(IValueMeta.TYPE_STRING);
    rule.setTargetLength("2000");
    DataTypeMappingMeta profile = new DataTypeMappingMeta("premodel-defaults");
    profile.getRules().add(rule);
    return profile;
  }
}
