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

/**
 * Location ids for hopper-edw AI advisors. Same string on {@code @AiAdvisorPlugin(locations)} and
 * {@code AiAdvisorOpenRequest.setLocation}. Not added to Hop {@code AiAdvisorLocations}.
 */
public final class EdwAiAdvisorLocations {

  public static final String DATA_VAULT_GRAPH = "data-vault-graph";
  public static final String BUSINESS_VAULT_GRAPH = "business-vault-graph";
  public static final String DIMENSIONAL_GRAPH = "dimensional-graph";
  public static final String SOURCE_MODEL_GRAPH = "source-model-graph";
  public static final String LINEAGE_VIEW = "lineage-view";
  public static final String EXECUTION_MAP = "execution-map";
  public static final String EDW_JOURNEY = "edw-journey";

  private EdwAiAdvisorLocations() {}
}
