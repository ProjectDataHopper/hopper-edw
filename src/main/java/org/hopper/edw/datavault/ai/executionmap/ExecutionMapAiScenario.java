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
package org.hopper.edw.datavault.ai.executionmap;

import lombok.Getter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** Advisory scenario for execution-map chat-only AI Help. */
@Getter
public enum ExecutionMapAiScenario implements IEnumHasCodeAndDescription {
  EXPLAIN_MAP("EXPLAIN_MAP", "ExecutionMapAiScenario.ExplainMap", "explain-map"),
  RUNTIME("RUNTIME", "ExecutionMapAiScenario.Runtime", "runtime"),
  DIFFS("DIFFS", "ExecutionMapAiScenario.Diffs", "diffs"),
  GENERAL("GENERAL", "ExecutionMapAiScenario.General", "general");

  private final String code;
  private final String descriptionKey;
  private final String promptResource;

  ExecutionMapAiScenario(String code, String descriptionKey, String promptResource) {
    this.code = code;
    this.descriptionKey = descriptionKey;
    this.promptResource = promptResource;
  }

  @Override
  public String getDescription() {
    return BaseMessages.getString(ExecutionMapAiScenario.class, descriptionKey);
  }

  public static ExecutionMapAiScenario resolve(String value) {
    if (Utils.isEmpty(value)) {
      return GENERAL;
    }
    String trimmed = value.trim();
    for (ExecutionMapAiScenario scenario : values()) {
      if (scenario.name().equalsIgnoreCase(trimmed) || scenario.code.equalsIgnoreCase(trimmed)) {
        return scenario;
      }
    }
    return IEnumHasCodeAndDescription.lookupDescription(
        ExecutionMapAiScenario.class, trimmed, GENERAL);
  }
}
