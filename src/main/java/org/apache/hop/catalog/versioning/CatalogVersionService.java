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
package org.apache.hop.catalog.versioning;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.spi.IDataCatalog;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvCatalogNamespaces;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.apache.hop.datavault.resourcedefinition.SourceUsage;
import org.apache.hop.datavault.resourcedefinition.SourceUsageIndexBuilder;
import org.apache.hop.datavault.resourcedefinition.ValidationModels;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Creates and loads semantic catalog version tags for file-based data catalogs. Snapshots freeze
 * source record definitions referenced by a resource definition group.
 */
public final class CatalogVersionService {

  private static final Class<?> PKG = CatalogVersionService.class;

  private static final DateTimeFormatter ISO_INSTANT =
      DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

  private CatalogVersionService() {}

  public static CatalogVersionEntry createFromGroup(
      ResourceDefinitionGroupMeta group,
      String tag,
      String description,
      String createdBy,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return createFromGroup(null, group, tag, description, createdBy, variables, metadataProvider);
  }

  public static CatalogVersionEntry createFromGroup(
      String catalogConnectionOverride,
      ResourceDefinitionGroupMeta group,
      String tag,
      String description,
      String createdBy,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (group == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionService.Error.MissingGroup"));
    }
    String effectiveTag = requireTag(tag);

    ValidationModels models =
        ResourceDefinitionGroupResolver.resolve(group, variables, metadataProvider);
    Map<RecordDefinitionKey, List<SourceUsage>> usageIndex =
        SourceUsageIndexBuilder.build(models, variables);
    if (usageIndex.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "CatalogVersionService.Error.NoSourcesInGroup", group.getName()));
    }

    String defaultNamespace = DvCatalogNamespaces.projectSourcesNamespace(variables);
    String preferredConnection =
        resolvePreferredConnection(
            catalogConnectionOverride, group, usageIndex, variables, metadataProvider);

    Map<String, RecordDefinition> definitionsByKey = new LinkedHashMap<>();
    Set<String> namespaces = new LinkedHashSet<>();
    List<String> missing = new ArrayList<>();

    for (Map.Entry<RecordDefinitionKey, List<SourceUsage>> entry : usageIndex.entrySet()) {
      RecordDefinitionKey templateKey = entry.getKey();
      List<SourceUsage> usages = entry.getValue();
      String catalogConnection =
          firstNonEmpty(
              preferredConnection,
              resolveCatalogConnection(usages),
              group.getDataCatalogConnection());
      if (Utils.isEmpty(catalogConnection)) {
        catalogConnection = preferredConnection;
      }
      if (!preferredConnection.equals(catalogConnection)) {
        // MVP: one version tag is bound to a single catalog connection storage root.
        continue;
      }
      RecordDefinitionKey resolvedKey =
          SourceUsageIndexBuilder.resolveKey(
              templateKey, catalogConnection, variables, defaultNamespace);
      RecordDefinition definition =
          RecordDefinitionRegistry.getInstance()
              .read(catalogConnection, resolvedKey, variables, metadataProvider);
      if (definition == null) {
        missing.add(resolvedKey.getNamespace() + "/" + resolvedKey.getName());
        continue;
      }
      String mapKey = resolvedKey.getNamespace() + "/" + resolvedKey.getName();
      definitionsByKey.putIfAbsent(mapKey, definition);
      if (!Utils.isEmpty(resolvedKey.getNamespace())) {
        namespaces.add(resolvedKey.getNamespace());
      }
    }

    if (definitionsByKey.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "CatalogVersionService.Error.NoLoadableDefinitions",
              group.getName(),
              preferredConnection));
    }
    if (!missing.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "CatalogVersionService.Error.MissingDefinitions", String.join(", ", missing)));
    }

    Instant now = Instant.now();
    String createdAt = ISO_INSTANT.format(now);
    String snapshotId = CatalogVersionStore.buildSnapshotId(effectiveTag, createdAt);

    CatalogVersionScope scope =
        new CatalogVersionScope(
            group.getName(),
            new ArrayList<>(namespaces),
            new ArrayList<>(definitionsByKey.keySet()));

    CatalogVersionStore store = openStore(preferredConnection, variables, metadataProvider);
    CatalogVersionManifest manifest = store.readManifest();
    if (manifest.hasTag(effectiveTag)) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionService.Error.TagExists", effectiveTag));
    }

    CatalogVersionSnapshotManifest snapshotManifest = new CatalogVersionSnapshotManifest();
    snapshotManifest.setTag(effectiveTag);
    snapshotManifest.setSnapshotId(snapshotId);
    snapshotManifest.setCreatedAt(createdAt);
    snapshotManifest.setCreatedBy(createdBy);
    snapshotManifest.setDescription(description);
    snapshotManifest.setScope(scope);

    store.writeSnapshot(snapshotManifest, new ArrayList<>(definitionsByKey.values()));

    CatalogVersionEntry entry =
        new CatalogVersionEntry(
            effectiveTag,
            snapshotId,
            createdAt,
            createdBy,
            description,
            scope,
            snapshotManifest.getRecordCount(),
            snapshotManifest.getContentHash());
    manifest.addVersion(entry);
    store.writeManifest(manifest);
    return entry;
  }

  public static CatalogVersionEntry createFromGroupByName(
      String groupName,
      String tag,
      String description,
      String createdBy,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    ResourceDefinitionGroupMeta group =
        ResourceDefinitionGroupResolver.loadGroup(groupName, metadataProvider);
    return createFromGroup(group, tag, description, createdBy, variables, metadataProvider);
  }

  public static List<CatalogVersionEntry> listVersions(
      String catalogConnectionName, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    CatalogVersionStore store = openStore(catalogConnectionName, variables, metadataProvider);
    return List.copyOf(store.readManifest().getVersions());
  }

  public static Optional<CatalogVersionEntry> findVersion(
      String catalogConnectionName,
      String tag,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(tag)) {
      return Optional.empty();
    }
    CatalogVersionStore store = openStore(catalogConnectionName, variables, metadataProvider);
    return store.readManifest().findByTag(tag.trim());
  }

  public static Optional<RecordDefinition> readDefinition(
      String catalogConnectionName,
      String tag,
      RecordDefinitionKey key,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    CatalogVersionEntry entry =
        findVersion(catalogConnectionName, tag, variables, metadataProvider)
            .orElseThrow(
                () ->
                    new HopException(
                        BaseMessages.getString(
                            PKG, "CatalogVersionService.Error.TagNotFound", tag)));
    CatalogVersionStore store = openStore(catalogConnectionName, variables, metadataProvider);
    return store.readDefinition(entry.getSnapshotId(), key);
  }

  public static List<RecordDefinition> readAllAtVersion(
      String catalogConnectionName,
      String tag,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    CatalogVersionEntry entry =
        findVersion(catalogConnectionName, tag, variables, metadataProvider)
            .orElseThrow(
                () ->
                    new HopException(
                        BaseMessages.getString(
                            PKG, "CatalogVersionService.Error.TagNotFound", tag)));
    CatalogVersionStore store = openStore(catalogConnectionName, variables, metadataProvider);
    return store.readAllDefinitions(entry.getSnapshotId());
  }

  public static CatalogVersionStore openStore(
      String catalogConnectionName, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(catalogConnectionName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionService.Error.MissingCatalogConnection"));
    }
    if (metadataProvider == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionService.Error.MissingCatalogConnection"));
    }
    DataCatalogMeta meta =
        metadataProvider.getSerializer(DataCatalogMeta.class).load(catalogConnectionName);
    if (meta == null || !meta.isEnabled()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "CatalogVersionService.Error.CatalogNotFound", catalogConnectionName));
    }
    IDataCatalog connected =
        org.apache.hop.catalog.spi.DataCatalogPluginFactory.createConnected(
            meta, variables, metadataProvider);
    try {
      if (!(connected instanceof FileDataCatalog fileCatalog)) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "CatalogVersionService.Error.UnsupportedCatalogType",
                catalogConnectionName,
                connected != null ? connected.getPluginId() : "?"));
      }
      Path root = fileCatalog.getResolvedRoot();
      if (root == null) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "CatalogVersionService.Error.CatalogNotConnected", catalogConnectionName));
      }
      return new CatalogVersionStore(root);
    } finally {
      try {
        connected.disconnect();
      } catch (Exception ignored) {
        // Best effort; version store only needs the resolved path.
      }
    }
  }

  private static String resolvePreferredConnection(
      String catalogConnectionOverride,
      ResourceDefinitionGroupMeta group,
      Map<RecordDefinitionKey, List<SourceUsage>> usageIndex,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (!Utils.isEmpty(catalogConnectionOverride)) {
      return catalogConnectionOverride.trim();
    }
    if (group != null && !Utils.isEmpty(group.getDataCatalogConnection())) {
      return group.getDataCatalogConnection().trim();
    }
    for (List<SourceUsage> usages : usageIndex.values()) {
      String fromUsage = resolveCatalogConnection(usages);
      if (!Utils.isEmpty(fromUsage)) {
        return fromUsage;
      }
    }
    String preferred =
        DvSourceCatalogService.resolvePreferredCatalogConnection(null, variables, metadataProvider);
    if (!Utils.isEmpty(preferred)) {
      return preferred;
    }
    throw new HopException(
        BaseMessages.getString(PKG, "CatalogVersionService.Error.MissingCatalogConnection"));
  }

  private static String resolveCatalogConnection(List<SourceUsage> usages) {
    if (usages == null) {
      return null;
    }
    for (SourceUsage usage : usages) {
      if (usage != null && !Utils.isEmpty(usage.catalogConnection())) {
        return usage.catalogConnection();
      }
    }
    return null;
  }

  private static String firstNonEmpty(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (!Utils.isEmpty(value)) {
        return value;
      }
    }
    return null;
  }

  private static String requireTag(String tag) throws HopException {
    if (Utils.isEmpty(tag) || tag.trim().isEmpty()) {
      throw new HopException(BaseMessages.getString(PKG, "CatalogVersionService.Error.MissingTag"));
    }
    String trimmed = tag.trim();
    if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionService.Error.InvalidTag", trimmed));
    }
    return trimmed;
  }
}
