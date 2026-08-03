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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.transforms.languagemodelchat.LanguageModelChatMeta;

/** Maps shared AI settings to Hop {@link LanguageModelChatMeta}. */
public final class HopAiLanguageModelFactory {

  private HopAiLanguageModelFactory() {}

  public static LanguageModelChatMeta fromConfig(HopAiConfig config, IVariables variables)
      throws HopException {
    if (config == null) {
      throw new HopException("Hop AI configuration is missing");
    }
    DvAiProviderPreset preset = DvAiProviderPreset.resolve(config.getAiProviderPreset());
    LanguageModelChatMeta meta = new LanguageModelChatMeta();
    meta.setDefault();
    meta.setModelType(preset.getHopModelType());
    meta.setMock(false);

    String baseUrl = resolve(variables, config.getAiBaseUrl());
    if (Utils.isEmpty(baseUrl)) {
      baseUrl = preset.getDefaultBaseUrl();
    }
    String modelName = resolve(variables, config.getAiModelName());
    if (Utils.isEmpty(modelName)) {
      modelName = preset.getDefaultModelName();
    }
    String apiKey = resolve(variables, config.getAiApiKey());
    String temperature = resolve(variables, config.getAiTemperature());
    double temp = 0.3;
    if (!Utils.isEmpty(temperature)) {
      try {
        temp = Double.parseDouble(temperature.trim());
      } catch (NumberFormatException ignored) {
        temp = 0.3;
      }
    }

    switch (preset) {
      case GROK, OPENAI, GOOGLE_GEMINI, CUSTOM ->
          applyOpenAi(meta, baseUrl, apiKey, modelName, temp);
      case ANTHROPIC -> {
        meta.setAnthropicApiKey(apiKey);
        meta.setAnthropicModelName(modelName);
        meta.setAnthropicTemperature(temp);
      }
      case OLLAMA -> {
        if (!Utils.isEmpty(baseUrl)) {
          meta.setOllamaImageEndpoint(baseUrl);
        }
        meta.setOllamaModelName(modelName);
        meta.setOllamaTemperature(temp);
      }
      case MISTRAL -> {
        if (!Utils.isEmpty(baseUrl)) {
          meta.setMistralBaseUrl(baseUrl);
        }
        meta.setMistralApiKey(apiKey);
        meta.setMistralModelName(modelName);
        meta.setMistralTemperature(temp);
      }
      case HUGGING_FACE -> {
        meta.setHuggingFaceAccessToken(apiKey);
        meta.setHuggingFaceModelId(modelName);
        meta.setHuggingFaceTemperature(temp);
      }
      default -> applyOpenAi(meta, baseUrl, apiKey, modelName, temp);
    }
    return meta;
  }

  private static void applyOpenAi(
      LanguageModelChatMeta meta, String baseUrl, String apiKey, String modelName, double temp) {
    if (!Utils.isEmpty(baseUrl)) {
      meta.setOpenAiBaseUrl(baseUrl);
    }
    meta.setOpenAiApiKey(apiKey);
    meta.setOpenAiModelName(modelName);
    meta.setOpenAiTemperature(temp);
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return "";
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
