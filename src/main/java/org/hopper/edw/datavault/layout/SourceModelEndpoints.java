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
package org.hopper.edw.datavault.layout;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJson;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMasking;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipeline;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;

/** Canvas cards on a source model, keyed so two endpoint kinds can share a display name. */
public final class SourceModelEndpoints {

  private SourceModelEndpoints() {}

  public record Endpoint(
      SourceEndpointKind kind, String name, IGuiPosition position, int width, int height) {}

  public static String id(SourceEndpointKind kind, String name) {
    SourceEndpointKind resolved = kind != null ? kind : SourceEndpointKind.TABLE;
    return resolved.name() + ":" + (name != null ? name : "");
  }

  public static List<Endpoint> cards(SourceModel model) {
    List<Endpoint> cards = new ArrayList<>();
    if (model == null) {
      return cards;
    }
    for (SourceTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      SourceModelCardSize.Size size = SourceModelCardSize.ofTable(table);
      cards.add(
          new Endpoint(
              SourceEndpointKind.TABLE, table.getName(), table, size.width(), size.height()));
    }
    for (SourceQuery query : model.getQueries()) {
      if (query == null || Utils.isEmpty(query.getName())) {
        continue;
      }
      SourceModelCardSize.Size size = SourceModelCardSize.ofNamedCard(query.getName());
      cards.add(
          new Endpoint(
              SourceEndpointKind.QUERY, query.getName(), query, size.width(), size.height()));
    }
    for (SourceJson json : model.getJsonSources()) {
      if (json == null || Utils.isEmpty(json.getName())) {
        continue;
      }
      SourceModelCardSize.Size size = SourceModelCardSize.ofNamedCard(json.getName());
      cards.add(
          new Endpoint(SourceEndpointKind.JSON, json.getName(), json, size.width(), size.height()));
    }
    for (SourcePipeline pipeline : model.getPipelineSources()) {
      if (pipeline == null || Utils.isEmpty(pipeline.getName())) {
        continue;
      }
      SourceModelCardSize.Size size = SourceModelCardSize.ofNamedCard(pipeline.getName());
      cards.add(
          new Endpoint(
              SourceEndpointKind.PIPELINE,
              pipeline.getName(),
              pipeline,
              size.width(),
              size.height()));
    }
    for (SourceMasking masking : model.getMaskingSources()) {
      if (masking == null || Utils.isEmpty(masking.getName())) {
        continue;
      }
      SourceModelCardSize.Size size = SourceModelCardSize.ofNamedCard(masking.getName());
      cards.add(
          new Endpoint(
              SourceEndpointKind.MASKING, masking.getName(), masking, size.width(), size.height()));
    }
    return cards;
  }

  public static boolean exists(SourceModel model, SourceEndpointKind kind, String name) {
    return SourceEndpointSupport.exists(model, kind, name);
  }
}
