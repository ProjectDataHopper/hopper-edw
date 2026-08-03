/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metadata.dimensional;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Fact-to-dimension join: target dimension, source stream field, and fact foreign key column. */
@Getter
@Setter
@NoArgsConstructor
public class DmFactDimensionRole {

  @HopMetadataProperty private String dimensionTableName;

  /** Deprecated: retained for backward-compatible HDM/XML only. */
  @HopMetadataProperty private String roleName;

  @HopMetadataProperty private String foreignKeyColumn;

  @HopMetadataProperty private String sourceFieldName;

  @HopMetadataProperty private boolean truncateToDateKey;

  /** When true, generated fact pipelines enable Dimension Lookup preloading cache for this join. */
  @HopMetadataProperty private boolean preloadLookupCache;

  /**
   * When true, copy the fact source surrogate/hash field into the fact foreign key column instead
   * of using Dimension Lookup.
   */
  @HopMetadataProperty private boolean skipDimensionLookup;

  /**
   * When true, always use Dimension Lookup even when a source hash key passthrough would otherwise
   * apply automatically.
   */
  @HopMetadataProperty private boolean forceDimensionLookup;

  public DmFactDimensionRole(String dimensionTableName, String foreignKeyColumn) {
    this.dimensionTableName = dimensionTableName;
    this.foreignKeyColumn = foreignKeyColumn;
  }

  public DmFactDimensionRole(String dimensionTableName, String roleName, String foreignKeyColumn) {
    this.dimensionTableName = dimensionTableName;
    this.roleName = roleName;
    this.foreignKeyColumn = foreignKeyColumn;
  }
}
