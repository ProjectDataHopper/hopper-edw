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
package org.apache.hop.datavault.metadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Loads Data Vault model files for cross-model table reference resolution. */
public final class DvModelLoadSupport {

  private static final Class<?> PKG = DvModelLoadSupport.class;
  private static final String VARIABLE_PROJECT_HOME = "PROJECT_HOME";
  private static final Map<String, DataVaultModel> MODEL_CACHE = new ConcurrentHashMap<>();

  private DvModelLoadSupport() {}

  public static void clearCache() {
    MODEL_CACHE.clear();
  }

  public static void invalidateCachedModel(
      String modelPath, String referringModelFilename, IVariables variables) throws HopException {
    MODEL_CACHE.remove(resolveModelPath(modelPath, referringModelFilename, variables));
  }

  public static void invalidateCachedModelByResolvedPath(String resolvedPath) {
    if (!Utils.isEmpty(resolvedPath)) {
      MODEL_CACHE.remove(resolvedPath);
    }
  }

  public static DataVaultModel loadDataVaultModel(
      String modelPath,
      String referringModelFilename,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String resolvedPath = resolveModelPath(modelPath, referringModelFilename, variables);
    DataVaultModel cached = MODEL_CACHE.get(resolvedPath);
    if (cached != null) {
      return cached;
    }
    DataVaultModel loaded = loadDataVaultModelUncached(resolvedPath, metadataProvider);
    MODEL_CACHE.put(resolvedPath, loaded);
    return loaded;
  }

  public static String resolveModelPath(
      String modelPath, String referringModelFilename, IVariables variables) throws HopException {
    if (Utils.isEmpty(modelPath)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvModelLoadSupport.Error.MissingModelPath"));
    }
    String resolved = variables != null ? variables.resolve(modelPath) : modelPath;
    if (!isAbsolutePath(resolved) && !Utils.isEmpty(referringModelFilename)) {
      String referring =
          variables != null ? variables.resolve(referringModelFilename) : referringModelFilename;
      Path parent = Path.of(referring).getParent();
      if (parent != null) {
        resolved = parent.resolve(resolved).normalize().toString();
      }
    }
    try {
      String normalized = HopVfs.normalize(resolved);
      // Foreign absolute paths (checked in from another host) → re-home under PROJECT_HOME.
      if (!localFileExists(normalized)) {
        String remapped = remapMissingAbsolutePath(normalized, variables);
        if (!Utils.isEmpty(remapped)) {
          return HopVfs.normalize(remapped);
        }
      }
      return normalized;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvModelLoadSupport.Error.InvalidModelPath", modelPath), e);
    }
  }

