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
package org.hopper.edw.datavault.ai.dimensional;

import lombok.Getter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** Advisory scenario selecting the dimensional model system prompt template. */
@Getter
public enum DmAiScenario implements IEnumHasCodeAndDescription {
  DM_MODELING("DM_MODELING", "DmAiScenario.DmModeling", "dm-modeling"),
  HOP_INTEGRATION("HOP_INTEGRATION", "DmAiScenario.HopIntegration", "hop-integration"),
  ERROR_DIAGNOSIS("ERROR_DIAGNOSIS", "DmAiScenario.ErrorDiagnosis", "error-diagnosis"),
  PERFORMANCE_TUNING("PERFORMANCE_TUNING", "DmAiScenario.PerformanceTuning", "performance-tuning"),
  GENERAL("GENERAL", "DmAiScenario.General", "general");

  private final String code;
  private final String descriptionKey;
  private final String promptResource;

  DmAiScenario(String code, String descriptionKey, String promptResource) {
    this.code = code;
    this.descriptionKey = descriptionKey;
    this.promptResource = promptResource;
  }

  @Override
  public String getDescription() {
    return BaseMessages.getString(DmAiScenario.class, descriptionKey);
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DmAiScenario.class);
  }

  public static DmAiScenario lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(DmAiScenario.class, description, GENERAL);
  }

  public static DmAiScenario lookupCode(String code) {
    return IEnumHasCode.lookupCode(DmAiScenario.class, code, GENERAL);
  }

  public static DmAiScenario resolve(String value) {
    if (Utils.isEmpty(value)) {
      return GENERAL;
    }
    String trimmed = value.trim();
    for (DmAiScenario scenario : values()) {
      if (scenario.name().equalsIgnoreCase(trimmed)) {
        return scenario;
      }
    }
    DmAiScenario byCode = IEnumHasCode.lookupCode(DmAiScenario.class, trimmed, null);
    if (byCode != null) {
      return byCode;
    }
    return lookupDescription(trimmed);
  }
}
