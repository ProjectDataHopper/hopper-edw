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
package org.apache.hop.datavault.ai.workflow;

import lombok.Getter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

@Getter
public enum WorkflowAiScenario implements IEnumHasCodeAndDescription {
  WORKFLOW_GENERAL("WORKFLOW_GENERAL", "WorkflowAiScenario.WorkflowGeneral", "workflow-general"),
  ACTION_SELECTION("ACTION_SELECTION", "WorkflowAiScenario.ActionSelection", "action-selection"),
  WORKFLOW_ERROR_DIAGNOSIS(
      "WORKFLOW_ERROR_DIAGNOSIS",
      "WorkflowAiScenario.WorkflowErrorDiagnosis",
      "workflow-error-diagnosis"),
  WORKFLOW_DESIGN("WORKFLOW_DESIGN", "WorkflowAiScenario.WorkflowDesign", "workflow-design");

  private final String code;
  private final String descriptionKey;
  private final String promptResource;

  WorkflowAiScenario(String code, String descriptionKey, String promptResource) {
    this.code = code;
    this.descriptionKey = descriptionKey;
    this.promptResource = promptResource;
  }

  @Override
  public String getDescription() {
    return BaseMessages.getString(WorkflowAiScenario.class, descriptionKey);
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(WorkflowAiScenario.class);
  }

  public static WorkflowAiScenario lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        WorkflowAiScenario.class, description, WORKFLOW_GENERAL);
  }

  public static WorkflowAiScenario lookupCode(String code) {
    return IEnumHasCode.lookupCode(WorkflowAiScenario.class, code, WORKFLOW_GENERAL);
  }

  public static WorkflowAiScenario resolve(String value) {
    if (Utils.isEmpty(value)) {
      return WORKFLOW_GENERAL;
    }
    String trimmed = value.trim();
    for (WorkflowAiScenario scenario : values()) {
      if (scenario.name().equalsIgnoreCase(trimmed)) {
        return scenario;
      }
    }
    WorkflowAiScenario byCode = IEnumHasCode.lookupCode(WorkflowAiScenario.class, trimmed, null);
    if (byCode != null) {
      return byCode;
    }
    return lookupDescription(trimmed);
  }
}
