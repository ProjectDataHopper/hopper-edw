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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLoadCycleSupport;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;

/** Target table layout helpers for Business Vault PIT tables. */
public final class BvPitLayoutSupport {

  private BvPitLayoutSupport() {}

  public static IRowMeta buildTargetTableLayout(
      BvPitTable pitTable, DataVaultModel dvModel, IVariables variables) throws HopException {
    return buildTargetTableLayout(pitTable, null, dvModel, variables);
  }

  public static IRowMeta buildTargetTableLayout(
      BvPitTable pitTable,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      IVariables variables)
      throws HopException {
    if (pitTable == null || dvModel == null) {
      throw new HopException("PIT table layout requires a Data Vault model");
    }

    DvHub hub = resolveHubDerivative(pitTable, dvModel);
    List<DvSatellite> satellites = resolveSatelliteDerivatives(pitTable, dvModel);
    if (hub == null) {
      throw new HopException(
          "PIT table " + pitTable.getName() + " must reference a hub derivative");
    }
    if (satellites.isEmpty() && !hasSourceQuery(pitTable)) {
      throw new HopException(
          "PIT table " + pitTable.getName() + " must reference at least one satellite derivative");
    }

    BvPitSnapshotSchedule schedule = pitTable.getSnapshotScheduleOrDefault();
    String snapshotField = resolveSnapshotDateField(pitTable, variables);
    String hashKeyName = resolveHubHashKeyFieldName(hub, variables);

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(BvScd2PipelineSupport.resolveHashKeyValueMeta(hashKeyName, dvModel));
    rowMeta.addValueMeta(new ValueMetaTimestamp(snapshotField));

    if (satellites.isEmpty()) {
      for (BvSourceQueryRef ref : pitTable.getSourceQueryRefs()) {
        if (ref == null || Utils.isEmpty(ref.getSourceQueryName())) {
          continue;
        }
        String pointerName =
            resolveSourceQueryPointerColumnName(ref.getSourceQueryName(), schedule, variables);
        rowMeta.addValueMeta(new ValueMetaTimestamp(pointerName));
      }
    } else {
      for (DvSatellite satellite : satellites) {
        String pointerName = resolveSatellitePointerColumnName(satellite, schedule, variables);
        if (rowMeta.indexOfValue(pointerName) >= 0) {
          throw new HopException(
              "Duplicate PIT satellite pointer column '"
                  + pointerName
                  + "' on table "
                  + pitTable.getName());
        }
        rowMeta.addValueMeta(new ValueMetaTimestamp(pointerName));
      }
    }
    if (bvConfig != null) {
      DvLoadCycleSupport.appendToLayout(
          rowMeta, bvConfig.isStoreLoadCycleId(), bvConfig.getLoadCycleIdField(), variables);
    }
    return rowMeta;
  }

  public static String resolveSnapshotDateField(BvPitTable pitTable, IVariables variables) {
    String field = pitTable != null ? pitTable.getSnapshotDateField() : null;
    if (variables != null) {
      field = variables.resolve(field);
    }
    if (Utils.isEmpty(field)) {
      return "snapshot_date";
    }
    return field;
  }

  public static String resolveSatellitePointerColumnName(
      DvSatellite satellite, BvPitSnapshotSchedule schedule, IVariables variables) {
    String baseName = resolveSatellitePhysicalName(satellite);
    String suffix = schedule != null ? schedule.getSatellitePointerSuffix() : null;
    if (variables != null) {
      suffix = variables.resolve(suffix);
    }
    if (Utils.isEmpty(suffix)) {
      suffix = BvPitSnapshotSchedule.DEFAULT_SATELLITE_POINTER_SUFFIX;
    }
    return baseName + suffix;
  }

  public static String resolveSourceQueryPointerColumnName(
      BvSourceQuery sourceQuery, BvPitSnapshotSchedule schedule, IVariables variables) {
    String baseName =
        sourceQuery != null
            ? (!Utils.isEmpty(sourceQuery.getTableName())
                ? sourceQuery.getTableName()
                : sourceQuery.getName())
            : "source_query";
    return resolveSourceQueryPointerColumnName(baseName, schedule, variables);
  }

  static String resolveSourceQueryPointerColumnName(
      String baseName, BvPitSnapshotSchedule schedule, IVariables variables) {
    String suffix = schedule != null ? schedule.getSatellitePointerSuffix() : null;
    if (variables != null) {
      suffix = variables.resolve(suffix);
    }
    if (Utils.isEmpty(suffix)) {
      suffix = BvPitSnapshotSchedule.DEFAULT_SATELLITE_POINTER_SUFFIX;
    }
    return (Utils.isEmpty(baseName) ? "source_query" : baseName) + suffix;
  }

  private static boolean hasSourceQuery(BvPitTable pitTable) {
    if (pitTable == null) {
      return false;
    }
    for (BvSourceQueryRef ref : pitTable.getSourceQueryRefs()) {
      if (ref != null && !Utils.isEmpty(ref.getSourceQueryName())) {
        return true;
      }
    }
    return false;
  }

  static String resolveSatellitePhysicalName(DvSatellite satellite) {
    if (satellite == null) {
      return "satellite";
    }
    if (!Utils.isEmpty(satellite.getTableName())) {
      return satellite.getTableName();
    }
    return satellite.getName();
  }

  static String resolveHubHashKeyFieldName(DvHub hub, IVariables variables) {
    if (hub == null) {
      return "hashkey";
    }
    String hashKey = hub.getHashKeyFieldName();
    if (variables != null) {
      hashKey = variables.resolve(hashKey);
    }
    if (!Utils.isEmpty(hashKey)) {
      return hashKey;
    }
    if (hub.getBusinessKeys() != null && !hub.getBusinessKeys().isEmpty()) {
      return hub.getBusinessKeys().get(0).getName() + "_hk";
    }
    return "hashkey";
  }

  public static DvHub resolveHubDerivative(BvPitTable pitTable, DataVaultModel dvModel) {
    if (pitTable == null || dvModel == null) {
      return null;
    }
    for (BvDerivativeRef derivative : pitTable.getDerivatives()) {
      if (derivative == null
          || derivative.getDvTableType() != DvTableType.HUB
          || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      if (dvModel.findHub(derivative.getDvTableName()) instanceof DvHub hub) {
        return hub;
      }
    }
    return null;
  }

  static List<DvSatellite> resolveSatelliteDerivatives(
      BvPitTable pitTable, DataVaultModel dvModel) {
    List<DvSatellite> satellites = new ArrayList<>();
    if (pitTable == null || dvModel == null) {
      return satellites;
    }
    for (BvDerivativeRef derivative : pitTable.getDerivatives()) {
      if (derivative == null
          || derivative.getDvTableType() != DvTableType.SATELLITE
          || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      if (dvModel.findTable(derivative.getDvTableName()) instanceof DvSatellite satellite) {
        satellites.add(satellite);
      }
    }
    return satellites;
  }
}
