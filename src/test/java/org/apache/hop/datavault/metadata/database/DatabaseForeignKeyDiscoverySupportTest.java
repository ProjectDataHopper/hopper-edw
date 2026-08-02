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

package org.apache.hop.datavault.metadata.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseForeignKeyDiscoverySupportTest {

  @Test
  void discoveredForeignKeyValidatesColumnPairs() {
    DiscoveredForeignKey fk = new DiscoveredForeignKey();
    assertFalse(fk.isValid());

    fk.setChildTable("product");
    fk.setParentTable("product_type");
    fk.addColumnPair("type_id", "type_id");
    assertTrue(fk.isValid());
    assertEquals(List.of("type_id"), fk.getChildColumns());
    assertEquals(List.of("type_id"), fk.getParentColumns());
  }

  @Test
  void discoveredForeignKeyIgnoresBlankColumnPairs() {
    DiscoveredForeignKey fk = new DiscoveredForeignKey();
    fk.setChildTable("child");
    fk.setParentTable("parent");
    fk.addColumnPair("", "a");
    fk.addColumnPair("b", null);
    assertFalse(fk.isValid());
    assertTrue(fk.getChildColumns().isEmpty());
  }

  @Test
  void discoveredForeignKeyDedupeKeyIncludesTablesAndColumns() {
    DiscoveredForeignKey a = new DiscoveredForeignKey();
    a.setChildSchema("public");
    a.setChildTable("product");
    a.setParentSchema("public");
    a.setParentTable("product_type");
    a.addColumnPair("type_id", "type_id");

    DiscoveredForeignKey b = new DiscoveredForeignKey();
    b.setChildSchema("public");
    b.setChildTable("product");
    b.setParentSchema("public");
    b.setParentTable("product_type");
    b.addColumnPair("type_id", "type_id");

    assertEquals(a.dedupeKey(), b.dedupeKey());

    DiscoveredForeignKey c = new DiscoveredForeignKey();
    c.setChildTable("product");
    c.setParentTable("product_type");
    c.addColumnPair("other_id", "type_id");
    assertFalse(a.dedupeKey().equals(c.dedupeKey()));
  }

  @Test
  void discoverImportedForeignKeysReturnsEmptyForNullInputs() throws Exception {
    assertTrue(
        DatabaseForeignKeyDiscoverySupport.discoverImportedForeignKeys(null, null, null, null)
            .isEmpty());
    assertTrue(
        DatabaseForeignKeyDiscoverySupport.discoverImportedForeignKeysForTables(
                null, null, null, List.of("a"))
            .isEmpty());
  }
}
