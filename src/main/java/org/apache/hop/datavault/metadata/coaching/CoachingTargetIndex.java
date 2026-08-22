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

import java.util.List;
import org.apache.hop.core.variables.IVariables;

/** Adapter-specific inverted index for coaching target lookups. */
public interface CoachingTargetIndex {

  List<CoachingTargetUsage> targetsFor(CoachingSourceRef sourceRef);

  static CoachingTargetIndex forAdapter(ICoachingModelAdapter adapter, IVariables variables) {
    if (adapter instanceof DvCoachingModelAdapter dvAdapter) {
      CoachingDvTargetIndex index = CoachingDvTargetIndex.build(dvAdapter.getModel(), variables);
      return sourceRef -> index.targetsFor(sourceRef, variables);
    }
    if (adapter instanceof BvCoachingModelAdapter bvAdapter) {
      CoachingBvTargetIndex index = CoachingBvTargetIndex.build(bvAdapter.getModel());
      return index::targetsFor;
    }
    if (adapter instanceof DmCoachingModelAdapter dmAdapter) {
      CoachingDmTargetIndex index = CoachingDmTargetIndex.build(dmAdapter.getModel());
      return index::targetsFor;
    }
    return sourceRef -> List.of();
  }
}
