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
package org.hopper.edw.catalog.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionQuery;
import org.hopper.edw.catalog.model.RecordDefinitionRef;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.spi.DataCatalogPluginFactory;
import org.hopper.edw.catalog.spi.IDataCatalog;
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

  /**
   * Session cache of list results keyed by connection + query fingerprint. Cleared on mutations and
   * {@link #invalidate()}.
   */
  private final Map<String, List<RecordDefinitionRef>> listCache = new ConcurrentHashMap<>();

  /**
   * Session cache of record names (sorted) for the same query keys as {@link #listCache}. Avoids
   * re-copying refs and re-sorting names on repeated combo/listSourceNames calls.
   */
  private final Map<String, List<String>> nameListCache = new ConcurrentHashMap<>();

  /**
   * Session cache for {@link #hasAny} results so empty-model canvas paint stays O(1) after the
   * first existence check.
   */
  private final Map<String, Boolean> anyMatchCache = new ConcurrentHashMap<>();

  private RecordDefinitionRegistry() {}

  public static RecordDefinitionRegistry getInstance() {
    return INSTANCE;
  }

  /** Called after the data catalog plugin type is registered at environment init. */
  public void environmentReady() {
    invalidate();
  }

  public void invalidate() {
    clearListCache();
    for (IDataCatalog catalog : connectedCatalogs.values()) {
      try {
        catalog.disconnect();
      } catch (Exception ignored) {
        // Best effort during cache reset.
      }
    }
    connectedCatalogs.clear();
  }

  /** Drops cached list results without disconnecting catalogs. */
  public void clearListCache() {
    listCache.clear();
    nameListCache.clear();
    anyMatchCache.clear();
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
    String cacheKey = listCacheKey(catalogConnectionName, query);
    List<RecordDefinitionRef> cached = listCache.get(cacheKey);
    if (cached != null) {
      return copyRefs(cached);
    }

    IDataCatalog catalog = getConnectedCatalog(catalogConnectionName, variables, metadataProvider);
    List<RecordDefinitionRef> results = new ArrayList<>();
    for (RecordDefinitionRef ref : catalog.list(query)) {
      if (ref == null) {
        continue;
      }
      ref.setCatalogConnectionName(catalogConnectionName);
      results.add(ref);
    }
    listCache.put(cacheKey, copyRefs(results));
    anyMatchCache.put(cacheKey, !results.isEmpty());
    return results;
  }

  /**
   * Returns sorted record names for the query (session-cached). Prefer this over listing refs when
   * only names are needed (source combos, model checks).
   */
  public List<String> listNames(
      String catalogConnectionName,
      RecordDefinitionQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String cacheKey = listCacheKey(catalogConnectionName, query);
    List<String> cachedNames = nameListCache.get(cacheKey);
    if (cachedNames != null) {
      return new ArrayList<>(cachedNames);
    }
    List<RecordDefinitionRef> refs =
        list(catalogConnectionName, query, variables, metadataProvider);
    List<String> names = new ArrayList<>();
    for (RecordDefinitionRef ref : refs) {
      if (ref == null || ref.getKey() == null || Utils.isEmpty(ref.getKey().getName())) {
        continue;
      }
      names.add(ref.getKey().getName());
    }
    names.sort(String.CASE_INSENSITIVE_ORDER);
    nameListCache.put(cacheKey, List.copyOf(names));
    return names;
  }

  /**
   * Existence check for onboarding / paint paths. Uses caches when warm; otherwise short-circuits
   * via {@link IDataCatalog#anyMatch(RecordDefinitionQuery)} without materializing the full list.
   */
  public boolean hasAny(
      String catalogConnectionName,
      RecordDefinitionQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String cacheKey = listCacheKey(catalogConnectionName, query);
    Boolean cached = anyMatchCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    List<RecordDefinitionRef> listCached = listCache.get(cacheKey);
    if (listCached != null) {
      boolean has = !listCached.isEmpty();
      anyMatchCache.put(cacheKey, has);
      return has;
    }
    IDataCatalog catalog = getConnectedCatalog(catalogConnectionName, variables, metadataProvider);
    boolean has = catalog.anyMatch(query);
    anyMatchCache.put(cacheKey, has);
    return has;
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
    clearListCache();
  }

  public void update(
      String catalogConnectionName,
      RecordDefinition definition,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    IDataCatalog catalog = getConnectedCatalog(catalogConnectionName, variables, metadataProvider);
    catalog.update(definition);
    clearListCache();
  }

  public void delete(
      String catalogConnectionName,
      RecordDefinitionKey key,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    IDataCatalog catalog = getConnectedCatalog(catalogConnectionName, variables, metadataProvider);
    catalog.delete(key);
    clearListCache();
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
    clearListCache();
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

  static String listCacheKey(String catalogConnectionName, RecordDefinitionQuery query) {
    RecordDefinitionQuery q = query != null ? query : new RecordDefinitionQuery();
    String types =
        q.getTypes() == null || q.getTypes().isEmpty()
            ? ""
            : q.getTypes().stream()
                .filter(Objects::nonNull)
                .map(RecordDefinitionType::name)
                .sorted()
                .collect(Collectors.joining(","));
    String tags =
        q.getTags() == null || q.getTags().isEmpty()
            ? ""
            : q.getTags().stream()
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.joining(","));
    return String.join(
        "\u0001",
        nullToEmpty(catalogConnectionName),
        nullToEmpty(q.getNamespacePrefix()),
        q.getType() != null ? q.getType().name() : "",
        types,
        nullToEmpty(q.getNameContains()),
        tags);
  }

  private static String nullToEmpty(String value) {
    return value != null ? value : "";
  }

  private static List<RecordDefinitionRef> copyRefs(List<RecordDefinitionRef> source) {
    List<RecordDefinitionRef> copy = new ArrayList<>(source.size());
    for (RecordDefinitionRef ref : source) {
      if (ref == null) {
        continue;
      }
      RecordDefinitionRef clone = new RecordDefinitionRef();
      clone.setCatalogConnectionName(ref.getCatalogConnectionName());
      if (ref.getKey() != null) {
        clone.setKey(new RecordDefinitionKey(ref.getKey().getNamespace(), ref.getKey().getName()));
      }
      clone.setType(ref.getType());
      clone.setDescription(ref.getDescription());
      copy.add(clone);
    }
    return copy;
  }
}
