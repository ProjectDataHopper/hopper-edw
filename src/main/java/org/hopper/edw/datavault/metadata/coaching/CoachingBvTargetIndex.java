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
package org.hopper.edw.datavault.metadata.coaching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2FieldMapping;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;

/** Inverted index of BV table usage keyed by DV derivative table name. */
public final class CoachingBvTargetIndex {

  private final Map<String, List<CoachingTargetUsage>> targetsByDvTableName;

  private CoachingBvTargetIndex(Map<String, List<CoachingTargetUsage>> targetsByDvTableName) {
    this.targetsByDvTableName = targetsByDvTableName;
  }

  public static CoachingBvTargetIndex build(BusinessVaultModel model) {
    Map<String, List<CoachingTargetUsage>> index = new HashMap<>();
    if (model == null || model.getTables() == null) {
      return new CoachingBvTargetIndex(index);
    }
    for (IBvTable table : model.getTables()) {
      if (!(table instanceof BvScd2Table scd2)) {
        continue;
      }
      for (String dvTableName : derivativeNamesFor(scd2)) {
        appendTarget(index, dvTableName, buildScd2Target(scd2, dvTableName));
      }
    }
    return new CoachingBvTargetIndex(index);
  }

  public List<CoachingTargetUsage> targetsFor(CoachingSourceRef sourceRef) {
    if (sourceRef == null || sourceRef.getSourceType() != CoachingSourceType.DV_DERIVATIVE) {
      return List.of();
    }
    String dvTableName = sourceRef.getDerivedDvTableName();
    if (Utils.isEmpty(dvTableName)) {
      return List.of();
    }
    return targetsByDvTableName.getOrDefault(dvTableName, List.of());
  }

  private static List<String> derivativeNamesFor(BvScd2Table scd2) {
    List<String> names = new ArrayList<>();
    if (scd2.getDerivatives() != null) {
      for (BvDerivativeRef derivative : scd2.getDerivatives()) {
        if (derivative != null && !Utils.isEmpty(derivative.getDvTableName())) {
          names.add(derivative.getDvTableName());
        }
      }
    }
    if (scd2.getFieldMappings() != null) {
      for (BvScd2FieldMapping mapping : scd2.getFieldMappings()) {
        if (mapping != null && !Utils.isEmpty(mapping.getSatelliteName())) {
          names.add(mapping.getSatelliteName());
        }
      }
    }
    return names.stream().distinct().toList();
  }

  private static CoachingTargetUsage buildScd2Target(BvScd2Table scd2, String dvTableName) {
    int mapped = 0;
    if (scd2.getFieldMappings() != null) {
      for (BvScd2FieldMapping mapping : scd2.getFieldMappings()) {
        if (mapping != null
            && dvTableName.equals(mapping.getSatelliteName())
            && !Utils.isEmpty(mapping.getSourceFieldName())) {
          mapped++;
        }
      }
    }
    return CoachingTargetUsage.builder()
        .tableName(scd2.getName())
        .tableRole("SCD2")
        .summary(mapped + " field mappings")
        .build();
  }

  private static void appendTarget(
      Map<String, List<CoachingTargetUsage>> index,
      String dvTableName,
      CoachingTargetUsage target) {
    index.computeIfAbsent(dvTableName, key -> new ArrayList<>()).add(target);
  }
}
