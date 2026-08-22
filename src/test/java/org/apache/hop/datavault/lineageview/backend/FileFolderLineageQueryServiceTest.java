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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileFolderLineageQueryServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void lastEventTimeWinsForSameJob() {
    ObjectNode older = event("retail-job", "dm/m/fact", "2026-08-01T00:00:00Z", "old");
    ObjectNode newer = event("retail-job", "dm/m/fact", "2026-08-14T00:00:00Z", "new");
    List<ObjectNode> selected =
        FileFolderLineageQueryService.selectLatest(
            List.of(
                new FileFolderLineageQueryService.EventRecord(
                    "a.json", "2026-08-01T00:00:00Z", older),
                new FileFolderLineageQueryService.EventRecord(
                    "b.json", "2026-08-14T00:00:00Z", newer)));
    assertEquals(1, selected.size());
    assertEquals("new", selected.get(0).path("producer").asText());
  }

  @Test
  void missingEventTimeLosesToTimestampedEvent() {
    ObjectNode noTime = event("retail-job", "dm/m/fact", null, "bare");
    ObjectNode timed = event("retail-job", "dm/m/fact", "2026-08-14T00:00:00Z", "timed");
    List<ObjectNode> selected =
        FileFolderLineageQueryService.selectLatest(
            List.of(
                new FileFolderLineageQueryService.EventRecord("z.json", null, noTime),
                new FileFolderLineageQueryService.EventRecord(
                    "a.json", "2026-08-14T00:00:00Z", timed)));
    assertSame(timed, selected.get(0));
  }

  @Test
  void resolveSeedPrefersDatasetThenJob() {
    ObjectNode event =
        OpenLineageEventGraphBuilderTest.runEvent(
            "retail-job", "dm/retail-pos/f_orders", "retail-dataset", "src", "f_orders");
    LineageQuery query =
        LineageQuery.builder()
            .dataset(OpenLineageRef.builder().namespace("retail-dataset").name("f_orders").build())
            .job(
                OpenLineageRef.builder()
                    .namespace("retail-job")
                    .name("dm/retail-pos/f_orders")
                    .build())
            .build();
    assertEquals(
        "dataset:retail-dataset:f_orders",
        OpenLineageEventGraphBuilder.resolveSeed(query, List.of(event)));
    assertNull(
        OpenLineageEventGraphBuilder.resolveSeed(
            LineageQuery.builder()
                .dataset(OpenLineageRef.builder().namespace("no").name("pe").build())
                .build(),
            List.of(event)));
  }

  private static ObjectNode event(String ns, String name, String eventTime, String producer) {
    ObjectNode event = MAPPER.createObjectNode();
    if (eventTime != null) {
      event.put("eventTime", eventTime);
    }
    event.put("producer", producer);
    ObjectNode job = MAPPER.createObjectNode();
    job.put("namespace", ns);
    job.put("name", name);
    event.set("job", job);
    return event;
  }
}
