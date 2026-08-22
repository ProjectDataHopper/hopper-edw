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
package org.hopper.edw.catalog.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.edw.quality.model.RecordQualityRuleBinding;

/** A named record layout and its catalog metadata (type, origin, tags, custom properties). */
@Getter
@Setter
@NoArgsConstructor
public class RecordDefinition {

  @HopMetadataProperty private RecordDefinitionKey key;

  @HopMetadataProperty private RecordDefinitionType type = RecordDefinitionType.UNKNOWN;

  @HopMetadataProperty private String description;

  @HopMetadataProperty private RecordOrigin origin;

  @HopMetadataProperty private PhysicalTableRef physicalTable;

  @HopMetadataProperty private PhysicalFileRef physicalFile;

  @HopMetadataProperty private PhysicalIcebergTableRef physicalIcebergTable;

  @HopMetadataProperty private List<String> tags = new ArrayList<>();

  @HopMetadataProperty private List<String> glossaryTerms = new ArrayList<>();

  @HopMetadataProperty
  private Map<String, CatalogCustomProperty> customProperties = new HashMap<>();

  @HopMetadataProperty(key = "validation_ack", groupKey = "validation_acks")
  private List<RecordDefinitionValidationAcknowledgement> validationAcknowledgements =
      new ArrayList<>();

  /**
   * Data quality rule bindings (library refs and/or inline rules) evaluated by the quality measure
   * engine against this dataset (source or target).
   */
  @HopMetadataProperty(key = "quality_rule", groupKey = "quality_rules")
  private List<RecordQualityRuleBinding> qualityRules = new ArrayList<>();

  /** Structured Data Vault source metadata when {@link #type} is {@code DV_SOURCE}. */
  @HopMetadataProperty private DvSourceRecord dvSource;

  /** Column layout; not directly serialized by Hop metadata (persisted via catalog plugins). */
  private IRowMeta fields;

  public void validate() {
    if (key == null) {
      throw new IllegalArgumentException("Record definition key is required");
    }
    key.validate();
  }
}
