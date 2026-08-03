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

import java.util.List;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.junit.jupiter.api.Test;

class DataCatalogSelectionSupportTest {

  @Test
  void collectRecordNodesIgnoresCatalogNodes() {
    DataCatalogTreeNode catalog = DataCatalogTreeNode.catalog("local");
    DataCatalogTreeNode record =
        DataCatalogTreeNode.record(
            "local",
            new RecordDefinitionRef(
                "local",
                new RecordDefinitionKey("dv", "customers"),
                RecordDefinitionType.DV_SOURCE));

    List<DataCatalogTreeNode> recordNodes =
        DataCatalogSelectionSupport.collectRecordNodesFromTreeNodes(List.of(catalog, record));

    assertEquals(1, recordNodes.size());
    assertEquals(DataCatalogTreeNode.Type.RECORD, recordNodes.get(0).getType());
    assertEquals("customers", recordNodes.get(0).getRecordKey().getName());
  }

  @Test
  void firstCatalogConnectionNameUsesFirstSelectedNode() {
    DataCatalogTreeNode record =
        DataCatalogTreeNode.record(
            "project-catalog",
            new RecordDefinitionRef(
                "project-catalog",
                new RecordDefinitionKey("dv", "orders"),
                RecordDefinitionType.DV_SOURCE));

    assertEquals(
        "project-catalog",
        DataCatalogSelectionSupport.firstCatalogConnectionNameFromTreeNodes(List.of(record)));
  }

  @Test
  void collectRecordNodesReturnsEmptyForNullInput() {
    assertTrue(DataCatalogSelectionSupport.collectRecordNodesFromTreeNodes(null).isEmpty());
    assertTrue(DataCatalogSelectionSupport.collectRecordNodes(null).isEmpty());
  }

  @Test
  void collectRecordNodesIgnoresNamespaceNodes() {
    DataCatalogTreeNode namespace =
        DataCatalogTreeNode.namespace("local-catalog", "hop/project/sources");
    DataCatalogTreeNode record =
        DataCatalogTreeNode.record(
            "local-catalog",
            new RecordDefinitionRef(
                "local-catalog",
                new RecordDefinitionKey("hop/project/sources", "CRM-customer"),
                RecordDefinitionType.DV_SOURCE));

    List<DataCatalogTreeNode> recordNodes =
        DataCatalogSelectionSupport.collectRecordNodesFromTreeNodes(List.of(namespace, record));

    assertEquals(1, recordNodes.size());
    assertEquals(DataCatalogTreeNode.Type.RECORD, recordNodes.get(0).getType());
  }

  @Test
  void firstCatalogConnectionNameResolvesFromNamespaceNode() {
    DataCatalogTreeNode namespace =
        DataCatalogTreeNode.namespace("vault-catalog", "hop/project/models/retail-360");

    assertEquals(
        "vault-catalog",
        DataCatalogSelectionSupport.firstCatalogConnectionNameFromTreeNodes(List.of(namespace)));
  }
}
