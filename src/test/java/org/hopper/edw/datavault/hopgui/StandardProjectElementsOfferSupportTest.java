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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.xp.RegisterModelConfigurationMetadataExtensionPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandardProjectElementsOfferSupportTest {

  private MemoryMetadataProvider provider;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    new RegisterModelConfigurationMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUp() {
    provider = new MemoryMetadataProvider();
  }

  @Test
  void missingConfigurationWhenProviderIsEmpty() {
    assertTrue(
        StandardProjectElementsOfferSupport.isMissingConfiguration(
            provider, DataVaultConfiguration.class));
  }

  @Test
  void notMissingWhenStandardConfigurationExists() throws Exception {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setName(ModelConfigurationResolver.DEFAULT_DATA_VAULT_NAME);
    provider.getSerializer(DataVaultConfiguration.class).save(config);

    assertFalse(
        StandardProjectElementsOfferSupport.isMissingConfiguration(
            provider, DataVaultConfiguration.class));
  }

  @Test
  void activeModelWithoutHopGuiIsNull() {
    assertNull(StandardProjectElementsOfferSupport.activeModel(null));
  }
}
