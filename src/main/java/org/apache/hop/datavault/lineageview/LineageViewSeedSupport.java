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
package org.apache.hop.datavault.lineageview;

import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvCatalogNamespaces;
import org.apache.hop.datavault.lineage.BvModelLineageCollector;
import org.apache.hop.datavault.lineage.DmModelLineageCollector;
import org.apache.hop.datavault.lineage.DvModelLineageCollector;
import org.apache.hop.datavault.lineage.LineageLayer;
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.lineageview.backend.LineageDirection;
import org.apache.hop.datavault.lineageview.backend.LineageQuery;
import org.apache.hop.datavault.lineageview.backend.LineageSeedKind;
import org.apache.hop.datavault.lineageview.backend.OpenLineageRef;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.lineage.FileFolderBackendSettings;
import org.apache.hop.datavault.metadata.lineage.ILineageBackendSettings;
import org.apache.hop.datavault.metadata.lineage.LineageBackendMeta;
import org.apache.hop.datavault.metadata.lineage.LocalModelsBackendSettings;
import org.apache.hop.datavault.metadata.lineage.MarquezBackendSettings;
import org.apache.hop.datavault.openlineage.OpenLineageSnapshotMapper;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Resolves OpenLineage seed ids from a view definition and the current backend. */
public final class LineageViewSeedSupport {

  private LineageViewSeedSupport() {}

  public static HopLineageViewDocument fromModelTable(
      LineageLayer layer, String modelName, String logicalTable, String modelFilename) {
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setSeedKind(LineageSeedKind.MODEL_TABLE);
    document.setModelLayer(layer != null ? layer : LineageLayer.DV);
    document.setModelName(modelName);
    document.setLogicalTable(logicalTable);
    document.setModelFilename(modelFilename);
    document.setDirection(LineageDirection.UPSTREAM);
    document.setDepth(6);
    document.setIncludeJobs(true);
    document.setIncludeOpsOverlay(true);
    return document;
  }

  public static LineageSnapshot collectOpenModel(
      LineageLayer layer,
      Object model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (model == null) {
      return null;
    }
    LineageLayer resolved = layer != null ? layer : LineageLayer.DV;
    return switch (resolved) {
      case DV ->
          model instanceof DataVaultModel vault
              ? DvModelLineageCollector.collect(vault, variables, metadataProvider, null)
              : null;
      case BV ->
          model instanceof BusinessVaultModel businessVault
              ? BvModelLineageCollector.collect(businessVault, variables)
              : null;
      case DM ->
          model instanceof DimensionalModel dimensional
              ? DmModelLineageCollector.collect(dimensional, variables, metadataProvider)
              : null;
      default -> null;
    };
  }

  /**
   * For {@link LineageSeedKind#MODEL_TABLE}, recompute job/dataset identities from the current
   * backend namespaces. Other seed kinds keep stored ids.
   */
  public static void refreshOpenLineageIds(
      HopLineageViewDocument document, LineageBackendMeta backend, IVariables variables) {
    if (document == null || document.getSeedKind() != LineageSeedKind.MODEL_TABLE) {
      return;
    }
    String logical = document.getLogicalTable();
    if (Utils.isEmpty(logical)) {
      return;
    }
    LineageLayer layer =
        document.getModelLayer() != null ? document.getModelLayer() : LineageLayer.DV;
    String model = !Utils.isEmpty(document.getModelName()) ? document.getModelName() : "model";
    document.setJobName(
        layer.name().toLowerCase()
            + "/"
            + OpenLineageSnapshotMapper.sanitizePathSegment(model)
            + "/"
            + OpenLineageSnapshotMapper.sanitizePathSegment(logical));
    String backendJobNs = resolveConfiguredJobNamespace(backend, variables);
    if (!Utils.isEmpty(backendJobNs)) {
      document.setJobNamespace(
          OpenLineageSnapshotMapper.resolveJobNamespace(
              backendJobNs, DvCatalogNamespaces.resolveProjectKey(variables)));
    } else if (Utils.isEmpty(document.getJobNamespace())) {
      document.setJobNamespace(resolveJobNamespace(backend, variables));
    }
    String backendDatasetNs = resolveDatasetNamespace(backend, variables);
    if (!Utils.isEmpty(backendDatasetNs)) {
      document.setDatasetNamespace(backendDatasetNs);
    }
    document.setDatasetName(logical);
  }

  public static LineageQuery toQuery(HopLineageViewDocument document) {
    return toQuery(document, List.of());
  }

  public static LineageQuery toQuery(
      HopLineageViewDocument document, List<LineageSnapshot> extraSnapshots) {
    if (document == null) {
      return LineageQuery.builder().extraSnapshots(orEmpty(extraSnapshots)).build();
    }
    LineageQuery.LineageQueryBuilder builder =
        LineageQuery.builder()
            .direction(document.getDirection())
            .depth(document.getDepth() > 0 ? document.getDepth() : 6)
            .includeJobs(document.isIncludeJobs())
            .layerFilters(document.getLayerFiltersOrEmpty())
            .resourceGroup(document.getResourceGroup())
            .modelLayer(document.getModelLayer())
            .modelName(document.getModelName())
            .logicalTable(document.getLogicalTable())
            .modelFilename(document.getModelFilename())
            .extraSnapshots(orEmpty(extraSnapshots));
    if (!Utils.isEmpty(document.getDatasetNamespace())
        && !Utils.isEmpty(document.getDatasetName())) {
      builder.dataset(
          OpenLineageRef.builder()
              .namespace(document.getDatasetNamespace())
              .name(document.getDatasetName())
              .build());
    }
    if (!Utils.isEmpty(document.getJobNamespace()) && !Utils.isEmpty(document.getJobName())) {
      builder.job(
          OpenLineageRef.builder()
              .namespace(document.getJobNamespace())
              .name(document.getJobName())
              .build());
    }
    return builder.build();
  }

  static String resolveJobNamespace(LineageBackendMeta backend, IVariables variables) {
    String configured = resolveConfiguredJobNamespace(backend, variables);
    String projectKey = DvCatalogNamespaces.resolveProjectKey(variables);
    return OpenLineageSnapshotMapper.resolveJobNamespace(configured, projectKey);
  }

  static String resolveConfiguredJobNamespace(LineageBackendMeta backend, IVariables variables) {
    ILineageBackendSettings settings = backend != null ? backend.getSettingsOrDefault() : null;
    if (settings instanceof LocalModelsBackendSettings local) {
      return resolvedOrNull(variables, local.getJobNamespace());
    }
    if (settings instanceof MarquezBackendSettings marquez) {
      return resolvedOrNull(variables, marquez.getDefaultJobNamespace());
    }
    if (settings instanceof FileFolderBackendSettings) {
      return null;
    }
    return null;
  }

  static String resolveDatasetNamespace(LineageBackendMeta backend, IVariables variables) {
    ILineageBackendSettings settings = backend != null ? backend.getSettingsOrDefault() : null;
    if (settings instanceof LocalModelsBackendSettings local) {
      return resolvedOrNull(variables, local.getDatasetNamespace());
    }
    if (settings instanceof MarquezBackendSettings marquez) {
      return resolvedOrNull(variables, marquez.getDefaultDatasetNamespace());
    }
    return null;
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static String resolvedOrNull(IVariables variables, String value) {
    String resolved = resolve(variables, value);
    if (Utils.isEmpty(resolved) || resolved.contains("${")) {
      return null;
    }
    return resolved;
  }

  private static List<LineageSnapshot> orEmpty(List<LineageSnapshot> extraSnapshots) {
    return extraSnapshots != null ? extraSnapshots : List.of();
  }
}
