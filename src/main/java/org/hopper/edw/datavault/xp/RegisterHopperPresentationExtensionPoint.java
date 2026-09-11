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
package org.hopper.edw.datavault.xp;

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
import org.hopper.core.HEnvironment;

/**
 * Registers hopper-presentation-engine <em>component and connector</em> plugins after Hop starts.
 *
 * <p>Those classes live in {@code plugins/misc/hopper-edw/lib/}, and Hop's jar scanner skips {@code
 * lib/} folders. Without this, opening a generated presentation fails with missing {@code
 * HLabelComponent}. Presentation {@code @HopMetadata} types are intentionally <em>not</em>
 * registered so they do not appear in the Metadata perspective.
 */
@ExtensionPoint(
    id = HEnvironment.HOP_PLUGIN_EXTENSION_POINT_ID,
    extensionPointId = "HopEnvironmentAfterInit",
    description = "Register Hopper presentation engine plugins from hopper-edw/lib")
public class RegisterHopperPresentationExtensionPoint implements IExtensionPoint<PluginRegistry> {

  @Override
  public void callExtensionPoint(
      ILogChannel log, IVariables variables, PluginRegistry pluginRegistry) throws HopException {

    IPlugin thisPlugin =
        pluginRegistry.findPluginWithId(
            ExtensionPointPluginType.class, HEnvironment.HOP_PLUGIN_EXTENSION_POINT_ID);
    ClassLoader classLoader = getClass().getClassLoader();
    List<String> libraries =
        thisPlugin != null ? new ArrayList<>(thisPlugin.getLibraries()) : new ArrayList<>();
    URL pluginUrl = thisPlugin != null ? thisPlugin.getPluginDirectory() : null;

    try {
      HEnvironment.initEmbed(classLoader, libraries, pluginUrl);
    } catch (Exception e) {
      throw new HopException("Unable to register Hopper presentation plugins", e);
    }

    if (log != null) {
      log.logBasic("Hopper presentation engine plugins registered");
    }
  }
}
