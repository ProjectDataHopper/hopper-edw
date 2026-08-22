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
package org.apache.hop.datavault.metadata.xp;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.ExtensionPointPluginType;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelConfiguration;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.plugin.MetadataPluginType;

/** Registers the four shared model-configuration metadata types after environment init. */
@ExtensionPoint(
    id = "RegisterModelConfigurationMetadataExtensionPoint",
    extensionPointId = "HopEnvironmentAfterInit",
    description = "Register source, Data Vault, Business Vault, and dimensional configurations")
public class RegisterModelConfigurationMetadataExtensionPoint
    implements IExtensionPoint<PluginRegistry> {

  @Override
  public void callExtensionPoint(
      ILogChannel log, IVariables variables, PluginRegistry pluginRegistry) throws HopException {

    IPlugin thisPlugin =
        pluginRegistry.findPluginWithId(
            ExtensionPointPluginType.class, "RegisterModelConfigurationMetadataExtensionPoint");
    ClassLoader classLoader = getClass().getClassLoader();
    List<String> libraries =
        thisPlugin != null ? new ArrayList<>(thisPlugin.getLibraries()) : new ArrayList<>();
    URL pluginUrl = thisPlugin != null ? thisPlugin.getPluginDirectory() : null;

    register(pluginRegistry, classLoader, libraries, pluginUrl, SourceModelConfiguration.class);
    register(pluginRegistry, classLoader, libraries, pluginUrl, DataVaultConfiguration.class);
    register(pluginRegistry, classLoader, libraries, pluginUrl, BusinessVaultConfiguration.class);
    register(pluginRegistry, classLoader, libraries, pluginUrl, DimensionalConfiguration.class);

    if (log != null) {
      log.logDetailed("Model configuration metadata types registered");
    }
  }

  private static void register(
      PluginRegistry pluginRegistry,
      ClassLoader classLoader,
      List<String> libraries,
      URL pluginUrl,
      Class<?> type)
      throws HopException {
    pluginRegistry.registerPluginClass(
        classLoader,
        libraries,
        pluginUrl,
        type.getName(),
        MetadataPluginType.class,
        HopMetadata.class,
        false);
  }
}
