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
package org.apache.hop.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileType;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvModelLoadSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Resolves the first argument of {@code {{ ref('model', 'object') }}} to a loadable {@code .hdv} /
 * {@code .hbv} path relative to the current Business Vault model (or project home).
 */
public final class BvSqlModelPathSupport {

  public enum ModelFileKind {
    HDV,
    HBV,
    UNKNOWN
  }

  /**
   * @param authoringArg model argument as written in SQL
   * @param loadPath absolute/normalized path used to open the file
   * @param storedPath portable path for persistence (e.g. {@code ${PROJECT_HOME}/…})
   * @param kind file kind inferred from extension
   */
  public record ResolvedModelPath(
      String authoringArg, String loadPath, String storedPath, ModelFileKind kind) {}

  private static final ThreadLocal<Map<String, BusinessVaultModel>> HBV_CACHE =
      ThreadLocal.withInitial(HashMap::new);

  private BvSqlModelPathSupport() {}

  public static void clearModelCache() {
    HBV_CACHE.remove();
  }

  /**
   * Builds candidate paths and returns the first existing model file. Throws if none exist.
   *
   * @param modelArg first argument of {@code ref()}
   * @param referringBvFilename current {@code .hbv} path (for relative resolution)
   * @param variables Hop variables ({@code PROJECT_HOME}, etc.)
   */
  public static ResolvedModelPath resolveExistingModelPath(
      String modelArg, String referringBvFilename, IVariables variables) throws HopException {
    if (Utils.isEmpty(modelArg)) {
      throw new HopException("Model path argument is required");
    }
    String authoring = modelArg.trim();
    List<String> candidates = buildCandidatePaths(authoring, referringBvFilename, variables);
    for (String candidate : candidates) {
      if (Utils.isEmpty(candidate)) {
        continue;
      }
      try {
        String loadPath =
            DvModelLoadSupport.resolveModelPath(candidate, referringBvFilename, variables);
        if (fileExists(loadPath)) {
          String stored =
              DvModelLoadSupport.toStoredModelPath(loadPath, referringBvFilename, variables);
          return new ResolvedModelPath(authoring, loadPath, stored, kindFromPath(loadPath));
        }
      } catch (HopException ignored) {
        // try next candidate
      }
    }
    throw new HopException(
        "Unable to resolve model path '"
            + authoring
            + "' relative to Business Vault model"
            + (Utils.isEmpty(referringBvFilename) ? "" : " '" + referringBvFilename + "'"));
  }

  /** True when the first ref arg should be treated as a filesystem path (not only basename). */
  public static boolean looksLikeFilesystemPath(String modelArg) {
    if (Utils.isEmpty(modelArg)) {
      return false;
    }
    String s = modelArg.trim();
    if (s.contains("${") || s.contains("/") || s.contains("\\")) {
      return true;
    }
    if (s.startsWith("..") || s.startsWith("./") || s.startsWith(".\\")) {
      return true;
    }
    String lower = s.toLowerCase(Locale.ROOT);
    return lower.endsWith(".hdv") || lower.endsWith(".hbv");
  }

  public static DataVaultModel loadDataVaultModel(
      ResolvedModelPath resolved, IHopMetadataProvider metadataProvider) throws HopException {
    if (resolved == null || resolved.kind() == ModelFileKind.HBV) {
      throw new HopException("Resolved path is not a Data Vault model: " + resolved);
    }
    return DvModelLoadSupport.loadDataVaultModel(resolved.loadPath(), null, null, metadataProvider);
  }

  /**
   * Loads a DV model from a path that may still need variable/relative resolution (convenience for
   * already-stored portable paths).
   */
  public static DataVaultModel loadDataVaultModel(
      String modelPath,
      String referringBvFilename,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return DvModelLoadSupport.loadDataVaultModel(
        modelPath, referringBvFilename, variables, metadataProvider);
  }

  public static BusinessVaultModel loadBusinessVaultModel(
      ResolvedModelPath resolved, IHopMetadataProvider metadataProvider) throws HopException {
    if (resolved == null) {
      throw new HopException("Resolved model path is required");
    }
    return loadBusinessVaultModelUncached(resolved.loadPath(), metadataProvider);
  }

  public static BusinessVaultModel loadBusinessVaultModelCached(
      String resolvedPath, IHopMetadataProvider metadataProvider) throws HopException {
    if (Utils.isEmpty(resolvedPath)) {
      throw new HopException("Resolved model path is required");
    }
    Map<String, BusinessVaultModel> cache = HBV_CACHE.get();
    if (cache.containsKey(resolvedPath)) {
      return cache.get(resolvedPath);
    }
    BusinessVaultModel model = loadBusinessVaultModelUncached(resolvedPath, metadataProvider);
    cache.put(resolvedPath, model);
    return model;
  }

  /**
   * {@code .hbv} files in the referring model's directory and, when set, {@code
   * ${PROJECT_HOME}/models}.
   */
  public static List<String> listNearbyHbvFiles(String referringBvFilename, IVariables variables) {
    Set<String> paths = new LinkedHashSet<>();
    addHbvFilesInParent(paths, referringBvFilename);
    if (variables != null) {
      String projectHome = variables.getVariable("PROJECT_HOME");
      if (!Utils.isEmpty(projectHome) && !projectHome.contains("${")) {
        String models = projectHome.replace('\\', '/');
        if (models.endsWith("/")) {
          models = models.substring(0, models.length() - 1);
        }
        addHbvFilesInDirectory(paths, models + "/models");
      }
    }
    if (!Utils.isEmpty(referringBvFilename)) {
      String self = referringBvFilename.replace('\\', '/');
      paths.removeIf(path -> path != null && sameFile(path, self));
    }
    return new ArrayList<>(paths);
  }

