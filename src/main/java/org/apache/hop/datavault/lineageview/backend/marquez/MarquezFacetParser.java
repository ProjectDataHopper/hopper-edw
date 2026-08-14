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
package org.apache.hop.datavault.lineageview.backend.marquez;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.lineageview.backend.HopExportFacet;
import org.apache.hop.datavault.lineageview.backend.HopLocationFacet;
import org.apache.hop.datavault.lineageview.backend.HopOpsFacet;

/** Maps hop_* JSON facets. Never copies {@code latestRun.durationMs} into {@link HopOpsFacet}. */
public final class MarquezFacetParser {

  private MarquezFacetParser() {}

  public static HopExportFacet hopExport(JsonNode facets) {
    JsonNode node = facet(facets, "hop_export");
    if (node == null) {
      return null;
    }
    return HopExportFacet.builder()
        .modelLayer(text(node, "modelLayer"))
        .modelName(text(node, "modelName"))
        .exportRunId(text(node, "exportRunId"))
        .modelFilename(text(node, "modelFilename"))
        .tableType(text(node, "tableType"))
        .logicalName(text(node, "logicalName"))
        .projectKey(text(node, "projectKey"))
        .resourceGroup(text(node, "resourceGroup"))
        .catalogConnection(text(node, "catalogConnection"))
        .physicalTableName(text(node, "physicalTableName"))
        .targetDatabase(text(node, "targetDatabase"))
        .build();
  }

  public static HopLocationFacet hopLocation(JsonNode facets) {
    JsonNode hop = facet(facets, "hop_location");
    JsonNode dataSource = facet(facets, "dataSource");
    if (hop == null && dataSource == null) {
      return null;
    }
    HopLocationFacet.HopLocationFacetBuilder builder = HopLocationFacet.builder();
    if (hop != null) {
      builder
          .kind(text(hop, "kind"))
          .connectionName(text(hop, "connectionName"))
          .schemaName(text(hop, "schemaName"))
          .tableName(text(hop, "tableName"))
          .catalogKey(text(hop, "catalogKey"))
          .catalogConnection(text(hop, "catalogConnection"))
          .uri(text(hop, "uri"));
    }
    if (Utils.isEmpty(builder.build().getUri()) && dataSource != null) {
      builder.uri(text(dataSource, "uri"));
    }
    HopLocationFacet built = builder.build();
    if (Utils.isEmpty(built.getKind())
        && Utils.isEmpty(built.getTableName())
        && Utils.isEmpty(built.getCatalogKey())
        && Utils.isEmpty(built.getUri())) {
      return null;
    }
    return built;
  }

  public static HopOpsFacet hopOps(JsonNode facets) {
    JsonNode node = facet(facets, "hop_ops");
    if (node == null) {
      return null;
    }
    Long duration = null;
    if (node.hasNonNull("durationMs") && node.get("durationMs").canConvertToLong()) {
      duration = node.get("durationMs").asLong();
    }
    return HopOpsFacet.builder()
        .lastSuccessAt(text(node, "lastSuccessAt"))
        .loadRunId(text(node, "loadRunId"))
        .pipelineName(text(node, "pipelineName"))
        .durationMs(duration)
        .build();
  }

  static JsonNode facet(JsonNode facets, String name) {
    if (facets == null || !facets.isObject() || Utils.isEmpty(name)) {
      return null;
    }
    JsonNode node = facets.get(name);
    return node != null && node.isObject() ? node : null;
  }

  static String text(JsonNode node, String field) {
    if (node == null || Utils.isEmpty(field) || !node.has(field) || node.get(field).isNull()) {
      return null;
    }
    String value = node.get(field).asText(null);
    return Utils.isEmpty(value) ? null : value;
  }
}
