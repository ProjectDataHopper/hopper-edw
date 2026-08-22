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
package org.hopper.edw.catalog.metadata;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Groups DV, BV, and DM models whose source record definitions should be validated together before
 * updates.
 */
@HopMetadata(
    key = "resource-definition-group",
    name = "i18n::ResourceDefinitionGroupMeta.name",
    description = "i18n::ResourceDefinitionGroupMeta.description",
    image = "resource-definition-group.svg",
    documentationUrl = "/metadata-types/resource-definition-group.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
public class ResourceDefinitionGroupMeta extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description;

  /** Optional default data catalog connection; individual models may override. */
  @HopMetadataProperty private String dataCatalogConnection;

  @HopMetadataProperty(key = "data_vault_model", groupKey = "data_vault_models")
  private List<String> dataVaultModelFiles = new ArrayList<>();

  @HopMetadataProperty(key = "business_vault_model", groupKey = "business_vault_models")
  private List<String> businessVaultModelFiles = new ArrayList<>();

  @HopMetadataProperty(key = "dimensional_model", groupKey = "dimensional_models")
  private List<String> dimensionalModelFiles = new ArrayList<>();

  /** Number of rows to attempt reading for CSV/Parquet readability checks. */
  @HopMetadataProperty private int previewRowLimit = 10;

  /** When true, compare live source metadata with stored catalog contracts where supported. */
  @HopMetadataProperty private boolean detailedDataTypeChecking = true;

  public ResourceDefinitionGroupMeta() {
    super();
  }

  public ResourceDefinitionGroupMeta(String name) {
    super(name);
  }

  public List<String> getDataVaultModelFiles() {
    if (dataVaultModelFiles == null) {
      dataVaultModelFiles = new ArrayList<>();
    }
    return dataVaultModelFiles;
  }

  public List<String> getBusinessVaultModelFiles() {
    if (businessVaultModelFiles == null) {
      businessVaultModelFiles = new ArrayList<>();
    }
    return businessVaultModelFiles;
  }

  public List<String> getDimensionalModelFiles() {
    if (dimensionalModelFiles == null) {
      dimensionalModelFiles = new ArrayList<>();
    }
    return dimensionalModelFiles;
  }
}
