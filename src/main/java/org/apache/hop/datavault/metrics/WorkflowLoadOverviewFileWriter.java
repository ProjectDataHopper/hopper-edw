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
package org.apache.hop.datavault.metrics;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Stream;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;

/** Writes workflow load overview report files. */
public final class WorkflowLoadOverviewFileWriter {

  public static final String MARKDOWN_EXTENSION = ".md";
  public static final String HTML_EXTENSION = ".html";

  private WorkflowLoadOverviewFileWriter() {}

  public static String writeMarkdown(
      String outputFolder,
      String fileBaseName,
      WorkflowLoadOverviewReport report,
      boolean includePipelineDetail,
      boolean includeInsights,
      IVariables variables)
      throws HopException {
    return writeFile(
        outputFolder,
        fileBaseName,
        report,
        MARKDOWN_EXTENSION,
        WorkflowLoadOverviewReportFormatter.formatMarkdown(
            report, includePipelineDetail, includeInsights),
        variables);
  }

  public static String writeHtml(
      String outputFolder,
      String fileBaseName,
      WorkflowLoadOverviewReport report,
      boolean includePipelineDetail,
      boolean includeInsights,
      IVariables variables)
      throws HopException {
    return writeFile(
        outputFolder,
        fileBaseName,
        report,
        HTML_EXTENSION,
        WorkflowLoadOverviewReportFormatter.formatHtml(
            report, includePipelineDetail, includeInsights),
        variables);
  }

  private static String writeFile(
      String outputFolder,
      String fileBaseName,
      WorkflowLoadOverviewReport report,
      String extension,
      String content,
      IVariables variables)
      throws HopException {
    if (Utils.isEmpty(outputFolder) || report == null) {
      return null;
    }
    String resolvedFolder = variables != null ? variables.resolve(outputFolder) : outputFolder;
    String baseName = resolveBaseName(fileBaseName, report, variables);
    String path = resolvedFolder;
    if (!path.endsWith("/")) {
      path += "/";
    }
    path += baseName + extension;
    try {
      FileObject fileObject = HopVfs.getFileObject(path);
      FileObject parent = fileObject.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
      try (OutputStreamWriter writer =
          new OutputStreamWriter(
              fileObject.getContent().getOutputStream(), StandardCharsets.UTF_8)) {
        writer.write(content);
      }
      return fileObject.getName().getPath();
    } catch (Exception e) {
      throw new HopException("Unable to write workflow load overview report to " + path, e);
    }
  }

  static String resolveBaseName(
      String fileBaseName, WorkflowLoadOverviewReport report, IVariables variables) {
    String resolved = variables != null ? variables.resolve(fileBaseName) : fileBaseName;
    if (!Utils.isEmpty(resolved)) {
      return sanitizeFileName(resolved);
    }
    String workflowName =
        report.getRootWorkflowName() != null
            ? report.getRootWorkflowName()
            : "workflow-load-overview";
    String executionPrefix =
        report.getWorkflowExecutionId() != null && report.getWorkflowExecutionId().length() >= 8
            ? report.getWorkflowExecutionId().substring(0, 8)
            : "run";
    String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
    return sanitizeFileName(workflowName + "-" + executionPrefix + "-" + timestamp);
  }

  static String sanitizeFileName(String value) {
    return value.replaceAll("[^A-Za-z0-9._-]+", "-");
  }

  public static String resolveConfiguredReportPath(
      String outputFolder, String fileBaseName, String extension, IVariables variables) {
    if (Utils.isEmpty(outputFolder) || Utils.isEmpty(extension)) {
      return null;
    }
    String resolvedFolder = variables != null ? variables.resolve(outputFolder) : outputFolder;
    String resolvedBase = variables != null ? variables.resolve(fileBaseName) : fileBaseName;
    if (Utils.isEmpty(resolvedBase)) {
      return null;
    }
    return buildReportPath(resolvedFolder, sanitizeFileName(resolvedBase), extension);
  }

  public static String resolveExistingReportPath(
      String outputFolder,
      String fileBaseName,
      String extension,
      String workflowName,
      IVariables variables)
      throws HopException {
    if (Utils.isEmpty(outputFolder) || Utils.isEmpty(extension)) {
      return null;
    }
    String configured =
        resolveConfiguredReportPath(outputFolder, fileBaseName, extension, variables);
    if (!Utils.isEmpty(configured) && fileExists(configured)) {
      return configured;
    }
    String resolvedFolder = variables != null ? variables.resolve(outputFolder) : outputFolder;
    return findLatestReportFile(resolvedFolder, extension, workflowName);
  }

  static String buildReportPath(String folder, String baseName, String extension) {
    String path = folder;
    if (!path.endsWith("/")) {
      path += "/";
    }
    return path + baseName + extension;
  }

  static String findLatestReportFile(String folder, String extension, String workflowName)
      throws HopException {
    Path folderPath = toLocalPath(folder);
    if (folderPath == null || !Files.isDirectory(folderPath)) {
      return null;
    }
    String namePrefix = null;
    if (!Utils.isEmpty(workflowName)) {
      namePrefix = sanitizeFileName(workflowName) + "-";
    }
    String finalNamePrefix = namePrefix;
    try (Stream<Path> paths = Files.list(folderPath)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(extension))
          .filter(
              path ->
                  finalNamePrefix == null
                      || path.getFileName().toString().startsWith(finalNamePrefix))
          .max(Comparator.comparingLong(WorkflowLoadOverviewFileWriter::lastModifiedMillis))
          .map(Path::toString)
          .orElse(null);
    } catch (IOException e) {
      throw new HopException("Unable to list workflow load overview reports in " + folder, e);
    }
  }

  private static boolean fileExists(String path) {
    Path localPath = toLocalPath(path);
    return localPath != null && Files.isRegularFile(localPath);
  }

  static Path toLocalPath(String path) {
    if (Utils.isEmpty(path) || path.contains("${")) {
      return null;
    }
    try {
      if (path.startsWith("file:")) {
        return Path.of(URI.create(path));
      }
      return Path.of(path);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static long lastModifiedMillis(Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (IOException e) {
      return -1L;
    }
  }
}
