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
package org.hopper.edw.datavault.metadata.coaching;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import org.apache.hop.core.util.Utils;

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

  /** Case-insensitive substring match on the source label, type, or mapped table names. */
  public boolean matchesFilter(String filter) {
    if (Utils.isEmpty(filter)) {
      return true;
    }
    String needle = filter.trim().toLowerCase();
    if (containsIgnoreCase(displayLabel, needle) || containsIgnoreCase(typeLabel, needle)) {
      return true;
    }
    if (sourceRef != null
        && (containsIgnoreCase(sourceRef.getRecordName(), needle)
            || containsIgnoreCase(sourceRef.resolvedDisplayLabel(), needle))) {
      return true;
    }
    for (CoachingTargetUsage target : getTargetsOrEmpty()) {
      if (target == null) {
        continue;
      }
      if (containsIgnoreCase(target.getTableName(), needle)
          || containsIgnoreCase(target.getTableRole(), needle)
          || containsIgnoreCase(target.getSummary(), needle)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsIgnoreCase(String value, String needle) {
    return !Utils.isEmpty(value) && value.toLowerCase().contains(needle);
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
