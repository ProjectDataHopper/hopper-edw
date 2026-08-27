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

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.hopper.edw.catalog.hopgui.LocalCatalogOfferSupport;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.datavault.config.DataVaultConfig;
import org.hopper.edw.datavault.config.DataVaultConfigSingleton;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopGuiBusinessVaultGraph;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopGuiDimensionalModelGraph;
import org.hopper.edw.datavault.hopgui.file.sourcemodel.HopGuiSourceModelGraph;
import org.hopper.edw.datavault.hopgui.file.vault.HopGuiVaultGraph;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelConfiguration;

/**
 * Offers to create the standard project catalog plus the four shared model-configuration metadata
 * types. Shown automatically when a model file is opened, and from Tools → Configure EDW setup…
 */
public final class StandardProjectElementsOfferSupport {

  private static final Class<?> PKG = StandardProjectElementsOfferSupport.class;

  private StandardProjectElementsOfferSupport() {}

  public static void maybeOffer(HopGui hopGui, Object model) {
    show(hopGui, model, false);
  }

  /** Tools menu: always open the dialog, even if it was dismissed earlier. */
  public static void openFromMenu(HopGui hopGui) {
    show(hopGui, activeModel(hopGui), true);
  }

  private static void show(HopGui hopGui, Object model, boolean fromMenu) {
    if (hopGui == null) {
      return;
    }
    if (!fromMenu && (model == null || isSuppressed())) {
      return;
    }
    IHopMetadataProvider provider = hopGui.getMetadataProvider();
    if (provider == null) {
      return;
    }

    boolean missingCatalog = !hasMetadata(provider, DataCatalogMeta.class);
    boolean missingSource = isMissingConfiguration(provider, SourceModelConfiguration.class);
    boolean missingDv = isMissingConfiguration(provider, DataVaultConfiguration.class);
    boolean missingBv = isMissingConfiguration(provider, BusinessVaultConfiguration.class);
    boolean missingDm = isMissingConfiguration(provider, DimensionalConfiguration.class);
    if (!fromMenu && !missingCatalog && !missingSource && !missingDv && !missingBv && !missingDm) {
      return;
    }

    StandardProjectElementsOfferDialog.Selection selection =
        StandardProjectElementsOfferDialog.open(
            hopGui.getShell(),
            fromMenu,
            isSuppressed(),
            missingCatalog,
            missingSource,
            missingDv,
            missingBv,
            missingDm);
    if (selection == null) {
      return;
    }
    setSuppressed(selection.dontShowAgain());
    if (!selection.accepted()) {
      return;
    }

    try {
      if (selection.createCatalog() && missingCatalog) {
        createDefault(provider, LocalCatalogOfferSupport.newDefaultLocalCatalog());
      }
      if (selection.createSourceModel() && missingSource) {
        SourceModelConfiguration config = SourceModelConfiguration.createDefault();
        config.setName(ModelConfigurationResolver.DEFAULT_SOURCE_MODEL_NAME);
        config.setDescription(
            BaseMessages.getString(PKG, "StandardProjectElementsOffer.SourceModel.Description"));
        createDefault(provider, config);
      }
      if (selection.createDataVault() && missingDv) {
        DataVaultConfiguration config = new DataVaultConfiguration();
        config.setName(ModelConfigurationResolver.DEFAULT_DATA_VAULT_NAME);
        config.setDescription(
            BaseMessages.getString(PKG, "StandardProjectElementsOffer.DataVault.Description"));
        createDefault(provider, config);
      }
      if (selection.createBusinessVault() && missingBv) {
        BusinessVaultConfiguration config = new BusinessVaultConfiguration();
        config.setName(ModelConfigurationResolver.DEFAULT_BUSINESS_VAULT_NAME);
        config.setDescription(
            BaseMessages.getString(PKG, "StandardProjectElementsOffer.BusinessVault.Description"));
        createDefault(provider, config);
      }
      if (selection.createDimensional() && missingDm) {
        DimensionalConfiguration config = DimensionalConfiguration.createFromPluginDefaults();
        config.setName(ModelConfigurationResolver.DEFAULT_DIMENSIONAL_NAME);
        config.setDescription(
            BaseMessages.getString(PKG, "StandardProjectElementsOffer.Dimensional.Description"));
        createDefault(provider, config);
      }
      bindCurrentModel(model, provider);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "StandardProjectElementsOffer.Error.Title"),
          BaseMessages.getString(PKG, "StandardProjectElementsOffer.Error.Message"),
          e);
    }
  }

  static boolean isMissingConfiguration(
      IHopMetadataProvider provider, Class<? extends IHopMetadata> type) {
    String standardName = standardName(type);
    return !(hasNamed(provider, type, standardName) || hasMetadata(provider, type));
  }

  private static String standardName(Class<? extends IHopMetadata> type) {
    if (SourceModelConfiguration.class.equals(type)) {
      return ModelConfigurationResolver.DEFAULT_SOURCE_MODEL_NAME;
    }
    if (DataVaultConfiguration.class.equals(type)) {
      return ModelConfigurationResolver.DEFAULT_DATA_VAULT_NAME;
    }
    if (BusinessVaultConfiguration.class.equals(type)) {
      return ModelConfigurationResolver.DEFAULT_BUSINESS_VAULT_NAME;
    }
    if (DimensionalConfiguration.class.equals(type)) {
      return ModelConfigurationResolver.DEFAULT_DIMENSIONAL_NAME;
    }
    return "";
  }

  static Object activeModel(HopGui hopGui) {
    if (hopGui == null) {
      return null;
    }
    IHopFileTypeHandler handler = hopGui.getActiveFileTypeHandler();
    if (handler instanceof HopGuiVaultGraph graph) {
      return graph.getModel();
    }
    if (handler instanceof HopGuiSourceModelGraph graph) {
      return graph.getModel();
    }
    if (handler instanceof HopGuiBusinessVaultGraph graph) {
      return graph.getModel();
    }
    if (handler instanceof HopGuiDimensionalModelGraph graph) {
      return graph.getModel();
    }
    return null;
  }

  static boolean hasMetadata(IHopMetadataProvider provider, Class<? extends IHopMetadata> type) {
    if (provider == null) {
      return false;
    }
    try {
      List<String> names = provider.getSerializer(type).listObjectNames();
      return names != null && !names.isEmpty();
    } catch (Exception e) {
      return false;
    }
  }

  static boolean hasNamed(
      IHopMetadataProvider provider, Class<? extends IHopMetadata> type, String name) {
    return ModelConfigurationResolver.exists(name, provider, type);
  }

  private static void createDefault(IHopMetadataProvider provider, IHopMetadata metadata)
      throws HopException {
    if (metadata == null || Utils.isEmpty(metadata.getName())) {
      return;
    }
    @SuppressWarnings("unchecked")
    Class<IHopMetadata> type = (Class<IHopMetadata>) metadata.getClass();
    var serializer = provider.getSerializer(type);
    if (!serializer.exists(metadata.getName())) {
      serializer.save(metadata);
    }
  }

  private static void bindCurrentModel(Object model, IHopMetadataProvider provider) {
    if (model == null) {
      return;
    }
    ModelConfigurationResolver.attach(model, provider);
    if (model instanceof DataVaultModel dataVaultModel) {
      ModelConfigurationResolver.applyDefaultNameIfPresent(dataVaultModel, provider);
      if (Utils.isEmpty(dataVaultModel.getConfigurationOrDefault().getDataCatalogConnection())
          && hasNamed(
              provider,
              DataCatalogMeta.class,
              LocalCatalogOfferSupport.DEFAULT_LOCAL_CATALOG_NAME)) {
        dataVaultModel
            .getConfigurationOrDefault()
            .setDataCatalogConnection(LocalCatalogOfferSupport.DEFAULT_LOCAL_CATALOG_NAME);
        dataVaultModel.setChanged();
      }
    } else if (model instanceof SourceModel sourceModel) {
      ModelConfigurationResolver.applyDefaultNameIfPresent(sourceModel, provider);
    } else if (model instanceof BusinessVaultModel businessVaultModel) {
      ModelConfigurationResolver.applyDefaultNameIfPresent(businessVaultModel, provider);
    } else if (model instanceof DimensionalModel dimensionalModel) {
      ModelConfigurationResolver.applyDefaultNameIfPresent(dimensionalModel, provider);
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
          .logError("Unable to save Data Vault configuration after project elements offer", e);
    }
  }
}
