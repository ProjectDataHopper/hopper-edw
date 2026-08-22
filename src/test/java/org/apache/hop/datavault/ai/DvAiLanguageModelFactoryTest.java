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
package org.apache.hop.datavault.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.pipeline.transforms.languagemodelchat.LanguageModelChatMeta;
import org.junit.jupiter.api.Test;

class DvAiLanguageModelFactoryTest {

  @Test
  void grokPresetMapsToXaiOpenAiCompatibleSettings() throws Exception {
    HopAiConfig config = new HopAiConfig();
    config.setAiProviderPreset(DvAiProviderPreset.GROK.name());
    config.setAiApiKey("test-key");
    config.setAiModelName("grok-4");

    LanguageModelChatMeta meta = DvAiLanguageModelFactory.fromConfig(config, null);

    assertEquals("OPEN_AI", meta.getModelType());
    assertEquals("https://api.x.ai/v1", meta.getOpenAiBaseUrl());
    assertEquals("test-key", meta.getOpenAiApiKey());
    assertEquals("grok-4", meta.getOpenAiModelName());
  }

  @Test
  void googleGeminiPresetMapsToOpenAiCompatibleSettings() throws Exception {
    HopAiConfig config = new HopAiConfig();
    config.setAiProviderPreset(DvAiProviderPreset.GOOGLE_GEMINI.name());
    config.setAiApiKey("test-key-gemini");
    config.setAiModelName("gemini-2.5-flash");

    LanguageModelChatMeta meta = DvAiLanguageModelFactory.fromConfig(config, null);

    assertEquals("OPEN_AI", meta.getModelType());
    assertEquals(
        "https://generativelanguage.googleapis.com/v1beta/openai", meta.getOpenAiBaseUrl());
    assertEquals("test-key-gemini", meta.getOpenAiApiKey());
    assertEquals("gemini-2.5-flash", meta.getOpenAiModelName());
  }
}
