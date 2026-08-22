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
package org.hopper.edw.datavault.layout;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;
import org.eclipse.elk.alg.layered.options.CrossingMinimizationStrategy;

/** Crossing minimization strategy for ELK layered layout. */
@Getter
public enum ElkCrossingMinimization implements IEnumHasCodeAndDescription {
  LAYER_SWEEP(
      "LAYER_SWEEP",
      BaseMessages.getString(ElkCrossingMinimization.class, "ElkCrossingMinimization.LayerSweep"),
      CrossingMinimizationStrategy.LAYER_SWEEP),
  INTERACTIVE(
      "INTERACTIVE",
      BaseMessages.getString(ElkCrossingMinimization.class, "ElkCrossingMinimization.Interactive"),
      CrossingMinimizationStrategy.INTERACTIVE),
  NONE(
      "NONE",
      BaseMessages.getString(ElkCrossingMinimization.class, "ElkCrossingMinimization.None"),
      CrossingMinimizationStrategy.NONE);

  private final String code;
  private final String description;
  private final CrossingMinimizationStrategy elkStrategy;

  ElkCrossingMinimization(
      String code, String description, CrossingMinimizationStrategy elkStrategy) {
    this.code = code;
    this.description = description;
    this.elkStrategy = elkStrategy;
  }

  public CrossingMinimizationStrategy toElkStrategy() {
    return elkStrategy;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(ElkCrossingMinimization.class);
  }

  public static ElkCrossingMinimization lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        ElkCrossingMinimization.class, description, LAYER_SWEEP);
  }

  public static ElkCrossingMinimization lookupCode(String code) {
    return IEnumHasCode.lookupCode(ElkCrossingMinimization.class, code, LAYER_SWEEP);
  }
}
