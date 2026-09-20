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
package org.hopper.edw.semantic.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Pointer from a logical entity to a physical table (dimensional, catalog, or RDBMS). */
@Getter
@Setter
@NoArgsConstructor
public class SemanticPhysicalBinding {

  @HopMetadataProperty(storeWithCode = true)
  private SemanticBindingKind kind = SemanticBindingKind.HDM;

  /** Path to the `.hdm` file when {@link #kind} is {@link SemanticBindingKind#HDM}. */
  @HopMetadataProperty private String modelFilename;

  /** Dimensional / catalog / logical table name. */
  @HopMetadataProperty private String tableName;

  /** Physical warehouse table (defaults to {@link #tableName}). */
  @HopMetadataProperty private String physicalTableName;

  /** Hop RDBMS metadata name when {@link #kind} is {@link SemanticBindingKind#RDBMS}. */
  @HopMetadataProperty private String databaseMetaName;

  @HopMetadataProperty private String schemaName;

  /** Catalog record key when {@link #kind} is {@link SemanticBindingKind#CATALOG}. */
  @HopMetadataProperty private String catalogRecordKey;

  public static SemanticPhysicalBinding hdm(
      String modelFilename, String tableName, String physicalTableName) {
    SemanticPhysicalBinding binding = new SemanticPhysicalBinding();
    binding.kind = SemanticBindingKind.HDM;
    binding.modelFilename = modelFilename;
    binding.tableName = tableName;
    binding.physicalTableName = physicalTableName;
    return binding;
  }
}
