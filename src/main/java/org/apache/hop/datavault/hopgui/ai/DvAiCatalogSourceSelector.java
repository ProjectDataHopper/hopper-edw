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
package org.apache.hop.datavault.hopgui.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.ai.DvAiContextBuilder;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

/**
 * Resolves the model catalog connection and lets the user pick record definitions for AI context.
 */
public final class DvAiCatalogSourceSelector {

  private static final Class<?> PKG = DvAiCatalogSourceSelector.class;

  private DvAiCatalogSourceSelector() {}

  /**
   * @return selected catalog source names, or {@code null} when the user cancels a selection step
   */
  public static List<String> selectSources(
      Shell parent,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Runnable onModelChanged) {
    return selectSources(parent, model, variables, metadataProvider, onModelChanged, null, true);
  }

  /**
   * @param sessionSources previously selected sources for this conversation; reused when non-empty
   *     and {@code forceRepick} is false
   * @return selected catalog source names, or {@code null} when the user cancels a selection step
   */
  public static List<String> selectSources(
      Shell parent,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Runnable onModelChanged,
      List<String> sessionSources,
      boolean forceRepick) {
    if (!forceRepick && sessionSources != null && !sessionSources.isEmpty()) {
      return List.copyOf(sessionSources);
    }
    if (model == null || metadataProvider == null) {
      return List.of();
    }
    try {
      String catalogConnection =
          ensureCatalogConnection(parent, model, variables, metadataProvider, onModelChanged);
      if (catalogConnection == null) {
        return null;
      }

      List<String> available =
          DvSourceCatalogService.listSourceNames(catalogConnection, variables, metadataProvider);
      if (available.isEmpty()) {
        MessageBox box = new MessageBox(parent, SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "DvAiCatalogSourceSelector.NoSources.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG, "DvAiCatalogSourceSelector.NoSources.Message", catalogConnection));
        box.open();
        return List.of();
      }

      return pickRecordDefinitions(parent, available, model);
    } catch (HopException e) {
      new ErrorDialog(
          parent,
          BaseMessages.getString(PKG, "DvAiCatalogSourceSelector.Error.Title"),
          BaseMessages.getString(PKG, "DvAiCatalogSourceSelector.Error.Message"),
          e);
      return null;
    }
  }

  private static String ensureCatalogConnection(
      Shell parent,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Runnable onModelChanged)
      throws HopException {
    DataVaultConfiguration configuration = model.getConfigurationOrDefault();
    String configured = configuration.getDataCatalogConnection();
    if (variables != null) {
      configured = variables.resolve(configured);
    }
    if (!Utils.isEmpty(configured)) {
      return configured;
    }

    List<String> connections =
        DvSourceCatalogService.listEnabledCatalogConnectionNames(metadataProvider);
    if (connections.isEmpty()) {
      MessageBox box = new MessageBox(parent, SWT.OK | SWT.ICON_ERROR);
      box.setText(BaseMessages.getString(PKG, "DvAiCatalogSourceSelector.NoCatalog.Title"));
      box.setMessage(BaseMessages.getString(PKG, "DvAiCatalogSourceSelector.NoCatalog.Message"));
      box.open();
      return null;
    }

    String[] choices = connections.toArray(new String[0]);
    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            parent,
            choices,
            BaseMessages.getString(PKG, "DvAiCatalogSourceSelector.Catalog.Title"),
            BaseMessages.getString(PKG, "DvAiCatalogSourceSelector.Catalog.Message"));
    String selected = dialog.open();
    if (selected == null) {
      return null;
    }

    configuration.setDataCatalogConnection(selected);
    model.setConfiguration(configuration);
    model.setChanged();
    if (onModelChanged != null) {
      onModelChanged.run();
    }
    return selected;
  }

  private static List<String> pickRecordDefinitions(
      Shell parent, List<String> available, DataVaultModel model) {
    String[] choices = available.toArray(new String[0]);
    Set<String> preselected = DvAiContextBuilder.collectSourceNamesFromModel(model);
    List<Integer> selectedIndexes = new ArrayList<>();
    for (int i = 0; i < choices.length; i++) {
      if (preselected.contains(choices[i])) {
        selectedIndexes.add(i);
      }
    }

    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            parent,
            choices,
            BaseMessages.getString(PKG, "DvAiCatalogSourceSelector.Sources.Title"),
            BaseMessages.getString(PKG, "DvAiCatalogSourceSelector.Sources.Message"));
    dialog.setMulti(true);
    if (!selectedIndexes.isEmpty()) {
      dialog.setSelectedNrs(selectedIndexes);
    }
    if (dialog.open() == null) {
      return null;
    }

    int[] indices = dialog.getSelectionIndeces();
    if (indices == null || indices.length == 0) {
      return List.of();
    }

    Set<String> selected = new LinkedHashSet<>();
    for (int index : indices) {
      if (index >= 0 && index < choices.length) {
        selected.add(choices[index]);
      }
    }
    return List.copyOf(selected);
  }
}
