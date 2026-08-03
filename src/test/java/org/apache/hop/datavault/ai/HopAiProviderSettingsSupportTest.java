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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hop.core.json.HopJson;
import org.junit.jupiter.api.Test;

class HopAiProviderSettingsSupportTest {

  @Test
  void migrateExistingFlatSettingsIntoActiveProvider() {
    HopAiConfig config = new HopAiConfig();
    config.setAiProviderPreset(DvAiProviderPreset.GROK.name());
    config.setAiApiKey("grok-key");
    config.setAiModelName("grok-4");

    HopAiProviderSettingsSupport.ensureMigrated(config);

    HopAiProviderSettings grok = config.getProviderSettings().get(DvAiProviderPreset.GROK.name());
    assertNotNull(grok);
    assertEquals("grok-key", grok.getApiKey());
    assertEquals("grok-4", grok.getModelName());
  }

  @Test
  void switchProviderRestoresRememberedSettings() {
    HopAiConfig config = new HopAiConfig();
    config.setAiProviderPreset(DvAiProviderPreset.GROK.name());
    config.setAiApiKey("grok-key");
    config.setAiBaseUrl("https://api.x.ai/v1");
    config.setAiModelName("grok-4");
    config.setAiTemperature("0.2");
    HopAiProviderSettingsSupport.saveCurrentProvider(config);

    HopAiProviderSettingsSupport.switchProvider(config, DvAiProviderPreset.OLLAMA);
    config.setAiApiKey("");
    config.setAiBaseUrl("http://localhost:11434");
    config.setAiModelName("llama3.2");
    config.setAiTemperature("0.5");
    HopAiProviderSettingsSupport.saveCurrentProvider(config);

    HopAiProviderSettingsSupport.switchProvider(config, DvAiProviderPreset.GROK);

    assertEquals(DvAiProviderPreset.GROK.name(), config.getAiProviderPreset());
    assertEquals("grok-key", config.getAiApiKey());
    assertEquals("https://api.x.ai/v1", config.getAiBaseUrl());
    assertEquals("grok-4", config.getAiModelName());
    assertEquals("0.2", config.getAiTemperature());

    HopAiProviderSettingsSupport.switchProvider(config, DvAiProviderPreset.OLLAMA);

    assertEquals(DvAiProviderPreset.OLLAMA.name(), config.getAiProviderPreset());
    assertEquals("", config.getAiApiKey());
    assertEquals("http://localhost:11434", config.getAiBaseUrl());
    assertEquals("llama3.2", config.getAiModelName());
    assertEquals("0.5", config.getAiTemperature());
  }

  @Test
  void switchProviderUsesWidgetSnapshotBeforeRestoringTarget() {
    HopAiConfig config = new HopAiConfig();
    config.setAiProviderPreset(DvAiProviderPreset.GROK.name());
    config.setAiApiKey("saved-grok-key");
    HopAiProviderSettingsSupport.saveCurrentProvider(config);

    HopAiProviderSettings widgetSnapshot = new HopAiProviderSettings();
    widgetSnapshot.setApiKey("edited-grok-key");
    widgetSnapshot.setModelName("grok-4-fast");

    HopAiProviderSettingsSupport.switchProvider(
        config, DvAiProviderPreset.GROK, DvAiProviderPreset.OLLAMA, widgetSnapshot);

    assertEquals(
        "edited-grok-key",
        config.getProviderSettings().get(DvAiProviderPreset.GROK.name()).getApiKey());
    assertEquals(DvAiProviderPreset.OLLAMA.name(), config.getAiProviderPreset());
  }

  @Test
  void coerceSettingsHandlesLinkedHashMapFromHopConfigJson() {
    Map<String, Object> grok = new LinkedHashMap<>();
    grok.put("apiKey", "grok-key");
    grok.put("modelName", "grok-4");

    HopAiProviderSettings settings = HopAiProviderSettingsSupport.coerceSettings(grok);

    assertNotNull(settings);
    assertEquals("grok-key", settings.getApiKey());
    assertEquals("grok-4", settings.getModelName());
  }

  @Test
  void hopConfigRoundTripPreservesProviderSettings() throws Exception {
    HopAiConfig original = new HopAiConfig();
    original.setAiProviderPreset(DvAiProviderPreset.GROK.name());
    original.setAiApiKey("grok-key");
    original.setAiModelName("grok-4");
    HopAiProviderSettingsSupport.saveCurrentProvider(original);

    HopAiProviderSettings ollama = new HopAiProviderSettings();
    ollama.setBaseUrl("http://localhost:11434");
    ollama.setModelName("llama3.2");
    original.getProviderSettings().put(DvAiProviderPreset.OLLAMA.name(), ollama);

    ObjectMapper mapper = HopJson.newMapper();
    HopAiConfig restored = mapper.readValue(new Gson().toJson(original), HopAiConfig.class);
    HopAiProviderSettingsSupport.normalizeProviderSettings(restored);

    assertEquals(
        "grok-key", restored.getProviderSettings().get(DvAiProviderPreset.GROK.name()).getApiKey());
    assertEquals(
        "llama3.2",
        restored.getProviderSettings().get(DvAiProviderPreset.OLLAMA.name()).getModelName());
  }
}
