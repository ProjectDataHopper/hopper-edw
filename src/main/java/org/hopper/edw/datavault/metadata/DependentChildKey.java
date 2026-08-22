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
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Dependent child key on a transactional (non-historized) link.
 *
 * <p>Together with the participating hub hash keys, dependent child values uniquely identify
 * repeated relationships / events (e.g. measurement timestamp, transaction id, order line number).
 * They are stored on the link table and included in the link hash key calculation.
 */
@Getter
@Setter
public class DependentChildKey {

  /**
   * Target column name on the link table (and preferred name after rename in the load pipeline).
   */
  @HopMetadataProperty private String name;

  /** Optional description of the dependent child key. */
  @HopMetadataProperty private String description;

  /**
   * Source column in the link record source. When empty, {@link #name} is used as the source field
   * name.
   */
  @HopMetadataProperty private String sourceFieldName;

  /**
   * Hop value meta type name used for DDL (e.g. String, Integer, Timestamp). Defaults to String.
   */
  @HopMetadataProperty private String dataType = "String";

  @HopMetadataProperty private String length;

  @HopMetadataProperty private String precision;

  public DependentChildKey() {}

  public DependentChildKey(String name) {
    this.name = name;
  }

  /**
   * Resolved source field name: explicit {@link #sourceFieldName} if set, otherwise {@link #name}.
   */
  public String resolveSourceFieldName() {
    if (!Utils.isEmpty(sourceFieldName)) {
      return sourceFieldName;
    }
    return name;
  }
}
