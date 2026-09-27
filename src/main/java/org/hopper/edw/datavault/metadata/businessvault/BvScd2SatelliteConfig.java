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

/** Per-satellite overrides used when building a multi-satellite SCD2 table. */
@Getter
@Setter
@NoArgsConstructor
public class BvScd2SatelliteConfig {

  @HopMetadataProperty private String satelliteName;

  @HopMetadataProperty private String functionalTimestampField;

  @HopMetadataProperty private String sourceIndicatorValue;

  /** Source id on the SCD2 table's {@link BvSourceCalendar}. Empty means this leg is not filtered. */
  @HopMetadataProperty private String sourceId;

  @HopMetadataProperty(storeWithCode = true)
  private BvLegOperation op;

  @HopMetadataProperty(storeWithCode = true)
  private BvNullPolicy nullPolicyDefault;

  /** Blank uses the calendar row priority. Survivorship reads this later. */
  @HopMetadataProperty private String priorityOverride;

  /**
   * Optional source column whose row value is upsert, delete, or seed. Blank uses {@link #op}. A
   * key that does not appear on the leg is not a delete.
   */
  @HopMetadataProperty private String opField;

  public BvScd2SatelliteConfig(String satelliteName) {
    this.satelliteName = satelliteName;
  }

  public BvScd2SatelliteConfig(BvScd2SatelliteConfig other) {
    if (other != null) {
      satelliteName = other.satelliteName;
      functionalTimestampField = other.functionalTimestampField;
      sourceIndicatorValue = other.sourceIndicatorValue;
      sourceId = other.sourceId;
      op = other.op;
      nullPolicyDefault = other.nullPolicyDefault;
      priorityOverride = other.priorityOverride;
      opField = other.opField;
    }
  }
}
