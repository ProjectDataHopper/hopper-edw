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
package org.hopper.edw.datavault.lineage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.resourcedefinition.ValidationModels;

/**
 * Builds a {@link ReverseLineageIndex} from model-derived lineage, including one-hop expansion so a
 * source feed field shows BV/DM consumers that sit behind intermediate DV tables.
 */
public final class ReverseLineageIndexBuilder {

  private ReverseLineageIndexBuilder() {}

  public static ReverseLineageIndex build(
      ValidationModels models, IVariables variables, IHopMetadataProvider metadataProvider) {
    ReverseLineageIndex index = new ReverseLineageIndex();
    if (models == null) {
      return index;
    }

    List<LineageSnapshot> snapshots = new ArrayList<>();
    Map<String, LineageSnapshot> dvByModel = new LinkedHashMap<>();

    for (ValidationModels.LoadedDataVaultModel loaded : models.dataVaultModels()) {
      if (loaded == null || loaded.model() == null) {
        continue;
      }
      DataVaultModel model = loaded.model();
      LineageSnapshot snapshot =
          DvModelLineageCollector.collect(
              model, variables, metadataProvider, loaded.catalogConnection());
      snapshots.add(snapshot);
      String key = modelKey(snapshot.getModelName(), snapshot.getModelFilename());
      dvByModel.put(key, snapshot);
    }
    for (ValidationModels.LoadedBusinessVaultModel loaded : models.businessVaultModels()) {
      if (loaded == null || loaded.model() == null) {
        continue;
      }
      BusinessVaultModel model = loaded.model();
      snapshots.add(BvModelLineageCollector.collect(model, variables));
    }
    for (ValidationModels.LoadedDimensionalModel loaded : models.dimensionalModels()) {
      if (loaded == null || loaded.model() == null) {
        continue;
      }
      DimensionalModel model = loaded.model();
      snapshots.add(DmModelLineageCollector.collect(model, variables, metadataProvider));
    }

    // Direct edges from every snapshot
    for (LineageSnapshot snapshot : snapshots) {
      indexSnapshot(index, snapshot, 1, null);
    }

    // Multi-hop: for BV/DM contributions whose source is a DV table field, also index under the
    // original DV_SOURCE fields that feed that DV field.
    Map<String, List<ReverseLineageKey>> dvFieldOrigins = buildDvFieldOrigins(dvByModel);
    for (LineageSnapshot snapshot : snapshots) {
      if (snapshot == null || snapshot.getModelLayer() == LineageLayer.DV) {
        continue;
      }
      for (TableLineage table : snapshot.getTables()) {
        if (table == null) {
          continue;
        }
        for (FieldLineage field : table.getFields()) {
          if (field == null) {
            continue;
          }
          for (FieldContribution contribution : field.getContributions()) {
            if (contribution == null
                || contribution.getSourceKind() != TableSourceKind.DV_TABLE
                || Utils.isEmpty(contribution.getSourceName())
                || Utils.isEmpty(contribution.getSourceFieldName())) {
              continue;
            }
            String dvFieldKey =
                contribution.getSourceName().toLowerCase(Locale.ROOT)
                    + "."
                    + contribution.getSourceFieldName().toLowerCase(Locale.ROOT);
            List<ReverseLineageKey> origins = dvFieldOrigins.get(dvFieldKey);
            if (origins == null || origins.isEmpty()) {
              continue;
            }
            ReverseLineageConsumer hop2 =
                toConsumer(
                    snapshot,
                    table,
                    field,
                    contribution,
                    2,
                    origins.get(0).display()
                        + " → "
                        + contribution.getSourceName()
                        + "."
                        + contribution.getSourceFieldName()
                        + " → "
                        + table.getLogicalName()
                        + "."
                        + field.getTargetFieldName());
            for (ReverseLineageKey origin : origins) {
              index.add(origin, hop2);
            }
          }
        }
      }
    }

    return index;
  }

