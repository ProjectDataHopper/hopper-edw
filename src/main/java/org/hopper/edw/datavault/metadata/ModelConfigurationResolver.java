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
package org.hopper.edw.datavault.metadata;

import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelConfiguration;

/**
 * Resolves named project model-configuration metadata and keeps inline {@code <configuration>} as a
 * backward-compatible fallback.
 */
public final class ModelConfigurationResolver {

  public static final String DEFAULT_SOURCE_MODEL_NAME = "source-model";
  public static final String DEFAULT_DATA_VAULT_NAME = "data-vault";
  public static final String DEFAULT_BUSINESS_VAULT_NAME = "business-vault";
  public static final String DEFAULT_DIMENSIONAL_NAME = "dimensional";

  private static final Class<?> PKG = ModelConfigurationResolver.class;

  private ModelConfigurationResolver() {}

  public static <T extends IHopMetadata> T resolveNamed(
      String configurationName, IHopMetadataProvider metadataProvider, Class<T> type) {
    if (Utils.isEmpty(configurationName) || metadataProvider == null || type == null) {
      return null;
    }
    try {
      return metadataProvider.getSerializer(type).load(configurationName);
    } catch (Exception e) {
      return null;
    }
  }

  public static boolean exists(
      String configurationName, IHopMetadataProvider metadataProvider, Class<?> type) {
    if (Utils.isEmpty(configurationName) || metadataProvider == null || type == null) {
      return false;
    }
    try {
      @SuppressWarnings("unchecked")
      Class<? extends IHopMetadata> metadataType = (Class<? extends IHopMetadata>) type;
      return metadataProvider.getSerializer(metadataType).exists(configurationName);
    } catch (Exception e) {
      return false;
    }
  }

  public static void attach(Object model, IHopMetadataProvider metadataProvider) {
    if (metadataProvider == null) {
      return;
    }
    if (model instanceof DataVaultModel dataVaultModel) {
      dataVaultModel.setMetadataProvider(metadataProvider);
    } else if (model instanceof SourceModel sourceModel) {
      sourceModel.setMetadataProvider(metadataProvider);
    } else if (model instanceof BusinessVaultModel businessVaultModel) {
      businessVaultModel.setMetadataProvider(metadataProvider);
    } else if (model instanceof DimensionalModel dimensionalModel) {
      dimensionalModel.setMetadataProvider(metadataProvider);
    }
  }

  public static void applyDefaultNameIfPresent(
      DataVaultModel model, IHopMetadataProvider metadataProvider) {
    applyDefaultNameIfPresent(
        model, metadataProvider, DataVaultConfiguration.class, DEFAULT_DATA_VAULT_NAME);
  }

  public static void applyDefaultNameIfPresent(
      SourceModel model, IHopMetadataProvider metadataProvider) {
    applyDefaultNameIfPresent(
        model, metadataProvider, SourceModelConfiguration.class, DEFAULT_SOURCE_MODEL_NAME);
  }

  public static void applyDefaultNameIfPresent(
      BusinessVaultModel model, IHopMetadataProvider metadataProvider) {
    applyDefaultNameIfPresent(
        model, metadataProvider, BusinessVaultConfiguration.class, DEFAULT_BUSINESS_VAULT_NAME);
  }

  public static void applyDefaultNameIfPresent(
      DimensionalModel model, IHopMetadataProvider metadataProvider) {
    applyDefaultNameIfPresent(
        model, metadataProvider, DimensionalConfiguration.class, DEFAULT_DIMENSIONAL_NAME);
  }

  private static void applyDefaultNameIfPresent(
      Object model,
      IHopMetadataProvider metadataProvider,
      Class<? extends IHopMetadata> type,
      String standardName) {
    if (model == null || metadataProvider == null || !Utils.isEmpty(configurationNameOf(model))) {
      return;
    }
    try {
      var serializer = metadataProvider.getSerializer(type);
      if (serializer.exists(standardName)) {
        setConfigurationName(model, standardName);
        return;
      }
      List<String> names = serializer.listObjectNames();
      if (names != null && names.size() == 1 && !Utils.isEmpty(names.get(0))) {
        setConfigurationName(model, names.get(0));
      }
    } catch (Exception ignored) {
      // New models stay on the inline default when the project has no shared config yet.
    }
  }

