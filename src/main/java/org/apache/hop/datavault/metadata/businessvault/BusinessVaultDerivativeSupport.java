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
package org.apache.hop.datavault.metadata.businessvault;

import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;

/** Rules for Business Vault derivative references to Data Vault tables. */
public final class BusinessVaultDerivativeSupport {

  private BusinessVaultDerivativeSupport() {}

  public static boolean isValidDerivativePair(BvTableType bvTableType, DvTableType dvTableType) {
    if (bvTableType == null || dvTableType == null) {
      return false;
    }
    return switch (bvTableType) {
      case SCD2 -> dvTableType == DvTableType.SATELLITE;
      case PIT -> dvTableType == DvTableType.HUB || dvTableType == DvTableType.SATELLITE;
      case BUSINESS_TABLE -> true;
    };
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
    if (!canAddDerivative(bvTable, dvReference)) {
      return false;
    }
    bvTable
        .getDerivatives()
        .add(new BvDerivativeRef(dvReference.getDvTableName(), dvReference.getDvTableType()));
    return true;
  }

  public static boolean addDerivative(IBvTable bvTable, IDvTable dvTable) {
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
    return bvTable
        .getDerivatives()
        .removeIf(ref -> ref != null && dvTableName.equalsIgnoreCase(ref.getDvTableName()));
  }
}
