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
package org.hopper.edw.catalog.harvest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.hopper.edw.datavault.resourcedefinition.SourceUsage;
import org.hopper.edw.datavault.resourcedefinition.SourceUsageIndexBuilder;
import org.hopper.edw.datavault.resourcedefinition.ValidationModels;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Resolves harvest subjects from a resource definition group. */
public final class SchemaHarvestSubjectResolver {

  private SchemaHarvestSubjectResolver() {}

  /**
   * One resolved subject: catalog key + definition + optional physical location hints for batching.
   */
  public record ResolvedSubject(
      RecordDefinitionKey key,
      String catalogConnection,
      RecordDefinition definition,
      List<SourceUsage> usages) {

    public String subjectKey() {
      if (key == null) {
        return "?";
      }
      return Const.NVL(key.getNamespace(), "") + "/" + Const.NVL(key.getName(), "");
    }
  }

  public static List<ResolvedSubject> resolveFromGroup(
      String groupName,
      String catalogConnectionOverride,
      String recordSourceGroupFilter,
      String connectionNameFilter,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    ResourceDefinitionGroupMeta group =
        ResourceDefinitionGroupResolver.loadGroup(groupName, metadataProvider);
    return resolveFromGroup(
        group,
        catalogConnectionOverride,
        recordSourceGroupFilter,
        connectionNameFilter,
        variables,
        metadataProvider);
  }

  public static List<ResolvedSubject> resolveFromGroup(
      ResourceDefinitionGroupMeta group,
      String catalogConnectionOverride,
      String recordSourceGroupFilter,
      String connectionNameFilter,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (group == null) {
      throw new HopException("Resource definition group is required for schema harvest");
    }
    ValidationModels models =
        ResourceDefinitionGroupResolver.resolve(group, variables, metadataProvider);
    Map<RecordDefinitionKey, List<SourceUsage>> usageIndex =
        SourceUsageIndexBuilder.build(models, variables);
    String defaultNamespace = DvCatalogNamespaces.projectSourcesNamespace(variables);
    String groupFilter =
        Utils.isEmpty(recordSourceGroupFilter) ? null : recordSourceGroupFilter.trim();
    String connectionFilter =
        Utils.isEmpty(connectionNameFilter)
            ? null
            : connectionNameFilter.trim().toLowerCase(Locale.ROOT);

    Map<String, ResolvedSubject> unique = new LinkedHashMap<>();
    for (Map.Entry<RecordDefinitionKey, List<SourceUsage>> entry : usageIndex.entrySet()) {
      RecordDefinitionKey templateKey = entry.getKey();
      List<SourceUsage> usages = entry.getValue();
      String catalogConnection =
          !Utils.isEmpty(catalogConnectionOverride)
              ? catalogConnectionOverride
              : resolveCatalogConnection(usages, group);
      RecordDefinitionKey resolvedKey =
          SourceUsageIndexBuilder.resolveKey(
              templateKey, catalogConnection, variables, defaultNamespace);
      RecordDefinition definition =
          loadDefinition(catalogConnection, resolvedKey, variables, metadataProvider);
      if (definition == null) {
        // Still harvest as missing-definition subject so operators see the gap.
        String subjectKey =
            Const.NVL(resolvedKey.getNamespace(), "") + "/" + Const.NVL(resolvedKey.getName(), "");
        unique.putIfAbsent(
            subjectKey,
            new ResolvedSubject(resolvedKey, catalogConnection, null, new ArrayList<>(usages)));
        continue;
      }
      if (groupFilter != null && !matchesRecordSourceGroup(definition, groupFilter)) {
        continue;
      }
      if (connectionFilter != null && !matchesConnectionFilter(definition, connectionFilter)) {
        continue;
      }
      String subjectKey =
          Const.NVL(resolvedKey.getNamespace(), "") + "/" + Const.NVL(resolvedKey.getName(), "");
      unique.putIfAbsent(
          subjectKey,
          new ResolvedSubject(resolvedKey, catalogConnection, definition, new ArrayList<>(usages)));
    }
    return new ArrayList<>(unique.values());
  }

  private static String resolveCatalogConnection(
      List<SourceUsage> usages, ResourceDefinitionGroupMeta group) {
    if (usages != null) {
      for (SourceUsage usage : usages) {
        if (usage != null && !Utils.isEmpty(usage.catalogConnection())) {
          return usage.catalogConnection();
        }
      }
    }
    if (group != null && !Utils.isEmpty(group.getDataCatalogConnection())) {
      return group.getDataCatalogConnection();
    }
    return null;
  }

  private static RecordDefinition loadDefinition(
      String catalogConnection,
      RecordDefinitionKey key,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(catalogConnection) || key == null) {
      return null;
    }
    return RecordDefinitionRegistry.getInstance()
        .read(catalogConnection, key, variables, metadataProvider);
  }

  private static boolean matchesRecordSourceGroup(RecordDefinition definition, String groupFilter) {
    if (definition == null || definition.getDvSource() == null) {
      return false;
    }
    String group = Const.NVL(definition.getDvSource().getGroup(), "").trim();
    return groupFilter.equalsIgnoreCase(group);
  }

  private static boolean matchesConnectionFilter(
      RecordDefinition definition, String connectionFilterLower) {
    if (definition == null || definition.getPhysicalTable() == null) {
      // Non-database subjects are excluded when a connection filter is set.
      return false;
    }
    String name = Const.NVL(definition.getPhysicalTable().getDatabaseMetaName(), "").trim();
    return connectionFilterLower.equals(name.toLowerCase(Locale.ROOT));
  }
}
