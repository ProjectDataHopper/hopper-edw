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
import org.eclipse.elk.alg.layered.options.NodePlacementStrategy;

/** Node placement strategy for ELK layered layout. */
@Getter
public enum ElkNodePlacement implements IEnumHasCodeAndDescription {
  SIMPLE(
      "SIMPLE",
      BaseMessages.getString(ElkNodePlacement.class, "ElkNodePlacement.Simple"),
      NodePlacementStrategy.SIMPLE),
  INTERACTIVE(
      "INTERACTIVE",
      BaseMessages.getString(ElkNodePlacement.class, "ElkNodePlacement.Interactive"),
      NodePlacementStrategy.INTERACTIVE),
  LINEAR_SEGMENTS(
      "LINEAR_SEGMENTS",
      BaseMessages.getString(ElkNodePlacement.class, "ElkNodePlacement.LinearSegments"),
      NodePlacementStrategy.LINEAR_SEGMENTS),
  BRANDES_KOEPF(
      "BRANDES_KOEPF",
      BaseMessages.getString(ElkNodePlacement.class, "ElkNodePlacement.BrandesKoepf"),
      NodePlacementStrategy.BRANDES_KOEPF),
  NETWORK_SIMPLEX(
      "NETWORK_SIMPLEX",
      BaseMessages.getString(ElkNodePlacement.class, "ElkNodePlacement.NetworkSimplex"),
      NodePlacementStrategy.NETWORK_SIMPLEX);

  private final String code;
  private final String description;
  private final NodePlacementStrategy elkStrategy;

  ElkNodePlacement(String code, String description, NodePlacementStrategy elkStrategy) {
    this.code = code;
    this.description = description;
    this.elkStrategy = elkStrategy;
  }

  public NodePlacementStrategy toElkStrategy() {
    return elkStrategy;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(ElkNodePlacement.class);
  }

  public static ElkNodePlacement lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        ElkNodePlacement.class, description, BRANDES_KOEPF);
  }

  public static ElkNodePlacement lookupCode(String code) {
    return IEnumHasCode.lookupCode(ElkNodePlacement.class, code, BRANDES_KOEPF);
  }
}
