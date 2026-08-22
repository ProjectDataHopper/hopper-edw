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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVfs;

/** Writes OpenLineage RunEvents to a folder (one JSON file per event + summary). */
public final class OpenLineageFileWriter {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private OpenLineageFileWriter() {}

  public static void writeEvents(
      String outputFolder, List<ObjectNode> events, OpenLineageExportResult result)
      throws HopException {
    if (Utils.isEmpty(outputFolder)) {
      throw new HopException("OpenLineage output folder is required");
    }
    if (events == null || events.isEmpty()) {
      return;
    }
    ensureFolder(outputFolder);
    for (ObjectNode event : events) {
      if (event == null) {
        continue;
      }
      String fileName = OpenLineageSnapshotMapper.fileNameForEvent(event);
      String path = joinPath(outputFolder, fileName);
      try {
        String json = OpenLineageSnapshotMapper.toPrettyJson(event);
        writeUtf8(path, json);
        result.incrementFilesWritten();
        result.addWrittenPath(path);
      } catch (HopException e) {
        throw e;
      } catch (Exception e) {
        throw new HopException("Unable to write OpenLineage event: " + path, e);
      }
    }
  }

  public static String writeSummary(String outputFolder, OpenLineageExportResult result)
      throws HopException {
    if (Utils.isEmpty(outputFolder) || result == null) {
      return null;
    }
    ensureFolder(outputFolder);
    String path = joinPath(outputFolder, "export-summary.json");
    try {
      ObjectNode summary = MAPPER.createObjectNode();
      summary.put("exportRunId", result.getExportRunId());
      summary.put("eventCount", result.getEventCount());
      summary.put("filesWritten", result.getFilesWritten());
      summary.put("httpPosted", result.getHttpPosted());
      summary.put("httpFailed", result.getHttpFailed());
      ArrayNode warnings = MAPPER.createArrayNode();
      result.getWarnings().forEach(warnings::add);
      summary.set("warnings", warnings);
      ArrayNode errors = MAPPER.createArrayNode();
      result.getErrors().forEach(errors::add);
      summary.set("errors", errors);
      ArrayNode paths = MAPPER.createArrayNode();
      result.getWrittenPaths().forEach(paths::add);
      summary.set("writtenPaths", paths);
      writeUtf8(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
      result.setSummaryPath(path);
      return path;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to write OpenLineage export summary: " + path, e);
    }
  }

  private static void ensureFolder(String folder) throws HopException {
    try {
      if (!HopVfs.getFileObject(folder).exists()) {
        HopVfs.getFileObject(folder).createFolder();
      }
    } catch (Exception e) {
      throw new HopException("Unable to create OpenLineage output folder: " + folder, e);
    }
  }

  private static void writeUtf8(String path, String content) throws HopException {
    try (var out = HopVfs.getOutputStream(path, false)) {
      out.write(content.getBytes(StandardCharsets.UTF_8));
      out.flush();
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to write file: " + path, e);
    }
  }

  private static String joinPath(String folder, String fileName) {
    if (folder.endsWith("/") || folder.endsWith("\\")) {
      return folder + fileName;
    }
    return folder + "/" + fileName;
  }
}
