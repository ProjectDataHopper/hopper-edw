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
package org.apache.hop.datavault.metadata.targettypemapping.xp;

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
import org.apache.hop.datavault.metadata.targettypemapping.TargetTypeMappingMeta;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.plugin.MetadataPluginType;

/** Registers {@link TargetTypeMappingMeta} with Hop metadata after environment init. */
@ExtensionPoint(
    id = "RegisterTargetTypeMappingMetadataExtensionPoint",
    extensionPointId = "HopEnvironmentAfterInit",
    description = "Register Target Type Mapping metadata")
public class RegisterTargetTypeMappingMetadataExtensionPoint
    implements IExtensionPoint<PluginRegistry> {

  @Override
  public void callExtensionPoint(
      ILogChannel log, IVariables variables, PluginRegistry pluginRegistry) throws HopException {

    IPlugin thisPlugin =
        pluginRegistry.findPluginWithId(
            ExtensionPointPluginType.class, "RegisterTargetTypeMappingMetadataExtensionPoint");
    ClassLoader classLoader = getClass().getClassLoader();
    List<String> libraries =
        thisPlugin != null ? new ArrayList<>(thisPlugin.getLibraries()) : new ArrayList<>();
    URL pluginUrl = thisPlugin != null ? thisPlugin.getPluginDirectory() : null;

    pluginRegistry.registerPluginClass(
        classLoader,
        libraries,
        pluginUrl,
        TargetTypeMappingMeta.class.getName(),
        MetadataPluginType.class,
        HopMetadata.class,
        false);

    if (log != null) {
      log.logDetailed("Target Type Mapping metadata registered");
    }
  }
}
