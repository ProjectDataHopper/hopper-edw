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
package org.apache.hop.catalog.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.DiscoveryStatus;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.FieldRole;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestSubjectResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedField;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedForeignKey;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SchemaHarvestSourceModelGeneratorTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void generateCreatesTablesAndRelationshipsWithStubParent() throws Exception {
    HarvestResult harvest =
        HarvestResult.builder()
            .harvestRunId("run-1")
            .resourceGroupName("retail-sources")
            .subjects(
                List.of(
                    HarvestSubjectResult.builder()
                        .subjectKey("hop/retail-example/sources/E2E-order-header")
                        .databaseMetaName("CRM")
                        .schemaName("public")
                        .tableName("order_header")
                        .discoveryStatus(DiscoveryStatus.OK)
                        .fields(
                            List.of(
                                HarvestedField.builder()
                                    .role(FieldRole.DISCOVERED)
                                    .fieldName("order_id")
                                    .hopType("Integer")
                                    .primaryKeyPosition(1)
                                    .build(),
                                HarvestedField.builder()
                                    .role(FieldRole.DISCOVERED)
                                    .fieldName("customer_id")
                                    .hopType("Integer")
                                    .build()))
                        .foreignKeys(
                            List.of(
                                HarvestedForeignKey.builder()
                                    .role(FieldRole.DISCOVERED)
                                    .constraintName("fk_order_customer")
                                    .childSchema("public")
                                    .childTable("order_header")
                                    .childColumns("customer_id")
                                    .parentSchema("public")
                                    .parentTable("customer_hub")
                                    .parentColumns("customer_id")
                                    .build()))
                        .build()))
            .build();

    var result =
        SchemaHarvestSourceModelGenerator.generate(
            harvest, null, SchemaHarvestSourceModelGenerator.GenerateOptions.defaults());

    SourceModel model = result.model();
    assertNotNull(model);
    assertTrue(result.tablesAdded() >= 2); // order_header + stub customer_hub
    assertEquals(1, result.relationshipsAdded());
    assertNotNull(model.findTable("order_header"));
    assertNotNull(model.findTable("customer_hub"));
    SourceRelationship rel = model.getRelationships().get(0);
    assertEquals("order_header", rel.getChildTableName());
    assertEquals("customer_hub", rel.getParentTableName());
    assertEquals(List.of("customer_id"), rel.getChildColumns());
  }

  @Test
  void mergeDoesNotDuplicateRelationships() throws Exception {
    HarvestResult harvest =
        HarvestResult.builder()
            .harvestRunId("run-2")
            .subjects(
                List.of(
                    HarvestSubjectResult.builder()
                        .subjectKey("hop/ns/orders")
                        .databaseMetaName("CRM")
                        .tableName("orders")
                        .discoveryStatus(DiscoveryStatus.OK)
                        .fields(
                            List.of(
                                HarvestedField.builder()
                                    .role(FieldRole.DISCOVERED)
                                    .fieldName("id")
                                    .hopType("Integer")
                                    .build(),
                                HarvestedField.builder()
                                    .role(FieldRole.DISCOVERED)
                                    .fieldName("customer_id")
                                    .hopType("Integer")
                                    .build()))
                        .foreignKeys(
                            List.of(
                                HarvestedForeignKey.builder()
                                    .role(FieldRole.DISCOVERED)
                                    .constraintName("fk1")
                                    .childTable("orders")
                                    .childColumns("customer_id")
                                    .parentTable("customers")
                                    .parentColumns("id")
                                    .build()))
                        .build(),
                    HarvestSubjectResult.builder()
                        .subjectKey("hop/ns/customers")
                        .databaseMetaName("CRM")
                        .tableName("customers")
                        .discoveryStatus(DiscoveryStatus.OK)
                        .fields(
                            List.of(
                                HarvestedField.builder()
                                    .role(FieldRole.DISCOVERED)
                                    .fieldName("id")
                                    .hopType("Integer")
                                    .primaryKeyPosition(1)
                                    .build()))
                        .build()))
            .build();

    var first =
        SchemaHarvestSourceModelGenerator.generate(
            harvest, null, SchemaHarvestSourceModelGenerator.GenerateOptions.defaults());
    var second =
        SchemaHarvestSourceModelGenerator.generate(
            harvest, first.model(), SchemaHarvestSourceModelGenerator.GenerateOptions.defaults());
    assertEquals(0, second.relationshipsAdded());
    assertEquals(1, second.model().getRelationships().size());
  }
}
