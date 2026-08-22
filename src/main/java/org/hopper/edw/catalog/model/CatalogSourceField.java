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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Column definition for a catalog-stored Data Vault source record layout. */
@Getter
@Setter
@NoArgsConstructor
public class CatalogSourceField {

  private String name;
  private String description;
  private String sourceDataType;
  private String length;
  private String precision;
  private int hopType;

  /**
   * Physical / stream field name before pre-model rename. Empty means the stream name equals {@link
   * #name} (the effective catalog name used by modelers).
   */
  private String sourceStreamName;

  /** 1-based position in the source primary key; zero when not part of the key. */
  private int primaryKeyPosition;

  /**
   * Optional foreign-key metadata (child column). Zero / empty when not part of an imported FK.
   * Populated by harvest refresh or Database Table Metadata import; used as the catalog contract
   * for FK drift detection and future source-model (.hsm) generation.
   */
  private String fkConstraintName;

  /** 1-based position within the composite FK; zero when not part of an FK. */
  private int fkPosition;

  private String fkReferencedSchema;
  private String fkReferencedTable;
  private String fkReferencedColumn;

  private CatalogSourceFieldInputOptions inputOptions;
}
