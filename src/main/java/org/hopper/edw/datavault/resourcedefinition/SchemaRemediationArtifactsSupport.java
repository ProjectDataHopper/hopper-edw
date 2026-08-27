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
package org.hopper.edw.datavault.resourcedefinition;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.config.DataVaultConfig;
import org.hopper.edw.datavault.config.DataVaultConfigSingleton;

/**
 * Resolves the schema-remediation root folder from plugin settings and writes named remediation
 * packages (sub-folder + HTML/Markdown report).
 */
public final class SchemaRemediationArtifactsSupport {

  private static final Class<?> PKG = SchemaRemediationArtifactsSupport.class;

  public record RemediationPackage(
      String remediationName,
      String folder,
      String reportMarkdownFilename,
      String reportHtmlFilename,
      String workflowFilename,
      String sqlFilename) {}

  private SchemaRemediationArtifactsSupport() {}

  public static String configuredRootFolder(IVariables variables) {
    String configured = DataVaultConfigSingleton.getConfig().getSchemaRemediationFolderOrDefault();
    if (Utils.isEmpty(configured)) {
      configured = DataVaultConfig.DEFAULT_SCHEMA_REMEDIATION_FOLDER;
    }
    String resolved = variables != null ? variables.resolve(configured) : configured;
    if (Utils.isEmpty(resolved)) {
      resolved = configured;
    }
    try {
      return HopVfs.normalize(resolved);
    } catch (Exception e) {
      return resolved.replace('\\', '/');
    }
  }

  public static String sanitizeRemediationName(String name) {
    if (Utils.isEmpty(name)) {
      return "remediation";
    }
    String cleaned =
        name.trim()
            .replaceAll("[^A-Za-z0-9._-]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
    if (cleaned.length() > 64) {
      cleaned = cleaned.substring(0, 64);
    }
    return Utils.isEmpty(cleaned) ? "remediation" : cleaned;
  }

  public static String packageFolder(IVariables variables, String remediationName)
      throws HopException {
    String root = configuredRootFolder(variables);
    String sub = sanitizeRemediationName(remediationName);
    String folder = root.endsWith("/") || root.endsWith("\\") ? root + sub : root + "/" + sub;
    ensureFolder(folder, variables);
    return folder;
  }

  public static RemediationPackage writeReports(
      String folder,
      String remediationName,
      String title,
      List<String> reportLines,
      String workflowFilename,
      String sqlFilename,
      IVariables variables)
      throws HopException {
    if (Utils.isEmpty(folder)) {
      throw new HopException(
          BaseMessages.getString(PKG, "SchemaRemediationArtifactsSupport.Error.MissingFolder"));
    }
    ensureFolder(folder, variables);
    String base = sanitizeRemediationName(remediationName);
    String mdPath = appendPath(folder, base + "-report.md");
    String htmlPath = appendPath(folder, base + "-report.html");
    String markdown =
        formatMarkdown(title, remediationName, reportLines, workflowFilename, sqlFilename);
    String html = formatHtml(title, remediationName, reportLines, workflowFilename, sqlFilename);
    writeText(mdPath, markdown, variables);
    writeText(htmlPath, html, variables);
    return new RemediationPackage(
        remediationName, folder, mdPath, htmlPath, workflowFilename, sqlFilename);
  }

  static String formatMarkdown(
      String title,
      String remediationName,
      List<String> reportLines,
      String workflowFilename,
      String sqlFilename) {
    StringBuilder b = new StringBuilder();
    b.append("# ").append(Const.NVL(title, "Schema remediation")).append("\n\n");
    b.append("- **Remediation name:** ").append(Const.NVL(remediationName, "")).append("\n");
    if (!Utils.isEmpty(workflowFilename)) {
      b.append("- **Workflow:** `").append(workflowFilename).append("`\n");
    }
    if (!Utils.isEmpty(sqlFilename)) {
      b.append("- **SQL script:** `").append(sqlFilename).append("`\n");
    }
    b.append("\n## What changed\n\n");
    if (reportLines == null || reportLines.isEmpty()) {
      b.append("(no line items)\n");
    } else {
      for (String line : reportLines) {
        b.append("- ").append(line).append("\n");
      }
    }
    return b.toString();
  }

  static String formatHtml(
      String title,
      String remediationName,
      List<String> reportLines,
      String workflowFilename,
      String sqlFilename) {
    StringBuilder b = new StringBuilder();
    b.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>");
    b.append("<title>").append(escape(title)).append("</title></head><body>");
    b.append("<h1>").append(escape(title)).append("</h1>");
    b.append("<ul>");
    b.append("<li><b>Remediation name:</b> ").append(escape(remediationName)).append("</li>");
    if (!Utils.isEmpty(workflowFilename)) {
      b.append("<li><b>Workflow:</b> <code>")
          .append(escape(workflowFilename))
          .append("</code></li>");
    }
    if (!Utils.isEmpty(sqlFilename)) {
      b.append("<li><b>SQL script:</b> <code>").append(escape(sqlFilename)).append("</code></li>");
    }
    b.append("</ul><h2>What changed</h2><ul>");
    if (reportLines == null || reportLines.isEmpty()) {
      b.append("<li>(no line items)</li>");
    } else {
      for (String line : reportLines) {
        b.append("<li>").append(escape(line)).append("</li>");
      }
    }
    b.append("</ul></body></html>");
    return b.toString();
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private static void ensureFolder(String folder, IVariables variables) throws HopException {
    try {
      FileObject dir = HopVfs.getFileObject(folder, variables);
      if (!dir.exists()) {
        dir.createFolder();
      }
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "SchemaRemediationArtifactsSupport.Error.CreateFolder", folder),
          e);
    }
  }

  private static void writeText(String filename, String content, IVariables variables)
      throws HopException {
    try {
      FileObject file = HopVfs.getFileObject(filename, variables);
      FileObject parent = file.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
      try (OutputStreamWriter writer =
          new OutputStreamWriter(HopVfs.getOutputStream(file, false), StandardCharsets.UTF_8)) {
        writer.write(content);
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "SchemaRemediationArtifactsSupport.Error.WriteFile", filename),
          e);
    }
  }

  private static String appendPath(String folder, String filename) {
    if (folder.endsWith("/") || folder.endsWith("\\")) {
      return folder + filename;
    }
    return folder + "/" + filename;
  }
}
