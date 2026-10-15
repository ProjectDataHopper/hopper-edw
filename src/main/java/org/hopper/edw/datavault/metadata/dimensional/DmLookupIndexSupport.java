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
package org.hopper.edw.datavault.metadata.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DvIndexDdlSupport;
import org.hopper.edw.datavault.metadata.dimensional.pipeline.DmPipelineBuilderSupport;

/** Lookup indexes for dimensional target tables. Facts that only insert are not indexed. */
public final class DmLookupIndexSupport {

  private DmLookupIndexSupport() {}

  public static void appendLookupIndexes(
      List<String> result,
      Database db,
      DatabaseMeta databaseMeta,
      IVariables variables,
      String tableName,
      IRowMeta layout,
      List<String> primaryKeyColumns,
      DmTableBase table,
      DimensionalConfiguration config)
      throws HopException {
    for (IndexSpec spec : indexSpecs(table, config, variables)) {
      DvIndexDdlSupport.appendIndex(
          result,
          db,
          databaseMeta,
          variables,
          null,
          tableName,
          spec.suffix(),
          spec.columns(),
          primaryKeyColumns,
          layout);
    }
  }

  static List<IndexSpec> indexSpecs(
      DmTableBase table, DimensionalConfiguration config, IVariables variables) {
    if (table instanceof DmDimension dimension) {
      return dimensionIndexes(dimension, config, variables);
    }
    if (table instanceof DmJunkDimension junk) {
      return junkIndexes(junk, config, variables);
    }
    if (table instanceof DmBridge bridge) {
      return List.of(new IndexSpec(DvIndexDdlSupport.SUFFIX_LOOKUP, bridgeKeys(bridge, variables)));
    }
    if (table instanceof DmAccumulatingSnapshotFact fact) {
      return List.of(
          new IndexSpec(
              DvIndexDdlSupport.SUFFIX_LOOKUP,
              DmPipelineBuilderSupport.naturalKeyFieldNamesFromList(
                  fact.getGrainKeysOrEmpty(), variables)));
    }
    return List.of();
  }

  private static List<IndexSpec> dimensionIndexes(
      DmDimension dimension, DimensionalConfiguration config, IVariables variables) {
    List<IndexSpec> specs = new ArrayList<>();
    List<String> naturalKeys =
        new ArrayList<>(DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, variables));
    if (DmDimensionLoadStrategySupport.usesVersionedLayout(dimension)) {
      String dateFrom = config.resolveDateFromField(variables);
      if (!Utils.isEmpty(dateFrom)
          && naturalKeys.stream().noneMatch(key -> key.equalsIgnoreCase(dateFrom))) {
        naturalKeys.add(dateFrom);
      }
    }
    if (!naturalKeys.isEmpty()) {
      specs.add(new IndexSpec(DvIndexDdlSupport.SUFFIX_NATURAL_KEY, naturalKeys));
    }
    if (DmSurrogateKeySupport.usesSurrogateColumn(dimension)) {
      String surrogate =
          DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, config, variables);
      boolean sameAsNaturalKey =
          naturalKeys.size() == 1 && naturalKeys.get(0).equalsIgnoreCase(surrogate);
      if (!Utils.isEmpty(surrogate) && !sameAsNaturalKey) {
        specs.add(new IndexSpec(DvIndexDdlSupport.SUFFIX_TECHNICAL_KEY, List.of(surrogate)));
      }
    }
    return specs;
  }

  private static List<IndexSpec> junkIndexes(
      DmJunkDimension junk, DimensionalConfiguration config, IVariables variables) {
    String hashField = DmJunkDimensionSupport.resolveHashKeyIndexField(junk, config, variables);
    if (!Utils.isEmpty(hashField)) {
      return List.of(new IndexSpec(DvIndexDdlSupport.SUFFIX_HASH_KEY, List.of(hashField)));
    }
    List<String> keyFields =
        DmPipelineBuilderSupport.naturalKeyFieldNamesFromList(junk.getKeyFieldsOrEmpty(), variables);
    if (keyFields.isEmpty()) {
      return List.of();
    }
    return List.of(new IndexSpec(DvIndexDdlSupport.SUFFIX_NATURAL_KEY, keyFields));
  }

  private static List<String> bridgeKeys(DmBridge bridge, IVariables variables) {
    List<String> keys = new ArrayList<>();
    for (DmBridgeDimensionRef ref : bridge.getDimensionRefsOrEmpty()) {
      if (ref == null || Utils.isEmpty(ref.getForeignKeyColumn())) {
        continue;
      }
      String fkColumn = ref.getForeignKeyColumn();
      if (variables != null) {
        fkColumn = variables.resolve(fkColumn);
      }
      if (!Utils.isEmpty(fkColumn)) {
        keys.add(fkColumn);
      }
    }
    return keys;
  }

  record IndexSpec(String suffix, List<String> columns) {}
}
