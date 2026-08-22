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
package org.hopper.edw.datavault.metadata;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** How a DV table participates in Hop-managed loading versus external or custom integration. */
@Getter
public enum DvIntegrationMode implements IEnumHasCodeAndDescription {
  HOP_MANAGED(
      "HOP_MANAGED",
      BaseMessages.getString(DvIntegrationMode.class, "DvIntegrationMode.HopManaged")),
  EXTERNAL_READ(
      "EXTERNAL_READ",
      BaseMessages.getString(DvIntegrationMode.class, "DvIntegrationMode.ExternalRead")),
  CUSTOM_PIPELINES(
      "CUSTOM_PIPELINES",
      BaseMessages.getString(DvIntegrationMode.class, "DvIntegrationMode.CustomPipelines"));

  private final String code;
  private final String description;

  DvIntegrationMode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DvIntegrationMode.class);
  }

  public static DvIntegrationMode lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DvIntegrationMode.class, description, HOP_MANAGED);
  }

  public static DvIntegrationMode lookupCode(String code) {
    return IEnumHasCode.lookupCode(DvIntegrationMode.class, code, HOP_MANAGED);
  }
}
