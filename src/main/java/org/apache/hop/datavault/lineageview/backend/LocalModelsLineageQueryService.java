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
package org.apache.hop.datavault.lineageview.backend;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.lineage.BvModelLineageCollector;
import org.apache.hop.datavault.lineage.DmModelLineageCollector;
import org.apache.hop.datavault.lineage.DvModelLineageCollector;
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.openlineage.OpenLineageExportService;
import org.apache.hop.datavault.openlineage.OpenLineageLocationContext;
import org.apache.hop.datavault.openlineage.OpenLineageSnapshotMapper;
import org.apache.hop.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.apache.hop.datavault.resourcedefinition.ValidationModels;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Builds a lineage graph from current DV/BV/DM collectors. No HopGui — unsaved models enter as
 * {@link LineageQuery#getExtraSnapshots()}.
 */
public final class LocalModelsLineageQueryService implements ILineageQueryService {

  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final String resourceDefinitionGroup;
  private final String jobNamespace;
  private final String datasetNamespace;

  public LocalModelsLineageQueryService(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String resourceDefinitionGroup,
      String jobNamespace,
      String datasetNamespace) {
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.resourceDefinitionGroup = resourceDefinitionGroup;
    this.jobNamespace = jobNamespace;
    this.datasetNamespace = datasetNamespace;
  }

  @Override
  public LineageBackendKind kind() {
    return LineageBackendKind.LOCAL_MODELS;
  }

  @Override
  public boolean supportsColumnLineage() {
    return false;
  }

  @Override
  public boolean facetsInlineOnGraph() {
    return true;
  }

  @Override
  public LineageGraph fetchGraph(LineageQuery query) throws HopException {
    if (query == null || !query.hasSeedIdentity()) {
      throw new HopException("Lineage query must include a dataset, job, or model table seed");
    }
    List<ObjectNode> events = toEvents(collectSnapshots(query, true), query);
    String seedId = OpenLineageEventGraphBuilder.resolveSeed(query, events);
    if (seedId == null) {
      throw new HopException(SEED_NOT_FOUND + ": seed not present in current models");
    }
    return OpenLineageEventGraphBuilder.build(events, seedId);
  }

  @Override
  public Optional<JobDetails> fetchJob(OpenLineageRef job) throws HopException {
    if (job == null || !job.isComplete()) {
      return Optional.empty();
    }
    String id = job.toNodeId(LineageNodeKind.JOB);
    LineageGraph graph =
        OpenLineageEventGraphBuilder.build(
            toEvents(collectSnapshots(LineageQuery.builder().job(job).build(), false), null), id);
    LineageNode node = graph.findNode(id);
    if (node == null) {
      return Optional.empty();
    }
    return Optional.of(
        JobDetails.builder()
            .ref(job)
            .hopExport(node.getHopExport())
            .hopOps(node.getHopOps())
            .latestRunId(node.getLatestRunId())
            .lastExportedAt(node.getLastExportedAt())
            .build());
  }

  @Override
  public Optional<DatasetDetails> fetchDataset(OpenLineageRef dataset) throws HopException {
    if (dataset == null || !dataset.isComplete()) {
      return Optional.empty();
    }
    String id = dataset.toNodeId(LineageNodeKind.DATASET);
    LineageGraph graph =
        OpenLineageEventGraphBuilder.build(
            toEvents(
                collectSnapshots(LineageQuery.builder().dataset(dataset).build(), false), null),
            id);
    LineageNode node = graph.findNode(id);
    if (node == null) {
      return Optional.empty();
    }
    return Optional.of(
        DatasetDetails.builder()
            .ref(dataset)
            .hopLocation(node.getHopLocation())
            .schemaFieldNames(node.getSchemaFieldNames())
            .build());
  }

  @Override
  public List<OpenLineageRef> searchDatasets(String nameHint) throws HopException {
    return search(nameHint, LineageNodeKind.DATASET);
  }

  @Override
  public List<OpenLineageRef> searchJobs(String nameHint) throws HopException {
    return search(nameHint, LineageNodeKind.JOB);
  }

  private List<OpenLineageRef> search(String nameHint, LineageNodeKind kind) throws HopException {
    LineageQuery query = LineageQuery.builder().resourceGroup(resourceDefinitionGroup).build();
    List<ObjectNode> events = toEvents(collectSnapshots(query, false), query);
    LineageGraph graph = OpenLineageEventGraphBuilder.build(events, null);
    String needle = nameHint == null ? "" : nameHint.trim().toLowerCase();
    List<OpenLineageRef> refs = new ArrayList<>();
    for (LineageNode node : graph.getNodesOrEmpty()) {
      if (node.getKind() != kind) {
        continue;
      }
      if (!needle.isEmpty() && !node.getName().toLowerCase().contains(needle)) {
        continue;
      }
      refs.add(
          OpenLineageRef.builder().namespace(node.getNamespace()).name(node.getName()).build());
      if (refs.size() >= 100) {
        break;
      }
    }
    return List.copyOf(refs);
  }

  List<LineageSnapshot> collectSnapshots(LineageQuery query, boolean requireSource)
      throws HopException {
    String groupName = resolveGroupName(query);
    List<LineageSnapshot> extras =
        query != null && query.getExtraSnapshots() != null ? query.getExtraSnapshots() : List.of();
    if (Utils.isEmpty(groupName) && extras.isEmpty()) {
      if (requireSource) {
        throw new HopException(
            "Local-models lineage needs a resource definition group or extraSnapshots");
      }
      return List.of();
    }
    List<LineageSnapshot> fromGroup = new ArrayList<>();
    if (!Utils.isEmpty(groupName)) {
      ResourceDefinitionGroupMeta group =
          ResourceDefinitionGroupResolver.loadGroup(groupName, metadataProvider);
      ValidationModels models =
          ResourceDefinitionGroupResolver.resolve(group, variables, metadataProvider);
      fromGroup.addAll(collectGroupSnapshots(models, groupName));
    }
    return mergeExtras(fromGroup, extras);
  }

  String resolveGroupName(LineageQuery query) {
    if (query != null && !Utils.isEmpty(query.getResourceGroup())) {
      return query.getResourceGroup().trim();
    }
    return Utils.isEmpty(resourceDefinitionGroup) ? null : resourceDefinitionGroup.trim();
  }

  static List<LineageSnapshot> mergeExtras(
      List<LineageSnapshot> groupSnapshots, List<LineageSnapshot> extras) {
    Map<String, LineageSnapshot> byFile = new LinkedHashMap<>();
    List<LineageSnapshot> unkeyed = new ArrayList<>();
    if (groupSnapshots != null) {
      for (LineageSnapshot snapshot : groupSnapshots) {
        if (snapshot == null) {
          continue;
        }
        String key = filenameKey(snapshot.getModelFilename());
        if (key == null) {
          unkeyed.add(snapshot);
        } else {
          byFile.put(key, snapshot);
        }
      }
    }
    if (extras != null) {
      for (LineageSnapshot extra : extras) {
        if (extra == null) {
          continue;
        }
        String key = filenameKey(extra.getModelFilename());
        if (key == null) {
          unkeyed.add(extra);
        } else {
          byFile.put(key, extra);
        }
      }
    }
    List<LineageSnapshot> merged = new ArrayList<>(byFile.values());
    merged.addAll(unkeyed);
    return merged;
  }

  private List<LineageSnapshot> collectGroupSnapshots(ValidationModels models, String groupName) {
    List<LineageSnapshot> snapshots = new ArrayList<>();
    if (models == null) {
      return snapshots;
    }
    String defaultCatalog = defaultCatalog(models);
    for (ValidationModels.LoadedDataVaultModel loaded : models.dataVaultModels()) {
      if (loaded == null || loaded.model() == null) {
        continue;
      }
      LineageSnapshot snapshot =
          DvModelLineageCollector.collect(
              loaded.model(), variables, metadataProvider, loaded.catalogConnection());
      OpenLineageExportService.stampHopIdentity(snapshot, groupName, loaded.catalogConnection());
      snapshots.add(snapshot);
    }
    for (ValidationModels.LoadedBusinessVaultModel loaded : models.businessVaultModels()) {
      if (loaded == null || loaded.model() == null) {
        continue;
      }
      LineageSnapshot snapshot = BvModelLineageCollector.collect(loaded.model(), variables);
      String catalog =
          !Utils.isEmpty(loaded.catalogConnection()) ? loaded.catalogConnection() : defaultCatalog;
      OpenLineageExportService.stampHopIdentity(snapshot, groupName, catalog);
      snapshots.add(snapshot);
    }
    for (ValidationModels.LoadedDimensionalModel loaded : models.dimensionalModels()) {
      if (loaded == null || loaded.model() == null) {
        continue;
      }
      LineageSnapshot snapshot =
          DmModelLineageCollector.collect(loaded.model(), variables, metadataProvider);
      String catalog =
          !Utils.isEmpty(loaded.catalogConnection()) ? loaded.catalogConnection() : defaultCatalog;
      OpenLineageExportService.stampHopIdentity(snapshot, groupName, catalog);
      snapshots.add(snapshot);
    }
    return snapshots;
  }

  private List<ObjectNode> toEvents(List<LineageSnapshot> snapshots, LineageQuery query) {
    String jobNs = resolveVar(jobNamespace);
    String datasetNs = resolveVar(datasetNamespace);
    String exportRunId = UUID.randomUUID().toString();
    List<ObjectNode> events = new ArrayList<>();
    if (snapshots == null) {
      return events;
    }
    for (LineageSnapshot snapshot : snapshots) {
      if (snapshot == null) {
        continue;
      }
      OpenLineageLocationContext locationContext =
          new OpenLineageLocationContext(
              variables, metadataProvider, snapshot.getCatalogConnection());
      events.addAll(
          OpenLineageSnapshotMapper.toRunEvents(
              snapshot, jobNs, datasetNs, false, exportRunId, locationContext));
    }
    OpenLineageSnapshotMapper.enrichInputSchemasFromOutputs(events);
    return events;
  }

  private String resolveVar(String value) {
    if (Utils.isEmpty(value)) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static String defaultCatalog(ValidationModels models) {
    if (!models.dataVaultModels().isEmpty() && models.dataVaultModels().get(0) != null) {
      return models.dataVaultModels().get(0).catalogConnection();
    }
    if (!models.businessVaultModels().isEmpty() && models.businessVaultModels().get(0) != null) {
      return models.businessVaultModels().get(0).catalogConnection();
    }
    if (!models.dimensionalModels().isEmpty() && models.dimensionalModels().get(0) != null) {
      return models.dimensionalModels().get(0).catalogConnection();
    }
    return null;
  }

  private static String filenameKey(String filename) {
    if (Utils.isEmpty(filename)) {
      return null;
    }
    return filename.trim();
  }
}
