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
package org.hopper.edw.datavault.metadata.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DvDatabaseSourceImportSupportTest {

  @Test
  void shouldPreselectAllTablesForSmallSchemas() {
    assertTrue(DvDatabaseSourceImportSupport.shouldPreselectAllTables(1));
    assertTrue(
        DvDatabaseSourceImportSupport.shouldPreselectAllTables(
            DvDatabaseSourceImportSupport.LARGE_SCHEMA_TABLE_THRESHOLD));
    assertFalse(
        DvDatabaseSourceImportSupport.shouldPreselectAllTables(
            DvDatabaseSourceImportSupport.LARGE_SCHEMA_TABLE_THRESHOLD + 1));
    assertFalse(DvDatabaseSourceImportSupport.shouldPreselectAllTables(0));
  }

  @Test
  void defaultPreselectedTableIndexesSelectsAllForSmallSchemas() {
    List<Integer> indexes = DvDatabaseSourceImportSupport.defaultPreselectedTableIndexes(3);
    assertEquals(List.of(0, 1, 2), indexes);
  }

  @Test
  void defaultPreselectedTableIndexesIsEmptyForLargeSchemas() {
    assertTrue(
        DvDatabaseSourceImportSupport.defaultPreselectedTableIndexes(
                DvDatabaseSourceImportSupport.LARGE_SCHEMA_TABLE_THRESHOLD + 5)
            .isEmpty());
  }

  @Test
  void sortedStrippedTableNamesSortsCaseInsensitiveAndStripsQuotes() {
    String[] sorted =
        DvDatabaseSourceImportSupport.sortedStrippedTableNames(
            new String[] {"\"Zebra\"", "alpha", "`Beta`"});

    assertArrayEquals(new String[] {"alpha", "Beta", "Zebra"}, sorted);
  }

  @Test
  void tableNamesForSelectionIndexesPreservesDialogOrder() {
    String[] choices = {"alpha", "beta", "gamma"};
    Set<String> picked =
        DvDatabaseSourceImportSupport.tableNamesForSelectionIndexes(choices, new int[] {2, 0});

    assertEquals(List.of("gamma", "alpha"), List.copyOf(picked));
  }
}
