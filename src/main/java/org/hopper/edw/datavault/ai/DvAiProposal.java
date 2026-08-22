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
import lombok.Getter;
import lombok.Setter;

/** A single suggested change the user can preview and apply. */
@Getter
@Setter
public class DvAiProposal {

  public enum Type {
    ADD_MODEL_NOTE,
    SET_CONFIGURATION_PROPERTY,
    RENAME_TABLE,
    ADD_HUB,
    ADD_LINK,
    ADD_SATELLITE,
    SET_BUSINESS_KEYS,
    BIND_RECORD_SOURCE,
    SET_TABLE_LOCATION
  }

  public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
  }

  private String id;
  private String description;
  private RiskLevel riskLevel = RiskLevel.MEDIUM;
  private Type type;
  private Map<String, String> parameters = new LinkedHashMap<>();

  public String parameter(String name) {
    return parameters != null ? parameters.get(name) : null;
  }
}
