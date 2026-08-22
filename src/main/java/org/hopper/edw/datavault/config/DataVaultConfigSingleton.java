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
package org.hopper.edw.datavault.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.apache.hop.core.config.HopConfig;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.json.HopJson;
import org.apache.hop.core.logging.LogChannel;

public class DataVaultConfigSingleton {

  private static DataVaultConfigSingleton configSingleton;

  private DataVaultConfig projectsConfig;

  private DataVaultConfigSingleton() {
    // Load from the HopConfig store
    //
    Object configObject =
        HopConfig.getInstance()
            .getConfigMap()
            .get(DataVaultConfig.HOP_CONFIG_DATA_VAULT_CONFIG_KEY);
    if (configObject == null) {
      projectsConfig = new DataVaultConfig();
    } else {
      // The way Jackson stores these simple POJO is with a map per default...
      // So we don't really need to mess around with Deserializer and so on.
      // This way we can keep the class name out of the JSON as well.
      //
      try {
        ObjectMapper mapper = HopJson.newMapper();
        projectsConfig = mapper.readValue(new Gson().toJson(configObject), DataVaultConfig.class);
      } catch (Exception e) {
        LogChannel.GENERAL.logError(
            "Error reading environments configuration, check property '"
                + DataVaultConfig.HOP_CONFIG_DATA_VAULT_CONFIG_KEY
                + "' in the Hop config json file",
            e);
        projectsConfig = new DataVaultConfig();
      }
    }
    HopConfig.getInstance()
        .getConfigMap()
        .put(DataVaultConfig.HOP_CONFIG_DATA_VAULT_CONFIG_KEY, projectsConfig);
  }

  public static DataVaultConfigSingleton getInstance() {
    return configSingleton;
  }

  public static DataVaultConfig getConfig() {
    if (configSingleton == null) {
      configSingleton = new DataVaultConfigSingleton();
    }
    return configSingleton.projectsConfig;
  }

  public static void saveConfig() throws HopException {
    HopConfig.getInstance()
        .saveOption(
            DataVaultConfig.HOP_CONFIG_DATA_VAULT_CONFIG_KEY, configSingleton.projectsConfig);
    HopConfig.getInstance().saveToFile();
  }
}
