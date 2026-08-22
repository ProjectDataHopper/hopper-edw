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
package org.apache.hop.datavault.metadata.coaching;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;

/** Inverted index of DM table usage keyed by table name. */
public final class CoachingDmTargetIndex {

  private final Map<String, CoachingTargetUsage> targetsByTableName;

  private CoachingDmTargetIndex(Map<String, CoachingTargetUsage> targetsByTableName) {
    this.targetsByTableName = targetsByTableName;
  }

  public static CoachingDmTargetIndex build(DimensionalModel model) {
    Map<String, CoachingTargetUsage> index = new HashMap<>();
    if (model == null || model.getTables() == null) {
      return new CoachingDmTargetIndex(index);
    }
    for (IDmTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getTableName())) {
        continue;
      }
      index.put(
          table.getTableName(),
          CoachingTargetUsage.builder()
              .tableName(table.getTableName())
              .tableRole(table.getTableType().name())
              .summary("table")
              .build());
    }
    return new CoachingDmTargetIndex(index);
  }

  public List<CoachingTargetUsage> targetsFor(CoachingSourceRef sourceRef) {
    if (sourceRef == null || Utils.isEmpty(sourceRef.getDerivedFromTable())) {
      return List.of();
    }
    CoachingTargetUsage target = targetsByTableName.get(sourceRef.getDerivedFromTable());
    if (target == null) {
      return List.of();
    }
    return List.of(
        CoachingTargetUsage.builder()
            .tableName(target.getTableName())
            .tableRole(target.getTableRole())
            .summary(sourceRef.getSourceType().name() + " source")
            .build());
  }
}
