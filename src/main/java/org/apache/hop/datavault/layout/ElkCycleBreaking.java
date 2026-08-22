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
package org.apache.hop.datavault.layout;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;
import org.eclipse.elk.alg.layered.options.CycleBreakingStrategy;

/** Cycle breaking strategy for ELK layered layout. */
@Getter
public enum ElkCycleBreaking implements IEnumHasCodeAndDescription {
  GREEDY(
      "GREEDY",
      BaseMessages.getString(ElkCycleBreaking.class, "ElkCycleBreaking.Greedy"),
      CycleBreakingStrategy.GREEDY),
  DEPTH_FIRST(
      "DEPTH_FIRST",
      BaseMessages.getString(ElkCycleBreaking.class, "ElkCycleBreaking.DepthFirst"),
      CycleBreakingStrategy.DEPTH_FIRST),
  INTERACTIVE(
      "INTERACTIVE",
      BaseMessages.getString(ElkCycleBreaking.class, "ElkCycleBreaking.Interactive"),
      CycleBreakingStrategy.INTERACTIVE),
  MODEL_ORDER(
      "MODEL_ORDER",
      BaseMessages.getString(ElkCycleBreaking.class, "ElkCycleBreaking.ModelOrder"),
      CycleBreakingStrategy.MODEL_ORDER),
  GREEDY_MODEL_ORDER(
      "GREEDY_MODEL_ORDER",
      BaseMessages.getString(ElkCycleBreaking.class, "ElkCycleBreaking.GreedyModelOrder"),
      CycleBreakingStrategy.GREEDY_MODEL_ORDER);

  private final String code;
  private final String description;
  private final CycleBreakingStrategy elkStrategy;

  ElkCycleBreaking(String code, String description, CycleBreakingStrategy elkStrategy) {
    this.code = code;
    this.description = description;
    this.elkStrategy = elkStrategy;
  }

  public CycleBreakingStrategy toElkStrategy() {
    return elkStrategy;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(ElkCycleBreaking.class);
  }

  public static ElkCycleBreaking lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        ElkCycleBreaking.class, description, GREEDY);
  }

  public static ElkCycleBreaking lookupCode(String code) {
    return IEnumHasCode.lookupCode(ElkCycleBreaking.class, code, GREEDY);
  }
}