  /**
   * Temporarily clears the inline configuration so XML save writes only {@code configurationName}.
   *
   * @return the detached inline object (may be {@code null})
   */
  public static Object detachInlineForSave(Object model) {
    if (model instanceof DataVaultModel dataVaultModel
        && !Utils.isEmpty(dataVaultModel.getConfigurationName())) {
      DataVaultConfiguration saved = dataVaultModel.getConfiguration();
      dataVaultModel.setConfiguration(null);
      return saved;
    }
    if (model instanceof SourceModel sourceModel
        && !Utils.isEmpty(sourceModel.getConfigurationName())) {
      SourceModelConfiguration saved = sourceModel.getConfiguration();
      sourceModel.setConfiguration(null);
      return saved;
    }
    if (model instanceof BusinessVaultModel businessVaultModel
        && !Utils.isEmpty(businessVaultModel.getConfigurationName())) {
      BusinessVaultConfiguration saved = businessVaultModel.getConfiguration();
      businessVaultModel.setConfiguration(null);
      return saved;
    }
    if (model instanceof DimensionalModel dimensionalModel
        && !Utils.isEmpty(dimensionalModel.getConfigurationName())) {
      DimensionalConfiguration saved = dimensionalModel.getConfiguration();
      dimensionalModel.setConfiguration(null);
      return saved;
    }
    return null;
  }

  public static void restoreInlineAfterSave(Object model, Object saved) {
    if (saved == null || model == null) {
      return;
    }
    if (model instanceof DataVaultModel dataVaultModel
        && saved instanceof DataVaultConfiguration configuration) {
      dataVaultModel.setConfiguration(configuration);
    } else if (model instanceof SourceModel sourceModel
        && saved instanceof SourceModelConfiguration configuration) {
      sourceModel.setConfiguration(configuration);
    } else if (model instanceof BusinessVaultModel businessVaultModel
        && saved instanceof BusinessVaultConfiguration configuration) {
      businessVaultModel.setConfiguration(configuration);
    } else if (model instanceof DimensionalModel dimensionalModel
        && saved instanceof DimensionalConfiguration configuration) {
      dimensionalModel.setConfiguration(configuration);
    }
  }

  public static void checkNamedConfiguration(
      List<ICheckResult> remarks,
      String configurationName,
      Object inlineConfiguration,
      IHopMetadataProvider metadataProvider,
      Class<? extends IHopMetadata> type) {
    if (remarks == null) {
      return;
    }
    if (Utils.isEmpty(configurationName)) {
      return;
    }
    if (metadataProvider == null) {
      return;
    }
    if (!exists(configurationName, metadataProvider, type)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "ModelConfigurationResolver.CheckResult.ConfigurationNotFound",
                  configurationName),
              null));
      return;
    }
    if (inlineConfiguration != null) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "ModelConfigurationResolver.CheckResult.InlineConfigurationIgnored",
                  configurationName),
              null));
    }
  }

  private static String configurationNameOf(Object model) {
    if (model instanceof DataVaultModel dataVaultModel) {
      return dataVaultModel.getConfigurationName();
    }
    if (model instanceof SourceModel sourceModel) {
      return sourceModel.getConfigurationName();
    }
    if (model instanceof BusinessVaultModel businessVaultModel) {
      return businessVaultModel.getConfigurationName();
    }
    if (model instanceof DimensionalModel dimensionalModel) {
      return dimensionalModel.getConfigurationName();
    }
    return null;
  }

  private static void setConfigurationName(Object model, String name) {
    if (model instanceof DataVaultModel dataVaultModel) {
      dataVaultModel.setConfigurationName(name);
    } else if (model instanceof SourceModel sourceModel) {
      sourceModel.setConfigurationName(name);
    } else if (model instanceof BusinessVaultModel businessVaultModel) {
      businessVaultModel.setConfigurationName(name);
    } else if (model instanceof DimensionalModel dimensionalModel) {
      dimensionalModel.setConfigurationName(name);
    }
  }
}
