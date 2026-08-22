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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/** Quick connectivity check for the configured AI provider. */
public final class HopAiHealthCheckSupport {

  private static final String HEALTH_SYSTEM_PROMPT =
      "You are a health-check endpoint. Reply with exactly OK and nothing else.";
  private static final String HEALTH_USER_PROMPT = "health";

  private HopAiHealthCheckSupport() {}

  public static String check(HopAiConfig config, IVariables variables) throws HopException {
    validateForHealthCheck(config);
    DvAiProviderPreset preset = DvAiProviderPreset.resolve(config.getAiProviderPreset());
    String modelName = resolveModelName(config, preset, variables);
    HopAiConfig probeConfig = new HopAiConfig(config);
    probeConfig.setAiEnabled(true);
    String response =
        HopAiAdvisorEngine.adviseRaw(
            probeConfig, variables, HEALTH_SYSTEM_PROMPT, HEALTH_USER_PROMPT, null);
    if (Utils.isEmpty(response)) {
      throw new HopException("The AI provider returned an empty response.");
    }
    return "Connected to "
        + preset.getLabel()
        + " (model: "
        + modelName
        + "). Response: "
        + response.trim();
  }

  private static void validateForHealthCheck(HopAiConfig config) throws HopException {
    if (config == null) {
      throw new HopException("Hop AI configuration is missing.");
    }
    if (!DvAiAvailability.isLanguageModelChatAvailable()) {
      throw new HopException(
          "The Hop Language Model Chat plugin is not installed. Add hop-transform-languagemodelchat to your Hop assembly.");
    }
    DvAiProviderPreset preset = DvAiProviderPreset.resolve(config.getAiProviderPreset());
    if (requiresApiKey(preset) && Utils.isEmpty(config.getAiApiKey())) {
      throw new HopException("Please configure an API key for the AI provider.");
    }
  }

  private static boolean requiresApiKey(DvAiProviderPreset preset) {
    return preset != DvAiProviderPreset.OLLAMA;
  }

  private static String resolveModelName(
      HopAiConfig config, DvAiProviderPreset preset, IVariables variables) {
    String modelName = config.getAiModelName();
    if (variables != null && modelName != null) {
      modelName = variables.resolve(modelName);
    }
    if (Utils.isEmpty(modelName)) {
      modelName = preset.getDefaultModelName();
    }
    return Utils.isEmpty(modelName) ? "(default)" : modelName;
  }
}
