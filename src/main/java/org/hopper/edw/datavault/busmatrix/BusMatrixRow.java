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

import java.util.LinkedHashMap;
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
    Map<String, BusMatrixCell> cells) {

  public BusMatrixRow {
    cells = cells != null ? Map.copyOf(cells) : Map.of();
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

  private static boolean contains(String value, String needle) {
    return !Utils.isEmpty(value) && value.toLowerCase(Locale.ROOT).contains(needle);
  }

  static Map<String, BusMatrixCell> newCellMap() {
    return new LinkedHashMap<>();
  }
}
