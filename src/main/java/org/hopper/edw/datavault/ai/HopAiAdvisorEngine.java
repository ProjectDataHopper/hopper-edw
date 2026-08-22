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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.transforms.languagemodelchat.LanguageModelChatMeta;
import org.apache.hop.pipeline.transforms.languagemodelchat.internals.LanguageModelFacade;

/** Shared LLM call helper for Hop AI advisory features. */
public final class HopAiAdvisorEngine {

  private HopAiAdvisorEngine() {}

  public static HopAiAdvisoryResponse advise(
      HopAiConfig config,
      IVariables variables,
      String systemPrompt,
      String userPrompt,
      List<ChatMessage> conversationHistory)
      throws HopException {
    String raw = adviseRaw(config, variables, systemPrompt, userPrompt, conversationHistory);
    HopAiAdvisoryResponse advisory = new HopAiAdvisoryResponse();
    advisory.setRawResponse(raw);
    advisory.setMarkdownAdvice(raw != null ? raw.trim() : "");
    return advisory;
  }

  public static String adviseRaw(
      HopAiConfig config,
      IVariables variables,
      String systemPrompt,
      String userPrompt,
      List<ChatMessage> conversationHistory)
      throws HopException {
    validateConfig(config);

    LanguageModelChatMeta meta = HopAiLanguageModelFactory.fromConfig(config, variables);
    LanguageModelFacade facade = new LanguageModelFacade(variables, meta);

    List<ChatMessage> messages = new ArrayList<>();
    messages.add(new SystemMessage(systemPrompt));
    if (conversationHistory != null) {
      messages.addAll(conversationHistory);
    }
    messages.add(new UserMessage(userPrompt));

    try {
      Response<AiMessage> response = facade.generate(messages);
      return response != null && response.content() != null ? response.content().text() : "";
    } catch (Exception e) {
      throw new HopException("AI advisory request failed: " + e.getMessage(), e);
    }
  }

  public static void validateConfig(HopAiConfig config) throws HopException {
    if (config == null || !config.isAiEnabled()) {
      throw new HopException(
          "AI advisory is disabled. Enable it under Hop Configuration → AI Assistant.");
    }
    if (!DvAiAvailability.isLanguageModelChatAvailable()) {
      throw new HopException(
          "The Hop Language Model Chat plugin is not installed. Add hop-transform-languagemodelchat to your Hop assembly.");
    }
    if (Utils.isEmpty(config.getAiApiKey()) && requiresApiKey(config)) {
      throw new HopException("Please configure an API key for the AI provider.");
    }
  }

  private static boolean requiresApiKey(HopAiConfig config) {
    DvAiProviderPreset preset = DvAiProviderPreset.resolve(config.getAiProviderPreset());
    if (preset == DvAiProviderPreset.OLLAMA) {
      return false;
    }
    if (preset == DvAiProviderPreset.CUSTOM && Utils.isEmpty(config.getAiApiKey())) {
      return false;
    }
    return true;
  }
}
