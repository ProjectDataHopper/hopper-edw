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
package org.hopper.edw.catalog.transform.tablemetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.IValueMeta;
import org.hopper.edw.datavault.metadata.database.DiscoveredForeignKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DatabaseTableMetadataSupportTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void hopTypeNameUsesValueMetaFactory() {
    assertEquals("Integer", DatabaseTableMetadataSupport.hopTypeName(IValueMeta.TYPE_INTEGER));
    assertEquals("String", DatabaseTableMetadataSupport.hopTypeName(IValueMeta.TYPE_STRING));
  }

  @Test
  void indexesForeignKeysByChildColumnFirstMatchWins() {
    DiscoveredForeignKey fk1 = new DiscoveredForeignKey();
    fk1.setConstraintName("fk_order_customer");
    fk1.setChildSchema("public");
    fk1.setChildTable("orders");
    fk1.setParentSchema("public");
    fk1.setParentTable("customers");
    fk1.addColumnPair("customer_id", "id");

    DiscoveredForeignKey fk2 = new DiscoveredForeignKey();
    fk2.setConstraintName("fk_order_customer_alt");
    fk2.setChildTable("orders");
    fk2.setParentTable("customers_alt");
    fk2.addColumnPair("customer_id", "alt_id");

    Map<String, DatabaseTableMetadataSupport.FkAttachment> byChild =
        DatabaseTableMetadataSupport.indexForeignKeysByChildColumn(List.of(fk1, fk2));

    assertTrue(byChild.containsKey("customer_id"));
    DatabaseTableMetadataSupport.FkAttachment attached = byChild.get("customer_id");
    assertEquals("fk_order_customer", attached.constraintName());
    assertEquals(1L, attached.position());
    assertEquals("public", attached.referencedSchema());
    assertEquals("customers", attached.referencedTable());
    assertEquals("id", attached.referencedColumn());
  }

  @Test
  void compositeForeignKeyUsesSequencePositions() {
    DiscoveredForeignKey fk = new DiscoveredForeignKey();
    fk.setConstraintName("fk_line");
    fk.setChildTable("order_lines");
    fk.setParentTable("orders");
    fk.addColumnPair("order_id", "id");
    fk.addColumnPair("order_version", "version");

    Map<String, DatabaseTableMetadataSupport.FkAttachment> byChild =
        DatabaseTableMetadataSupport.indexForeignKeysByChildColumn(List.of(fk));

    assertEquals(1L, byChild.get("order_id").position());
    assertEquals("id", byChild.get("order_id").referencedColumn());
    assertEquals(2L, byChild.get("order_version").position());
    assertEquals("version", byChild.get("order_version").referencedColumn());
    assertNull(byChild.get("missing"));
  }
}
