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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.plugin.MetadataPluginType;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.connector.type.HConnectorPluginType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RegisterHopperPresentationExtensionPointTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    new RegisterHopperPresentationExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @Test
  void registersComponentsButNotPresentationMetadata() {
    PluginRegistry registry = PluginRegistry.getInstance();
    assertNotNull(
        registry.getPlugin(HComponentPluginType.class, "HLabelComponent"),
        "HLabelComponent must be registered (Hop skips plugin lib/ jars)");
    assertNotNull(registry.getPlugin(HConnectorPluginType.class, "SampleDataConnector"));
    assertNull(
        registry.getPlugin(MetadataPluginType.class, "presentation"),
        "Presentation types must not appear in the Metadata perspective");
    assertNotNull(
        registry.getPlugin(MetadataPluginType.class, "source-model-service"),
        "hopper-edw metadata (source-model-service) must survive presentation embed");
  }
}