  private static void addHbvFilesInDirectory(Set<String> paths, String directory) {
    if (Utils.isEmpty(directory)) {
      return;
    }
    try {
      FileObject folder = HopVfs.getFileObject(directory);
      if (folder == null || !folder.exists() || folder.getType() != FileType.FOLDER) {
        return;
      }
      collectHbvChildren(paths, folder);
    } catch (Exception ignored) {
      // directory missing or not listable
    }
  }

  private static void addHbvFilesInParent(Set<String> paths, String fileInFolder) {
    if (Utils.isEmpty(fileInFolder)) {
      return;
    }
    try {
      FileObject file = HopVfs.getFileObject(fileInFolder);
      FileObject folder =
          file.exists() && file.getType() == FileType.FOLDER ? file : file.getParent();
      if (folder == null || !folder.exists() || folder.getType() != FileType.FOLDER) {
        return;
      }
      collectHbvChildren(paths, folder);
    } catch (Exception ignored) {
      // directory missing or not listable
    }
  }

  private static void collectHbvChildren(Set<String> paths, FileObject folder) throws Exception {
    FileObject[] children = folder.getChildren();
    if (children == null) {
      return;
    }
    for (FileObject child : children) {
      if (child == null || child.getName() == null) {
        continue;
      }
      String base = child.getName().getBaseName();
      if (base != null && base.toLowerCase(Locale.ROOT).endsWith(".hbv")) {
        paths.add(HopVfs.getFilename(child));
      }
    }
  }

  private static boolean sameFile(String left, String right) {
    if (left == null || right == null) {
      return false;
    }
    String a = left.replace('\\', '/');
    String b = right.replace('\\', '/');
    return a.equalsIgnoreCase(b);
  }

  public static BusinessVaultModel loadBusinessVaultModelUncached(
      String resolvedPath, IHopMetadataProvider metadataProvider) throws HopException {
    try {
      Document document = XmlHandler.loadXmlFile(resolvedPath);
      Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      BusinessVaultModel model = new BusinessVaultModel();
      XmlMetadataUtil.deSerializeFromXml(
          rootNode, BusinessVaultModel.class, model, metadataProvider);
      model.setFilename(resolvedPath);
      model.clearChanged();
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to load Business Vault model '" + resolvedPath + "'", e);
    }
  }

  static List<String> buildCandidatePaths(
      String authoringArg, String referringBvFilename, IVariables variables) {
    Set<String> candidates = new LinkedHashSet<>();
    String raw = variables != null ? variables.resolve(authoringArg.trim()) : authoringArg.trim();
    if (Utils.isEmpty(raw)) {
      return List.of();
    }

    addWithExtensions(candidates, raw);

    // Basename / short form: also try under PROJECT_HOME.
    if (!looksLikeFilesystemPath(raw) || !raw.contains("/") && !raw.contains("\\")) {
      if (variables != null) {
        String projectHome = variables.resolve("${PROJECT_HOME}");
        if (!Utils.isEmpty(projectHome) && !projectHome.contains("${")) {
          String base = projectHome.replace('\\', '/');
          if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
          }
          addWithExtensions(candidates, base + "/" + stripLeadingDotSlash(raw));
          // Common layout: models/<name>
          addWithExtensions(candidates, base + "/models/" + stripLeadingDotSlash(raw));
        }
      }
    }

    // Explicit portable prefix if authoring already relative.
    if (!raw.contains("${")) {
      addWithExtensions(candidates, "${PROJECT_HOME}/" + stripLeadingDotSlash(raw));
      addWithExtensions(candidates, "${PROJECT_HOME}/models/" + stripLeadingDotSlash(raw));
    }

    return new ArrayList<>(candidates);
  }

  private static void addWithExtensions(Set<String> candidates, String path) {
    if (Utils.isEmpty(path)) {
      return;
    }
    candidates.add(path);
    String lower = path.toLowerCase(Locale.ROOT);
    if (!lower.endsWith(".hdv") && !lower.endsWith(".hbv")) {
      candidates.add(path + ".hdv");
      candidates.add(path + ".hbv");
    }
  }

  private static String stripLeadingDotSlash(String path) {
    String p = path.replace('\\', '/');
    while (p.startsWith("./")) {
      p = p.substring(2);
    }
    return p;
  }

  static ModelFileKind kindFromPath(String path) {
    if (Utils.isEmpty(path)) {
      return ModelFileKind.UNKNOWN;
    }
    String lower = path.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".hdv")) {
      return ModelFileKind.HDV;
    }
    if (lower.endsWith(".hbv")) {
      return ModelFileKind.HBV;
    }
    return ModelFileKind.UNKNOWN;
  }

  static boolean fileExists(String path) {
    if (Utils.isEmpty(path)) {
      return false;
    }
    // Prefer NIO for local filesystem paths (stable in unit tests; avoids VFS classloader issues).
    try {
      java.nio.file.Path nio = java.nio.file.Path.of(path);
      if (java.nio.file.Files.exists(nio)) {
        return true;
      }
    } catch (Exception ignored) {
      // fall through to VFS for non-local schemes
    }
    try {
      return HopVfs.getFileObject(path).exists();
    } catch (Exception e) {
      return false;
    }
  }
}
