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
package org.apache.hop.datavault.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.apache.hop.core.config.HopConfig;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.json.HopJson;
import org.apache.hop.core.logging.LogChannel;

public class HopAiConfigSingleton {

  private static HopAiConfigSingleton instance;
  private HopAiConfig hopAiConfig;

  private HopAiConfigSingleton() {
    Object configObject =
        HopConfig.getInstance().getConfigMap().get(HopAiConfig.HOP_CONFIG_HOP_AI_CONFIG_KEY);
    if (configObject == null) {
      hopAiConfig = new HopAiConfig();
    } else {
      try {
        ObjectMapper mapper = HopJson.newMapper();
        hopAiConfig = mapper.readValue(new Gson().toJson(configObject), HopAiConfig.class);
      } catch (Exception e) {
        LogChannel.GENERAL.logError(
            "Error reading AI assistant configuration, check property '"
                + HopAiConfig.HOP_CONFIG_HOP_AI_CONFIG_KEY
                + "' in the Hop config json file",
            e);
        hopAiConfig = new HopAiConfig();
      }
    }
    HopAiProviderSettingsSupport.normalizeProviderSettings(hopAiConfig);
    HopAiProviderSettingsSupport.ensureMigrated(hopAiConfig);
    HopConfig.getInstance()
        .getConfigMap()
        .put(HopAiConfig.HOP_CONFIG_HOP_AI_CONFIG_KEY, hopAiConfig);
  }

  public static HopAiConfig getConfig() {
    if (instance == null) {
      instance = new HopAiConfigSingleton();
    }
    return instance.hopAiConfig;
  }

  public static void saveConfig() throws HopException {
    if (instance == null) {
      instance = new HopAiConfigSingleton();
    }
    HopConfig.getInstance()
        .saveOption(HopAiConfig.HOP_CONFIG_HOP_AI_CONFIG_KEY, instance.hopAiConfig);
    HopConfig.getInstance().saveToFile();
  }
}
