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

import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** Kimball table kinds on a dimensional model canvas. */
public enum DmTableType implements IEnumHasCodeAndDescription {
  DIMENSION("DmTableType.Dimension"),
  DIMENSION_ALIAS("DmTableType.DimensionAlias"),
  JUNK_DIMENSION("DmTableType.JunkDimension"),
  RANGE_DIMENSION("DmTableType.RangeDimension"),
  FACT("DmTableType.Fact"),
  FACTLESS_FACT("DmTableType.FactlessFact"),
  PERIODIC_SNAPSHOT_FACT("DmTableType.PeriodicSnapshotFact"),
  ACCUMULATING_SNAPSHOT_FACT("DmTableType.AccumulatingSnapshotFact"),
  BRIDGE("DmTableType.Bridge"),
  AGGREGATE_FACT("DmTableType.AggregateFact");

  private final String descriptionKey;

  DmTableType(String descriptionKey) {
    this.descriptionKey = descriptionKey;
  }

  @Override
  public String getCode() {
    return name();
  }

  @Override
  public String getDescription() {
    return BaseMessages.getString(DmTableType.class, descriptionKey);
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DmTableType.class);
  }

  public static DmTableType lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(DmTableType.class, description, DIMENSION);
  }

  public static DmTableType lookupCode(String code) {
    return IEnumHasCode.lookupCode(DmTableType.class, code, DIMENSION);
  }
}
