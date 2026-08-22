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
package org.apache.hop.datavault.ai.businessvault;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/** User request parameters for a Business Vault advisory call. */
@Getter
@Builder
public class BvAiRequest {

  private final String userPrompt;
  private final BvAiScenario scenario;
  private final boolean includeCheckResults;
  private final boolean includeLinkedDvModel;
  private final boolean includeModelXml;
  @Builder.Default private final boolean includeLoadRunMetrics = false;
  @Builder.Default private final boolean includeExecutionInfo = false;
  private final String logsExcerpt;
  @Builder.Default private final boolean followUp = false;

  @Singular("appliedChangeSummary")
  private final List<String> appliedChangeSummaries;
}
