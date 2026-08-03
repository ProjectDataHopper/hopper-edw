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
package org.apache.hop.datavault.metadata.businessvault;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Snapshot spine settings for a {@link BvPitTable}. */
@Getter
@Setter
public class BvPitSnapshotSchedule {

  public static final String DEFAULT_SATELLITE_POINTER_SUFFIX = "_ldts";
  public static final int DEFAULT_HORIZON_DAYS = 1;

  @HopMetadataProperty(storeWithCode = true)
  private BvPitCadence cadence = BvPitCadence.DAILY;

  @HopMetadataProperty(storeWithCode = true)
  private BvPitSnapshotAnchor snapshotAnchor = BvPitSnapshotAnchor.END_OF_PERIOD;

  @HopMetadataProperty private int horizonDays = DEFAULT_HORIZON_DAYS;

  @HopMetadataProperty(storeWithCode = true)
  private BvPitRangeStart rangeStart = BvPitRangeStart.EARLIEST_PARTICIPATING_SATELLITE_LOAD;

  @HopMetadataProperty private String rangeStartFixed;

  @HopMetadataProperty(storeWithCode = true)
  private BvPitRangeEnd rangeEnd = BvPitRangeEnd.NOW_MINUS_HORIZON;

  @HopMetadataProperty private String rangeEndFixed;

  @HopMetadataProperty private String satellitePointerSuffix = DEFAULT_SATELLITE_POINTER_SUFFIX;
}
