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
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Runs a single model validation pass for coaching analysis. */
public final class CoachingModelCheckSupport {

  private CoachingModelCheckSupport() {}

  public static List<ICheckResult> runModelCheck(
      ICoachingModelAdapter adapter, IVariables variables, IHopMetadataProvider metadataProvider) {
    if (adapter instanceof DvCoachingModelAdapter dvAdapter) {
      return dvAdapter.getModel().check(metadataProvider, variables);
    }
    if (adapter instanceof BvCoachingModelAdapter bvAdapter) {
      return bvAdapter.getModel().check(metadataProvider, variables);
    }
    if (adapter instanceof DmCoachingModelAdapter dmAdapter) {
      return dmAdapter.getModel().check(metadataProvider, variables);
    }
    return List.of();
  }
}
