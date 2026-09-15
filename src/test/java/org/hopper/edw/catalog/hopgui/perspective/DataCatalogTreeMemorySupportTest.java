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
package org.hopper.edw.catalog.hopgui.perspective;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataCatalogTreeMemorySupportTest {

  private static final String TREE_KEY = "test-data-catalog-tree";
  private Set<String> seededPaths;
  private MapExpandMemory memory;

  @BeforeEach
  void setUp() {
    memory = new MapExpandMemory();
    seededPaths = new HashSet<>();
  }

  @Test
  void seedsDefaultExpandedStateOncePerPath() {
    String[] catalogPath = new String[] {"local-catalog"};
    String[] namespacePath = new String[] {"local-catalog", "hop/project/sources"};

    assertTrue(
        DataCatalogTreeMemorySupport.resolveExpanded(
            TREE_KEY, catalogPath, seededPaths, true, memory));
    assertTrue(
        DataCatalogTreeMemorySupport.resolveExpanded(
            TREE_KEY, namespacePath, seededPaths, true, memory));

    memory.storeExpanded(TREE_KEY, namespacePath, false);
    assertFalse(
        DataCatalogTreeMemorySupport.resolveExpanded(
            TREE_KEY, namespacePath, seededPaths, true, memory));
    assertTrue(
        DataCatalogTreeMemorySupport.resolveExpanded(
            TREE_KEY, catalogPath, seededPaths, true, memory));
  }

  @Test
  void remembersCollapsedCatalogAcrossResolveCalls() {
    String[] catalogPath = new String[] {"vault-catalog"};

    assertTrue(
        DataCatalogTreeMemorySupport.resolveExpanded(
            TREE_KEY, catalogPath, seededPaths, true, memory));
    memory.storeExpanded(TREE_KEY, catalogPath, false);

    assertFalse(
        DataCatalogTreeMemorySupport.resolveExpanded(
            TREE_KEY, catalogPath, seededPaths, true, memory));
  }

  /** Same put/remove semantics as {@code TreeMemory}, without initializing SWT. */
  private static final class MapExpandMemory implements DataCatalogTreeMemorySupport.ExpandMemory {
    private final Map<String, Boolean> expanded = new HashMap<>();

    @Override
    public boolean isExpanded(String treeKey, String[] path) {
      return Boolean.TRUE.equals(expanded.get(key(treeKey, path)));
    }

    @Override
    public void storeExpanded(String treeKey, String[] path, boolean expandedState) {
      String key = key(treeKey, path);
      if (expandedState) {
        expanded.put(key, true);
      } else {
        expanded.remove(key);
      }
    }

    private static String key(String treeKey, String[] path) {
      return treeKey + "\0" + String.join("\0", path);
    }
  }
}