  /**
   * When a stored absolute path no longer exists (e.g. another developer's {@code /home/...}), try
   * the same basename / {@code models/…} tail under the current {@code PROJECT_HOME}.
   */
  static String remapMissingAbsolutePath(String absolutePath, IVariables variables) {
    if (variables == null || Utils.isEmpty(absolutePath) || !isAbsolutePath(absolutePath)) {
      return null;
    }
    String projectHome = variables.resolve("${" + VARIABLE_PROJECT_HOME + "}");
    if (Utils.isEmpty(projectHome) || projectHome.contains("${")) {
      return null;
    }
    try {
      Path home = Path.of(HopVfs.normalize(projectHome)).normalize();
      Path original = Path.of(absolutePath).normalize();
      String basename = original.getFileName() != null ? original.getFileName().toString() : null;
      if (!Utils.isEmpty(basename)) {
        Path underModels = home.resolve("models").resolve(basename);
        if (Files.isRegularFile(underModels)) {
          return underModels.toString();
        }
        Path underHome = home.resolve(basename);
        if (Files.isRegularFile(underHome)) {
          return underHome.toString();
        }
      }
      String slashPath = absolutePath.replace('\\', '/');
      int modelsIdx = slashPath.lastIndexOf("/models/");
      if (modelsIdx >= 0) {
        String tail = slashPath.substring(modelsIdx + 1); // models/...
        Path underHomeModels = home.resolve(tail);
        if (Files.isRegularFile(underHomeModels)) {
          return underHomeModels.toString();
        }
      }
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

  private static boolean localFileExists(String path) {
    if (Utils.isEmpty(path)) {
      return false;
    }
    try {
      Path nio = Path.of(path);
      if (Files.exists(nio)) {
        return true;
      }
    } catch (Exception ignored) {
      // fall through to VFS
    }
    try {
      return HopVfs.getFileObject(path).exists();
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Converts a browsed or absolute path into a portable stored value when the Hop Projects
   * file-open extension point did not already emit a variable-based path.
   */
  public static String toStoredModelPath(
      String selectedPath, String referringModelFilename, IVariables variables)
      throws HopException {
    if (Utils.isEmpty(selectedPath)) {
      return selectedPath;
    }
    String trimmed = selectedPath.trim();
    if (trimmed.contains("${")) {
      return trimmed;
    }

    String normalized;
    try {
      String resolved = variables != null ? variables.resolve(trimmed) : trimmed;
      normalized = HopVfs.normalize(resolved);
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvModelLoadSupport.Error.InvalidModelPath", selectedPath),
          e);
    }

    String projectHomeRelative = relativizeToProjectHome(normalized, variables);
    if (!Utils.isEmpty(projectHomeRelative)) {
      return projectHomeRelative;
    }

    String modelRelative =
        relativizeToReferringModel(normalized, referringModelFilename, variables);
    if (!Utils.isEmpty(modelRelative)) {
      return modelRelative;
    }

    return normalized;
  }

  public static List<String> listTableNames(DataVaultModel model, DvTableType tableType) {
    if (model == null || tableType == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (IDvTable table : model.getTables()) {
      if (table == null
          || Utils.isEmpty(table.getName())
          || table.getTableType() != tableType
          || table instanceof DvLinkedTable) {
        continue;
      }
      names.add(table.getName());
    }
    Collections.sort(names);
    return names;
  }

  private static DataVaultModel loadDataVaultModelUncached(
      String resolvedPath, IHopMetadataProvider metadataProvider) throws HopException {
    try {
      Document document = XmlHandler.loadXmlFile(resolvedPath);
      Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      DataVaultModel model = new DataVaultModel();
      XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, metadataProvider);
      model.setFilename(resolvedPath);
      model.clearChanged();
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvModelLoadSupport.Error.LoadFailed", resolvedPath), e);
    }
  }

  private static String relativizeToProjectHome(String normalizedPath, IVariables variables) {
    if (variables == null || Utils.isEmpty(normalizedPath)) {
      return null;
    }
    String projectHome = variables.resolve("${" + VARIABLE_PROJECT_HOME + "}");
    if (Utils.isEmpty(projectHome)) {
      return null;
    }
    try {
      String resolvedHome = HopVfs.normalize(projectHome);
      Path homePath = Path.of(resolvedHome).normalize();
      Path selectedPath = Path.of(normalizedPath).normalize();
      if (!selectedPath.startsWith(homePath)) {
        return null;
      }
      String relative = homePath.relativize(selectedPath).toString().replace('\\', '/');
      return "${" + VARIABLE_PROJECT_HOME + "}/" + relative;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String relativizeToReferringModel(
      String normalizedPath, String referringModelFilename, IVariables variables) {
    if (Utils.isEmpty(normalizedPath) || Utils.isEmpty(referringModelFilename)) {
      return null;
    }
    try {
      String referring =
          variables != null ? variables.resolve(referringModelFilename) : referringModelFilename;
      Path parent = Path.of(referring).getParent();
      if (parent == null) {
        return null;
      }
      Path selectedPath = Path.of(normalizedPath).normalize();
      Path normalizedParent = parent.normalize();
      if (!selectedPath.startsWith(normalizedParent)) {
        return null;
      }
      return normalizedParent.relativize(selectedPath).toString().replace('\\', '/');
    } catch (Exception ignored) {
      return null;
    }
  }

  private static boolean isAbsolutePath(String path) {
    if (Utils.isEmpty(path)) {
      return false;
    }
    return path.startsWith("/") || path.matches("^[A-Za-z]:[/\\\\].*") || path.contains("://");
  }
}
