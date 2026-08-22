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

import java.util.Locale;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.metadata.DvModelLoadSupport;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapArtifactSnapshot;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;

/**
 * Portable filesystem paths for {@code .hem} documents. Logical schemes ({@code dataset://}, {@code
 * generated://}, {@code synthetic://}) are left unchanged; real paths under {@code PROJECT_HOME}
 * become {@code ${PROJECT_HOME}/…}.
 */
public final class ExecutionMapPathSupport {

  private ExecutionMapPathSupport() {}

  public static boolean isLogicalScheme(String path) {
    if (Utils.isEmpty(path)) {
      return true;
    }
    String lower = path.trim().toLowerCase(Locale.ROOT);
    return lower.startsWith("dataset://")
        || lower.startsWith("generated://")
        || lower.startsWith("synthetic://");
  }

  /**
   * Converts a runtime absolute (or already variable) path into a portable stored form for the
   * {@code .hem} file.
   */
  public static String toStoredPath(String path, IVariables variables) {
    if (Utils.isEmpty(path) || isLogicalScheme(path)) {
      return path;
    }
    String trimmed = path.trim();
    if (trimmed.contains("${")) {
      return trimmed;
    }
    try {
      return DvModelLoadSupport.toStoredModelPath(trimmed, null, variables);
    } catch (HopException e) {
      return trimmed;
    }
  }

  /** Resolves variables and normalizes for open/file checks. */
  public static String toResolvedPath(String path, IVariables variables) {
    if (Utils.isEmpty(path) || variables == null) {
      return path;
    }
    if (isLogicalScheme(path)) {
      return path;
    }
    try {
      return HopVfs.normalize(variables.resolve(path));
    } catch (Exception e) {
      return variables.resolve(path);
    }
  }

  /**
   * Rewrites all filesystem path fields on the document to portable {@code ${PROJECT_HOME}/…}
   * values. Safe to call before every save.
   */
  public static void portableizeDocument(ExecutionMapDocument document, IVariables variables) {
    if (document == null) {
      return;
    }
    document.setRootArtifactPath(toStoredPath(document.getRootArtifactPath(), variables));
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node != null) {
        node.setPath(toStoredPath(node.getPath(), variables));
      }
    }
    for (ExecutionMapArtifactSnapshot snapshot : document.getSnapshotsOrEmpty()) {
      if (snapshot != null) {
        snapshot.setSourcePath(toStoredPath(snapshot.getSourcePath(), variables));
      }
    }
    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge != null && !Utils.isEmpty(edge.getLabel()) && !isLogicalScheme(edge.getLabel())) {
        String label = edge.getLabel().trim();
        // Only rewrite labels that look like filesystem paths (not free-text hop descriptions).
        if (label.contains("/")
            || label.contains("\\")
            || label.contains("${PROJECT_HOME}")
            || label.startsWith("${")) {
          edge.setLabel(toStoredPath(label, variables));
        }
      }
    }
  }
}
