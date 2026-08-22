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
package org.apache.hop.datavault.metadata.coaching;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/** Resolved coaching source entry for the coach panel tree. */
@Getter
@Builder
public class CoachingSourceNode {
  private final CoachingSourceRef sourceRef;
  private final String displayLabel;
  private final String typeLabel;
  @Singular private final List<CoachingTargetUsage> targets;
  @Singular private final List<CoachingInsight> insights;

  public List<CoachingTargetUsage> getTargetsOrEmpty() {
    return targets == null ? List.of() : targets;
  }

  public List<CoachingInsight> getInsightsOrEmpty() {
    return insights == null ? List.of() : insights;
  }

  public static CoachingSourceNode fromRef(CoachingSourceRef ref) {
    return CoachingSourceNode.builder()
        .sourceRef(ref)
        .displayLabel(ref.resolvedDisplayLabel())
        .typeLabel(ref.getSourceType().name())
        .targets(new ArrayList<>())
        .insights(new ArrayList<>())
        .build();
  }
}
