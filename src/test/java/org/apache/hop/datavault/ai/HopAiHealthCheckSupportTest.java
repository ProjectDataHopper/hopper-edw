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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.Test;

class HopAiHealthCheckSupportTest {

  @Test
  void ollamaDoesNotRequireApiKeyForValidation() throws Exception {
    HopAiConfig config = new HopAiConfig();
    config.setAiProviderPreset(DvAiProviderPreset.OLLAMA.name());
    config.setAiBaseUrl("http://localhost:11434");
    config.setAiModelName("llama3.2");

    try {
      HopAiHealthCheckSupport.check(config, null);
    } catch (HopException e) {
      assertTrue(
          e.getMessage().contains("Language Model Chat")
              || e.getMessage().contains("AI advisory request failed")
              || e.getMessage().contains("empty response"),
          () -> "Unexpected validation error: " + e.getMessage());
    }
  }

  @Test
  void grokRequiresApiKeyForValidation() {
    HopAiConfig config = new HopAiConfig();
    config.setAiProviderPreset(DvAiProviderPreset.GROK.name());

    HopException error =
        assertThrows(HopException.class, () -> HopAiHealthCheckSupport.check(config, null));
    assertTrue(error.getMessage().contains("API key"));
  }
}
