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
 *
 */

package org.apache.hop.catalog.impl.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.discovery.HopVariableResolutionSupport;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.spi.IDataCatalog;
import org.apache.hop.catalog.versioning.CatalogVersionStore;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * File-based data catalog: stores record definitions as JSON files under a configurable directory.
 */
@GuiPlugin(id = "GUI-FileDataCatalog")
@Getter
@Setter
public class FileDataCatalog implements IDataCatalog {

  private static final Class<?> PKG = FileDataCatalog.class;

  public static final String PLUGIN_ID = "FILE";

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "FileDataCatalog-PluginSpecific-Options";

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  @HopMetadataProperty private String pluginId = PLUGIN_ID;

  @GuiWidgetElement(
      order = "10",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::FileDataCatalog.StorageDirectory.Label",
      toolTip = "i18n::FileDataCatalog.StorageDirectory.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String storageDirectory = "${PROJECT_HOME}/catalog-data";

  private transient Path resolvedRoot;
  private transient String connectionName;

  @Override
  public void connect(
      DataCatalogMeta meta, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    connectionName = meta != null ? meta.getName() : null;
    if (Utils.isEmpty(storageDirectory)) {
      throw new HopException(
          BaseMessages.getString(PKG, "FileDataCatalog.Error.StorageDirectoryNotConfigured"));
    }
    // Refuse unresolved ${PROJECT_HOME}/... so we never mkdir a literal path under Hop CWD
    // (e.g. $HOP_HOME/${PROJECT_HOME}/work/edw-catalog when project variables are missing).
    HopVariableResolutionSupport.requireResolved(
        variables,
        storageDirectory,
        BaseMessages.getString(PKG, "FileDataCatalog.StorageDirectory.Label"));
    String resolved = HopVariableResolutionSupport.resolve(variables, storageDirectory);
    if (Utils.isEmpty(resolved)) {
      throw new HopException(
          BaseMessages.getString(PKG, "FileDataCatalog.Error.StorageDirectoryNotConfigured"));
    }
    resolvedRoot = Path.of(resolved).toAbsolutePath().normalize();
    try {
      Files.createDirectories(resolvedRoot);
    } catch (IOException e) {
      throw new HopException("Unable to create catalog storage directory: " + resolvedRoot, e);
    }
  }

  @Override
  public void disconnect() {
    resolvedRoot = null;
    connectionName = null;
  }

  @Override
  public void create(RecordDefinition definition) throws HopException {
    ensureConnected();
    definition.validate();
    Path target = toRecordPath(definition.getKey());
    if (Files.exists(target)) {
      throw new HopException("Record definition already exists: " + definition.getKey());
    }
    writeRecord(target, definition);
  }

  @Override
  public RecordDefinition read(RecordDefinitionKey key) throws HopException {
    ensureConnected();
    key.validate();
    Path target = toRecordPath(key);
    if (!Files.exists(target)) {
      return null;
    }
    return readRecord(target);
  }

  @Override
  public void update(RecordDefinition definition) throws HopException {
    ensureConnected();
    definition.validate();
    Path target = toRecordPath(definition.getKey());
    if (!Files.exists(target)) {
      throw new HopException("Record definition does not exist: " + definition.getKey());
    }
    writeRecord(target, definition);
  }

  @Override
  public void delete(RecordDefinitionKey key) throws HopException {
    ensureConnected();
    key.validate();
    Path target = toRecordPath(key);
    try {
      Files.deleteIfExists(target);
    } catch (IOException e) {
      throw new HopException("Unable to delete record definition: " + key, e);
    }
  }

  @Override
  public List<RecordDefinitionRef> list(RecordDefinitionQuery query) throws HopException {
    ensureConnected();
    RecordDefinitionQuery effectiveQuery = query != null ? query : new RecordDefinitionQuery();
    List<RecordDefinitionRef> results = new ArrayList<>();
    Path versionsRoot = resolvedRoot.resolve(CatalogVersionStore.VERSIONS_DIRECTORY_NAME);
    try (Stream<Path> paths = Files.walk(resolvedRoot)) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".json"))
          .filter(path -> !isUnderDirectory(path, versionsRoot))
          .forEach(
              path -> {
                try {
                  RecordDefinition definition = readRecord(path);
                  if (effectiveQuery.matches(definition)) {
                    results.add(RecordDefinitionRef.of(connectionName, definition));
                  }
                } catch (HopException e) {
                  LogChannel.GENERAL.logError(
                      "Skipping unreadable catalog record definition: " + path, e);
                }
              });
    } catch (IOException e) {
      throw new HopException("Unable to list record definitions under " + resolvedRoot, e);
    }
    return results;
  }

  /**
   * Resolved absolute storage root after {@link #connect}. Used by catalog versioning to place
   * snapshots beside the working-tree record definitions.
   */
  public Path getResolvedRoot() {
    return resolvedRoot;
  }

  private void ensureConnected() throws HopException {
    if (resolvedRoot == null) {
      throw new HopException("File data catalog is not connected");
    }
  }

  private static boolean isUnderDirectory(Path path, Path directory) {
    if (path == null || directory == null || !Files.exists(directory)) {
      return false;
    }
    Path normalizedPath = path.toAbsolutePath().normalize();
    Path normalizedDir = directory.toAbsolutePath().normalize();
    return normalizedPath.startsWith(normalizedDir);
  }

  private Path toRecordPath(RecordDefinitionKey key) throws HopException {
    Path path = resolvedRoot;
    for (String segment : key.getNamespace().split("/")) {
      if (!segment.isBlank()) {
        path = path.resolve(sanitizePathSegment(segment));
      }
    }
    return path.resolve(sanitizePathSegment(key.getName()) + ".json");
  }

  public static String sanitizePathSegment(String segment) {
    if (segment == null) {
      return "_";
    }
    return segment.replace('\\', '/').replace("..", "_");
  }

  private void writeRecord(Path target, RecordDefinition definition) throws HopException {
    try {
      Files.createDirectories(target.getParent());
      RecordDefinitionDocument doc = RecordDefinitionDocument.from(definition);
      MAPPER.writeValue(target.toFile(), doc);
    } catch (IOException e) {
      throw new HopException("Unable to write record definition to " + target, e);
    }
  }

  private RecordDefinition readRecord(Path path) throws HopException {
    try {
      RecordDefinitionDocument doc = MAPPER.readValue(path.toFile(), RecordDefinitionDocument.class);
      return doc.toRecordDefinition();
    } catch (IOException e) {
      throw new HopException("Unable to read record definition from " + path, e);
    }
  }
}