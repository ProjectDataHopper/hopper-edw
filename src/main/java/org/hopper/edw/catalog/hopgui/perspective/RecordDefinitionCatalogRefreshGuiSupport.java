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
package org.hopper.edw.catalog.hopgui.perspective;

import java.util.Date;
import org.hopper.edw.catalog.discovery.RecordDefinitionCatalogRefreshSupport;
import org.hopper.edw.catalog.discovery.RecordDefinitionPhysicalRefSupport;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

/** GUI workflow for refreshing a catalog record definition from its physical source. */
public final class RecordDefinitionCatalogRefreshGuiSupport {

  private static final Class<?> PKG = RecordDefinitionCatalogRefreshGuiSupport.class;

  private RecordDefinitionCatalogRefreshGuiSupport() {}

  public static void refreshFromSource(
      Shell shell,
      RecordDefinition definition,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Runnable onSuccess) {
    if (definition == null || catalogConnectionName == null) {
      return;
    }
    if (!RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(definition)) {
      MessageBox messageBox = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      messageBox.setText(
          BaseMessages.getString(
              PKG, "RecordDefinitionCatalogRefreshGuiSupport.Unsupported.Title"));
      messageBox.setMessage(
          BaseMessages.getString(
              PKG, "RecordDefinitionCatalogRefreshGuiSupport.Unsupported.Message"));
      messageBox.open();
      return;
    }

    try {
      RecordDefinitionCatalogRefreshSupport.RefreshPreview preview =
          RecordDefinitionCatalogRefreshSupport.preview(definition, variables, metadataProvider);
      if (!new RefreshRecordDefinitionFromSourceDialog(shell, preview).openConfirmed()) {
        return;
      }

      RecordDefinitionCatalogRefreshSupport.applyDiscoveredFields(
          definition, preview.discoveredFields(), new Date(), preview.physicalSchemaId());
      RecordDefinitionRegistry.getInstance()
          .update(catalogConnectionName, definition, variables, metadataProvider);

      if (onSuccess != null) {
        onSuccess.run();
      }

      MessageBox messageBox = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      messageBox.setText(
          BaseMessages.getString(PKG, "RecordDefinitionCatalogRefreshGuiSupport.Success.Title"));
      messageBox.setMessage(
          BaseMessages.getString(
              PKG,
              "RecordDefinitionCatalogRefreshGuiSupport.Success.Message",
              preview.discoveredFields().size()));
      messageBox.open();
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "RecordDefinitionCatalogRefreshGuiSupport.Error.Title"),
          BaseMessages.getString(PKG, "RecordDefinitionCatalogRefreshGuiSupport.Error.Message"),
          e);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "RecordDefinitionCatalogRefreshGuiSupport.Error.Title"),
          BaseMessages.getString(PKG, "RecordDefinitionCatalogRefreshGuiSupport.Error.Message"),
          e);
    }
  }

  public static void refreshFromSource(
      Shell shell,
      RecordDefinition definition,
      String catalogConnectionName,
      IVariables variables,
      Runnable onSuccess) {
    refreshFromSource(
        shell,
        definition,
        catalogConnectionName,
        variables,
        HopGui.getInstance().getMetadataProvider(),
        onSuccess);
  }
}
