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
package org.apache.hop.datavault.openlineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * File naming and JSON payload tests. HopVfs write path is exercised in runtime Hop; unit tests
 * avoid commons-vfs2 / commons-io surefire classpath clashes (same pattern as execution-map
 * serialization tests).
 */
class OpenLineageFileWriterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TempDir Path tempDir;

  @Test
  void eventFileNameAndJsonShapeAreStable() throws Exception {
    ObjectNode event = MAPPER.createObjectNode();
    event.put("eventType", "COMPLETE");
    ObjectNode job = MAPPER.createObjectNode();
    job.put("name", "dv/retail-360/hub_customer");
    event.set("job", job);

    assertEquals(
        "dv_retail-360_hub_customer.json", OpenLineageSnapshotMapper.fileNameForEvent(event));

    String json = OpenLineageSnapshotMapper.toPrettyJson(event);
    Path eventFile = tempDir.resolve(OpenLineageSnapshotMapper.fileNameForEvent(event));
    Files.writeString(eventFile, json);

    assertTrue(Files.exists(eventFile));
    String content = Files.readString(eventFile);
    assertTrue(content.contains("COMPLETE"));
    assertTrue(content.contains("hub_customer"));
  }

  @Test
  void exportResultTracksCounts() {
    OpenLineageExportResult result = new OpenLineageExportResult("export-1");
    result.incrementEventCount();
    result.incrementFilesWritten();
    result.addWrittenPath("/tmp/a.json");
    assertEquals(1, result.getEventCount());
    assertEquals(1, result.getFilesWritten());
    assertEquals(1, result.getWrittenPaths().size());
    assertEquals("export-1", result.getExportRunId());
  }
}
