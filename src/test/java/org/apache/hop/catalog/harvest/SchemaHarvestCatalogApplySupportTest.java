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
package org.apache.hop.catalog.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.FieldRole;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedForeignKey;
import org.apache.hop.catalog.model.CatalogSourceField;
import org.junit.jupiter.api.Test;

class SchemaHarvestCatalogApplySupportTest {

  @Test
  void applyForeignKeysStampsChildColumns() {
    List<CatalogSourceField> fields = new ArrayList<>();
    fields.add(field("order_id"));
    fields.add(field("customer_id"));
    fields.add(field("amount"));

    HarvestedForeignKey fk =
        HarvestedForeignKey.builder()
            .role(FieldRole.DISCOVERED)
            .constraintName("fk_order_customer")
            .childTable("order_header")
            .childColumns("customer_id")
            .parentTable("customer_hub")
            .parentColumns("customer_id")
            .build();

    int applied =
        SchemaHarvestCatalogApplySupport.applyForeignKeysToCatalogFields(fields, List.of(fk));
    assertEquals(1, applied);
    CatalogSourceField customerId = fields.get(1);
    assertEquals("fk_order_customer", customerId.getFkConstraintName());
    assertEquals(1, customerId.getFkPosition());
    assertEquals("customer_hub", customerId.getFkReferencedTable());
    assertEquals("customer_id", customerId.getFkReferencedColumn());
    assertEquals(0, fields.get(0).getFkPosition());
  }

  @Test
  void applyForeignKeysClearsPreviousContract() {
    CatalogSourceField stale = field("x");
    stale.setFkConstraintName("old");
    stale.setFkPosition(1);
    stale.setFkReferencedTable("old_parent");
    stale.setFkReferencedColumn("id");

    int applied =
        SchemaHarvestCatalogApplySupport.applyForeignKeysToCatalogFields(List.of(stale), List.of());
    assertEquals(0, applied);
    assertEquals(0, stale.getFkPosition());
    assertTrue(stale.getFkConstraintName() == null || stale.getFkConstraintName().isEmpty());
  }

  @Test
  void compositeFkUsesPositions() {
    List<CatalogSourceField> fields = new ArrayList<>();
    fields.add(field("order_id"));
    fields.add(field("line_number"));

    HarvestedForeignKey fk =
        HarvestedForeignKey.builder()
            .role(FieldRole.DISCOVERED)
            .constraintName("fk_line_order")
            .childColumns("order_id,line_number")
            .parentTable("order_header")
            .parentColumns("order_id,line_number")
            .build();

    SchemaHarvestCatalogApplySupport.applyForeignKeysToCatalogFields(fields, List.of(fk));
    assertEquals(1, fields.get(0).getFkPosition());
    assertEquals(2, fields.get(1).getFkPosition());
    assertEquals("order_id", fields.get(0).getFkReferencedColumn());
    assertEquals("line_number", fields.get(1).getFkReferencedColumn());
  }

  private static CatalogSourceField field(String name) {
    CatalogSourceField f = new CatalogSourceField();
    f.setName(name);
    return f;
  }
}
