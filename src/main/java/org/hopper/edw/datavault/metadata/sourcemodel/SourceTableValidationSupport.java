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
package org.hopper.edw.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceTablePreviewSupport;

/** Structural checks for a single {@link SourceTable}. */
public final class SourceTableValidationSupport {

  private static final Class<?> PKG = SourceModel.class;

  private SourceTableValidationSupport() {}

  public static List<ICheckResult> check(
      SourceModel model, SourceTable table, IVariables variables) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (table == null) {
      return remarks;
    }
    String tableName = ConstNvl(table.getName());
    if (Utils.isEmpty(table.getName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SourceModel.CheckResult.TableMissingName"),
              null));
      return remarks;
    }

    String connection = SourceTablePreviewSupport.resolveConnectionName(model, table, variables);
    if (Utils.isEmpty(connection)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.TableMissingConnection", tableName),
              null));
    }
    String physical =
        !Utils.isEmpty(table.getTableName()) ? table.getTableName().trim() : table.getName();
    if (Utils.isEmpty(physical)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.TableMissingPhysicalName", tableName),
              null));
    }

    if (table.getColumns().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(PKG, "SourceModel.CheckResult.TableEmptyColumns", tableName),
              null));
    }

    Set<String> columnNames = new HashSet<>();
    int missingType = 0;
    int duplicatePkPos = 0;
    Set<Integer> pkPositions = new HashSet<>();
    boolean hasPk = false;
    for (SourceColumn column : table.getColumns()) {
      if (column == null) {
        continue;
      }
      if (Utils.isEmpty(column.getName())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.TableColumnMissingName", tableName),
                null));
        continue;
      }
      String colName = column.getName().trim();
      if (!columnNames.add(colName.toLowerCase())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.TableDuplicateColumn", tableName, colName),
                null));
      }
      if (column.getHopType() <= 0) {
        missingType++;
      }
      if (column.getPrimaryKeyPosition() > 0) {
        hasPk = true;
        if (!pkPositions.add(column.getPrimaryKeyPosition())) {
          duplicatePkPos++;
        }
      }
    }
    if (missingType > 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "SourceModel.CheckResult.TableColumnsMissingType",
                  tableName,
                  Integer.toString(missingType)),
              null));
    }
    if (duplicatePkPos > 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.TableDuplicatePkPosition", tableName),
              null));
    }
    if (!table.getColumns().isEmpty() && !hasPk) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.TableMissingPrimaryKey", tableName),
              null));
    }
    return remarks;
  }

  private static String ConstNvl(String value) {
    return Utils.isEmpty(value) ? "?" : value;
  }
}
