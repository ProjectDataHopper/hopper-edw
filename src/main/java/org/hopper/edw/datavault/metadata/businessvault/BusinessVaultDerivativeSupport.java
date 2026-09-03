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

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;

/** Rules for Business Vault derivative references to Data Vault tables. */
public final class BusinessVaultDerivativeSupport {

  private BusinessVaultDerivativeSupport() {}

  public static boolean isValidDerivativePair(BvTableType bvTableType, DvTableType dvTableType) {
    if (bvTableType == null || dvTableType == null) {
      return false;
    }
    return switch (bvTableType) {
      case SCD2 -> dvTableType == DvTableType.SATELLITE || dvTableType == DvTableType.HUB;
      case PIT -> dvTableType == DvTableType.HUB || dvTableType == DvTableType.SATELLITE;
      case BUSINESS_TABLE -> true;
      case SOURCE_QUERY -> false;
    };
  }

  /** Satellite (or linked-table) feed for an SCD2 table — not the parent hub. */
  public static boolean isSatelliteDerivative(BvDerivativeRef ref) {
    if (ref == null || Utils.isEmpty(ref.getDvTableName())) {
      return false;
    }
    return ref.getDvTableType() == null
        || ref.getDvTableType() == DvTableType.SATELLITE
        || ref.getDvTableType().isLinkedTable();
  }

  public static boolean isHubDerivative(BvDerivativeRef ref) {
    return ref != null
        && !Utils.isEmpty(ref.getDvTableName())
        && ref.getDvTableType() == DvTableType.HUB;
  }

  public static String findHubDerivativeName(IBvTable bvTable) {
    if (bvTable == null) {
      return null;
    }
    for (BvDerivativeRef ref : bvTable.getDerivatives()) {
      if (isHubDerivative(ref)) {
        return ref.getDvTableName();
      }
    }
    return null;
  }

  /**
   * Declared parent hub: {@link BvScd2Table#getParentHubName()} or a HUB derivative. Empty means
   * infer from linked satellites.
   */
  public static String resolveDeclaredParentHubName(BvScd2Table scd2Table) {
    if (scd2Table == null) {
      return null;
    }
    if (!Utils.isEmpty(scd2Table.getParentHubName())) {
      return scd2Table.getParentHubName().trim();
    }
    return findHubDerivativeName(scd2Table);
  }

  /**
   * Hub to draw on the BV canvas: declared parent hub, else the shared parent of satellite
   * derivatives when every satellite points at the same hub.
   */
  public static String resolveCanvasParentHubName(
      BvScd2Table scd2Table, DataVaultModel dataVaultModel) {
    Map<String, IDvTable> byName = null;
    if (dataVaultModel != null) {
      byName = new LinkedHashMap<>();
      for (IDvTable table : dataVaultModel.getTables()) {
        if (table != null && !Utils.isEmpty(table.getName())) {
          byName.putIfAbsent(table.getName(), table);
        }
      }
    }
    return resolveCanvasParentHubName(scd2Table, byName);
  }

  public static String resolveCanvasParentHubName(
      BvScd2Table scd2Table, Map<String, ? extends IDvTable> dvTables) {
    String declared = resolveDeclaredParentHubName(scd2Table);
    if (!Utils.isEmpty(declared)) {
      return declared;
    }
    if (scd2Table == null || dvTables == null || dvTables.isEmpty()) {
      return null;
    }
    String inferred = null;
    for (BvDerivativeRef ref : scd2Table.getDerivatives()) {
      if (!isSatelliteDerivative(ref)) {
        continue;
      }
      IDvTable table = lookupDvTable(dvTables, ref.getDvTableName());
      if (!(table instanceof DvSatellite satellite) || Utils.isEmpty(satellite.getHubName())) {
        continue;
      }
      if (inferred == null) {
        inferred = satellite.getHubName();
      } else if (!inferred.equalsIgnoreCase(satellite.getHubName())) {
        return null;
      }
    }
    return inferred;
  }

