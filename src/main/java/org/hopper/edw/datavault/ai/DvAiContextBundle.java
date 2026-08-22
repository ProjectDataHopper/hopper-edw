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

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Context artifacts assembled for the LLM advisory call. */
@Getter
@Builder
public class DvAiContextBundle {

  private final DvAiScenario scenario;
  private final String userPrompt;
  private final String modelXml;
  private final String modelSummaryJson;
  private final String modelStructureJson;
  private final String recordDefinitionsJson;
  private final String hopMetadataJson;
  private final String checkResultsJson;
  private final String loadRunMetricsJson;
  private final String executionInfoJson;
  private final String sourceClassificationJson;
  private final String logsExcerpt;

  /** True when this is a follow-up turn in an ongoing conversation. */
  private final boolean followUp;

  /** Summaries of proposals the user applied since the previous turn. */
  private final List<String> appliedChangeSummaries;
}
