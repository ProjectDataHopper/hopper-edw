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
package org.hopper.edw.datavault.busmatrix;

import java.util.List;
import java.util.Locale;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;

/** One bus-matrix column (a resolved physical/conformed dimension). */
public record BusMatrixColumn(
    String key,
    String label,
    String physicalTableName,
    String tableType,
    String modelFilename,
    String dimensionName,
    List<String> naturalKeys,
    String scdNature,
    String sourceType,
    String sourceDetail) {

  public BusMatrixColumn(
      String key,
      String label,
      String physicalTableName,
      String tableType,
      String modelFilename,
      String dimensionName) {
    this(
        key,
        label,
        physicalTableName,
        tableType,
        modelFilename,
        dimensionName,
        List.of(),
        "",
        "",
        "");
  }

  public BusMatrixColumn {
    key = Const.NVL(key, "");
    label = Const.NVL(label, key);
    physicalTableName = Const.NVL(physicalTableName, label);
    tableType = Const.NVL(tableType, "");
    modelFilename = Const.NVL(modelFilename, "");
    dimensionName = Const.NVL(dimensionName, label);
    naturalKeys = naturalKeys != null ? List.copyOf(naturalKeys) : List.of();
    scdNature = Const.NVL(scdNature, "");
    sourceType = Const.NVL(sourceType, "");
    sourceDetail = Const.NVL(sourceDetail, "");
  }

  public boolean matches(String needle) {
    return contains(label, needle)
        || contains(physicalTableName, needle)
        || contains(dimensionName, needle)
        || contains(scdNature, needle)
        || contains(key, needle);
  }

  public String tooltip() {
    StringBuilder sb = new StringBuilder();
    sb.append("Dimension: ").append(label);
    if (!Utils.isEmpty(dimensionName) && !dimensionName.equals(label)) {
      sb.append(" (").append(dimensionName).append(")");
    }
    if (!Utils.isEmpty(tableType)) {
      sb.append("\nType: ").append(tableType);
    }
    if (!Utils.isEmpty(scdNature)) {
      sb.append("\nSCD Nature: ").append(scdNature);
    }
    if (!naturalKeys.isEmpty()) {
      sb.append("\nNatural Key(s): ").append(String.join(", ", naturalKeys));
    }
    if (!Utils.isEmpty(sourceType)) {
      sb.append("\nSource: ").append(sourceType);
      if (!Utils.isEmpty(sourceDetail)) {
        sb.append(" (").append(sourceDetail).append(")");
      }
    }
    if (!Utils.isEmpty(modelFilename)) {
      sb.append("\nModel: ").append(modelFilename);
    }
    return sb.toString();
  }

  public boolean hasRecordDefinitionSource() {
    return !Utils.isEmpty(sourceType)
        && sourceType.toUpperCase(Locale.ROOT).contains("RECORD_DEFINITION")
        && !Utils.isEmpty(sourceDetail)
        && sourceDetail.contains("/");
  }

  public String recordDefinitionNamespace() {
    return hasRecordDefinitionSource() ? sourceDetail.substring(0, sourceDetail.indexOf('/')) : "";
  }

  public String recordDefinitionName() {
    return hasRecordDefinitionSource() ? sourceDetail.substring(sourceDetail.indexOf('/') + 1) : "";
  }

  private static boolean contains(String value, String needle) {
    return !Utils.isEmpty(value) && value.toLowerCase(Locale.ROOT).contains(needle);
  }
}
