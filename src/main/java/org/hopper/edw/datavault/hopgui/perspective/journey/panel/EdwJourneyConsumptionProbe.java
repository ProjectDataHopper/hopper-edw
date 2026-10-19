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
package org.hopper.edw.datavault.hopgui.perspective.journey.panel;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.ModelRef;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelContent.JdbcService;
import org.hopper.edw.datavault.metadata.sourcemodel.service.SourceModelService;

/** Finds semantic-layer files and JDBC services that belong to the group's models. */
public final class EdwJourneyConsumptionProbe {

  private EdwJourneyConsumptionProbe() {}

  public static boolean semanticLayerPresent(List<ModelRef> dimensional, IVariables variables) {
    return !semanticFiles(dimensional, variables).isEmpty();
  }

  public static List<String> semanticFiles(List<ModelRef> dimensional, IVariables variables) {
    List<String> files = new ArrayList<>();
    if (dimensional == null) {
      return files;
    }
    for (ModelRef model : dimensional) {
      if (model == null || Utils.isEmpty(model.storedPath())) {
        continue;
      }
      String path = model.storedPath();
      String hsl = path.replaceAll("(?i)\\.hdm$", ".hsl");
      if (hsl.equals(path)) {
        continue;
      }
      try {
        String resolved = variables == null ? hsl : variables.resolve(hsl);
        if (HopVfs.getFileObject(resolved).exists()) {
          files.add(hsl);
        }
      } catch (Exception ignored) {
        // A missing sibling is an empty consumption row, not a Journey failure.
      }
    }
    return files;
  }

  public static List<JdbcService> jdbcServices(
      IHopMetadataProvider provider, List<ModelRef> sources, IVariables variables) {
    List<JdbcService> services = new ArrayList<>();
    if (provider == null || sources == null || sources.isEmpty()) {
      return services;
    }
    try {
      IHopMetadataSerializer<SourceModelService> serializer =
          provider.getSerializer(SourceModelService.class);
      for (String name : serializer.listObjectNames()) {
        SourceModelService service = serializer.load(name);
        if (service == null || Utils.isEmpty(service.getModelFilename())) {
          continue;
        }
        String filename = service.getModelFilename();
        String resolved = variables == null ? filename : variables.resolve(filename);
        for (ModelRef source : sources) {
          if (source == null || Utils.isEmpty(source.storedPath())) {
            continue;
          }
          String model =
              variables == null ? source.storedPath() : variables.resolve(source.storedPath());
          if (resolved.equals(model) || filename.equals(source.storedPath())) {
            services.add(new JdbcService(name, source.storedPath()));
            break;
          }
        }
      }
    } catch (HopException e) {
      return services;
    }
    return services;
  }
}
