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
package org.hopper.edw.datavault.lineageview.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVfs;

/** Offline adapter over a folder of OpenLineage RunEvent JSON files. */
public final class FileFolderLineageQueryService implements ILineageQueryService {

  public static final int EVENT_FILE_CAP = 5_000;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String folder;

  public FileFolderLineageQueryService(String folder) {
    this.folder = folder;
  }

  @Override
  public LineageBackendKind kind() {
    return LineageBackendKind.FILE_FOLDER;
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
    LoadedEvents loaded = loadEvents(folder);
    if (loaded.events.isEmpty()) {
      throw new HopException("No OpenLineage events in folder: " + folder);
    }
    String seedId = OpenLineageEventGraphBuilder.resolveSeed(query, loaded.events);
    if (seedId == null) {
      throw new HopException(SEED_NOT_FOUND + ": seed not present in export folder");
    }
    LineageGraph graph = OpenLineageEventGraphBuilder.build(loaded.events, seedId);
    if (!loaded.warnings.isEmpty()) {
      List<LineageWarning> warnings = new ArrayList<>(graph.getWarnings());
      warnings.addAll(loaded.warnings);
      graph = graph.toBuilder().warnings(List.copyOf(warnings)).build();
    }
    return graph;
  }

  @Override
  public Optional<JobDetails> fetchJob(OpenLineageRef job) throws HopException {
    if (job == null || !job.isComplete()) {
      return Optional.empty();
    }
    String id = job.toNodeId(LineageNodeKind.JOB);
    LineageGraph graph = OpenLineageEventGraphBuilder.build(loadEvents(folder).events, id);
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
    LineageGraph graph = OpenLineageEventGraphBuilder.build(loadEvents(folder).events, id);
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
    LineageGraph graph = OpenLineageEventGraphBuilder.build(loadEvents(folder).events, null);
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

  static LoadedEvents loadEvents(String folder) throws HopException {
    if (Utils.isEmpty(folder)) {
      throw new HopException("OpenLineage export folder is required");
    }
    List<LineageWarning> warnings = new ArrayList<>();
    List<EventRecord> records = new ArrayList<>();
    try {
      FileObject dir = HopVfs.getFileObject(folder);
      if (dir == null || !dir.exists() || !dir.isFolder()) {
        throw new HopException("OpenLineage export folder does not exist: " + folder);
      }
      FileObject[] children = dir.getChildren();
      if (children == null) {
        throw new HopException("No OpenLineage events in folder: " + folder);
      }
      int considered = 0;
      for (FileObject child : children) {
        if (child == null || child.isFolder()) {
          continue;
        }
        String name = child.getName().getBaseName();
        if (name == null || !name.toLowerCase().endsWith(".json")) {
          continue;
        }
        if ("export-summary.json".equalsIgnoreCase(name)) {
          continue;
        }
        considered++;
        if (records.size() >= EVENT_FILE_CAP) {
          continue;
        }
        try (InputStream in = HopVfs.getInputStream(child)) {
          JsonNode root = MAPPER.readTree(in);
          if (root == null || !root.isObject()) {
            continue;
          }
          records.add(new EventRecord(name, text(root, "eventTime"), (ObjectNode) root));
        }
      }
      if (considered > EVENT_FILE_CAP) {
        warnings.add(
            LineageWarning.builder()
                .code(LineageWarning.EVENT_CAP)
                .message("Read " + EVENT_FILE_CAP + " of " + considered + " event files")
                .build());
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to read OpenLineage folder: " + folder, e);
    }
    return new LoadedEvents(selectLatest(records), warnings);
  }

  static List<ObjectNode> selectLatest(List<EventRecord> records) {
    Map<String, EventRecord> byJob = new LinkedHashMap<>();
    for (EventRecord record : records) {
      if (record == null || record.event == null) {
        continue;
      }
      String ns = text(record.event.path("job"), "namespace");
      String name = text(record.event.path("job"), "name");
      if (Utils.isEmpty(ns) || Utils.isEmpty(name)) {
        continue;
      }
      String jobId = "job:" + ns + ":" + name;
      EventRecord previous = byJob.get(jobId);
      if (previous == null || isNewer(record, previous)) {
        byJob.put(jobId, record);
      }
    }
    List<EventRecord> selected = new ArrayList<>(byJob.values());
    selected.sort(Comparator.comparing(r -> r.fileName));
    List<ObjectNode> events = new ArrayList<>();
    for (EventRecord record : selected) {
      events.add(record.event);
    }
    return events;
  }

  private static boolean isNewer(EventRecord candidate, EventRecord current) {
    Instant left = parseTime(candidate.eventTime);
    Instant right = parseTime(current.eventTime);
    if (left != null && right != null) {
      return left.isAfter(right);
    }
    if (left != null) {
      return true;
    }
    if (right != null) {
      return false;
    }
    return candidate.fileName.compareTo(current.fileName) > 0;
  }

  private static Instant parseTime(String value) {
    if (Utils.isEmpty(value)) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (Exception e) {
      return null;
    }
  }

  private static String text(JsonNode node, String field) {
    if (node == null || !node.isObject() || Utils.isEmpty(field) || !node.has(field)) {
      return null;
    }
    String value = node.get(field).asText(null);
    return Utils.isEmpty(value) ? null : value;
  }

  static final class EventRecord {
    final String fileName;
    final String eventTime;
    final ObjectNode event;

    EventRecord(String fileName, String eventTime, ObjectNode event) {
      this.fileName = fileName;
      this.eventTime = eventTime;
      this.event = event;
    }
  }

  static final class LoadedEvents {
    final List<ObjectNode> events;
    final List<LineageWarning> warnings;

    LoadedEvents(List<ObjectNode> events, List<LineageWarning> warnings) {
      this.events = events;
      this.warnings = warnings;
    }
  }
}
