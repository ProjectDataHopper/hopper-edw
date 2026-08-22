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
package org.apache.hop.datavault.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Validation outcome for a single Hop AI proposal. */
public final class HopAiProposalValidation {

  public enum Status {
    OK,
    WARNING,
    BLOCKED
  }

  private HopAiProposalValidation() {}

  @Getter
  @AllArgsConstructor
  public static class Result {
    private final HopAiProposal proposal;
    private final Status status;
    private final String message;
  }
}
