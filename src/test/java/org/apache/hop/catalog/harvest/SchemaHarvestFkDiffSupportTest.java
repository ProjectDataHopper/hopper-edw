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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.FieldRole;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestChange;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedForeignKey;
import org.apache.hop.catalog.model.CatalogSourceField;
import org.apache.hop.datavault.metadata.database.DiscoveredForeignKey;
import org.junit.jupiter.api.Test;

class SchemaHarvestFkDiffSupportTest {

  @Test
  void discoveredOnlyWithoutCatalogContractIsInfo() {
    DiscoveredForeignKey live = new DiscoveredForeignKey();
    live.setConstraintName("fk_order_customer");
    live.setChildTable("order_header");
    live.setParentTable("customer_hub");
    live.addColumnPair("customer_id", "customer_id");

    List<HarvestedForeignKey> discovered =
        SchemaHarvestFkDiffSupport.fromDiscovered(List.of(live), FieldRole.DISCOVERED);
    List<HarvestChange> changes = SchemaHarvestFkDiffSupport.diff(List.of(), discovered);

    assertEquals(1, changes.size());
    assertEquals(HarvestChange.KIND_FOREIGN_KEY_ADDED, changes.get(0).getChangeKind());
    assertEquals("INFO", changes.get(0).getSeverity());
  }

  @Test
  void removedCatalogFkIsBlocking() {
    HarvestedForeignKey expected =
        HarvestedForeignKey.builder()
            .role(FieldRole.EXPECTED)
            .constraintName("fk_order_customer")
            .childTable("order_header")
            .childColumns("customer_id")
            .parentTable("customer_hub")
            .parentColumns("customer_id")
            .build();

    List<HarvestChange> changes = SchemaHarvestFkDiffSupport.diff(List.of(expected), List.of());
    assertEquals(1, changes.size());
    assertEquals(HarvestChange.KIND_FOREIGN_KEY_REMOVED, changes.get(0).getChangeKind());
    assertEquals("BLOCKING", changes.get(0).getSeverity());
  }

  @Test
  void parentTableChangeIsBlocking() {
    HarvestedForeignKey expected =
        HarvestedForeignKey.builder()
            .role(FieldRole.EXPECTED)
            .constraintName("fk1")
            .childTable("child")
            .childColumns("a")
            .parentTable("parent_a")
            .parentColumns("id")
            .build();
    HarvestedForeignKey discovered =
        HarvestedForeignKey.builder()
            .role(FieldRole.DISCOVERED)
            .constraintName("fk1")
            .childTable("child")
            .childColumns("a")
            .parentTable("parent_b")
            .parentColumns("id")
            .build();

    List<HarvestChange> changes =
        SchemaHarvestFkDiffSupport.diff(List.of(expected), List.of(discovered));
    assertEquals(1, changes.size());
    assertEquals(HarvestChange.KIND_FOREIGN_KEY_CHANGED, changes.get(0).getChangeKind());
    assertEquals("BLOCKING", changes.get(0).getSeverity());
  }

  @Test
  void fromCatalogFieldsGroupsCompositeKey() {
    CatalogSourceField c1 = new CatalogSourceField();
    c1.setName("order_id");
    c1.setFkConstraintName("fk_line_order");
    c1.setFkPosition(1);
    c1.setFkReferencedTable("order_header");
    c1.setFkReferencedColumn("order_id");
    CatalogSourceField c2 = new CatalogSourceField();
    c2.setName("line_number");
    c2.setFkConstraintName("fk_line_order");
    c2.setFkPosition(2);
    c2.setFkReferencedTable("order_header");
    c2.setFkReferencedColumn("line_number");

    List<HarvestedForeignKey> fks =
        SchemaHarvestFkDiffSupport.fromCatalogFields(List.of(c1, c2), "public", "order_line");
    assertEquals(1, fks.size());
    assertEquals("order_id,line_number", fks.get(0).getChildColumns());
    assertEquals("order_id,line_number", fks.get(0).getParentColumns());
    assertEquals("order_header", fks.get(0).getParentTable());
  }

  @Test
  void constraintRenameWithSameColumnsIsWarning() {
    HarvestedForeignKey expected =
        HarvestedForeignKey.builder()
            .role(FieldRole.EXPECTED)
            .constraintName("old_name")
            .childTable("child")
            .childColumns("a")
            .parentTable("parent")
            .parentColumns("id")
            .build();
    HarvestedForeignKey discovered =
        HarvestedForeignKey.builder()
            .role(FieldRole.DISCOVERED)
            .constraintName("new_name")
            .childTable("child")
            .childColumns("a")
            .parentTable("parent")
            .parentColumns("id")
            .build();

    List<HarvestChange> changes =
        SchemaHarvestFkDiffSupport.diff(List.of(expected), List.of(discovered));
    assertEquals(1, changes.size());
    assertEquals(HarvestChange.KIND_FOREIGN_KEY_CHANGED, changes.get(0).getChangeKind());
    assertEquals("WARNING", changes.get(0).getSeverity());
    assertTrue(changes.get(0).getActualDetail().contains("constraint name"));
  }
}
