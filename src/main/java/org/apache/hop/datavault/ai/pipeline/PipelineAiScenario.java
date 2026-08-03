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
package org.apache.hop.datavault.ai.pipeline;

import lombok.Getter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

@Getter
public enum PipelineAiScenario implements IEnumHasCodeAndDescription {
  PIPELINE_GENERAL("PIPELINE_GENERAL", "PipelineAiScenario.PipelineGeneral", "pipeline-general"),
  TRANSFORM_SELECTION(
      "TRANSFORM_SELECTION", "PipelineAiScenario.TransformSelection", "transform-selection"),
  PIPELINE_ERROR_DIAGNOSIS(
      "PIPELINE_ERROR_DIAGNOSIS",
      "PipelineAiScenario.PipelineErrorDiagnosis",
      "pipeline-error-diagnosis"),
  PIPELINE_DESIGN("PIPELINE_DESIGN", "PipelineAiScenario.PipelineDesign", "pipeline-design");

  private final String code;
  private final String descriptionKey;
  private final String promptResource;

  PipelineAiScenario(String code, String descriptionKey, String promptResource) {
    this.code = code;
    this.descriptionKey = descriptionKey;
    this.promptResource = promptResource;
  }

  @Override
  public String getDescription() {
    return BaseMessages.getString(PipelineAiScenario.class, descriptionKey);
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(PipelineAiScenario.class);
  }

  public static PipelineAiScenario lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        PipelineAiScenario.class, description, PIPELINE_GENERAL);
  }

  public static PipelineAiScenario lookupCode(String code) {
    return IEnumHasCode.lookupCode(PipelineAiScenario.class, code, PIPELINE_GENERAL);
  }

  public static PipelineAiScenario resolve(String value) {
    if (Utils.isEmpty(value)) {
      return PIPELINE_GENERAL;
    }
    String trimmed = value.trim();
    for (PipelineAiScenario scenario : values()) {
      if (scenario.name().equalsIgnoreCase(trimmed)) {
        return scenario;
      }
    }
    PipelineAiScenario byCode = IEnumHasCode.lookupCode(PipelineAiScenario.class, trimmed, null);
    if (byCode != null) {
      return byCode;
    }
    return lookupDescription(trimmed);
  }
}
