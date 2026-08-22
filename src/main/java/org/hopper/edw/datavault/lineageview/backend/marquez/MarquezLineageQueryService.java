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
package org.hopper.edw.datavault.lineageview.backend.marquez;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.lineageview.backend.DatasetDetails;
import org.hopper.edw.datavault.lineageview.backend.HopExportFacet;
import org.hopper.edw.datavault.lineageview.backend.HopOpsFacet;
import org.hopper.edw.datavault.lineageview.backend.ILineageQueryService;
import org.hopper.edw.datavault.lineageview.backend.JobDetails;
import org.hopper.edw.datavault.lineageview.backend.LineageBackendKind;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;
import org.hopper.edw.datavault.lineageview.backend.LineageNodeKind;
import org.hopper.edw.datavault.lineageview.backend.LineageQuery;
import org.hopper.edw.datavault.lineageview.backend.OpenLineageRef;

/** Marquez 0.50 read adapter. {@code GET /api/v1} only. */
public final class MarquezLineageQueryService implements ILineageQueryService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String baseUrl;
  private final String apiKeyHeader;
  private final String apiKey;
  private final int timeoutMs;
  private final HttpClient httpClient;

  public MarquezLineageQueryService(
      String baseUrl, String apiKeyHeader, String apiKey, int timeoutMs) {
    this.baseUrl = MarquezUrls.normalizeBaseUrl(baseUrl);
    this.apiKeyHeader = apiKeyHeader;
    this.apiKey = apiKey;
    this.timeoutMs = timeoutMs > 0 ? timeoutMs : 30_000;
    this.httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(this.timeoutMs)).build();
  }

  @Override
  public LineageBackendKind kind() {
    return LineageBackendKind.MARQUEZ;
  }

  @Override
  public boolean supportsColumnLineage() {
    return false;
  }

  @Override
  public boolean facetsInlineOnGraph() {
    return false;
  }

  @Override
  public LineageGraph fetchGraph(LineageQuery query) throws HopException {
    if (query == null || !query.hasSeedIdentity()) {
      throw new HopException("Lineage query must include a dataset, job, or model table seed");
    }
    if (Utils.isEmpty(baseUrl)) {
      throw new HopException("Marquez base URL is required");
    }
    int depth = query.getDepth() > 0 ? query.getDepth() : 6;
    List<String> tried = new ArrayList<>();
    LineageGraph graph = null;
    if (query.getDataset() != null && query.getDataset().isComplete()) {
      String nodeId = query.getDataset().toNodeId(LineageNodeKind.DATASET);
      tried.add(nodeId);
      graph = fetchNeighborhood(nodeId, depth);
    }
    if (isEmpty(graph) && query.getJob() != null && query.getJob().isComplete()) {
      String nodeId = query.getJob().toNodeId(LineageNodeKind.JOB);
      tried.add(nodeId);
      graph = fetchNeighborhood(nodeId, depth);
    }
    if (isEmpty(graph)) {
      graph = fetchBySearch(query, depth, tried);
    }
    if (isEmpty(graph)) {
      throw new HopException(
          SEED_NOT_FOUND
              + ": no Marquez node for dataset or job seed"
              + (tried.isEmpty() ? "" : " (tried " + String.join(", ", tried) + ")"));
    }
    return enrichSeed(graph);
  }

  @Override
  public Optional<JobDetails> fetchJob(OpenLineageRef job) throws HopException {
    if (job == null || !job.isComplete()) {
      return Optional.empty();
    }
    JsonNode body = getJson(MarquezUrls.jobUrl(baseUrl, job.getNamespace(), job.getName()));
    if (body == null) {
      return Optional.empty();
    }
    String latestRunId = MarquezFacetParser.text(body.path("latestRun"), "id");
    String lastExportedAt = MarquezFacetParser.text(body.path("latestRun"), "endedAt");
    if (Utils.isEmpty(lastExportedAt)) {
      lastExportedAt = MarquezFacetParser.text(body.path("latestRun"), "startedAt");
    }
    HopExportFacet hopExport = MarquezFacetParser.hopExport(body.path("facets"));
    HopOpsFacet hopOps = MarquezFacetParser.hopOps(body.path("facets"));
    if (!Utils.isEmpty(latestRunId) && hopExport == null && hopOps == null) {
      JsonNode facets = getJson(MarquezUrls.runFacetsUrl(baseUrl, latestRunId));
      JsonNode facetRoot = facets != null && facets.has("facets") ? facets.path("facets") : facets;
      hopExport = MarquezFacetParser.hopExport(facetRoot);
      hopOps = MarquezFacetParser.hopOps(facetRoot);
    }
    return Optional.of(
        JobDetails.builder()
            .ref(job)
            .hopExport(hopExport)
            .hopOps(hopOps)
            .latestRunId(latestRunId)
            .lastExportedAt(lastExportedAt)
            .build());
  }

  @Override
  public Optional<DatasetDetails> fetchDataset(OpenLineageRef dataset) throws HopException {
    if (dataset == null || !dataset.isComplete()) {
      return Optional.empty();
    }
    JsonNode body =
        getJson(MarquezUrls.datasetUrl(baseUrl, dataset.getNamespace(), dataset.getName()));
    if (body == null) {
      return Optional.empty();
    }
    List<String> names = new ArrayList<>();
    JsonNode fields = body.path("fields");
    if (!fields.isArray()) {
      fields = body.path("facets").path("schema").path("fields");
    }
    if (fields.isArray()) {
      for (JsonNode field : fields) {
        String name = MarquezFacetParser.text(field, "name");
        if (!Utils.isEmpty(name)) {
          names.add(name);
        }
      }
    }
    return Optional.of(
        DatasetDetails.builder()
            .ref(dataset)
            .hopLocation(MarquezFacetParser.hopLocation(body.path("facets")))
            .schemaFieldNames(List.copyOf(names))
            .build());
  }

  /** Used by Test connection. Returns null on HTTP 404. */
  public JsonNode getNamespaces() throws HopException {
    return getJson(MarquezUrls.namespacesUrl(baseUrl));
  }

  @Override
  public List<OpenLineageRef> searchDatasets(String nameHint) throws HopException {
    return search(nameHint, "dataset");
  }

  @Override
  public List<OpenLineageRef> searchJobs(String nameHint) throws HopException {
    return search(nameHint, "job");
  }

  private List<OpenLineageRef> search(String nameHint, String filter) throws HopException {
    JsonNode body = getJson(MarquezUrls.searchUrl(baseUrl, nameHint, filter));
    if (body == null) {
      return List.of();
    }
    JsonNode results = body.path("results");
    if (!results.isArray()) {
      return List.of();
    }
    List<OpenLineageRef> refs = new ArrayList<>();
    for (JsonNode item : results) {
      if (refs.size() >= 100) {
        break;
      }
      String namespace = MarquezFacetParser.text(item, "namespace");
      String name = MarquezFacetParser.text(item, "name");
      if (Utils.isEmpty(namespace) || Utils.isEmpty(name)) {
        OpenLineageRef fromId = OpenLineageRef.fromNodeId(MarquezFacetParser.text(item, "nodeId"));
        if (fromId != null && fromId.isComplete()) {
          refs.add(fromId);
        }
        continue;
      }
      refs.add(OpenLineageRef.builder().namespace(namespace).name(name).build());
    }
    return List.copyOf(refs);
  }

  private LineageGraph fetchBySearch(LineageQuery query, int depth, List<String> tried)
      throws HopException {
    if (query.getJob() != null && !Utils.isEmpty(query.getJob().getName())) {
      LineageGraph graph =
          fetchFirstMatching(
              searchJobs(query.getJob().getName()),
              LineageNodeKind.JOB,
              query.getJob().getName(),
              depth,
              tried);
      if (!isEmpty(graph)) {
        return graph;
      }
    }
    if (query.getDataset() != null && !Utils.isEmpty(query.getDataset().getName())) {
      LineageGraph graph =
          fetchFirstMatching(
              searchDatasets(query.getDataset().getName()),
              LineageNodeKind.DATASET,
              query.getDataset().getName(),
              depth,
              tried);
      if (!isEmpty(graph)) {
        return graph;
      }
    }
    if (!Utils.isEmpty(query.getLogicalTable())) {
      return fetchFirstMatching(
          searchDatasets(query.getLogicalTable()),
          LineageNodeKind.DATASET,
          query.getLogicalTable(),
          depth,
          tried);
    }
    return null;
  }

  private LineageGraph fetchFirstMatching(
      List<OpenLineageRef> refs,
      LineageNodeKind kind,
      String wantedName,
      int depth,
      List<String> tried)
      throws HopException {
    if (refs == null) {
      return null;
    }
    for (OpenLineageRef ref : refs) {
      if (ref == null || !ref.isComplete() || !nameMatches(wantedName, ref.getName())) {
        continue;
      }
      String nodeId = ref.toNodeId(kind);
      if (tried.contains(nodeId)) {
        continue;
      }
      tried.add(nodeId);
      LineageGraph graph = fetchNeighborhood(nodeId, depth);
      if (!isEmpty(graph)) {
        return graph;
      }
    }
    return null;
  }

  static boolean nameMatches(String wanted, String found) {
    if (Utils.isEmpty(wanted) || Utils.isEmpty(found)) {
      return false;
    }
    return wanted.equals(found);
  }

  private LineageGraph fetchNeighborhood(String nodeId, int depth) throws HopException {
    JsonNode body = getJson(MarquezUrls.lineageUrl(baseUrl, nodeId, depth));
    if (body == null) {
      return null;
    }
    LineageGraph graph = MarquezLineageGraphParser.parse(body, nodeId);
    if (graph.findNode(nodeId) == null) {
      return null;
    }
    return graph;
  }

  private LineageGraph enrichSeed(LineageGraph graph) throws HopException {
    LineageNode seed = graph.findNode(graph.getSeedNodeId());
    if (seed == null) {
      return graph;
    }
    if (seed.getKind() == LineageNodeKind.JOB && seed.getHopExport() == null) {
      Optional<JobDetails> details =
          fetchJob(
              OpenLineageRef.builder().namespace(seed.getNamespace()).name(seed.getName()).build());
      if (details.isPresent()) {
        JobDetails job = details.get();
        LineageNode updated =
            seed.toBuilder()
                .hopExport(job.getHopExport())
                .hopOps(job.getHopOps())
                .latestRunId(
                    !Utils.isEmpty(job.getLatestRunId())
                        ? job.getLatestRunId()
                        : seed.getLatestRunId())
                .lastExportedAt(
                    !Utils.isEmpty(job.getLastExportedAt())
                        ? job.getLastExportedAt()
                        : seed.getLastExportedAt())
                .build();
        return replaceNode(graph, updated);
      }
    }
    if (seed.getKind() == LineageNodeKind.DATASET && seed.getHopLocation() == null) {
      Optional<DatasetDetails> details =
          fetchDataset(
              OpenLineageRef.builder().namespace(seed.getNamespace()).name(seed.getName()).build());
      if (details.isPresent()) {
        DatasetDetails dataset = details.get();
        LineageNode updated =
            seed.toBuilder()
                .hopLocation(dataset.getHopLocation())
                .schemaFieldNames(
                    dataset.getSchemaFieldNames() != null
                            && !dataset.getSchemaFieldNames().isEmpty()
                        ? dataset.getSchemaFieldNames()
                        : seed.getSchemaFieldNames())
                .build();
        return replaceNode(graph, updated);
      }
    }
    return graph;
  }

  private static LineageGraph replaceNode(LineageGraph graph, LineageNode updated) {
    List<LineageNode> nodes = new ArrayList<>();
    for (LineageNode node : graph.getNodesOrEmpty()) {
      nodes.add(updated.getId().equals(node.getId()) ? updated : node);
    }
    return graph.toBuilder().nodes(List.copyOf(nodes)).build();
  }

  private static boolean isEmpty(LineageGraph graph) {
    return graph == null
        || graph.getNodesOrEmpty().isEmpty()
        || graph.findNode(graph.getSeedNodeId()) == null;
  }

  JsonNode getJson(String url) throws HopException {
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofMillis(timeoutMs))
              .header("Accept", "application/json")
              .GET();
      if (!Utils.isEmpty(apiKey) && !Utils.isEmpty(apiKeyHeader)) {
        builder.header(apiKeyHeader.trim(), apiKey);
      } else if (!Utils.isEmpty(apiKey)) {
        builder.header("Authorization", apiKey);
      }
      HttpResponse<String> response =
          httpClient.send(
              builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int status = response.statusCode();
      if (status == 404) {
        return null;
      }
      if (status < 200 || status >= 300) {
        String body = response.body();
        String snippet = body == null ? "" : body.length() > 500 ? body.substring(0, 500) : body;
        throw new HopException(
            "Marquez GET failed with status " + status + " for " + url + ": " + snippet);
      }
      String body = response.body();
      if (Utils.isEmpty(body)) {
        return null;
      }
      return MAPPER.readTree(body);
    } catch (HopException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HopException("Marquez GET interrupted: " + url, e);
    } catch (Exception e) {
      throw new HopException("Unable to GET Marquez URL " + url, e);
    }
  }
}
