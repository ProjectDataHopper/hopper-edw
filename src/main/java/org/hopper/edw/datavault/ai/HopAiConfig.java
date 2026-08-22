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
package org.hopper.edw.datavault.ai;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** Shared Hop AI assistant configuration (Data Vault, pipelines, workflows). */
@Getter
@Setter
public class HopAiConfig {

  public static final String HOP_CONFIG_HOP_AI_CONFIG_KEY = "hopAiConfig";

  private boolean aiEnabled;

  /**
   * Provider preset: GROK, OPENAI, GOOGLE_GEMINI, ANTHROPIC, OLLAMA, MISTRAL, HUGGING_FACE, CUSTOM.
   */
  private String aiProviderPreset = "GROK";

  private String aiApiKey;
  private String aiBaseUrl;
  private String aiModelName;
  private String aiTemperature = "0.3";

  /** Remembered settings keyed by {@link DvAiProviderPreset#name()}. */
  private Map<String, HopAiProviderSettings> providerSettings = new HashMap<>();

  public HopAiConfig() {}

  public HopAiConfig(HopAiConfig other) {
    if (other == null) {
      return;
    }
    aiEnabled = other.aiEnabled;
    aiProviderPreset = other.aiProviderPreset;
    aiApiKey = other.aiApiKey;
    aiBaseUrl = other.aiBaseUrl;
    aiModelName = other.aiModelName;
    aiTemperature = other.aiTemperature;
    if (other.providerSettings != null) {
      providerSettings = new HashMap<>();
      for (Map.Entry<String, HopAiProviderSettings> entry : other.providerSettings.entrySet()) {
        providerSettings.put(entry.getKey(), new HopAiProviderSettings(entry.getValue()));
      }
    }
  }
}
