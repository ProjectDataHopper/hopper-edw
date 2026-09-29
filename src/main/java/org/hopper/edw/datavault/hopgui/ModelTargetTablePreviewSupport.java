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
package org.hopper.edw.datavault.hopgui;

import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.perspective.database.DatabaseWorkbenchDialog;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSpecialRecordSupport;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvTargetDatabaseSupport;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.hopper.edw.datavault.metadata.dimensional.DmTargetDatabaseSupport;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;

/**
 * Previews target tables for DV, BV, and DM model tables by opening a SELECT query in Hop's
 * Database perspective popup dialog (Issue #189).
 */
public final class ModelTargetTablePreviewSupport {

  public static final int DEFAULT_PREVIEW_ROW_LIMIT = 1000;
  private static final Class<?> PKG = ModelTargetTablePreviewSupport.class;

  private ModelTargetTablePreviewSupport() {}

  /**
   * Builds {@code SELECT * FROM schema.table} plus the dialect row limit clause (e.g. LIMIT 1000 or
   * TOP 1000).
   */
  public static String previewSelectSql(
      DatabaseMeta meta, IVariables variables, String schemaName, String tableName, int rowLimit) {
    if (meta == null || Utils.isEmpty(tableName)) {
      return "";
    }
    String schema = schemaName;
    String table = tableName;
    if (Utils.isEmpty(schema) && table.contains(".")) {
      String[] parts = table.split("\\.", 2);
      schema = parts[0];
      table = parts[1];
    }
    String qualified = meta.getQuotedSchemaTableCombination(variables, schema, table);
    String prefix = rowLimit > 0 ? Const.NVL(meta.getLimitClausePrefix(rowLimit), "") : "";
    String limit = rowLimit > 0 ? Const.NVL(meta.getLimitClause(rowLimit), "") : "";
    return "SELECT" + prefix + " * FROM " + qualified + limit;
  }

  public static void previewDvTargetTable(
      Shell shell,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      IDvTable table) {
    if (table == null) {
      return;
    }
    try {
      if (model == null) {
        throw new HopException(
            BaseMessages.getString(PKG, "ModelTargetTablePreviewSupport.Error.MissingDvModel"));
      }
      DatabaseMeta dbMeta =
          DvSpecialRecordSupport.loadTargetDatabase(
              metadataProvider, model.getConfigurationOrDefault());
      if (dbMeta == null) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "ModelTargetTablePreviewSupport.Error.MissingTargetDatabase"));
      }
      String tableName = resolveTableName(table);
      String sql =
          previewSelectSql(
              dbMeta, variables, null, tableName, DEFAULT_PREVIEW_ROW_LIMIT);
      DatabaseWorkbenchDialog.openSql(dbMeta, sql);
    } catch (Exception e) {
      showError(shell, e);
    }
  }

  public static void previewBvTargetTable(
      Shell shell,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      BusinessVaultModel model,
      IBvTable table) {
    if (table == null) {
      return;
    }
    try {
      if (model == null) {
        throw new HopException(
            BaseMessages.getString(PKG, "ModelTargetTablePreviewSupport.Error.MissingBvModel"));
      }
      DatabaseMeta dbMeta =
          BvTargetDatabaseSupport.loadTargetDatabase(
              metadataProvider, model.getConfigurationOrDefault());
      if (dbMeta == null) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "ModelTargetTablePreviewSupport.Error.MissingTargetDatabase"));
      }
      String schema = null;
      if (table instanceof BvBusinessTable bt && !Utils.isEmpty(bt.getSchemaName())) {
        schema = bt.getSchemaName();
      }
      String tableName = resolveTableName(table);
      String sql =
          previewSelectSql(
              dbMeta, variables, schema, tableName, DEFAULT_PREVIEW_ROW_LIMIT);
      DatabaseWorkbenchDialog.openSql(dbMeta, sql);
    } catch (Exception e) {
      showError(shell, e);
    }
  }

  public static void previewDmTargetTable(
      Shell shell,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DimensionalModel model,
      IDmTable table) {
    if (table == null) {
      return;
    }
    try {
      if (model == null) {
        throw new HopException(
            BaseMessages.getString(PKG, "ModelTargetTablePreviewSupport.Error.MissingDmModel"));
      }
      DatabaseMeta dbMeta =
          DmTargetDatabaseSupport.loadTargetDatabase(
              metadataProvider, model.getConfigurationOrDefault());
      if (dbMeta == null) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "ModelTargetTablePreviewSupport.Error.MissingTargetDatabase"));
      }
      String tableName = resolveTableName(table);
      String sql =
          previewSelectSql(
              dbMeta, variables, null, tableName, DEFAULT_PREVIEW_ROW_LIMIT);
      DatabaseWorkbenchDialog.openSql(dbMeta, sql);
    } catch (Exception e) {
      showError(shell, e);
    }
  }

  private static String resolveTableName(IDvTable table) throws HopException {
    String name = !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
    if (Utils.isEmpty(name)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ModelTargetTablePreviewSupport.Error.MissingTableName"));
    }
    return name;
  }

  private static String resolveTableName(IBvTable table) throws HopException {
    String name = !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
    if (Utils.isEmpty(name)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ModelTargetTablePreviewSupport.Error.MissingTableName"));
    }
    return name;
  }

  private static String resolveTableName(IDmTable table) throws HopException {
    String name = !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
    if (Utils.isEmpty(name)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ModelTargetTablePreviewSupport.Error.MissingTableName"));
    }
    return name;
  }

  private static void showError(Shell shell, Exception e) {
    new ErrorDialog(
        shell,
        BaseMessages.getString(PKG, "ModelTargetTablePreviewSupport.Error.Title"),
        BaseMessages.getString(PKG, "ModelTargetTablePreviewSupport.Error.Message"),
        e instanceof HopException ? e : new HopException(e));
  }
}
