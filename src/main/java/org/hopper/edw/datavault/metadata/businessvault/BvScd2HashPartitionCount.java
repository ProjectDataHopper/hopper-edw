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
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/**
 * Optional hash-key split for a full-rebuild Business Vault SCD2 load. {@link #NONE} keeps a single
 * pipeline; 4 / 8 / 16 run sequential partition loads after a one-time truncate.
 */
@Getter
public enum BvScd2HashPartitionCount implements IEnumHasCodeAndDescription {
  NONE(
      "NONE",
      BaseMessages.getString(BvScd2HashPartitionCount.class, "BvScd2HashPartitionCount.None"),
      1),
  FOUR(
      "4",
      BaseMessages.getString(BvScd2HashPartitionCount.class, "BvScd2HashPartitionCount.Four"),
      4),
  EIGHT(
      "8",
      BaseMessages.getString(BvScd2HashPartitionCount.class, "BvScd2HashPartitionCount.Eight"),
      8),
  SIXTEEN(
      "16",
      BaseMessages.getString(BvScd2HashPartitionCount.class, "BvScd2HashPartitionCount.Sixteen"),
      16);

  private final String code;
  private final String description;
  private final int partitionCount;

  BvScd2HashPartitionCount(String code, String description, int partitionCount) {
    this.code = code;
    this.description = description;
    this.partitionCount = partitionCount;
  }

  public boolean isPartitioned() {
    return partitionCount > 1;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(BvScd2HashPartitionCount.class);
  }

  public static BvScd2HashPartitionCount lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        BvScd2HashPartitionCount.class, description, NONE);
  }

  public static BvScd2HashPartitionCount lookupCode(String code) {
    return IEnumHasCode.lookupCode(BvScd2HashPartitionCount.class, code, NONE);
  }
}
