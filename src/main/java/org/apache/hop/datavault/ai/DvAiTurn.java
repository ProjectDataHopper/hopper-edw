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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** One user question and assistant reply in an AI advisory conversation. */
@Getter
@Setter
public class DvAiTurn {

  private String userPrompt;

  /** Exact user message content sent to the LLM for this turn. */
  private String llmUserMessage;

  /** Assistant advice with dv_proposals blocks removed. */
  private String assistantAdvice;

  private List<DvAiProposal> proposals = new ArrayList<>();
  private List<String> appliedSummaries = new ArrayList<>();
  private boolean proposalBlockPresent;
  private boolean proposalsDropped;
}
