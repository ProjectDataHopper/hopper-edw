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
package org.hopper.edw.datavault.metadata.businessvault;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * A parsed {@code ref()} from SQL, optionally enriched with resolution results for canvas
 * dependencies and SQL rewrite.
 */
@Getter
@Setter
@NoArgsConstructor
public class BvSqlRef {

  /** Optional model basename/path from {@code ref('model', 'object')}; empty for one-arg ref. */
  @HopMetadataProperty private String modelName;

  /** Object name from {@code ref(...)} (BV or DV logical/physical name). */
  @HopMetadataProperty private String objectName;

  @HopMetadataProperty(storeWithCode = true)
  private BvSqlResolvedKind resolvedKind = BvSqlResolvedKind.UNKNOWN;

  /** Resolved model file path when cross-model. */
  @HopMetadataProperty private String resolvedModelFilename;

  /** Physical table/view name after resolution. */
  @HopMetadataProperty private String resolvedTableName;

  /** When {@link #resolvedKind} is {@link BvSqlResolvedKind#DV_TABLE}. */
  @HopMetadataProperty(storeWithCode = true)
  private DvTableType resolvedDvTableType;

  public BvSqlRef(String modelName, String objectName) {
    this.modelName = modelName;
    this.objectName = objectName;
  }

  public BvSqlRef(String objectName) {
    this.objectName = objectName;
  }
}
