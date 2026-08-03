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
package org.apache.hop.catalog.hopgui.perspective;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.apache.hop.ui.core.widget.TreeMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataCatalogTreeMemorySupportTest {

  private static final String TREE_KEY = "test-data-catalog-tree";
  private Set<String> seededPaths;

  @BeforeEach
  void setUp() {
    TreeMemory.getInstance().clear();
    seededPaths = new HashSet<>();
  }

  @Test
  void seedsDefaultExpandedStateOncePerPath() {
    String[] catalogPath = new String[] {"local-catalog"};
    String[] namespacePath = new String[] {"local-catalog", "hop/project/sources"};

    assertTrue(
        DataCatalogTreeMemorySupport.resolveExpanded(TREE_KEY, catalogPath, seededPaths, true));
    assertTrue(
        DataCatalogTreeMemorySupport.resolveExpanded(TREE_KEY, namespacePath, seededPaths, true));

    TreeMemory.getInstance().storeExpanded(TREE_KEY, namespacePath, false);
    assertFalse(
        DataCatalogTreeMemorySupport.resolveExpanded(TREE_KEY, namespacePath, seededPaths, true));
    assertTrue(
        DataCatalogTreeMemorySupport.resolveExpanded(TREE_KEY, catalogPath, seededPaths, true));
  }

  @Test
  void remembersCollapsedCatalogAcrossResolveCalls() {
    String[] catalogPath = new String[] {"vault-catalog"};

    assertTrue(
        DataCatalogTreeMemorySupport.resolveExpanded(TREE_KEY, catalogPath, seededPaths, true));
    TreeMemory.getInstance().storeExpanded(TREE_KEY, catalogPath, false);

    assertFalse(
        DataCatalogTreeMemorySupport.resolveExpanded(TREE_KEY, catalogPath, seededPaths, true));
  }
}
