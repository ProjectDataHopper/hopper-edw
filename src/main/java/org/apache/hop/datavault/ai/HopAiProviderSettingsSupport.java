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
import java.util.HashMap;
import java.util.Map;
import org.apache.hop.core.json.HopJson;
import org.apache.hop.core.util.Utils;

/** Persists and restores AI settings per provider preset. */
public final class HopAiProviderSettingsSupport {

  private HopAiProviderSettingsSupport() {}

  public static void ensureMigrated(HopAiConfig config) {
    if (config == null) {
      return;
    }
    normalizeProviderSettings(config);
    Map<String, HopAiProviderSettings> settings = config.getProviderSettings();
    String activePreset = DvAiProviderPreset.resolve(config.getAiProviderPreset()).name();
    if (!settings.containsKey(activePreset) && hasAnyProviderFields(config)) {
      settings.put(activePreset, snapshotFromConfig(config));
    }
  }

  public static void normalizeProviderSettings(HopAiConfig config) {
    if (config == null) {
      return;
    }
    Map<String, HopAiProviderSettings> rawSettings = config.getProviderSettings();
    if (rawSettings == null) {
      config.setProviderSettings(new HashMap<>());
      return;
    }
    Map<String, HopAiProviderSettings> normalized = new HashMap<>();
    for (Map.Entry<String, HopAiProviderSettings> entry : rawSettings.entrySet()) {
      HopAiProviderSettings coerced = coerceSettings(entry.getValue());
      if (coerced != null) {
        normalized.put(entry.getKey(), coerced);
      }
    }
    config.setProviderSettings(normalized);
  }

  public static HopAiProviderSettings coerceSettings(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof HopAiProviderSettings settings) {
      return new HopAiProviderSettings(settings);
    }
    if (value instanceof Map<?, ?> map) {
      ObjectMapper mapper = HopJson.newMapper();
      return mapper.convertValue(map, HopAiProviderSettings.class);
    }
    return null;
  }

  public static HopAiProviderSettings getStoredSettings(
      HopAiConfig config, DvAiProviderPreset preset) {
    ensureMigrated(config);
    if (preset == null) {
      return null;
    }
    return coerceSettings(config.getProviderSettings().get(preset.name()));
  }

  public static HopAiProviderSettings snapshotFromConfig(HopAiConfig config) {
    HopAiProviderSettings settings = new HopAiProviderSettings();
    if (config == null) {
      return settings;
    }
    settings.setApiKey(config.getAiApiKey());
    settings.setBaseUrl(config.getAiBaseUrl());
    settings.setModelName(config.getAiModelName());
    settings.setTemperature(resolveTemperature(config.getAiTemperature()));
    return settings;
  }

  public static void applyToConfig(HopAiConfig config, HopAiProviderSettings settings) {
    if (config == null) {
      return;
    }
    HopAiProviderSettings resolved = settings != null ? settings : new HopAiProviderSettings();
    config.setAiApiKey(resolved.getApiKey());
    config.setAiBaseUrl(resolved.getBaseUrl());
    config.setAiModelName(resolved.getModelName());
    config.setAiTemperature(resolveTemperature(resolved.getTemperature()));
  }

  public static void saveCurrentProvider(HopAiConfig config) {
    if (config == null) {
      return;
    }
    ensureMigrated(config);
    String activePreset = DvAiProviderPreset.resolve(config.getAiProviderPreset()).name();
    config.getProviderSettings().put(activePreset, snapshotFromConfig(config));
  }

  public static void switchProvider(HopAiConfig config, DvAiProviderPreset newPreset) {
    if (config == null || newPreset == null) {
      return;
    }
    ensureMigrated(config);
    DvAiProviderPreset currentPreset = DvAiProviderPreset.resolve(config.getAiProviderPreset());
    if (currentPreset == newPreset) {
      return;
    }
    saveCurrentProvider(config);
    config.setAiProviderPreset(newPreset.name());
    HopAiProviderSettings stored = getStoredSettings(config, newPreset);
    applyToConfig(config, stored);
    if (stored == null) {
      saveCurrentProvider(config);
    }
  }

  public static void switchProvider(
      HopAiConfig config,
      DvAiProviderPreset currentPreset,
      DvAiProviderPreset newPreset,
      HopAiProviderSettings currentWidgetSettings) {
    if (config == null || newPreset == null || currentPreset == newPreset) {
      return;
    }
    ensureMigrated(config);
    if (currentWidgetSettings != null) {
      config.getProviderSettings().put(currentPreset.name(), currentWidgetSettings);
    } else {
      saveCurrentProvider(config);
    }
    config.setAiProviderPreset(newPreset.name());
    HopAiProviderSettings stored = getStoredSettings(config, newPreset);
    applyToConfig(config, stored);
    if (stored == null) {
      saveCurrentProvider(config);
    }
  }

  private static boolean hasAnyProviderFields(HopAiConfig config) {
    return !Utils.isEmpty(config.getAiApiKey())
        || !Utils.isEmpty(config.getAiBaseUrl())
        || !Utils.isEmpty(config.getAiModelName())
        || !Utils.isEmpty(config.getAiTemperature());
  }

  private static String resolveTemperature(String temperature) {
    return Utils.isEmpty(temperature) ? "0.3" : temperature;
  }
}
