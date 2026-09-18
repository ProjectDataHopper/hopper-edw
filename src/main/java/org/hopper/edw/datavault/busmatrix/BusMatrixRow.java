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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;

/** One bus-matrix row (a fact-like table). */
public record BusMatrixRow(
    String factName,
    String physicalTableName,
    String grain,
    String tableType,
    String modelFilename,
    String modelName,
    String business,
    String level1,
    String level2,
    String level3,
    List<String> measures,
    String sourceType,
    String sourceDetail,
    Map<String, BusMatrixCell> cells) {

  public BusMatrixRow(
      String factName,
      String physicalTableName,
      String grain,
      String tableType,
      String modelFilename,
      String modelName,
      String business,
      String level1,
      String level2,
      String level3,
      Map<String, BusMatrixCell> cells) {
    this(
        factName,
        physicalTableName,
        grain,
        tableType,
        modelFilename,
        modelName,
        business,
        level1,
        level2,
        level3,
        List.of(),
        "",
        "",
        cells);
  }

  public BusMatrixRow {
    cells = cells != null ? Map.copyOf(cells) : Map.of();
    measures = measures != null ? List.copyOf(measures) : List.of();
    sourceType = Const.NVL(sourceType, "");
    sourceDetail = Const.NVL(sourceDetail, "");
    business = Const.NVL(business, "");
    level1 = Const.NVL(level1, "");
    level2 = Const.NVL(level2, "");
    level3 = Const.NVL(level3, "");
    factName = Const.NVL(factName, "");
    physicalTableName = Const.NVL(physicalTableName, factName);
    grain = Const.NVL(grain, "");
    tableType = Const.NVL(tableType, "");
    modelFilename = Const.NVL(modelFilename, "");
    modelName = Const.NVL(modelName, "");
  }

  public BusMatrixCell cell(String columnKey) {
    BusMatrixCell cell = cells.get(columnKey);
    return cell != null ? cell : BusMatrixCell.EMPTY;
  }

  public boolean matches(String needle) {
    return contains(factName, needle)
        || contains(physicalTableName, needle)
        || contains(business, needle)
        || contains(level1, needle)
        || contains(level2, needle)
        || contains(level3, needle)
        || contains(grain, needle)
        || contains(modelName, needle);
  }

  public String tooltip() {
    StringBuilder sb = new StringBuilder();
    sb.append("Fact: ").append(factName);
    if (!Utils.isEmpty(physicalTableName) && !physicalTableName.equals(factName)) {
      sb.append(" (").append(physicalTableName).append(")");
    }
    if (!Utils.isEmpty(grain)) {
      sb.append("\nGranularity: ").append(grain);
    } else {
      sb.append("\nGranularity: (not defined)");
    }
    List<String> taxonomy = new ArrayList<>();
    if (!Utils.isEmpty(business)) taxonomy.add(business);
    if (!Utils.isEmpty(level1)) taxonomy.add(level1);
    if (!Utils.isEmpty(level2)) taxonomy.add(level2);
    if (!Utils.isEmpty(level3)) taxonomy.add(level3);
    if (!taxonomy.isEmpty()) {
      sb.append("\nProcess: ").append(String.join(" > ", taxonomy));
    }
    if (!Utils.isEmpty(sourceType)) {
      sb.append("\nSource: ").append(sourceType);
      if (!Utils.isEmpty(sourceDetail)) {
        sb.append(" (").append(sourceDetail).append(")");
      }
    }
    if (!measures.isEmpty()) {
      sb.append("\nMeasures (").append(measures.size()).append("): ");
      if (measures.size() <= 5) {
        sb.append(String.join(", ", measures));
      } else {
        sb.append(String.join(", ", measures.subList(0, 5)))
            .append("... (+")
            .append(measures.size() - 5)
            .append(" more)");
      }
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

  static Map<String, BusMatrixCell> newCellMap() {
    return new LinkedHashMap<>();
  }
}
