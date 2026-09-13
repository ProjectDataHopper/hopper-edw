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
package org.hopper.edw.datavault.diagram.schema;

import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.architecture.ArchitectureEdge;
import org.hopper.edw.datavault.architecture.ArchitectureGraph;
import org.hopper.edw.datavault.architecture.ArchitectureNode;

/** PlantUML / Mermaid flow diagrams from an execution-map architecture graph. */
public final class ExecutionMapFlowRenderer {

  private ExecutionMapFlowRenderer() {}

  public static String renderPlantUml(ArchitectureGraph graph) {
    StringBuilder sb = new StringBuilder();
    sb.append("@startuml\n");
    sb.append("left to right direction\n");
    if (graph != null && !Utils.isEmpty(graph.getName())) {
      sb.append("title ").append(SchemaIdentifiers.display(graph.getName())).append('\n');
    }
    if (graph != null) {
      for (ArchitectureNode node : graph.getNodes()) {
        if (node == null || Utils.isEmpty(node.getId())) {
          continue;
        }
        String label = !Utils.isEmpty(node.getName()) ? node.getName() : node.getId();
        sb.append("component \"")
            .append(SchemaIdentifiers.display(label))
            .append("\" as ")
            .append(SchemaIdentifiers.alias(node.getId()));
        if (!Utils.isEmpty(node.getDetailType())) {
          sb.append(" <<").append(SchemaIdentifiers.display(node.getDetailType())).append(">>");
        }
        sb.append('\n');
      }
      for (ArchitectureEdge edge : graph.getEdges()) {
        if (edge == null
            || Utils.isEmpty(edge.getFromNodeId())
            || Utils.isEmpty(edge.getToNodeId())) {
          continue;
        }
        sb.append(SchemaIdentifiers.alias(edge.getFromNodeId()))
            .append(" --> ")
            .append(SchemaIdentifiers.alias(edge.getToNodeId()));
        if (!Utils.isEmpty(edge.getLabel())) {
          sb.append(" : ").append(SchemaIdentifiers.display(edge.getLabel()));
        }
        sb.append('\n');
      }
    }
    sb.append("@enduml\n");
    return sb.toString();
  }

  public static String renderMermaid(ArchitectureGraph graph, SchemaRenderOptions options) {
    StringBuilder sb = new StringBuilder();
    sb.append("flowchart LR\n");
    if (graph != null) {
      for (ArchitectureNode node : graph.getNodes()) {
        if (node == null || Utils.isEmpty(node.getId())) {
          continue;
        }
        String label = !Utils.isEmpty(node.getName()) ? node.getName() : node.getId();
        sb.append("  ")
            .append(SchemaIdentifiers.alias(node.getId()))
            .append("[\"")
            .append(escapeMermaid(label))
            .append("\"]\n");
      }
      for (ArchitectureEdge edge : graph.getEdges()) {
        if (edge == null
            || Utils.isEmpty(edge.getFromNodeId())
            || Utils.isEmpty(edge.getToNodeId())) {
          continue;
        }
        sb.append("  ")
            .append(SchemaIdentifiers.alias(edge.getFromNodeId()))
            .append(" --> ")
            .append(SchemaIdentifiers.alias(edge.getToNodeId()))
            .append('\n');
      }
    }
    String body = sb.toString();
    if (options != null && options.isMarkdownFence()) {
      return "```mermaid\n" + body + "```\n";
    }
    return body;
  }

  private static String escapeMermaid(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("\"", "#34;").replace("[", "#91;").replace("]", "#93;").replace("\n", " ");
  }
}
