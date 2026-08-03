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

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/** User request parameters for a single advisory call. */
@Getter
@Builder
public class DvAiRequest {

  private final String userPrompt;
  private final DvAiScenario scenario;
  private final boolean includeCheckResults;
  private final boolean includeCatalogSources;
  private final boolean includeModelXml;

  /** When true, attach recent load-run metrics from the operations database. */
  @Builder.Default private final boolean includeLoadRunMetrics = false;

  /**
   * When true, attach execution logs and transform metrics from the Execution Information Location.
   */
  @Builder.Default private final boolean includeExecutionInfo = false;

  private final String logsExcerpt;

  /** Catalog record definition names chosen for the advisory context (GUI picker). */
  @Singular("catalogSourceName")
  private final List<String> catalogSourceNames;

  /** True when this request continues an existing conversation. */
  @Builder.Default private final boolean followUp = false;

  /** Summaries of proposals applied since the previous turn. */
  @Singular("appliedChangeSummary")
  private final List<String> appliedChangeSummaries;
}
