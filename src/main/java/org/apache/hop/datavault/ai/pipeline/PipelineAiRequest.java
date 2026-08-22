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
package org.apache.hop.datavault.ai.pipeline;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

@Getter
@Builder
public class PipelineAiRequest {
  private final String userPrompt;
  private final PipelineAiScenario scenario;
  private final boolean includeCheckResults;
  private final boolean includeTopologyXml;
  private final boolean includeExecutionLog;
  private final boolean includeTransformCatalog;
  private final String focusTransformName;
  private final String logsExcerpt;
  private final boolean followUp;

  @Singular("appliedChangeSummary")
  private final List<String> appliedChangeSummaries;
}
