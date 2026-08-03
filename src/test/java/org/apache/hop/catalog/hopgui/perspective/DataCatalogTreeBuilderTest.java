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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.junit.jupiter.api.Test;

class DataCatalogTreeBuilderTest {

  @Test
  void sortRefsOrdersByNamespaceThenName() {
    List<RecordDefinitionRef> refs =
        new ArrayList<>(
            List.of(
                ref("hop/project/sources", "beta"),
                ref("hop/project/operations", "load_run"),
                ref("hop/project/sources", "alpha")));

    List<RecordDefinitionRef> sorted = DataCatalogTreeBuilder.sortRefs(refs);

    assertEquals("load_run", sorted.get(0).getKey().getName());
    assertEquals("alpha", sorted.get(1).getKey().getName());
    assertEquals("beta", sorted.get(2).getKey().getName());
  }

  @Test
  void groupByNamespacePreservesNamespaceOrderAndRecordSorting() {
    Map<String, List<RecordDefinitionRef>> grouped =
        DataCatalogTreeBuilder.groupByNamespace(
            List.of(
                ref("hop/project/sources", "beta"),
                ref("hop/project/operations", "load_run"),
                ref("hop/project/sources", "alpha")));

    assertEquals(
        List.of("hop/project/operations", "hop/project/sources"), List.copyOf(grouped.keySet()));
    assertEquals("load_run", grouped.get("hop/project/operations").get(0).getKey().getName());
    assertEquals("alpha", grouped.get("hop/project/sources").get(0).getKey().getName());
    assertEquals("beta", grouped.get("hop/project/sources").get(1).getKey().getName());
  }

  @Test
  void displayRecordNameUsesNameOnlyWhenGrouped() {
    RecordDefinitionRef ref = ref("hop/project/sources", "CRM-customer");

    assertEquals("CRM-customer", DataCatalogTreeBuilder.displayRecordName(ref, true));
    assertEquals(
        "hop/project/sources::CRM-customer", DataCatalogTreeBuilder.displayRecordName(ref, false));
  }

  @Test
  void groupByNamespaceReturnsEmptyMapForEmptyInput() {
    assertTrue(DataCatalogTreeBuilder.groupByNamespace(List.of()).isEmpty());
  }

  private static RecordDefinitionRef ref(String namespace, String name) {
    return new RecordDefinitionRef(
        "local-catalog", new RecordDefinitionKey(namespace, name), RecordDefinitionType.DV_SOURCE);
  }
}
