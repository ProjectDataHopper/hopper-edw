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

/** Assembles Milestone 2 system-prompt supplements from bundled Hop standards resources. */
public final class HopAiM2PromptSupport {

  private static final String PROMPT_ROOT = "/org/apache/hop/datavault/ai/prompts/hop-standards/";

  private HopAiM2PromptSupport() {}

  public static String buildM2Supplement() throws HopException {
    StringBuilder prompt = new StringBuilder();
    prompt
        .append(HopAiPromptLoader.loadResource(PROMPT_ROOT, "preamble-hop-m2-supplement.txt"))
        .append('\n');
    prompt
        .append(HopAiPromptLoader.loadResource(PROMPT_ROOT, "standards-condensed.txt"))
        .append("\n\n");
    prompt
        .append(HopAiPromptLoader.loadResource(PROMPT_ROOT, "skeleton-reference.txt"))
        .append("\n\n");
    prompt
        .append(HopAiPromptLoader.loadResource(PROMPT_ROOT, "hop-proposals-schema.txt"))
        .append("\n\n");
    prompt.append("Transform plugin type index JSON:\n");
    prompt.append(HopAiPromptLoader.loadResource(PROMPT_ROOT, "transform-type-index.json"));
    return prompt.toString();
  }
}
