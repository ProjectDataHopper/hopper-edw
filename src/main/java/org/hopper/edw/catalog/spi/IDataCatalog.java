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
package org.hopper.edw.catalog.spi;

import java.util.List;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.metadata.DataCatalogMetaObjectFactory;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionQuery;
import org.hopper.edw.catalog.model.RecordDefinitionRef;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataObject;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * SPI for interacting with an external data catalog (DataHub, file store, OpenMetadata, ...).
 * Implementations are selected by type id via {@link DataCatalogMetaObjectFactory} (same pattern as
 * {@link org.apache.hop.core.database.IDatabase}).
 */
@HopMetadataObject(objectFactory = DataCatalogMetaObjectFactory.class)
public interface IDataCatalog {

  String getPluginId();

  void setPluginId(String pluginId);

  /**
   * Prepare this catalog implementation for use with the given connection metadata.
   *
   * @param meta connection metadata (name, description, plugin-specific options)
   */
  void connect(DataCatalogMeta meta, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException;

  /** Release resources held by this catalog connection. */
  void disconnect() throws HopException;

  void create(RecordDefinition definition) throws HopException;

  RecordDefinition read(RecordDefinitionKey key) throws HopException;

  void update(RecordDefinition definition) throws HopException;

  void delete(RecordDefinitionKey key) throws HopException;

  List<RecordDefinitionRef> list(RecordDefinitionQuery query) throws HopException;

  /**
   * Returns whether at least one record matches the query. Default implementation lists all matches
   * and checks emptiness; file catalogs override with a short-circuit walk for paint/onboarding hot
   * paths.
   */
  default boolean anyMatch(RecordDefinitionQuery query) throws HopException {
    List<RecordDefinitionRef> refs = list(query);
    return refs != null && !refs.isEmpty();
  }
}
