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
package org.apache.hop.datavault.hopgui;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.dialog.ShowRowsDialog;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.eclipse.swt.widgets.Shell;

/** Previews the projected target table layout for DV, BV, and DM model tables. */
public final class ModelTableLayoutPreviewSupport {

  public static final String COL_INDEX = "#";
  public static final String COL_FIELD = "field";
  public static final String COL_TYPE = "type";
  public static final String COL_LENGTH = "length";
  public static final String COL_PRECISION = "precision";

  private static final Class<?> PKG = ModelTableLayoutPreviewSupport.class;

  private ModelTableLayoutPreviewSupport() {}

  public static void previewDmTableLayout(
      Shell shell,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DimensionalModel model,
      IDmTable table) {
    if (shell == null || table == null) {
      return;
    }
    try {
      IRowMeta layout = resolveDmTableLayout(metadataProvider, variables, model, table);
      openLayoutPreview(shell, variables, resolveTableLabel(table), layout);
    } catch (HopException e) {
      showLayoutError(shell, e);
    }
  }

  public static void previewDvTableLayout(
      Shell shell,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      IDvTable table) {
    if (shell == null || table == null) {
      return;
    }
    try {
      IRowMeta layout = resolveDvTableLayout(metadataProvider, variables, model, table);
      openLayoutPreview(shell, variables, resolveTableLabel(table), layout);
    } catch (HopException e) {
      showLayoutError(shell, e);
    }
  }

  public static void previewBvTableLayout(
      Shell shell,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      BusinessVaultModel bvModel,
      IBvTable table) {
    if (shell == null || table == null) {
      return;
    }
    try {
      IRowMeta layout = resolveBvTableLayout(metadataProvider, variables, bvModel, table);
      openLayoutPreview(shell, variables, resolveTableLabel(table), layout);
    } catch (HopException e) {
      showLayoutError(shell, e);
    }
  }

  public static IRowMeta resolveDmTableLayout(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model,
      IDmTable table)
      throws HopException {
    if (table == null) {
      throw new HopException("Dimensional table is required");
    }
    return table.getTargetTableLayout(metadataProvider, variables, model);
  }

  public static IRowMeta resolveDvTableLayout(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      IDvTable table)
      throws HopException {
    if (table == null) {
      throw new HopException("Data Vault table is required");
    }
    return table.getTargetTableLayout(metadataProvider, variables, model);
  }

  public static IRowMeta resolveBvTableLayout(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      IBvTable table)
      throws HopException {
    if (table == null) {
      throw new HopException("Business Vault table is required");
    }
    if (bvModel == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "ModelTableLayoutPreviewSupport.Error.MissingBvModel"));
    }
    DataVaultModel dvModel = loadReferencedDataVaultModel(bvModel, variables, metadataProvider);
    return table.getTargetTableLayout(metadataProvider, variables, bvModel, dvModel);
  }

  public static DataVaultModel loadReferencedDataVaultModel(
      BusinessVaultModel bvModel, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (bvModel == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "ModelTableLayoutPreviewSupport.Error.MissingDvModel"));
    }
    DataVaultModel effective =
        BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(
            bvModel, variables, metadataProvider);
    if (effective.getTables().isEmpty()
        && bvModel.getDvReferences().isEmpty()
        && Utils.isEmpty(bvModel.getDataVaultModelPath())) {
      throw new HopException(
          BaseMessages.getString(PKG, "ModelTableLayoutPreviewSupport.Error.MissingDvModel"));
    }
    return effective;
  }

  public static MetadataPreview buildMetadataPreviewRows(IRowMeta layout) {
    IRowMeta previewMeta = createMetadataPreviewRowMeta();
    List<Object[]> previewRows = new ArrayList<>();
    if (layout == null) {
      return new MetadataPreview(previewMeta, previewRows);
    }
    for (int i = 0; i < layout.size(); i++) {
      IValueMeta valueMeta = layout.getValueMeta(i);
      if (valueMeta == null) {
        continue;
      }
      previewRows.add(
          new Object[] {
            (long) (previewRows.size() + 1),
            Const.NVL(valueMeta.getName(), ""),
            Const.NVL(valueMeta.getTypeDesc(), ""),
            (long) valueMeta.getLength(),
            (long) valueMeta.getPrecision()
          });
    }
    return new MetadataPreview(previewMeta, previewRows);
  }

  public static IRowMeta createMetadataPreviewRowMeta() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger(COL_INDEX));
    rowMeta.addValueMeta(new ValueMetaString(COL_FIELD));
    rowMeta.addValueMeta(new ValueMetaString(COL_TYPE));
    rowMeta.addValueMeta(new ValueMetaInteger(COL_LENGTH));
    rowMeta.addValueMeta(new ValueMetaInteger(COL_PRECISION));
    return rowMeta;
  }

  private static void openLayoutPreview(
      Shell shell, IVariables variables, String tableLabel, IRowMeta layout) {
    MetadataPreview preview = buildMetadataPreviewRows(layout);
    new ShowRowsDialog(
            shell,
            variables,
            BaseMessages.getString(PKG, "ModelTableLayoutPreviewSupport.Preview.Title"),
            BaseMessages.getString(
                PKG, "ModelTableLayoutPreviewSupport.Preview.Message", tableLabel),
            preview.previewMeta(),
            preview.previewRows())
        .open();
  }

  private static void showLayoutError(Shell shell, HopException exception) {
    new ErrorDialog(
        shell,
        BaseMessages.getString(PKG, "ModelTableLayoutPreviewSupport.Error.LayoutTitle"),
        BaseMessages.getString(PKG, "ModelTableLayoutPreviewSupport.Error.LayoutMessage"),
        exception);
  }

  private static String resolveTableLabel(IDmTable table) {
    if (!Utils.isEmpty(table.getTableName())) {
      return table.getTableName();
    }
    return table.getName();
  }

  private static String resolveTableLabel(IDvTable table) {
    if (!Utils.isEmpty(table.getTableName())) {
      return table.getTableName();
    }
    return table.getName();
  }

  private static String resolveTableLabel(IBvTable table) {
    if (!Utils.isEmpty(table.getTableName())) {
      return table.getTableName();
    }
    return table.getName();
  }

  public record MetadataPreview(IRowMeta previewMeta, List<Object[]> previewRows) {}
}
