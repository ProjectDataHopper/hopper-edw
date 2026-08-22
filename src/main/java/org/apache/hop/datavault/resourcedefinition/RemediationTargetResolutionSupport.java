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
package org.apache.hop.datavault.resourcedefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BvScd2FieldMapping;
import org.apache.hop.datavault.metadata.businessvault.BvScd2Table;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAttribute;
import org.apache.hop.datavault.metadata.dimensional.DmSourceConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmSourceType;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;

/**
 * Resolves downstream BV/DM columns that should be widened when a catalog source field length
 * changes. Catalog is never modified here.
 */
public final class RemediationTargetResolutionSupport {

  private RemediationTargetResolutionSupport() {}

  /**
   * @param sourceFieldName catalog / satellite source field (e.g. address_line1)
   * @param catalogLength length from the catalog contract
   * @param satelliteNames DV satellite element names that map the source field (may be empty)
   */
  public static List<RemediationTargetColumn> resolveDownstreamTargets(
      ValidationModels models,
      String sourceFieldName,
      String catalogLength,
      Set<String> satelliteNames,
      IVariables variables) {
    List<RemediationTargetColumn> targets = new ArrayList<>();
    if (models == null || Utils.isEmpty(sourceFieldName) || Utils.isEmpty(catalogLength)) {
      return targets;
    }

    // BV SCD2 explicit mappings: sourceFieldName → targetFieldName on BV table.
    Map<String, List<BvFieldLink>> bvLinksBySource = indexBvFieldLinks(models, variables);
    List<BvFieldLink> bvHits = bvLinksBySource.getOrDefault(normalize(sourceFieldName), List.of());
    for (BvFieldLink link : bvHits) {
      if (!satelliteNames.isEmpty()
          && !Utils.isEmpty(link.satelliteName())
          && !containsIgnoreCase(satelliteNames, link.satelliteName())) {
        // Still accept if satellite filter is empty; otherwise require sat match when known.
        continue;
      }
      targets.add(
          new RemediationTargetColumn(
              RemediationTargetColumn.LAYER_BV,
              link.modelName(),
              link.modelFilename(),
              link.tableElementName(),
              link.physicalTableName(),
              link.targetFieldName(),
              sourceFieldName,
              catalogLength,
              RemediationTargetColumn.CONFIDENCE_EXPLICIT_MAP,
              link.connectionName()));
    }

    // DM SQL: table references a BV physical table and dimension attributes share BV target names.
    Set<String> bvTargetFields = new LinkedHashSet<>();
    Map<String, BvFieldLink> bvTargetToLink = new LinkedHashMap<>();
    for (BvFieldLink link : bvHits) {
      bvTargetFields.add(normalize(link.targetFieldName()));
      bvTargetToLink.put(normalize(link.targetFieldName()), link);
    }
    // Also index all BV mappings for the source field's satellites (broader) when no hit on source
    // field alone — for DM we need target field names that flow from this source.
    if (bvHits.isEmpty()) {
      for (List<BvFieldLink> links : bvLinksBySource.values()) {
        for (BvFieldLink link : links) {
          if (!satelliteNames.isEmpty()
              && !containsIgnoreCase(satelliteNames, link.satelliteName())) {
            continue;
          }
          if (sourceFieldName.equalsIgnoreCase(link.sourceFieldName())) {
            bvTargetFields.add(normalize(link.targetFieldName()));
            bvTargetToLink.put(normalize(link.targetFieldName()), link);
          }
        }
      }
    }

    for (ValidationModels.LoadedDimensionalModel loaded : models.dimensionalModels()) {
      if (loaded == null || loaded.model() == null) {
        continue;
      }
      DimensionalModel dmModel = loaded.model();
      DimensionalConfiguration config = dmModel.getConfigurationOrDefault();
      String connection = config != null ? config.getTargetDatabase() : null;
      for (IDmTable table : dmModel.getTables()) {
        if (!(table instanceof DmDimension dimension)) {
          continue;
        }
        DmSourceConfiguration source = dimension.getSourceOrDefault();
        if (source == null || source.resolveSourceType() != DmSourceType.SQL) {
          continue;
        }
        String sql =
            variables != null ? variables.resolve(source.getSourceSql()) : source.getSourceSql();
        if (Utils.isEmpty(sql)) {
          continue;
        }
        Set<String> sqlTables = SqlSourceLineageSupport.extractTableNames(sql);
        Map<String, String> aliases = SqlSourceLineageSupport.extractTableAliases(sql);
        Set<String> starAliases = SqlSourceLineageSupport.extractStarAliases(sql);

        for (BvFieldLink bvLink : bvTargetToLink.values()) {
          String bvPhysical = normalize(bvLink.physicalTableName());
          String bvElement = normalize(bvLink.tableElementName());
          boolean sqlRefsBv = sqlTables.contains(bvPhysical) || sqlTables.contains(bvElement);
          if (!sqlRefsBv) {
            // Also: star alias bound to BV table
            for (String starAlias : starAliases) {
              String bound = aliases.get(starAlias);
              if (bvPhysical.equals(bound) || bvElement.equals(bound)) {
                sqlRefsBv = true;
                break;
              }
            }
          }
          if (!sqlRefsBv) {
            continue;
          }
          String targetField = bvLink.targetFieldName();
          if (!dimensionHasAttribute(dimension, targetField)) {
            continue;
          }
          String physical =
              !Utils.isEmpty(dimension.getTableName())
                  ? dimension.getTableName()
                  : dimension.getName();
          targets.add(
              new RemediationTargetColumn(
                  RemediationTargetColumn.LAYER_DM,
                  dmModel.getName(),
                  dmModel.getFilename(),
                  dimension.getName(),
                  physical,
                  targetField,
                  sourceFieldName,
                  catalogLength,
                  RemediationTargetColumn.CONFIDENCE_DERIVED_VIA_BV,
                  connection));
        }
      }
    }

    return dedupe(targets);
  }

