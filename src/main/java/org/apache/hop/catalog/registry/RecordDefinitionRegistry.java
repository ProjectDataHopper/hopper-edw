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
package org.apache.hop.catalog.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.spi.DataCatalogPluginFactory;
import org.apache.hop.catalog.spi.IDataCatalog;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;

/**
 * Central facade that aggregates CRUD operations across all enabled {@link DataCatalogMeta}
 * connections in the active metadata provider.
 */
public final class RecordDefinitionRegistry {

  private static final RecordDefinitionRegistry INSTANCE = new RecordDefinitionRegistry();

  private final Map<String, IDataCatalog> connectedCatalogs = new ConcurrentHashMap<>();

  private RecordDefinitionRegistry() {}

  public static RecordDefinitionRegistry getInstance() {
    return INSTANCE;
  }

  /** Called after the data catalog plugin type is registered at environment init. */
  public void environmentReady() {
    invalidate();
  }

  public void invalidate() {
    for (IDataCatalog catalog : connectedCatalogs.values()) {
      try {
        catalog.disconnect();
      } catch (Exception ignored) {
        // Best effort during cache reset.
      }
    }
    connectedCatalogs.clear();
  }

  public List<RecordDefinitionRef> listAll(
      RecordDefinitionQuery query, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    List<RecordDefinitionRef> results = new ArrayList<>();
    for (DataCatalogMeta meta : listEnabledConnections(metadataProvider)) {
      results.addAll(list(meta.getName(), query, variables, metadataProvider));
    }
    return results;
  }

  public List<RecordDefinitionRef> list(
      String catalogConnectionName,
      RecordDefinitionQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    IDataCatalog catalog = getConnectedCatalog(catalogConnectionName, variables, metadataProvider);
    List<RecordDefinitionRef> results = new ArrayList<>();
    for (RecordDefinitionRef ref : catalog.list(query)) {
      ref.setCatalogConnectionName(catalogConnectionName);
      results.add(ref);
    }
    return results;
  }

  public RecordDefinition read(
      String catalogConnectionName,
      RecordDefinitionKey key,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    IDataCatalog catalog = getConnectedCatalog(catalogConnectionName, variables, metadataProvider);
    return catalog.read(key);
  }

  public void create(
      String catalogConnectionName,
      RecordDefinition definition,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    IDataCatalog catalog = getConnectedCatalog(catalogConnectionName, variables, metadataProvider);
    catalog.create(definition);
  }

  public void update(
      String catalogConnectionName,
      RecordDefinition definition,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    IDataCatalog catalog = getConnectedCatalog(catalogConnectionName, variables, metadataProvider);
    catalog.update(definition);
  }

  public void delete(
      String catalogConnectionName,
      RecordDefinitionKey key,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    IDataCatalog catalog = getConnectedCatalog(catalogConnectionName, variables, metadataProvider);
    catalog.delete(key);
  }

  public void upsert(
      String catalogConnectionName,
      RecordDefinition definition,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    definition.validate();
    IDataCatalog catalog = getConnectedCatalog(catalogConnectionName, variables, metadataProvider);
    RecordDefinition existing = catalog.read(definition.getKey());
    if (existing == null) {
      catalog.create(definition);
    } else {
      catalog.update(definition);
    }
  }

  private IDataCatalog getConnectedCatalog(
      String connectionName, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(connectionName)) {
      throw new HopException("Catalog connection name is required");
    }
    IDataCatalog cached = connectedCatalogs.get(connectionName);
    if (cached != null) {
      return cached;
    }
    synchronized (connectedCatalogs) {
      cached = connectedCatalogs.get(connectionName);
      if (cached != null) {
        return cached;
      }
      DataCatalogMeta meta = loadConnection(connectionName, metadataProvider);
      IDataCatalog catalog =
          DataCatalogPluginFactory.createConnected(meta, variables, metadataProvider);
      connectedCatalogs.put(connectionName, catalog);
      return catalog;
    }
  }

  private IDataCatalog getConnectedCatalog(
      DataCatalogMeta meta, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return getConnectedCatalog(meta.getName(), variables, metadataProvider);
  }

  private List<DataCatalogMeta> listEnabledConnections(IHopMetadataProvider metadataProvider)
      throws HopException {
    List<DataCatalogMeta> connections = new ArrayList<>();
    IHopMetadataSerializer<DataCatalogMeta> serializer =
        metadataProvider.getSerializer(DataCatalogMeta.class);
    for (String name : serializer.listObjectNames()) {
      DataCatalogMeta meta = serializer.load(name);
      if (meta != null && meta.isEnabled()) {
        connections.add(meta);
      }
    }
    return connections;
  }

  private DataCatalogMeta loadConnection(String name, IHopMetadataProvider metadataProvider)
      throws HopException {
    IHopMetadataSerializer<DataCatalogMeta> serializer =
        metadataProvider.getSerializer(DataCatalogMeta.class);
    DataCatalogMeta meta = serializer.load(name);
    if (meta == null) {
      throw new HopException("Data catalog connection '" + name + "' was not found");
    }
    if (!meta.isEnabled()) {
      throw new HopException("Data catalog connection '" + name + "' is disabled");
    }
    return meta;
  }
}
