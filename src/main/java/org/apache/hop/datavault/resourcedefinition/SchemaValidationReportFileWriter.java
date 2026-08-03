/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.resourcedefinition;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;

/** Writes schema validation Markdown/HTML reports via Hop VFS. */
public final class SchemaValidationReportFileWriter {

  public static final String MARKDOWN_EXTENSION = ".md";
  public static final String HTML_EXTENSION = ".html";

  public enum ReportFormat {
    MARKDOWN,
    HTML,
    BOTH
  }

  private static final Class<?> PKG = SchemaValidationReportFileWriter.class;

  private SchemaValidationReportFileWriter() {}

  public static List<String> write(
      String outputPath,
      String fileBaseName,
      SchemaImpactSimulationResult result,
      ReportFormat format,
      IVariables variables)
      throws HopException {
    if (result == null || Utils.isEmpty(outputPath)) {
      return List.of();
    }
    ReportFormat effective = format != null ? format : ReportFormat.MARKDOWN;
    String folder = resolveFolder(outputPath, variables);
    String baseName = resolveBaseName(fileBaseName, result, variables);
    List<String> written = new ArrayList<>();
    if (effective == ReportFormat.MARKDOWN || effective == ReportFormat.BOTH) {
      written.add(
          writeFile(
              folder,
              baseName,
              MARKDOWN_EXTENSION,
              SchemaValidationReportFormatter.formatMarkdown(result)));
    }
    if (effective == ReportFormat.HTML || effective == ReportFormat.BOTH) {
      written.add(
          writeFile(
              folder,
              baseName,
              HTML_EXTENSION,
              SchemaValidationReportFormatter.formatHtml(result)));
    }
    return List.copyOf(written);
  }

  public static String writeMarkdown(
      String outputPath,
      String fileBaseName,
      SchemaImpactSimulationResult result,
      IVariables variables)
      throws HopException {
    List<String> paths = write(outputPath, fileBaseName, result, ReportFormat.MARKDOWN, variables);
    return paths.isEmpty() ? null : paths.getFirst();
  }

  public static String writeHtml(
      String outputPath,
      String fileBaseName,
      SchemaImpactSimulationResult result,
      IVariables variables)
      throws HopException {
    List<String> paths = write(outputPath, fileBaseName, result, ReportFormat.HTML, variables);
    return paths.isEmpty() ? null : paths.getFirst();
  }

  private static String writeFile(String folder, String baseName, String extension, String content)
      throws HopException {
    String path = folder;
    if (!path.endsWith("/") && !path.endsWith("\\")) {
      path += "/";
    }
    path += baseName + extension;
    String body = content != null ? content : "";

    // Prefer local NIO for plain filesystem paths (stable in unit tests and simple CI runners).
    if (isLocalFilesystemPath(path)) {
      try {
        Path local =
            path.toLowerCase(Locale.ROOT).startsWith("file:")
                ? Path.of(java.net.URI.create(path))
                : Path.of(path);
        if (local.getParent() != null) {
          Files.createDirectories(local.getParent());
        }
        Files.writeString(local, body, StandardCharsets.UTF_8);
        return local.toAbsolutePath().normalize().toString();
      } catch (Exception e) {
        throw new HopException(
            BaseMessages.getString(PKG, "SchemaValidationReportFileWriter.Error.Write", path), e);
      }
    }

    try {
      FileObject fileObject = HopVfs.getFileObject(path);
      FileObject parent = fileObject.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
      try (OutputStreamWriter writer =
          new OutputStreamWriter(
              fileObject.getContent().getOutputStream(), StandardCharsets.UTF_8)) {
        writer.write(body);
      }
      return fileObject.getName().getPath();
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "SchemaValidationReportFileWriter.Error.Write", path), e);
    }
  }

  static boolean isLocalFilesystemPath(String path) {
    if (Utils.isEmpty(path) || path.contains("${")) {
      return false;
    }
    String lower = path.toLowerCase(Locale.ROOT);
    if (lower.startsWith("s3:")
        || lower.startsWith("s3a:")
        || lower.startsWith("hdfs:")
        || lower.startsWith("azfs:")
        || lower.startsWith("gs:")
        || lower.startsWith("http:")
        || lower.startsWith("https:")
        || lower.startsWith("ftp:")
        || lower.startsWith("sftp:")) {
      return false;
    }
    if (lower.startsWith("file:")) {
      return true;
    }
    // Absolute or relative local path without a VFS scheme.
    return !path.contains("://");
  }

  static String resolveFolder(String outputPath, IVariables variables) {
    String resolved = variables != null ? variables.resolve(outputPath) : outputPath;
    if (Utils.isEmpty(resolved)) {
      return resolved;
    }
    // If a full file path with known extension is given, use its parent folder.
    String lower = resolved.toLowerCase(Locale.ROOT);
    if (lower.endsWith(MARKDOWN_EXTENSION) || lower.endsWith(HTML_EXTENSION)) {
      int slash = Math.max(resolved.lastIndexOf('/'), resolved.lastIndexOf('\\'));
      if (slash > 0) {
        return resolved.substring(0, slash);
      }
    }
    return resolved;
  }

  static String resolveBaseName(
      String fileBaseName, SchemaImpactSimulationResult result, IVariables variables) {
    String resolved = variables != null ? variables.resolve(fileBaseName) : fileBaseName;
    if (!Utils.isEmpty(resolved)) {
      String lower = resolved.toLowerCase(Locale.ROOT);
      if (lower.endsWith(MARKDOWN_EXTENSION)) {
        resolved = resolved.substring(0, resolved.length() - MARKDOWN_EXTENSION.length());
      } else if (lower.endsWith(HTML_EXTENSION)) {
        resolved = resolved.substring(0, resolved.length() - HTML_EXTENSION.length());
      }
      return sanitizeFileName(resolved);
    }
    String group =
        result != null && result.validationReport() != null
            ? result.validationReport().getGroupName()
            : "schema-validation";
    String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
    return sanitizeFileName(group + "-schema-validation-" + timestamp);
  }

  static String sanitizeFileName(String value) {
    if (value == null) {
      return "schema-validation";
    }
    return value.replaceAll("[^A-Za-z0-9._-]+", "-");
  }
}
