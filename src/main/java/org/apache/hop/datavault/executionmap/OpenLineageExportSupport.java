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
package org.apache.hop.datavault.executionmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.executionmap.ExecutionMapLineageEdge.DatasetRef;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;

/** Serializes execution map lineage to OpenLineage-compatible JSON. */
public final class OpenLineageExportSupport {

  /**
   * @deprecated use {@link org.apache.hop.datavault.openlineage.OpenLineageConstants#PRODUCER}
   */
  public static final String PRODUCER =
      org.apache.hop.datavault.openlineage.OpenLineageConstants.PRODUCER;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private OpenLineageExportSupport() {}

  public static String toJson(ExecutionMapDocument document) throws HopException {
    List<ExecutionMapLineageEdge> edges = ExecutionMapLineageCollector.collect(document);
    try {
      ArrayNode events = MAPPER.createArrayNode();
      String eventTime = Instant.now().toString();
      for (ExecutionMapLineageEdge edge : edges) {
        events.add(toRunEvent(edge, eventTime));
      }
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(events);
    } catch (Exception e) {
      throw new HopException("Unable to serialize execution map lineage to JSON", e);
    }
  }

  public static void writeJson(ExecutionMapDocument document, String outputPath)
      throws HopException {
    if (Utils.isEmpty(outputPath)) {
      throw new HopException("Lineage output path is required");
    }
    String json = toJson(document);
    try (var outputStream = HopVfs.getOutputStream(outputPath, false)) {
      outputStream.write(json.getBytes(StandardCharsets.UTF_8));
      outputStream.flush();
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to write lineage JSON: " + outputPath, e);
    }
  }

  private static ObjectNode toRunEvent(ExecutionMapLineageEdge edge, String eventTime) {
    ObjectNode event = MAPPER.createObjectNode();
    event.put("eventType", "COMPLETE");
    event.put("eventTime", eventTime);
    event.put("producer", PRODUCER);

    ObjectNode run = MAPPER.createObjectNode();
    run.put("runId", UUID.randomUUID().toString());
    event.set("run", run);

    ObjectNode job = MAPPER.createObjectNode();
    job.put("namespace", edge.getJobNamespace());
    job.put("name", edge.getJobName());
    event.set("job", job);

    event.set("inputs", datasetArray(edge.getInputs()));
    event.set("outputs", datasetArray(edge.getOutputs()));
    return event;
  }

  private static ArrayNode datasetArray(List<DatasetRef> datasets) {
    ArrayNode array = MAPPER.createArrayNode();
    for (DatasetRef dataset : datasets) {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("namespace", dataset.getNamespace());
      node.put("name", dataset.getName());
      if (!Utils.isEmpty(dataset.getKind())) {
        node.put("datasetKind", dataset.getKind());
      }
      array.add(node);
    }
    return array;
  }
}
