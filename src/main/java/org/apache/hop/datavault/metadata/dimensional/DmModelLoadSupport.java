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
package org.apache.hop.datavault.metadata.dimensional;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Loads dimensional model files for cross-model dimension alias resolution. */
public final class DmModelLoadSupport {

  private static final Class<?> PKG = DmModelLoadSupport.class;
  private static final String VARIABLE_PROJECT_HOME = "PROJECT_HOME";
  private static final Map<String, DimensionalModel> MODEL_CACHE = new ConcurrentHashMap<>();

  private DmModelLoadSupport() {}

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

  public static DimensionalModel loadDimensionalModel(
      String modelPath,
      String referringModelFilename,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String resolvedPath = resolveModelPath(modelPath, referringModelFilename, variables);
    DimensionalModel cached = MODEL_CACHE.get(resolvedPath);
    if (cached != null) {
      return cached;
    }
    DimensionalModel loaded =
        loadDimensionalModelUncached(resolvedPath, variables, metadataProvider);
    MODEL_CACHE.put(resolvedPath, loaded);
    return loaded;
  }

  public static String resolveModelPath(
      String modelPath, String referringModelFilename, IVariables variables) throws HopException {
    if (Utils.isEmpty(modelPath)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmModelLoadSupport.Error.MissingModelPath"));
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
      return HopVfs.normalize(resolved);
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmModelLoadSupport.Error.InvalidModelPath", modelPath), e);
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
          BaseMessages.getString(PKG, "DmModelLoadSupport.Error.InvalidModelPath", selectedPath),
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

  public static String[] listBaseDimensionNames(DimensionalModel model) {
    if (model == null) {
      return new String[0];
    }
    return model.getTables().stream()
        .filter(table -> table instanceof DmDimension)
        .map(IDmTable::getName)
        .filter(name -> !Utils.isEmpty(name))
        .sorted()
        .toArray(String[]::new);
  }

  private static DimensionalModel loadDimensionalModelUncached(
      String resolvedPath, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    try {
      Document document = XmlHandler.loadXmlFile(resolvedPath);
      Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      DimensionalModel model = new DimensionalModel();
      XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, metadataProvider);
      model.setFilename(resolvedPath);
      model.clearChanged();
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmModelLoadSupport.Error.LoadFailed", resolvedPath), e);
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
