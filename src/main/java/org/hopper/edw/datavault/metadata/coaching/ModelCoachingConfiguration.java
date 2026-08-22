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
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Coaching sources curated for the model coach panel (panel visibility is in audit state). */
@Getter
@Setter
public class ModelCoachingConfiguration {

  @HopMetadataProperty(key = "coaching_source", groupKey = "coaching_sources")
  private List<CoachingSourceRef> coachingSources = new ArrayList<>();

  public List<CoachingSourceRef> getCoachingSourcesOrEmpty() {
    if (coachingSources == null) {
      coachingSources = new ArrayList<>();
    }
    return coachingSources;
  }

  public void addCoachingSource(CoachingSourceRef sourceRef) {
    if (sourceRef == null) {
      return;
    }
    List<CoachingSourceRef> sources = getCoachingSourcesOrEmpty();
    if (sources.stream().noneMatch(existing -> existing.equals(sourceRef))) {
      sources.add(sourceRef);
    }
  }

  public boolean removeCoachingSource(CoachingSourceRef sourceRef) {
    if (sourceRef == null) {
      return false;
    }
    return getCoachingSourcesOrEmpty().removeIf(existing -> existing.equals(sourceRef));
  }

  public static ModelCoachingConfiguration createEmpty() {
    return new ModelCoachingConfiguration();
  }

  public static String resolveCatalogConnectionName(
      ModelCoachingConfiguration coaching, String fallback) {
    if (coaching == null || coaching.getCoachingSourcesOrEmpty().isEmpty()) {
      return fallback;
    }
    for (CoachingSourceRef ref : coaching.getCoachingSourcesOrEmpty()) {
      if (ref != null && !Utils.isEmpty(ref.getCatalogConnection())) {
        return ref.getCatalogConnection();
      }
    }
    return fallback;
  }
}
