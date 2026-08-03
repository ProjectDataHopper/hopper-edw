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

import lombok.Getter;
import lombok.Setter;

/** Provider-specific AI assistant settings remembered when switching presets. */
@Getter
@Setter
public class HopAiProviderSettings {

  private String apiKey;
  private String baseUrl;
  private String modelName;
  private String temperature = "0.3";

  public HopAiProviderSettings() {}

  public HopAiProviderSettings(HopAiProviderSettings other) {
    if (other == null) {
      return;
    }
    apiKey = other.apiKey;
    baseUrl = other.baseUrl;
    modelName = other.modelName;
    temperature = other.temperature;
  }
}
