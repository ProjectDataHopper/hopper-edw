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
 *
 */

package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;

/**
 * Helpers for Data Vault table references and hub role-playing aliases on a subject-area canvas.
 */
public final class DvTableReferenceSupport {

  private DvTableReferenceSupport() {}

  public static DvTableType effectiveTableType(IDvTable table) {
    if (table instanceof DvTableReference reference && reference.getReferencedTableType() != null) {
      return reference.getReferencedTableType();
    }
    return table != null ? table.getTableType() : null;
  }

  public static boolean hasTableReference(DataVaultModel model, String tableName) {
    if (model == null || Utils.isEmpty(tableName)) {
      return false;
    }
    IDvTable table = model.findTable(tableName);
    return table instanceof DvTableReference;
  }

  /**
   * Lists physical tables of the given type from {@code sourceModel} that can be referenced.
   *
   * <p>For the classic cross-model picker (default alias name = target name), skips targets whose
   * canvas name is already taken on the subject model. Role-playing aliases with a different name
   * can still target a hub that is already present as a physical table or another alias.
   */
  public static List<String> listAvailableTableNames(
      DataVaultModel sourceModel, DataVaultModel subjectModel, DvTableType tableType) {
    return listAvailableTableNames(sourceModel, subjectModel, tableType, false);
  }

  /**
   * @param allowTargetsAlreadyOnCanvas when true (same-model hub aliases), list all physical tables
   *     of the type even if the subject canvas already has a table with that name
   */
  public static List<String> listAvailableTableNames(
      DataVaultModel sourceModel,
      DataVaultModel subjectModel,
      DvTableType tableType,
      boolean allowTargetsAlreadyOnCanvas) {
    if (sourceModel == null || tableType == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (IDvTable table : sourceModel.getTables()) {
      if (table == null
          || Utils.isEmpty(table.getName())
          || table.getTableType() != tableType
          || table instanceof DvTableReference) {
        continue;
      }
      if (!allowTargetsAlreadyOnCanvas
          && subjectModel != null
          && subjectModel.findTable(table.getName()) != null) {
        continue;
      }
      names.add(table.getName());
    }
    Collections.sort(names);
    return names;
  }

  /** Cross-model reference with canvas name equal to the external table name. */
  public static DvTableReference createReference(
      IDvTable externalTable, String externalModelFilename, Point location) {
    if (externalTable == null
        || Utils.isEmpty(externalTable.getName())
        || externalTable.getTableType() == null
        || externalTable.getTableType() == DvTableType.TABLE_REFERENCE
        || Utils.isEmpty(externalModelFilename)) {
      return null;
    }
    return createAlias(
        externalTable.getName(),
        externalTable,
        externalModelFilename,
        null,
        location);
  }

  /**
   * Creates a table reference/alias. Same-model aliases pass a null/empty {@code
   * referencedModelFilename}. Role-playing hub aliases should set a distinct {@code aliasName} and
   * optional {@code roleHashKeyFieldName}.
   */
  public static DvTableReference createAlias(
      String aliasName,
      IDvTable targetTable,
      String referencedModelFilename,
      String roleHashKeyFieldName,
      Point location) {
    if (targetTable == null
        || Utils.isEmpty(targetTable.getName())
        || targetTable.getTableType() == null
        || targetTable.getTableType() == DvTableType.TABLE_REFERENCE
        || Utils.isEmpty(aliasName)) {
      return null;
    }
    DvTableReference reference = new DvTableReference();
    reference.setName(aliasName);
    reference.setReferencedTableName(targetTable.getName());
    if (!Utils.isEmpty(referencedModelFilename)) {
      reference.setReferencedModelFilename(referencedModelFilename);
    }
    reference.setReferencedTableType(targetTable.getTableType());
    reference.setTableName(
        !Utils.isEmpty(targetTable.getTableName())
            ? targetTable.getTableName()
            : targetTable.getName());
    if (!Utils.isEmpty(roleHashKeyFieldName)) {
      reference.setHashKeyFieldName(roleHashKeyFieldName);
    } else if (!aliasName.equalsIgnoreCase(targetTable.getName())
        && targetTable.getTableType() == DvTableType.HUB) {
      reference.setHashKeyFieldName(
          DvTableResolutionSupport.deriveRoleHashKeyFieldName(aliasName));
    }
    if (location != null) {
      reference.setLocation(new Point(location.x, location.y));
    }
    return reference;
  }
}