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
 */
package org.apache.hop.catalog.hopgui;

import java.util.List;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.config.DataVaultConfig;
import org.apache.hop.datavault.config.DataVaultConfigSingleton;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageDialogWithToggle;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;

/** Offers to create a local file-based data catalog when a project has none yet. */
public final class LocalCatalogOfferSupport {

  private static final Class<?> PKG = LocalCatalogOfferSupport.class;

  public static final String DEFAULT_LOCAL_CATALOG_NAME = "local-catalog";

  private LocalCatalogOfferSupport() {}

  public static void maybeOffer(HopGui hopGui, DataVaultModel model) {
    if (hopGui == null || model == null) {
      return;
    }
    if (isSuppressed()) {
      return;
    }
    if (hasProjectDataCatalog(hopGui.getMetadataProvider())) {
      return;
    }

    MessageDialogWithToggle dialog =
        new MessageDialogWithToggle(
            hopGui.getShell(),
            BaseMessages.getString(PKG, "LocalCatalogOffer.Title"),
            BaseMessages.getString(PKG, "LocalCatalogOffer.Message"),
            SWT.ICON_QUESTION,
            new String[] {
              BaseMessages.getString(PKG, "LocalCatalogOffer.Yes"),
              BaseMessages.getString(PKG, "LocalCatalogOffer.No")
            },
            BaseMessages.getString(PKG, "LocalCatalogOffer.DontShowAgain"),
            false);

    int answer = dialog.open();
    if (dialog.getToggleState()) {
      setSuppressed(true);
    }
    if (answer != 0) {
      return;
    }

    createLocalCatalog(hopGui, model);
  }

  public static boolean hasProjectDataCatalog(IHopMetadataProvider metadataProvider) {
    if (metadataProvider == null) {
      return false;
    }
    try {
      IHopMetadataSerializer<DataCatalogMeta> serializer =
          metadataProvider.getSerializer(DataCatalogMeta.class);
      List<String> names = serializer.listObjectNames();
      return names != null && !names.isEmpty();
    } catch (Exception ignored) {
      return false;
    }
  }

  private static void createLocalCatalog(HopGui hopGui, DataVaultModel model) {
    try {
      MetadataManager<DataCatalogMeta> manager =
          new MetadataManager<>(
              hopGui.getVariables(),
              hopGui.getMetadataProvider(),
              DataCatalogMeta.class,
              hopGui.getShell());

      DataCatalogMeta created = manager.newMetadata(newDefaultLocalCatalog());
      if (created == null || Utils.isEmpty(created.getName())) {
        return;
      }

      applyCatalogToModel(model, created.getName());
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "LocalCatalogOffer.Error.Title"),
          BaseMessages.getString(PKG, "LocalCatalogOffer.Error.Message"),
          e);
    }
  }

  public static DataCatalogMeta newDefaultLocalCatalog() {
    DataCatalogMeta meta = new DataCatalogMeta(DEFAULT_LOCAL_CATALOG_NAME);
    meta.setDescription(BaseMessages.getString(PKG, "LocalCatalogOffer.DefaultDescription"));
    meta.setEnabled(true);
    meta.setCatalog(new FileDataCatalog());
    return meta;
  }

  private static void applyCatalogToModel(DataVaultModel model, String catalogName) {
    DataVaultConfiguration configuration = model.getConfigurationOrDefault();
    if (Utils.isEmpty(configuration.getDataCatalogConnection())) {
      configuration.setDataCatalogConnection(catalogName);
      model.setConfiguration(configuration);
      model.setChanged();
    }
  }

  private static boolean isSuppressed() {
    return DataVaultConfigSingleton.getConfig().isSuppressLocalCatalogOffer();
  }

  private static void setSuppressed(boolean suppressed) {
    DataVaultConfig config = DataVaultConfigSingleton.getConfig();
    config.setSuppressLocalCatalogOffer(suppressed);
    try {
      DataVaultConfigSingleton.saveConfig();
    } catch (HopException e) {
      HopGui.getInstance()
          .getLog()
          .logError("Unable to save Data Vault configuration after catalog offer", e);
    }
  }
}
