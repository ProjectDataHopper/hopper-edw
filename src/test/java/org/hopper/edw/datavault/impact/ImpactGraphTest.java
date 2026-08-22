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
package org.hopper.edw.datavault.impact;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.datavault.resourcedefinition.SourceUsageIndexBuilder;
import org.junit.jupiter.api.Test;

class ImpactGraphTest {

  @Test
  void blastRadius_followsSourceFieldThroughDvAndBv() {
    ImpactGraph graph = sampleGraph();
    RecordDefinitionKey key = new RecordDefinitionKey("hop/demo/sources", "customers");

    List<ImpactNode> radius = graph.blastRadius(key, "customer_id");
    List<String> labels = radius.stream().map(ImpactNode::displayLabel).toList();

    assertTrue(labels.stream().anyMatch(l -> l.contains("hub_customer")), labels.toString());
    assertTrue(labels.stream().anyMatch(l -> l.contains("sat_customer")), labels.toString());
    assertTrue(labels.stream().anyMatch(l -> l.contains("customer_360_bv")), labels.toString());
    assertTrue(labels.stream().anyMatch(l -> l.contains("cust_id")), labels.toString());
  }

  @Test
  void formatBlastRadiusLabels_joinsDistinctDownstream() {
    ImpactGraph graph = sampleGraph();
    String labels =
        graph.formatBlastRadiusLabels(
            new RecordDefinitionKey("hop/demo/sources", "customers"), "email");
    assertTrue(labels.contains("sat_customer"), labels);
    assertTrue(labels.contains("customer_360_bv"), labels);
    assertFalse(labels.contains("source "), labels);
  }

  @Test
  void unmappedField_hasEmptyOrWeakRadius() {
    ImpactGraph graph = sampleGraph();
    String labels =
        graph.formatBlastRadiusLabels(
            new RecordDefinitionKey("hop/demo/sources", "customers"), "never_mapped");
    // Object-level table edges may still appear; field-specific SCD2 should not.
    assertFalse(labels.contains("cust_email"), labels);
  }

  private static ImpactGraph sampleGraph() {
    Map<String, ImpactNode> nodes = new LinkedHashMap<>();
    Map<String, List<ImpactEdge>> outgoing = new LinkedHashMap<>();

    ImpactNode source =
        node(ImpactNodeKind.SOURCE_OBJECT, null, null, null, null, "hop/demo/sources", "customers");
    ImpactNode fieldId =
        node(
            ImpactNodeKind.SOURCE_FIELD,
            null,
            null,
            null,
            "customer_id",
            "hop/demo/sources",
            "customers");
    ImpactNode fieldEmail =
        node(
            ImpactNodeKind.SOURCE_FIELD,
            null,
            null,
            null,
            "email",
            "hop/demo/sources",
            "customers");
    ImpactNode hub =
        node(
            ImpactNodeKind.DV_TABLE,
            SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
            "demo",
            "demo.hdv",
            "hub_customer",
            null,
            null,
            null);
    ImpactNode sat =
        node(
            ImpactNodeKind.DV_TABLE,
            SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
            "demo",
            "demo.hdv",
            "sat_customer",
            null,
            null,
            null);
    ImpactNode satEmail =
        node(
            ImpactNodeKind.DV_FIELD,
            SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
            "demo",
            "demo.hdv",
            "sat_customer",
            "email",
            null,
            null);
    ImpactNode hubField =
        node(
            ImpactNodeKind.DV_FIELD,
            SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
            "demo",
            "demo.hdv",
            "hub_customer",
            "customer_id",
            null,
            null);
    ImpactNode bv =
        node(
            ImpactNodeKind.BV_TABLE,
            SourceUsageIndexBuilder.MODEL_TYPE_BUSINESS_VAULT,
            "demo-bv",
            "demo.hbv",
            "customer_360_bv",
            null,
            null,
            null);
    ImpactNode bvId =
        node(
            ImpactNodeKind.BV_FIELD,
            SourceUsageIndexBuilder.MODEL_TYPE_BUSINESS_VAULT,
            "demo-bv",
            "demo.hbv",
            "customer_360_bv",
            "cust_id",
            null,
            null);
    ImpactNode bvEmail =
        node(
            ImpactNodeKind.BV_FIELD,
            SourceUsageIndexBuilder.MODEL_TYPE_BUSINESS_VAULT,
            "demo-bv",
            "demo.hbv",
            "customer_360_bv",
            "cust_email",
            null,
            null);

    for (ImpactNode n :
        List.of(source, fieldId, fieldEmail, hub, sat, satEmail, hubField, bv, bvId, bvEmail)) {
      nodes.put(n.id(), n);
    }

    edge(outgoing, ImpactEdgeType.SOURCE_TO_DV, source, hub);
    edge(outgoing, ImpactEdgeType.SOURCE_TO_DV, source, sat);
    edge(outgoing, ImpactEdgeType.SOURCE_TO_DV, fieldId, hub);
    edge(outgoing, ImpactEdgeType.SOURCE_TO_DV, fieldId, hubField);
    edge(outgoing, ImpactEdgeType.SOURCE_TO_DV, fieldEmail, sat);
    edge(outgoing, ImpactEdgeType.SOURCE_TO_DV, fieldEmail, satEmail);
    edge(outgoing, ImpactEdgeType.DV_PARENT, sat, hub);
    edge(outgoing, ImpactEdgeType.DV_TO_BV_SCD2, satEmail, bvEmail);
    edge(outgoing, ImpactEdgeType.DV_TO_BV_SCD2, satEmail, bv);
    edge(outgoing, ImpactEdgeType.DV_TO_BV_SCD2, hubField, bvId);
    edge(outgoing, ImpactEdgeType.DV_TO_BV_SCD2, hubField, bv);

    return new ImpactGraph(nodes, outgoing);
  }

  private static ImpactNode node(
      ImpactNodeKind kind,
      String modelType,
      String modelName,
      String modelFilename,
      String elementName,
      String fieldName,
      String sourceNamespace,
      String sourceName) {
    return new ImpactNode(
        kind,
        modelType,
        modelName,
        modelFilename,
        elementName,
        fieldName,
        sourceNamespace,
        sourceName);
  }

  private static ImpactNode node(
      ImpactNodeKind kind,
      String modelType,
      String modelName,
      String modelFilename,
      String fieldName,
      String sourceNamespace,
      String sourceName) {
    return node(
        kind, modelType, modelName, modelFilename, null, fieldName, sourceNamespace, sourceName);
  }

  private static void edge(
      Map<String, List<ImpactEdge>> outgoing, ImpactEdgeType type, ImpactNode from, ImpactNode to) {
    outgoing
        .computeIfAbsent(from.id(), ignored -> new ArrayList<>())
        .add(new ImpactEdge(type, from.id(), to.id()));
  }
}
