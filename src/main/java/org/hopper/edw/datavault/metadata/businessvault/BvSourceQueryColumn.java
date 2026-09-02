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
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Persisted column on a {@link BvSourceQuery} for check model and SCD2 field mapping. */
@Getter
@Setter
@NoArgsConstructor
public class BvSourceQueryColumn {

  @HopMetadataProperty private String name;

  @HopMetadataProperty private String dataType;

  @HopMetadataProperty private String length;

  @HopMetadataProperty private String precision;

  public BvSourceQueryColumn(String name) {
    this.name = name;
  }

  public BvSourceQueryColumn(BvSourceQueryColumn other) {
    if (other == null) {
      return;
    }
    this.name = other.name;
    this.dataType = other.dataType;
    this.length = other.length;
    this.precision = other.precision;
  }
}
