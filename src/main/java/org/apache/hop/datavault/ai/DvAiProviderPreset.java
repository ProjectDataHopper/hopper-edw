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

import org.apache.hop.core.util.Utils;

/** LLM provider presets mapped to Hop Language Model Chat {@code ModelType} codes. */
public enum DvAiProviderPreset {
  GROK("Grok (xAI)", "OPEN_AI", "https://api.x.ai/v1", "grok-4"),
  OPENAI("OpenAI", "OPEN_AI", "https://api.openai.com/v1", "gpt-4o-mini"),
  GOOGLE_GEMINI(
      "Google (Gemini)",
      "OPEN_AI",
      "https://generativelanguage.googleapis.com/v1beta/openai",
      "gemini-2.5-flash"),
  ANTHROPIC("Anthropic", "ANTHROPIC", "", "claude-3-5-sonnet-20241022"),
  OLLAMA("Ollama", "OLLAMA", "http://localhost:11434", "llama3.2"),
  MISTRAL("Mistral", "MISTRAL", "https://api.mistral.ai/v1", "mistral-small-latest"),
  HUGGING_FACE("Hugging Face", "HUGGING_FACE", "", ""),
  CUSTOM("Custom", "OPEN_AI", "", "");

  private final String label;
  private final String hopModelType;
  private final String defaultBaseUrl;
  private final String defaultModelName;

  DvAiProviderPreset(
      String label, String hopModelType, String defaultBaseUrl, String defaultModelName) {
    this.label = label;
    this.hopModelType = hopModelType;
    this.defaultBaseUrl = defaultBaseUrl;
    this.defaultModelName = defaultModelName;
  }

  public String getLabel() {
    return label;
  }

  /** Hop {@code ModelType} enum code stored in {@code LanguageModelChatMeta#getModelType()}. */
  public String getHopModelType() {
    return hopModelType;
  }

  public String getDefaultBaseUrl() {
    return defaultBaseUrl;
  }

  public String getDefaultModelName() {
    return defaultModelName;
  }

  public static DvAiProviderPreset resolve(String value) {
    if (Utils.isEmpty(value)) {
      return GROK;
    }
    for (DvAiProviderPreset preset : values()) {
      if (preset.name().equalsIgnoreCase(value.trim())) {
        return preset;
      }
    }
    return GROK;
  }

  public static String[] labels() {
    DvAiProviderPreset[] values = values();
    String[] labels = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      labels[i] = values[i].label;
    }
    return labels;
  }

  public static DvAiProviderPreset fromLabel(String label) {
    if (Utils.isEmpty(label)) {
      return GROK;
    }
    for (DvAiProviderPreset preset : values()) {
      if (preset.label.equalsIgnoreCase(label.trim())) {
        return preset;
      }
    }
    return GROK;
  }
}