  /** Collect satellite element names from DV usages of the source field. */
  public static Set<String> satelliteNamesFromUsages(
      List<SourceUsage> usages, String sourceFieldName) {
    Set<String> names = new LinkedHashSet<>();
    if (usages == null) {
      return names;
    }
    for (SourceUsage usage : usages) {
      if (usage == null
          || !SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usage.modelType())) {
        continue;
      }
      if (!Utils.isEmpty(sourceFieldName)
          && usage.mappedFields() != null
          && usage.mappedFields().stream()
              .noneMatch(f -> f != null && f.equalsIgnoreCase(sourceFieldName))) {
        continue;
      }
      if (!Utils.isEmpty(usage.modelElementName())) {
        names.add(usage.modelElementName());
      }
    }
    return names;
  }

  private static Map<String, List<BvFieldLink>> indexBvFieldLinks(
      ValidationModels models, IVariables variables) {
    Map<String, List<BvFieldLink>> bySource = new LinkedHashMap<>();
    for (ValidationModels.LoadedBusinessVaultModel loaded : models.businessVaultModels()) {
      if (loaded == null || loaded.model() == null) {
        continue;
      }
      BusinessVaultModel bvModel = loaded.model();
      BusinessVaultConfiguration config = bvModel.getConfigurationOrDefault();
      String connection = config != null ? config.getTargetDatabase() : null;
      for (IBvTable table : bvModel.getTables()) {
        if (!(table instanceof BvScd2Table scd2) || scd2.getFieldMappings() == null) {
          continue;
        }
        String physical =
            !Utils.isEmpty(scd2.getTableName()) ? scd2.getTableName() : scd2.getName();
        for (BvScd2FieldMapping mapping : scd2.getFieldMappings()) {
          if (mapping == null
              || Utils.isEmpty(mapping.getSourceFieldName())
              || Utils.isEmpty(mapping.getTargetFieldName())) {
            continue;
          }
          String source =
              variables != null
                  ? variables.resolve(mapping.getSourceFieldName())
                  : mapping.getSourceFieldName();
          String target =
              variables != null
                  ? variables.resolve(mapping.getTargetFieldName())
                  : mapping.getTargetFieldName();
          String sat =
              variables != null
                  ? variables.resolve(mapping.getSatelliteName())
                  : mapping.getSatelliteName();
          BvFieldLink link =
              new BvFieldLink(
                  bvModel.getName(),
                  bvModel.getFilename(),
                  scd2.getName(),
                  physical,
                  source,
                  target,
                  sat,
                  connection);
          bySource.computeIfAbsent(normalize(source), k -> new ArrayList<>()).add(link);
        }
      }
    }
    return bySource;
  }

  private static boolean dimensionHasAttribute(DmDimension dimension, String fieldName) {
    if (dimension == null || Utils.isEmpty(fieldName)) {
      return false;
    }
    for (DmDimensionAttribute attr : dimension.getAttributesOrEmpty()) {
      if (attr == null) {
        continue;
      }
      if (fieldName.equalsIgnoreCase(attr.getFieldName())) {
        return true;
      }
      if (!Utils.isEmpty(attr.getSourceFieldName())
          && fieldName.equalsIgnoreCase(attr.getSourceFieldName())) {
        return true;
      }
    }
    return false;
  }

  private static List<RemediationTargetColumn> dedupe(List<RemediationTargetColumn> targets) {
    Map<String, RemediationTargetColumn> unique = new LinkedHashMap<>();
    for (RemediationTargetColumn target : targets) {
      if (target == null) {
        continue;
      }
      String key =
          target.layer()
              + "|"
              + normalize(target.modelFilename())
              + "|"
              + normalize(target.tableElementName())
              + "|"
              + normalize(target.targetFieldName());
      unique.putIfAbsent(key, target);
    }
    return new ArrayList<>(unique.values());
  }

  private static boolean containsIgnoreCase(Set<String> values, String candidate) {
    if (values == null || Utils.isEmpty(candidate)) {
      return false;
    }
    for (String value : values) {
      if (candidate.equalsIgnoreCase(value)) {
        return true;
      }
    }
    return false;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private record BvFieldLink(
      String modelName,
      String modelFilename,
      String tableElementName,
      String physicalTableName,
      String sourceFieldName,
      String targetFieldName,
      String satelliteName,
      String connectionName) {}
}
