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
package org.apache.hop.datavault.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.json.HopJson;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;

/** Writes a {@link DvUpdateMetricsReport} as JSON to a configured output folder. */
public final class DvUpdateMetricsJsonWriter {

  private DvUpdateMetricsJsonWriter() {}

  public static String writeReport(
      String outputFolder,
      String modelName,
      String logChannelId,
      DvUpdateMetricsReport report,
      IVariables variables)
      throws HopException {
    if (Utils.isEmpty(outputFolder) || report == null) {
      return null;
    }

    String folder = variables != null ? variables.resolve(outputFolder) : outputFolder;
    if (Utils.isEmpty(folder)) {
      return null;
    }

    String filename = buildFilename(modelName, logChannelId);
    String filePath = appendPath(ensureTrailingSlash(folder), filename);

    try {
      FileObject file = HopVfs.getFileObject(filePath, variables);
      FileObject parent = file.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }

      ObjectMapper mapper = HopJson.newMapper();
      try (OutputStreamWriter writer =
          new OutputStreamWriter(HopVfs.getOutputStream(file, false), StandardCharsets.UTF_8)) {
        mapper.writerWithDefaultPrettyPrinter().writeValue(writer, report);
      }
      return filePath;
    } catch (Exception e) {
      throw new HopException("Unable to write Data Vault update metrics JSON to " + filePath, e);
    }
  }

  static String buildFilename(String modelName, String logChannelId) {
    String modelToken = sanitizeFilenameToken(modelName, "model");
    String channelToken = sanitizeFilenameToken(logChannelId, "channel");
    return modelToken + "-" + channelToken + ".json";
  }

  private static String sanitizeFilenameToken(String value, String fallback) {
    if (Utils.isEmpty(value)) {
      return fallback;
    }
    String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
    return Utils.isEmpty(sanitized) ? fallback : sanitized;
  }

  private static String ensureTrailingSlash(String folder) {
    if (folder.endsWith("/") || folder.endsWith("\\")) {
      return folder;
    }
    return folder + "/";
  }

  private static String appendPath(String folder, String filename) {
    return ensureTrailingSlash(folder) + filename;
  }
}
