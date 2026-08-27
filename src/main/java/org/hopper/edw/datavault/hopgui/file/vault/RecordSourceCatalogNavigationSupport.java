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
package org.hopper.edw.datavault.hopgui.file.vault;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.hopper.edw.catalog.hopgui.perspective.DataCatalogPerspective;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.metadata.DataVaultModel;

/** Opens a catalog Data Vault source (record definition) from a model dialog source list. */
public final class RecordSourceCatalogNavigationSupport {

  private static final Class<?> PKG = RecordSourceCatalogNavigationSupport.class;

  private RecordSourceCatalogNavigationSupport() {}

  /**
   * Reads the selected Data Vault source name from a sources {@link TableView} (column 1 is the
   * source name combo).
   */
  public static String selectedSourceName(TableView sourcesTable) {
    if (sourcesTable == null
        || sourcesTable.getTable() == null
        || sourcesTable.getTable().isDisposed()) {
      return null;
    }
    Table table = sourcesTable.getTable();
    int[] indices = table.getSelectionIndices();
    if (indices != null && indices.length > 0) {
      return trimToNull(table.getItem(indices[0]).getText(1));
    }
    int focus = table.getSelectionIndex();
    if (focus >= 0 && focus < table.getItemCount()) {
      return trimToNull(table.getItem(focus).getText(1));
    }
    // Fall back to first non-empty row when nothing is selected.
    for (TableItem item : sourcesTable.getNonEmptyItems()) {
      String name = trimToNull(item.getText(1));
      if (name != null) {
        return name;
      }
    }
    return null;
  }

  public static void openSelectedSourceInCatalog(
      Shell parent,
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      TableView sourcesTable) {
    String sourceName = selectedSourceName(sourcesTable);
    openSourceInCatalog(parent, hopGui, variables, metadataProvider, model, sourceName);
  }

  public static void openSourceInCatalog(
      Shell parent,
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      String sourceName) {
    try {
      if (Utils.isEmpty(sourceName)) {
        throw new HopException(
            BaseMessages.getString(PKG, "RecordSourceCatalogNavigationSupport.Error.NoSelection"));
      }
      if (hopGui == null) {
        throw new HopException(
            BaseMessages.getString(PKG, "RecordSourceCatalogNavigationSupport.Error.NoHopGui"));
      }
      String catalogConnection =
          DvSourceCatalogService.resolveCatalogConnection(model, variables, metadataProvider);
      String namespace = DvSourceCatalogService.projectSourcesNamespace(variables);
      String resolvedName =
          variables != null ? variables.resolve(sourceName.trim()) : sourceName.trim();
      DataCatalogPerspective perspective = DataCatalogPerspective.getInstance();
      if (perspective == null) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "RecordSourceCatalogNavigationSupport.Error.CatalogUnavailable"));
      }
      perspective.selectRecordDefinition(
          catalogConnection, new RecordDefinitionKey(namespace, resolvedName));
    } catch (Exception e) {
      Shell shell = parent != null ? parent : (hopGui != null ? hopGui.getShell() : null);
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "RecordSourceCatalogNavigationSupport.Error.Title"),
          BaseMessages.getString(PKG, "RecordSourceCatalogNavigationSupport.Error.Message"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  private static String trimToNull(String value) {
    if (Utils.isEmpty(value)) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
