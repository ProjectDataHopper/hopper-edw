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
package org.apache.hop.datavault.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.junit.jupiter.api.Test;

class DmDefaultFieldNamesTest {

  @Test
  void applyToCopiesConfiguredFieldNames() {
    DmDefaultFieldNames defaults = new DmDefaultFieldNames();
    defaults.setSurrogateKeyField("sk_id");
    defaults.setVersionField("ver_no");
    defaults.setEffectiveFromField("valid_from");
    defaults.setEffectiveToField("valid_to");
    defaults.setLoadTimestampField("loaded_at");
    defaults.setCurrentFlagField("is_active");

    DimensionalConfiguration configuration = new DimensionalConfiguration();
    defaults.applyTo(configuration);

    assertEquals("sk_id", configuration.getDimKeyField());
    assertEquals("ver_no", configuration.getVersionField());
    assertEquals("valid_from", configuration.getDateFromField());
    assertEquals("valid_to", configuration.getDateToField());
    assertEquals("loaded_at", configuration.getLoadDateField());
    assertEquals("is_active", configuration.getCurrentFlagField());
  }

  @Test
  void createFromPluginDefaultsUsesPluginConfig() {
    DataVaultConfig config = DataVaultConfigSingleton.getConfig();
    DmDefaultFieldNames original =
        new DmDefaultFieldNames(config.getDimensionalDefaultFieldNames());
    try {
      DmDefaultFieldNames defaults = config.getDimensionalDefaultFieldNames();
      defaults.setSurrogateKeyField("warehouse_key");
      defaults.setLoadTimestampField("load_ts");

      DimensionalConfiguration configuration = DimensionalConfiguration.createFromPluginDefaults();
      assertEquals("warehouse_key", configuration.getDimKeyField());
      assertEquals("load_ts", configuration.getLoadDateField());

      DimensionalModel model = new DimensionalModel();
      assertEquals("warehouse_key", model.getConfigurationOrDefault().getDimKeyField());
    } finally {
      config.setDimensionalDefaultFieldNames(original);
    }
  }
}