  private static void indexSnapshot(
      ReverseLineageIndex index, LineageSnapshot snapshot, int hopCount, String pathPrefix) {
    if (snapshot == null) {
      return;
    }
    for (TableLineage table : snapshot.getTables()) {
      if (table == null) {
        continue;
      }
      for (FieldLineage field : table.getFields()) {
        if (field == null) {
          continue;
        }
        for (FieldContribution contribution : field.getContributions()) {
          if (contribution == null || Utils.isEmpty(contribution.getSourceFieldName())) {
            continue;
          }
          // Index feed/table sources only (not pure CONFIG technical columns without source field)
          if (contribution.getSourceKind() == TableSourceKind.CONFIG
              && Utils.isEmpty(contribution.getSourceName())) {
            continue;
          }
          String path =
              (pathPrefix != null ? pathPrefix + " → " : "")
                  + nvl(contribution.getSourceName())
                  + "."
                  + contribution.getSourceFieldName()
                  + " → "
                  + nvl(table.getLogicalName())
                  + "."
                  + nvl(field.getTargetFieldName());
          ReverseLineageKey key =
              new ReverseLineageKey(
                  contribution.getSourceName(), contribution.getSourceFieldName());
          index.add(key, toConsumer(snapshot, table, field, contribution, hopCount, path));
        }
      }
    }
  }

  /** Map {@code dvTable.field} (lower) → original DV_SOURCE keys that contribute to that field. */
  private static Map<String, List<ReverseLineageKey>> buildDvFieldOrigins(
      Map<String, LineageSnapshot> dvByModel) {
    Map<String, List<ReverseLineageKey>> origins = new LinkedHashMap<>();
    for (LineageSnapshot snapshot : dvByModel.values()) {
      if (snapshot == null) {
        continue;
      }
      for (TableLineage table : snapshot.getTables()) {
        if (table == null || Utils.isEmpty(table.getLogicalName())) {
          continue;
        }
        for (FieldLineage field : table.getFields()) {
          if (field == null || Utils.isEmpty(field.getTargetFieldName())) {
            continue;
          }
          String fieldKey =
              table.getLogicalName().toLowerCase(Locale.ROOT)
                  + "."
                  + field.getTargetFieldName().toLowerCase(Locale.ROOT);
          List<ReverseLineageKey> keys = new ArrayList<>();
          for (FieldContribution contribution : field.getContributions()) {
            if (contribution == null
                || contribution.getSourceKind() != TableSourceKind.DV_SOURCE
                || Utils.isEmpty(contribution.getSourceFieldName())) {
              continue;
            }
            keys.add(
                new ReverseLineageKey(
                    contribution.getSourceName(), contribution.getSourceFieldName()));
          }
          if (!keys.isEmpty()) {
            origins.put(fieldKey, keys);
          }
        }
      }
    }
    return origins;
  }

  private static ReverseLineageConsumer toConsumer(
      LineageSnapshot snapshot,
      TableLineage table,
      FieldLineage field,
      FieldContribution contribution,
      int hopCount,
      String pathSummary) {
    List<String> codes =
        contribution.getReasons().stream()
            .filter(r -> r != null && r.getCode() != null)
            .map(r -> r.getCode().name())
            .collect(Collectors.toList());
    return ReverseLineageConsumer.builder()
        .layer(table.getLayer() != null ? table.getLayer() : snapshot.getModelLayer())
        .modelName(snapshot.getModelName())
        .modelFilename(snapshot.getModelFilename())
        .tableName(table.getLogicalName())
        .tableType(table.getTableType())
        .targetField(field.getTargetFieldName())
        .transform(contribution.getTransform() != null ? contribution.getTransform().name() : null)
        .reasonCodes(codes)
        .pathSummary(pathSummary)
        .hopCount(hopCount)
        .build();
  }

  private static String modelKey(String name, String filename) {
    return nvl(name) + "|" + nvl(filename);
  }

  private static String nvl(String value) {
    return value != null ? value : "";
  }
}
