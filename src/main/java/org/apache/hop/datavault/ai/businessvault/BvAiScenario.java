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
package org.apache.hop.datavault.ai.businessvault;

import lombok.Getter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** Advisory scenario selecting the Business Vault system prompt template. */
@Getter
public enum BvAiScenario implements IEnumHasCodeAndDescription {
  BV_MODELING("BV_MODELING", "BvAiScenario.BvModeling", "bv-modeling"),
  HOP_INTEGRATION("HOP_INTEGRATION", "BvAiScenario.HopIntegration", "hop-integration"),
  ERROR_DIAGNOSIS("ERROR_DIAGNOSIS", "BvAiScenario.ErrorDiagnosis", "error-diagnosis"),
  PERFORMANCE_TUNING("PERFORMANCE_TUNING", "BvAiScenario.PerformanceTuning", "performance-tuning"),
  GENERAL("GENERAL", "BvAiScenario.General", "general");

  private final String code;
  private final String descriptionKey;
  private final String promptResource;

  BvAiScenario(String code, String descriptionKey, String promptResource) {
    this.code = code;
    this.descriptionKey = descriptionKey;
    this.promptResource = promptResource;
  }

  @Override
  public String getDescription() {
    return BaseMessages.getString(BvAiScenario.class, descriptionKey);
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(BvAiScenario.class);
  }

  public static BvAiScenario lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(BvAiScenario.class, description, GENERAL);
  }

  public static BvAiScenario lookupCode(String code) {
    return IEnumHasCode.lookupCode(BvAiScenario.class, code, GENERAL);
  }

  public static BvAiScenario resolve(String value) {
    if (Utils.isEmpty(value)) {
      return GENERAL;
    }
    String trimmed = value.trim();
    for (BvAiScenario scenario : values()) {
      if (scenario.name().equalsIgnoreCase(trimmed)) {
        return scenario;
      }
    }
    BvAiScenario byCode = IEnumHasCode.lookupCode(BvAiScenario.class, trimmed, null);
    if (byCode != null) {
      return byCode;
    }
    return lookupDescription(trimmed);
  }
}
