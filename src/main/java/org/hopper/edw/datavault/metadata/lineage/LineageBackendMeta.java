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
package org.hopper.edw.datavault.metadata.lineage;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;

/** Named lineage server / folder / local-models connection. */
@HopMetadata(
    key = "lineage-backend",
    name = "i18n::LineageBackendMeta.name",
    description = "i18n::LineageBackendMeta.description",
    image = "lineage-view.svg",
    documentationUrl = "/metadata-types/lineage-backend.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
public class LineageBackendMeta extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description;
  @HopMetadataProperty private boolean enabled = true;

  @HopMetadataProperty(key = "settings")
  private ILineageBackendSettings settings = new MarquezBackendSettings();

  public LineageBackendMeta() {
    super();
  }

  public LineageBackendMeta(String name) {
    super(name);
  }

  public ILineageBackendSettings getSettingsOrDefault() {
    if (settings == null) {
      settings = new MarquezBackendSettings();
    }
    return settings;
  }
}
