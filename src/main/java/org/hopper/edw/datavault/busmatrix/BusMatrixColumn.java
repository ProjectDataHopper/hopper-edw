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
    String dimensionName) {

  public BusMatrixColumn {
    key = Const.NVL(key, "");
    label = Const.NVL(label, key);
    physicalTableName = Const.NVL(physicalTableName, label);
    tableType = Const.NVL(tableType, "");
    modelFilename = Const.NVL(modelFilename, "");
    dimensionName = Const.NVL(dimensionName, label);
  }

  public boolean matches(String needle) {
    return contains(label, needle)
        || contains(physicalTableName, needle)
        || contains(dimensionName, needle)
        || contains(key, needle);
  }

  private static boolean contains(String value, String needle) {
    return !Utils.isEmpty(value) && value.toLowerCase(Locale.ROOT).contains(needle);
  }
}
