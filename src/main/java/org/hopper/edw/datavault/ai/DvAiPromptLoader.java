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

/** Loads system prompt templates from classpath resources. */
public final class DvAiPromptLoader {

  private static final String PROMPT_ROOT = "/org/hopper/edw/datavault/ai/prompts/";

  private DvAiPromptLoader() {}

  public static String loadPreamble() throws HopException {
    return HopAiPromptLoader.loadResource(PROMPT_ROOT, "preamble.txt");
  }

  public static String loadScenarioPrompt(DvAiScenario scenario) throws HopException {
    String name =
        scenario != null ? scenario.getPromptResource() : DvAiScenario.GENERAL.getPromptResource();
    return HopAiPromptLoader.loadResource(PROMPT_ROOT, name + ".txt");
  }
}
