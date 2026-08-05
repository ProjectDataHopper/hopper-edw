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
package org.apache.hop.catalog.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceDefinitionGroupModelListSupportTest {

  @Test
  void matchesFilterIsCaseInsensitiveSubstring() {
    assertTrue(
        ResourceDefinitionGroupModelListSupport.matchesFilter(
            "${PROJECT_HOME}/models/Retail.hdv", "retail"));
    assertFalse(
        ResourceDefinitionGroupModelListSupport.matchesFilter(
            "${PROJECT_HOME}/models/Retail.hdv", "orders"));
    assertTrue(ResourceDefinitionGroupModelListSupport.matchesFilter("any", ""));
  }

  @Test
  void filterPathsReturnsMatchingOnly() {
    List<String> paths =
        List.of(
            "${PROJECT_HOME}/a.hdv", "${PROJECT_HOME}/models/b.hdv", "${PROJECT_HOME}/other/c.hdv");
    List<String> filtered = ResourceDefinitionGroupModelListSupport.filterPaths(paths, "models");
    assertEquals(1, filtered.size());
    assertEquals("${PROJECT_HOME}/models/b.hdv", filtered.get(0));
  }

  @Test
  void mergeFilteredTableEditReplacesFullListWhenFilterEmpty() {
    List<String> merged =
        ResourceDefinitionGroupModelListSupport.mergeFilteredTableEdit(
            List.of("a", "b"), "", List.of("x", "y"));
    assertEquals(List.of("x", "y"), merged);
  }

  @Test
  void mergeFilteredTableEditKeepsNonMatchingAndReplacesMatchingBlock() {
    List<String> full =
        List.of(
            "${PROJECT_HOME}/keep1.hdv",
            "${PROJECT_HOME}/models/a.hdv",
            "${PROJECT_HOME}/models/b.hdv",
            "${PROJECT_HOME}/keep2.hdv");
    List<String> tableEdit = List.of("${PROJECT_HOME}/models/a-edited.hdv");
    List<String> merged =
        ResourceDefinitionGroupModelListSupport.mergeFilteredTableEdit(full, "models", tableEdit);
    assertEquals(
        List.of(
            "${PROJECT_HOME}/keep1.hdv",
            "${PROJECT_HOME}/models/a-edited.hdv",
            "${PROJECT_HOME}/keep2.hdv"),
        merged);
  }
}
