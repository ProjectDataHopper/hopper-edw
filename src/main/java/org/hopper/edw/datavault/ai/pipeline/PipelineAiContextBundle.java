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
package org.hopper.edw.datavault.ai.pipeline;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PipelineAiContextBundle {
  private final PipelineAiScenario scenario;
  private final String userPrompt;
  private final String structureJson;
  private final String summaryJson;
  private final String topologyXml;
  private final String checkResultsJson;
  private final String logsExcerpt;
  private final String transformCatalogJson;
  private final String focusTransformName;
  private final boolean followUp;
  private final List<String> appliedChangeSummaries;
}
