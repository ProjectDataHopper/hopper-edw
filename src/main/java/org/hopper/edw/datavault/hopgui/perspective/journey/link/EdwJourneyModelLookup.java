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
package org.hopper.edw.datavault.hopgui.perspective.journey.link;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.ModelRef;

/** Resolves a model file from a layer and a display name or table name. */
public final class EdwJourneyModelLookup {

  /** Separates a model name from its layer when the same name exists in more than one layer. */
  public static final String LAYER_MARK = " · ";

  private EdwJourneyModelLookup() {}

  /** A model file found by display name, file name, or table name. */
  public record Located(String layer, String file, ModelRef model) {}

  /**
   * Finds one model. A label {@code name · dv} forces that layer. Otherwise a layer hint is tried
   * first, then every layer. Several matches without a layer mark return a result whose model is
   * null and whose {@code matches} count is greater than one.
   */
  public static LocateResult locate(EdwJourneySnapshot snapshot, String layer, String element) {
    if (snapshot == null || element == null || element.isBlank()) {
      return new LocateResult(null, 0);
    }
    String name = element.trim();
    String hinted = layer == null ? "" : layer.trim();
    int mark = name.lastIndexOf(LAYER_MARK);
    if (mark > 0) {
      String suffix = name.substring(mark + LAYER_MARK.length()).trim();
      String suffixLayer = layerForModelType(suffix);
      if (!suffixLayer.isBlank() && suffix.equalsIgnoreCase(suffixLayer)) {
        name = name.substring(0, mark).trim();
        hinted = suffixLayer;
      }
    }
    if (!hinted.isBlank()) {
      ModelRef found = findInLayer(snapshot, hinted, name);
      if (found != null) {
        return new LocateResult(new Located(hinted, found.storedPath(), found), 1);
      }
    }
    List<Located> matches = new ArrayList<>();
    for (String candidate : List.of("hsm", "dv", "bv", "dm")) {
      if (candidate.equals(hinted)) {
        continue;
      }
      ModelRef found = findInLayer(snapshot, candidate, name);
      if (found != null) {
        matches.add(new Located(candidate, found.storedPath(), found));
      }
    }
    if (matches.size() == 1) {
      return new LocateResult(matches.get(0), 1);
    }
    return new LocateResult(null, matches.size());
  }

  public record LocateResult(Located located, int matches) {}

  public static String file(EdwJourneySnapshot snapshot, String layer, String element) {
    if (snapshot == null || element == null || element.isBlank()) {
      return null;
    }
    List<ModelRef> models =
        switch (layer == null ? "" : layer) {
          case "hsm" -> snapshot.sourceModels();
          case "dv" -> snapshot.dataVaultModels();
          case "bv" -> snapshot.businessVaultModels();
          case "dm" -> snapshot.dimensionalModels();
          default -> List.of();
        };
    ModelRef byName = null;
    ModelRef byTable = null;
    for (ModelRef model : models) {
      if (model == null) {
        continue;
      }
      if (element.equals(model.displayName()) || element.equals(basename(model.storedPath()))) {
        byName = model;
        break;
      }
      if (byTable == null && model.tableNames().contains(element)) {
        byTable = model;
      }
    }
    ModelRef found = byName != null ? byName : byTable;
    return found == null ? null : found.storedPath();
  }

  private static ModelRef findInLayer(EdwJourneySnapshot snapshot, String layer, String element) {
    String path = file(snapshot, layer, element);
    if (path == null) {
      return null;
    }
    List<ModelRef> models =
        switch (layer == null ? "" : layer) {
          case "hsm" -> snapshot.sourceModels();
          case "dv" -> snapshot.dataVaultModels();
          case "bv" -> snapshot.businessVaultModels();
          case "dm" -> snapshot.dimensionalModels();
          default -> List.of();
        };
    for (ModelRef model : models) {
      if (model != null && path.equals(model.storedPath())) {
        return model;
      }
    }
    return null;
  }

  public static String basename(String path) {
    if (path == null || path.isBlank()) {
      return "";
    }
    String name = path.replace('\\', '/');
    int slash = name.lastIndexOf('/');
    if (slash >= 0) {
      name = name.substring(slash + 1);
    }
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      name = name.substring(0, dot);
    }
    return name;
  }

  public static String layerForModelType(String modelType) {
    if (modelType == null) {
      return "";
    }
    String type = modelType.toLowerCase(Locale.ROOT).trim();
    if ("hsm".equals(type) || type.contains("source")) {
      return "hsm";
    }
    if ("bv".equals(type) || type.contains("business")) {
      return "bv";
    }
    if ("dm".equals(type) || type.contains("dimensional")) {
      return "dm";
    }
    if ("dv".equals(type) || type.contains("vault")) {
      return "dv";
    }
    return "";
  }
}
