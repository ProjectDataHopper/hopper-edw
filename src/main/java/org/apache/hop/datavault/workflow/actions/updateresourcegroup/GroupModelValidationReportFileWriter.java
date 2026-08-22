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
package org.apache.hop.datavault.workflow.actions.updateresourcegroup;

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
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;

/** Writes group model-validation Markdown/HTML reports via Hop VFS. */
public final class GroupModelValidationReportFileWriter {

  public static final String MARKDOWN_EXTENSION = ".md";
  public static final String HTML_EXTENSION = ".html";

  public enum ReportFormat {
    MARKDOWN,
    HTML,
    BOTH
  }

  private static final Class<?> PKG = ActionUpdateResourceDefinitionGroup.class;

  private GroupModelValidationReportFileWriter() {}

  public static List<String> write(
      String outputPath,
      String fileBaseName,
      GroupModelValidationReport report,
      ReportFormat format,
      IVariables variables)
      throws HopException {
    if (report == null || Utils.isEmpty(outputPath)) {
      return List.of();
    }
    ReportFormat effective = format != null ? format : ReportFormat.MARKDOWN;
    String folder = resolveFolder(outputPath, variables);
    String baseName = resolveBaseName(fileBaseName, report, variables);
    List<String> written = new ArrayList<>();
    if (effective == ReportFormat.MARKDOWN || effective == ReportFormat.BOTH) {
      written.add(
          writeFile(
              folder,
              baseName,
              MARKDOWN_EXTENSION,
              GroupModelValidationReportFormatter.formatMarkdown(report)));
    }
    if (effective == ReportFormat.HTML || effective == ReportFormat.BOTH) {
      written.add(
          writeFile(
              folder,
              baseName,
              HTML_EXTENSION,
              GroupModelValidationReportFormatter.formatHtml(report)));
    }
    return List.copyOf(written);
  }

  static String resolveFolder(String outputPath, IVariables variables) {
    String path = variables != null ? variables.resolve(Const.NVL(outputPath, "")) : outputPath;
    path = Const.NVL(path, "").trim();
    if (path.endsWith(".md") || path.endsWith(".html") || path.endsWith(".htm")) {
      int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
      if (slash > 0) {
        return path.substring(0, slash);
      }
    }
    return path;
  }

  static String resolveBaseName(
      String fileBaseName, GroupModelValidationReport report, IVariables variables) {
    String base = variables != null ? variables.resolve(Const.NVL(fileBaseName, "")) : fileBaseName;
    base = Const.NVL(base, "").trim();
    if (!Utils.isEmpty(base)) {
      if (base.endsWith(".md") || base.endsWith(".html") || base.endsWith(".htm")) {
        int dot = base.lastIndexOf('.');
        base = base.substring(0, dot);
      }
      return base;
    }
    String group =
        report != null && !Utils.isEmpty(report.groupName())
            ? report.groupName().replaceAll("[^a-zA-Z0-9._-]+", "-")
            : "group";
    String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
    return group + "-model-validation-" + ts;
  }

  private static String writeFile(String folder, String baseName, String extension, String content)
      throws HopException {
    String path = folder;
    if (!path.endsWith("/") && !path.endsWith("\\")) {
      path += "/";
    }
    path += baseName + extension;
    String body = content != null ? content : "";

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
            BaseMessages.getString(
                PKG, "ActionUpdateResourceDefinitionGroup.Error.ValidationReportWrite", path),
            e);
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
          BaseMessages.getString(
              PKG, "ActionUpdateResourceDefinitionGroup.Error.ValidationReportWrite", path),
          e);
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
    return true;
  }
}
