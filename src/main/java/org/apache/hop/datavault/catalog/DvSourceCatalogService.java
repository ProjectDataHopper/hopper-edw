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
package org.apache.hop.datavault.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;

/** Resolves and persists Data Vault sources through the data catalog. */
public final class DvSourceCatalogService {

  /** Default catalog for reading version-controlled DV source record definitions. */
  public static final String DEFAULT_SOURCE_CATALOG_CONNECTION = "local-catalog";

  private DvSourceCatalogService() {}

  public static DataVaultSource resolveSource(
      String sourceName,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return resolveSource(
        sourceName,
        resolveCatalogConnection(model, variables, metadataProvider),
        variables,
        metadataProvider);
  }

  public static DataVaultSource resolveSource(
      String sourceName,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(sourceName)) {
      return null;
    }
    String resolvedName = variables != null ? variables.resolve(sourceName) : sourceName;
    if (Utils.isEmpty(resolvedName)) {
      return null;
    }
    String connectionName =
        resolvePreferredCatalogConnection(catalogConnectionName, variables, metadataProvider);
    String namespace = projectSourcesNamespace(variables);
    RecordDefinition definition =
        RecordDefinitionRegistry.getInstance()
            .read(
                connectionName,
                new RecordDefinitionKey(namespace, resolvedName),
                variables,
                metadataProvider);
    if (definition == null) {
      throw new HopException(sourceNotFoundMessage(resolvedName, connectionName, namespace));
    }
    return CatalogDvSourceMapper.toDataVaultSource(definition, variables);
  }

  public static List<String> listSourceNames(
      DataVaultModel model, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return listSourceNames(
        resolveCatalogConnection(model, variables, metadataProvider), variables, metadataProvider);
  }

  public static List<String> listEnabledCatalogConnectionNames(
      IHopMetadataProvider metadataProvider) throws HopException {
    List<String> names = new ArrayList<>();
    for (DataCatalogMeta connection : listEnabledCatalogConnections(metadataProvider)) {
      names.add(connection.getName());
    }
    return names;
  }

  public static List<String> listSourceNames(
      String catalogConnectionName, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    String connectionName =
        resolvePreferredCatalogConnection(catalogConnectionName, variables, metadataProvider);
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.setNamespacePrefix(projectSourcesNamespace(variables));
    query.setType(RecordDefinitionType.DV_SOURCE);
    List<RecordDefinitionRef> refs =
        RecordDefinitionRegistry.getInstance()
            .list(connectionName, query, variables, metadataProvider);
    List<String> names = new ArrayList<>();
    for (RecordDefinitionRef ref : refs) {
      if (ref == null || ref.getKey() == null) {
        continue;
      }
      names.add(ref.getKey().getName());
    }
    names.sort(String.CASE_INSENSITIVE_ORDER);
    return names;
  }

  public static List<DataVaultSource> listSources(
      String catalogConnectionName, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    List<DataVaultSource> sources = new ArrayList<>();
    for (String name : listSourceNames(catalogConnectionName, variables, metadataProvider)) {
      sources.add(resolveSource(name, catalogConnectionName, variables, metadataProvider));
    }
    return sources;
  }

  public static boolean exists(
      String sourceName,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(sourceName)) {
      return false;
    }
    String connectionName =
        resolvePreferredCatalogConnection(catalogConnectionName, variables, metadataProvider);
    String resolvedName = variables != null ? variables.resolve(sourceName) : sourceName;
    RecordDefinition existing =
        RecordDefinitionRegistry.getInstance()
            .read(
                connectionName,
                new RecordDefinitionKey(projectSourcesNamespace(variables), resolvedName),
                variables,
                metadataProvider);
    return existing != null;
  }

  public static void upsertSource(
      DataVaultSource source,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    upsertSource(
        source, catalogConnectionName, null, variables, metadataProvider, new Date(), null);
  }

  public static void upsertSource(
      DataVaultSource source,
      String catalogConnectionName,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt,
      String workflowName)
      throws HopException {
    if (source == null || Utils.isEmpty(source.getName())) {
      throw new HopException("Data Vault source is missing a name");
    }
    String connectionName =
        resolvePreferredCatalogConnection(catalogConnectionName, variables, metadataProvider);
    RecordDefinition definition =
        DvSourceCatalogMapper.toRecordDefinition(
            source,
            projectSourcesNamespace(variables),
            RecordDefinitionType.DV_SOURCE,
            model,
            variables,
            metadataProvider,
            updatedAt != null ? updatedAt : new Date(),
            workflowName,
            null);
    RecordDefinitionRegistry.getInstance()
        .upsert(connectionName, definition, variables, metadataProvider);
  }

  public static void deleteSource(
      String sourceName,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(sourceName)) {
      return;
    }
    String connectionName =
        resolvePreferredCatalogConnection(catalogConnectionName, variables, metadataProvider);
    String resolvedName = variables != null ? variables.resolve(sourceName) : sourceName;
    RecordDefinitionRegistry.getInstance()
        .delete(
            connectionName,
            new RecordDefinitionKey(projectSourcesNamespace(variables), resolvedName),
            variables,
            metadataProvider);
  }

  public static String resolveCatalogConnection(
      DataVaultModel model, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model != null && model.getConfigurationOrDefault() != null) {
      String configured = model.getConfigurationOrDefault().getDataCatalogConnection();
      if (variables != null) {
        configured = variables.resolve(configured);
      }
      if (!Utils.isEmpty(configured)) {
        return configured;
      }
    }
    return resolvePreferredCatalogConnection(null, variables, metadataProvider);
  }

  public static String resolvePreferredCatalogConnection(
      String preferredConnectionName, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return resolveCatalogConnectionName(preferredConnectionName, variables, metadataProvider);
  }

  private static String resolveCatalogConnectionName(
      String preferredConnectionName, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (variables != null && !Utils.isEmpty(preferredConnectionName)) {
      preferredConnectionName = variables.resolve(preferredConnectionName);
    }
    if (!Utils.isEmpty(preferredConnectionName)) {
      return preferredConnectionName;
    }
    List<DataCatalogMeta> enabled = listEnabledCatalogConnections(metadataProvider);
    if (enabled.isEmpty()) {
      throw new HopException(
          "No enabled data catalog connection is configured. Set dataCatalogConnection on the Data Vault model or create an enabled DataCatalogMeta connection.");
    }
    if (enabled.size() > 1) {
      for (DataCatalogMeta connection : enabled) {
        if (DEFAULT_SOURCE_CATALOG_CONNECTION.equals(connection.getName())) {
          return connection.getName();
        }
      }
      throw new HopException(
          "Multiple enabled data catalog connections found ("
              + enabled.stream().map(DataCatalogMeta::getName).sorted().toList()
              + "). Set dataCatalogConnection on the Data Vault model or workflow.");
    }
    return enabled.get(0).getName();
  }

  private static List<DataCatalogMeta> listEnabledCatalogConnections(
      IHopMetadataProvider metadataProvider) throws HopException {
    List<DataCatalogMeta> connections = new ArrayList<>();
    IHopMetadataSerializer<DataCatalogMeta> serializer =
        metadataProvider.getSerializer(DataCatalogMeta.class);
    for (String name : serializer.listObjectNames()) {
      DataCatalogMeta meta = serializer.load(name);
      if (meta != null && meta.isEnabled()) {
        connections.add(meta);
      }
    }
    connections.sort(Comparator.comparing(DataCatalogMeta::getName, String.CASE_INSENSITIVE_ORDER));
    return connections;
  }

  public static String projectSourcesNamespace(IVariables variables) {
    return DvCatalogNamespaces.projectSourcesNamespace(variables);
  }

  static String sourceNotFoundMessage(
      String sourceName, String catalogConnectionName, String namespace) {
    return "Data Vault source '"
        + sourceName
        + "' was not found in data catalog connection '"
        + catalogConnectionName
        + "' in namespace '"
        + namespace
        + "'";
  }

  public static String uniqueSourceName(
      String baseName,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String candidate = baseName;
    int suffix = 1;
    while (exists(candidate, catalogConnectionName, variables, metadataProvider)) {
      candidate = baseName + "-" + suffix++;
    }
    return candidate;
  }
}
