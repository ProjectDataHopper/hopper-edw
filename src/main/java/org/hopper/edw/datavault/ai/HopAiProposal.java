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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** A single suggested pipeline or workflow graph change the user can preview and apply. */
@Getter
@Setter
public class HopAiProposal {

  public enum Type {
    ADD_TRANSFORM,
    DELETE_TRANSFORM,
    RENAME_TRANSFORM,
    ADD_PIPELINE_HOP,
    DELETE_PIPELINE_HOP,
    SET_TRANSFORM_LOCATION,
    ADD_PIPELINE_NOTE,
    ADD_ACTION,
    DELETE_ACTION,
    RENAME_ACTION,
    ADD_WORKFLOW_HOP,
    DELETE_WORKFLOW_HOP,
    SET_ACTION_LOCATION,
    ADD_WORKFLOW_NOTE
  }

  public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
  }

  private static final Set<Type> PIPELINE_TYPES =
      Set.of(
          Type.ADD_TRANSFORM,
          Type.DELETE_TRANSFORM,
          Type.RENAME_TRANSFORM,
          Type.ADD_PIPELINE_HOP,
          Type.DELETE_PIPELINE_HOP,
          Type.SET_TRANSFORM_LOCATION,
          Type.ADD_PIPELINE_NOTE);

  private static final Set<Type> WORKFLOW_TYPES =
      Set.of(
          Type.ADD_ACTION,
          Type.DELETE_ACTION,
          Type.RENAME_ACTION,
          Type.ADD_WORKFLOW_HOP,
          Type.DELETE_WORKFLOW_HOP,
          Type.SET_ACTION_LOCATION,
          Type.ADD_WORKFLOW_NOTE);

  private String id;
  private String description;
  private RiskLevel riskLevel = RiskLevel.MEDIUM;
  private Type type;
  private Map<String, String> parameters = new LinkedHashMap<>();

  public String parameter(String name) {
    return parameters != null ? parameters.get(name) : null;
  }

  public boolean isPipelineType() {
    return type != null && PIPELINE_TYPES.contains(type);
  }

  public boolean isWorkflowType() {
    return type != null && WORKFLOW_TYPES.contains(type);
  }
}
