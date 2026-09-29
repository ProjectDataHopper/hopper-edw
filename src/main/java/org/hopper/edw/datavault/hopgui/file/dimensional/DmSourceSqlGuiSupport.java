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
package org.hopper.edw.datavault.hopgui.file.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.perspective.database.DatabaseWorkbenchDialog;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSourcePreviewSupport;

/** Preview and field discovery helpers for dimensional source SQL in Hop GUI dialogs. */
public final class DmSourceSqlGuiSupport {

  private static final Class<?> PKG = DmSourceSqlGuiSupport.class;

  private DmSourceSqlGuiSupport() {}

  public static List<String> resolveFieldNames(
      IVariables variables, DatabaseMeta databaseMeta, String sourceSql) throws HopException {
    IRowMeta rowMeta = resolveFieldRowMeta(variables, databaseMeta, sourceSql);
    List<String> fieldNames = new ArrayList<>();
    for (int i = 0; i < rowMeta.size(); i++) {
      String name = rowMeta.getValueMeta(i).getName();
      if (!Utils.isEmpty(name)) {
        fieldNames.add(name);
      }
    }
    if (fieldNames.isEmpty()) {
      throw new HopException(BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.NoFields"));
    }
    return fieldNames;
  }

  public static IRowMeta resolveFieldRowMeta(
      IVariables variables, DatabaseMeta databaseMeta, String sourceSql) throws HopException {
    if (databaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.MissingConnection"));
    }
    String sql = variables != null ? variables.resolve(sourceSql) : sourceSql;
    if (Utils.isEmpty(sql)) {
      throw new HopException(BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.MissingSql"));
    }

    LoggingObject loggingObject = new LoggingObject("DmSourceSqlGuiSupport");
    try (Database database = new Database(loggingObject, variables, databaseMeta)) {
      database.connect();
      IRowMeta rowMeta = database.getQueryFields(sql, false);
      if (rowMeta == null || rowMeta.isEmpty()) {
        throw new HopException(BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.NoFields"));
      }
      return rowMeta;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.ResolveFieldsFailed"), e);
    }
  }

  public static void previewSourceSql(
      Shell shell,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DatabaseMeta databaseMeta,
      String sourceSql) {
    try {
      if (databaseMeta == null) {
        throw new HopException(
            BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.MissingConnection"));
      }
      String sql = variables != null ? variables.resolve(sourceSql) : sourceSql;
      if (Utils.isEmpty(sql)) {
        throw new HopException(
            BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.MissingSql"));
      }

      String limitedSql = DvDatabaseSourcePreviewSupport.applyRowLimit(databaseMeta, sql, 1000);
      DatabaseWorkbenchDialog.openSql(databaseMeta, limitedSql);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.PreviewTitle"),
          BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.PreviewMessage"),
          e instanceof HopException ? e : new HopException(e));
    }
  }
}
