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
package org.hopper.edw.datavault.lineage;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.catalog.BvCatalogNamespaces;
import org.hopper.edw.datavault.catalog.DmCatalogNamespaces;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.resourcedefinition.ValidationModels;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Builds current lineage from loaded models and diffs against catalog-published lineage baselines.
 */
public final class LineageDiffService {

  private LineageDiffService() {}

  /**
   * Diffs all DV/BV/DM models in a validation group against catalog lineage siblings. Returns one
   * result per model that has either a baseline or current tables.
   */
  public static List<LineageDiffResult> compareModelsToCatalog(
      ValidationModels models, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    List<LineageDiffResult> results = new ArrayList<>();
    if (models == null) {
      return results;
    }

    for (ValidationModels.LoadedDataVaultModel loaded : models.dataVaultModels()) {
      if (loaded == null || loaded.model() == null) {
        continue;
      }
      DataVaultModel model = loaded.model();
      String catalog = firstNonEmpty(loaded.catalogConnection(), defaultCatalog(models));
      LineageSnapshot current =
          DvModelLineageCollector.collect(model, variables, metadataProvider, catalog);
      String basename = DvCatalogNamespaces.resolveModelBasename(model);
      LineageSnapshot baseline =
          Utils.isEmpty(catalog)
              ? null
              : LineageCatalogBaselineLoader.load(
                  catalog, LineageLayer.DV, basename, variables, metadataProvider);
      results.add(
          LineageSnapshotDiffSupport.compare(
              baseline, current, baselineLabel(catalog, LineageLayer.DV, basename, variables)));
    }

    for (ValidationModels.LoadedBusinessVaultModel loaded : models.businessVaultModels()) {
      if (loaded == null || loaded.model() == null) {
        continue;
      }
      BusinessVaultModel model = loaded.model();
      String catalog = firstNonEmpty(loaded.catalogConnection(), defaultCatalog(models));
      LineageSnapshot current = BvModelLineageCollector.collect(model, variables);
      String basename = BvCatalogNamespaces.resolveModelBasename(model);
      LineageSnapshot baseline =
          Utils.isEmpty(catalog)
              ? null
              : LineageCatalogBaselineLoader.load(
                  catalog, LineageLayer.BV, basename, variables, metadataProvider);
      results.add(
          LineageSnapshotDiffSupport.compare(
              baseline, current, baselineLabel(catalog, LineageLayer.BV, basename, variables)));
    }

    for (ValidationModels.LoadedDimensionalModel loaded : models.dimensionalModels()) {
      if (loaded == null || loaded.model() == null) {
        continue;
      }
      DimensionalModel model = loaded.model();
      String catalog = firstNonEmpty(loaded.catalogConnection(), defaultCatalog(models));
      LineageSnapshot current = DmModelLineageCollector.collect(model, variables, metadataProvider);
      String basename = DmCatalogNamespaces.resolveModelBasename(model);
      LineageSnapshot baseline =
          Utils.isEmpty(catalog)
              ? null
              : LineageCatalogBaselineLoader.load(
                  catalog, LineageLayer.DM, basename, variables, metadataProvider);
      results.add(
          LineageSnapshotDiffSupport.compare(
              baseline, current, baselineLabel(catalog, LineageLayer.DM, basename, variables)));
    }

    return results;
  }

  public static boolean hasBlocking(List<LineageDiffResult> results) {
    if (results == null) {
      return false;
    }
    return results.stream().anyMatch(LineageDiffResult::hasBlocking);
  }

  public static boolean hasWarnings(List<LineageDiffResult> results) {
    if (results == null) {
      return false;
    }
    return results.stream().anyMatch(r -> r.hasWarnings() || r.hasBlocking());
  }

  private static String defaultCatalog(ValidationModels models) {
    if (models.group() == null) {
      return null;
    }
    return models.group().getDataCatalogConnection();
  }

  private static String baselineLabel(
      String catalog, LineageLayer layer, String model, IVariables variables) {
    if (Utils.isEmpty(catalog)) {
      return "no-catalog";
    }
    return catalog
        + ":"
        + LineageCatalogNamespaces.projectLineageNamespace(variables, layer, model);
  }

  private static String firstNonEmpty(String a, String b) {
    if (!Utils.isEmpty(a)) {
      return a;
    }
    return b;
  }
}
