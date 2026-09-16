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

import org.apache.hop.core.Const;

/** UTF-8 CSV (with BOM) for Excel. */
public final class BusMatrixCsvWriter {

  private BusMatrixCsvWriter() {}

  public static String write(BusMatrix matrix) {
    BusMatrix safe = matrix != null ? matrix : new BusMatrix("", null, null, null);
    StringBuilder csv = new StringBuilder();
    csv.append('\uFEFF');
    csv.append("Business,Level 1,Level 2,Level 3,Fact,Grain,Model,Model file,Table type");
    for (BusMatrixColumn column : safe.getColumns()) {
      csv.append(',').append(quote(column.label()));
    }
    csv.append('\n');
    for (BusMatrixRow row : safe.getRows()) {
      csv.append(quote(row.business()))
          .append(',')
          .append(quote(row.level1()))
          .append(',')
          .append(quote(row.level2()))
          .append(',')
          .append(quote(row.level3()))
          .append(',')
          .append(quote(row.factName()))
          .append(',')
          .append(quote(row.grain()))
          .append(',')
          .append(quote(row.modelName()))
          .append(',')
          .append(quote(row.modelFilename()))
          .append(',')
          .append(quote(row.tableType()));
      for (BusMatrixColumn column : safe.getColumns()) {
        csv.append(',').append(quote(row.cell(column.key()).mark()));
      }
      csv.append('\n');
    }
    return csv.toString();
  }

  private static String quote(String value) {
    String text = Const.NVL(value, "");
    if (text.indexOf('"') >= 0 || text.indexOf(',') >= 0 || text.indexOf('\n') >= 0) {
      return '"' + text.replace("\"", "\"\"") + '"';
    }
    return text;
  }
}
