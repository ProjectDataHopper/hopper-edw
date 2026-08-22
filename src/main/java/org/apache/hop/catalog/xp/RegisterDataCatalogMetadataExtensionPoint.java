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
package org.apache.hop.catalog.xp;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.ExtensionPointPluginType;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.plugin.MetadataPluginType;

/**
 * Registers {@link DataCatalogMeta} with Hop metadata after environment init (neo4j-style). Catalog
 * backend implementations are selected via {@link
 * org.apache.hop.catalog.metadata.DataCatalogMetaObjectFactory}, not a separate plugin type.
 */
@ExtensionPoint(
    id = "RegisterDataCatalogMetadataExtensionPoint",
    extensionPointId = "HopEnvironmentAfterInit",
    description = "Register Data Catalog connection metadata")
public class RegisterDataCatalogMetadataExtensionPoint implements IExtensionPoint<PluginRegistry> {

  @Override
  public void callExtensionPoint(
      ILogChannel log, IVariables variables, PluginRegistry pluginRegistry) throws HopException {

    IPlugin thisPlugin =
        pluginRegistry.findPluginWithId(
            ExtensionPointPluginType.class, "RegisterDataCatalogMetadataExtensionPoint");
    ClassLoader classLoader = getClass().getClassLoader();
    List<String> libraries =
        thisPlugin != null ? new ArrayList<>(thisPlugin.getLibraries()) : new ArrayList<>();
    URL pluginUrl = thisPlugin != null ? thisPlugin.getPluginDirectory() : null;

    pluginRegistry.registerPluginClass(
        classLoader,
        libraries,
        pluginUrl,
        DataCatalogMeta.class.getName(),
        MetadataPluginType.class,
        HopMetadata.class,
        false);

    RecordDefinitionRegistry.getInstance().environmentReady();

    if (log != null) {
      log.logDetailed("Data Catalog metadata registered");
    }
  }
}
