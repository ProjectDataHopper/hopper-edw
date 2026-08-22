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
package org.hopper.edw.datavault.architecture;

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.executionmap.ExecutionMapPathSupport;

/**
 * Project-relative path formatting for architecture graphs and Draw.io (and future) exports.
 *
 * <p>Stored execution-map paths often use {@code ${PROJECT_HOME}/models/….hdv}; absolute host paths
 * also appear on edge labels after model load. Exports should show portable relative forms such as
 * {@code models/retail-360.hdv}.
 */
public final class ArchitecturePathSupport {

  private static final String PROJECT_HOME_VAR = "${PROJECT_HOME}";
  private static final String PROJECT_HOME_PREFIX = PROJECT_HOME_VAR + "/";

  private ArchitecturePathSupport() {}

  /**
   * Convert a filesystem path (absolute, {@code ${PROJECT_HOME}/…}, or already relative) into a
   * project-relative display path without the {@code ${PROJECT_HOME}/} prefix.
   *
   * <p>Logical schemes ({@code dataset://}, {@code generated://}, {@code synthetic://}) and
   * non-path labels are returned unchanged.
   */
  public static String toProjectRelativePath(String path, IVariables variables) {
    if (Utils.isEmpty(path) || ExecutionMapPathSupport.isLogicalScheme(path)) {
      return path;
    }
    String trimmed = path.trim().replace('\\', '/');
    if (!looksLikeFilesystemPath(trimmed)) {
      return path;
    }

    if (trimmed.startsWith(PROJECT_HOME_PREFIX)) {
      return trimmed.substring(PROJECT_HOME_PREFIX.length());
    }
    if (trimmed.equals(PROJECT_HOME_VAR)) {
      return ".";
    }

    // Absolute or unresolved → portable ${PROJECT_HOME}/… when under project home
    String stored = ExecutionMapPathSupport.toStoredPath(trimmed, variables);
    if (!Utils.isEmpty(stored) && stored.startsWith(PROJECT_HOME_PREFIX)) {
      return stored.substring(PROJECT_HOME_PREFIX.length());
    }
    if (!Utils.isEmpty(stored) && stored.equals(PROJECT_HOME_VAR)) {
      return ".";
    }
    // Outside PROJECT_HOME (e.g. Hop install absolute path): use file basename for display
    if (isAbsoluteFilesystemPath(trimmed) || isAbsoluteFilesystemPath(stored)) {
      return fileBasename(!Utils.isEmpty(stored) ? stored : trimmed);
    }
    return !Utils.isEmpty(stored) ? stored : trimmed;
  }

  /** Absolute Unix/Windows path (not a logical scheme). */
  public static boolean isAbsoluteFilesystemPath(String path) {
    if (Utils.isEmpty(path)) {
      return false;
    }
    String n = path.trim().replace('\\', '/');
    if (n.startsWith("/")) {
      return true;
    }
    return n.length() >= 3
        && Character.isLetter(n.charAt(0))
        && n.charAt(1) == ':'
        && (n.charAt(2) == '/' || n.charAt(2) == '\\');
  }

  public static String fileBasename(String path) {
    if (Utils.isEmpty(path)) {
      return path;
    }
    String n = path.replace('\\', '/');
    int slash = n.lastIndexOf('/');
    if (slash >= 0 && slash < n.length() - 1) {
      return n.substring(slash + 1);
    }
    return n;
  }

  /**
   * True when the string looks like a filesystem path (absolute, project-variable, or
   * extension-bearing relative path) rather than a free-form label.
   */
  public static boolean looksLikeFilesystemPath(String value) {
    if (Utils.isEmpty(value)) {
      return false;
    }
    String v = value.trim();
    if (ExecutionMapPathSupport.isLogicalScheme(v)) {
      return false;
    }
    if (v.contains("${PROJECT_HOME}") || v.contains("${project_home}")) {
      return true;
    }
    String n = v.replace('\\', '/');
    if (n.startsWith("/") || n.startsWith("./") || n.startsWith("../")) {
      return true;
    }
    // Windows drive
    if (n.length() >= 3
        && Character.isLetter(n.charAt(0))
        && n.charAt(1) == ':'
        && (n.charAt(2) == '/' || n.charAt(2) == '\\')) {
      return true;
    }
    // Relative project paths with common Hop / vault extensions
    String lower = n.toLowerCase();
    return lower.endsWith(".hdv")
        || lower.endsWith(".hbv")
        || lower.endsWith(".hdm")
        || lower.endsWith(".hem")
        || lower.endsWith(".hwf")
        || lower.endsWith(".hpl")
        || lower.endsWith(".drawio")
        || lower.contains("/models/")
        || lower.contains("/workflows/")
        || lower.contains("/pipelines/");
  }

  /**
   * Rewrite path-like fields on the graph to project-relative display form for export. Does not
   * change node/edge ids (connectivity).
   */
  public static void portableizeGraph(ArchitectureGraph graph, IVariables variables) {
    if (graph == null) {
      return;
    }
    for (ArchitectureNode node : graph.getNodes()) {
      if (node == null) {
        continue;
      }
      if (!Utils.isEmpty(node.getPath())) {
        node.setPath(toProjectRelativePath(node.getPath(), variables));
      }
      if (looksLikeFilesystemPath(node.getName())) {
        node.setName(toProjectRelativePath(node.getName(), variables));
      }
      if (looksLikeFilesystemPath(node.getDescription())) {
        node.setDescription(toProjectRelativePath(node.getDescription(), variables));
      }
    }
    for (ArchitectureEdge edge : graph.getEdges()) {
      if (edge == null || Utils.isEmpty(edge.getLabel())) {
        continue;
      }
      if (looksLikeFilesystemPath(edge.getLabel())) {
        edge.setLabel(toProjectRelativePath(edge.getLabel(), variables));
      }
    }
  }
}
