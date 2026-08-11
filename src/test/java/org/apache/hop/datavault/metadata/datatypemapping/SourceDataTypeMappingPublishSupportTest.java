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
package org.apache.hop.datavault.metadata.datatypemapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.metadata.sourcemodel.publish.SourceTableCatalogPublisher;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.Test;

class SourceDataTypeMappingPublishSupportTest {

  @Test
  void publishAppliesStringDefaultLengthAndConversion() throws Exception {
    DataTypeMappingRule rule = new DataTypeMappingRule();
    rule.setId("string-default");
    rule.setMatchHopType("String");
    rule.setMatchLengthAbsent(true);
    rule.setTargetHopType(IValueMeta.TYPE_STRING);
    rule.setTargetLength("2000");

    DataTypeMappingRule dateRule = new DataTypeMappingRule();
    dateRule.setId("created-at");
    dateRule.setMatchFieldNamePattern("created_at");
    dateRule.setMatchHopType("String");
    dateRule.setTargetHopType(IValueMeta.TYPE_TIMESTAMP);
    dateRule.getConversion().setConversionMask("yyyy-MM-dd HH:mm:ss");

    DataTypeMappingMeta profile = new DataTypeMappingMeta("premodel-defaults");
    profile.getRules().add(rule);
    profile.getRules().add(dateRule);

    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    metadata.getSerializer(DataTypeMappingMeta.class).save(profile);

    SourceTable table = new SourceTable("customer");
    table.getDataTypeMappingNames().add("premodel-defaults");
    SourceColumn name = new SourceColumn("customer_name");
    name.setHopType(IValueMeta.TYPE_STRING);
    table.getColumns().add(name);
    SourceColumn created = new SourceColumn("created_at");
    created.setHopType(IValueMeta.TYPE_STRING);
    created.setLength("30");
    table.getColumns().add(created);

    List<SourceField> fields =
        SourceTableCatalogPublisher.buildFieldsFromTable(table, metadata);

    assertEquals(2, fields.size());
    SourceField nameField = fields.get(0);
    assertEquals("customer_name", nameField.getName());
    assertEquals("2000", nameField.getLength());

    SourceField createdField = fields.get(1);
    assertEquals(IValueMeta.TYPE_TIMESTAMP, createdField.getHopType());
    assertNotNull(createdField.getInputOptions());
    assertNotNull(createdField.getInputOptions().getConversion());
    assertEquals(
        "yyyy-MM-dd HH:mm:ss",
        createdField.getInputOptions().getConversion().getConversionMask());
  }

  @Test
  void publishRenameSetsSourceStreamName() throws Exception {
    SourceTable table = new SourceTable("customer");
    SourceColumn id = new SourceColumn("CUST_ID");
    id.setHopType(IValueMeta.TYPE_INTEGER);
    id.setLength("9");
    table.getColumns().add(id);

    SourceFieldTypeMapping override = new SourceFieldTypeMapping("CUST_ID");
    override.setTargetFieldName("customer_id");
    table.getFieldTypeMappings().add(override);

    List<SourceField> fields =
        SourceDataTypeMappingPublishSupport.toEffectiveSourceFields(
            table, SourceDataTypeMappingSupport.physicalFields(table), null);

    assertEquals(1, fields.size());
    assertEquals("customer_id", fields.get(0).getName());
    assertEquals("CUST_ID", fields.get(0).getSourceStreamName());
    assertTrue(fields.get(0).isRenamedFromStream());
  }
}
