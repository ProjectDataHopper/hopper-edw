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

import lombok.Getter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** Advisory scenario selecting the system prompt template. */
@Getter
public enum DvAiScenario implements IEnumHasCodeAndDescription {
  SOURCE_ANALYSIS("SOURCE_ANALYSIS", "DvAiScenario.SourceAnalysis", "source-analysis"),
  TYPE_MAPPING("TYPE_MAPPING", "DvAiScenario.TypeMapping", "type-mapping"),
  DV_MODELING("DV_MODELING", "DvAiScenario.DvModeling", "dv-modeling"),
  HOP_INTEGRATION("HOP_INTEGRATION", "DvAiScenario.HopIntegration", "hop-integration"),
  ERROR_DIAGNOSIS("ERROR_DIAGNOSIS", "DvAiScenario.ErrorDiagnosis", "error-diagnosis"),
  PERFORMANCE_TUNING("PERFORMANCE_TUNING", "DvAiScenario.PerformanceTuning", "performance-tuning"),
  GENERAL("GENERAL", "DvAiScenario.General", "general");

  private final String code;
  private final String descriptionKey;
  private final String promptResource;

  DvAiScenario(String code, String descriptionKey, String promptResource) {
    this.code = code;
    this.descriptionKey = descriptionKey;
    this.promptResource = promptResource;
  }

  @Override
  public String getDescription() {
    return BaseMessages.getString(DvAiScenario.class, descriptionKey);
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DvAiScenario.class);
  }

  public static DvAiScenario lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(DvAiScenario.class, description, GENERAL);
  }

  public static DvAiScenario lookupCode(String code) {
    return IEnumHasCode.lookupCode(DvAiScenario.class, code, GENERAL);
  }

  public static DvAiScenario resolve(String value) {
    if (Utils.isEmpty(value)) {
      return GENERAL;
    }
    String trimmed = value.trim();
    for (DvAiScenario scenario : values()) {
      if (scenario.name().equalsIgnoreCase(trimmed)) {
        return scenario;
      }
    }
    DvAiScenario byCode = IEnumHasCode.lookupCode(DvAiScenario.class, trimmed, null);
    if (byCode != null) {
      return byCode;
    }
    return lookupDescription(trimmed);
  }
}