  /**
   * Links exactly one hub to an SCD2 table (canvas grain / business keys). Replaces any previous
   * hub derivative and writes {@code parentHubName}. Empty {@code hubName} clears the link.
   *
   * @return true when the model changed
   */
  public static boolean setParentHub(BvScd2Table scd2Table, String hubName) {
    if (scd2Table == null) {
      return false;
    }
    String name = hubName == null ? "" : hubName.trim();
    String existingDerivative = findHubDerivativeName(scd2Table);
    String existingParent =
        scd2Table.getParentHubName() == null ? "" : scd2Table.getParentHubName().trim();
    if (Utils.isEmpty(name)) {
      boolean changed = !Utils.isEmpty(existingDerivative) || !Utils.isEmpty(existingParent);
      removeHubDerivatives(scd2Table);
      scd2Table.setParentHubName(null);
      return changed;
    }
    if (name.equalsIgnoreCase(existingDerivative) && name.equalsIgnoreCase(existingParent)) {
      return false;
    }
    removeHubDerivatives(scd2Table);
    scd2Table.getDerivatives().add(0, new BvDerivativeRef(name, DvTableType.HUB));
    scd2Table.setParentHubName(name);
    return true;
  }

  public static boolean hasDerivative(IBvTable bvTable, String dvTableName) {
    if (bvTable == null || Utils.isEmpty(dvTableName)) {
      return false;
    }
    return bvTable.getDerivatives().stream()
        .anyMatch(ref -> ref != null && dvTableName.equalsIgnoreCase(ref.getDvTableName()));
  }

  public static boolean canAddDerivative(IBvTable bvTable, BvDvTableReference dvReference) {
    if (bvTable == null || dvReference == null || Utils.isEmpty(dvReference.getDvTableName())) {
      return false;
    }
    if (hasDerivative(bvTable, dvReference.getDvTableName())) {
      return false;
    }
    return isValidDerivativePair(bvTable.getTableType(), dvReference.getDvTableType());
  }

  public static boolean addDerivative(IBvTable bvTable, BvDvTableReference dvReference) {
    if (bvTable instanceof BvScd2Table scd2
        && dvReference != null
        && dvReference.getDvTableType() == DvTableType.HUB) {
      if (hasDerivative(scd2, dvReference.getDvTableName())) {
        return false;
      }
      return setParentHub(scd2, dvReference.getDvTableName());
    }
    if (!canAddDerivative(bvTable, dvReference)) {
      return false;
    }
    bvTable
        .getDerivatives()
        .add(new BvDerivativeRef(dvReference.getDvTableName(), dvReference.getDvTableType()));
    return true;
  }

  public static boolean addDerivative(IBvTable bvTable, IDvTable dvTable) {
    if (bvTable instanceof BvScd2Table scd2
        && dvTable != null
        && dvTable.getTableType() == DvTableType.HUB) {
      if (Utils.isEmpty(dvTable.getName()) || hasDerivative(scd2, dvTable.getName())) {
        return false;
      }
      return setParentHub(scd2, dvTable.getName());
    }
    if (bvTable == null || dvTable == null || Utils.isEmpty(dvTable.getName())) {
      return false;
    }
    if (hasDerivative(bvTable, dvTable.getName())) {
      return false;
    }
    if (!isValidDerivativePair(bvTable.getTableType(), dvTable.getTableType())) {
      return false;
    }
    bvTable.getDerivatives().add(new BvDerivativeRef(dvTable.getName(), dvTable.getTableType()));
    return true;
  }

  public static boolean removeDerivative(IBvTable bvTable, String dvTableName) {
    if (bvTable == null || Utils.isEmpty(dvTableName)) {
      return false;
    }
    boolean removed =
        bvTable
            .getDerivatives()
            .removeIf(ref -> ref != null && dvTableName.equalsIgnoreCase(ref.getDvTableName()));
    if (removed
        && bvTable instanceof BvScd2Table scd2
        && dvTableName.equalsIgnoreCase(scd2.getParentHubName())) {
      scd2.setParentHubName(null);
    }
    return removed;
  }

  private static void removeHubDerivatives(BvScd2Table scd2Table) {
    scd2Table.getDerivatives().removeIf(BusinessVaultDerivativeSupport::isHubDerivative);
  }

  private static IDvTable lookupDvTable(Map<String, ? extends IDvTable> dvTables, String name) {
    if (dvTables == null || Utils.isEmpty(name)) {
      return null;
    }
    IDvTable exact = dvTables.get(name);
    if (exact != null) {
      return exact;
    }
    for (Map.Entry<String, ? extends IDvTable> entry : dvTables.entrySet()) {
      if (name.equalsIgnoreCase(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }
}
