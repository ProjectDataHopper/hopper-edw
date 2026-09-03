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
 * Parallel copies of the generated SCD2 SQL Expression transform. {@link #ONE} is a single copy; 2
 * / 4 / 8 round-robin rows across copies for CPU-heavy calculations.
 */
@Getter
public enum BvScd2SqlExpressionCopyCount implements IEnumHasCodeAndDescription {
  ONE(
      "1",
      BaseMessages.getString(
          BvScd2SqlExpressionCopyCount.class, "BvScd2SqlExpressionCopyCount.One"),
      1),
  TWO(
      "2",
      BaseMessages.getString(
          BvScd2SqlExpressionCopyCount.class, "BvScd2SqlExpressionCopyCount.Two"),
      2),
  FOUR(
      "4",
      BaseMessages.getString(
          BvScd2SqlExpressionCopyCount.class, "BvScd2SqlExpressionCopyCount.Four"),
      4),
  EIGHT(
      "8",
      BaseMessages.getString(
          BvScd2SqlExpressionCopyCount.class, "BvScd2SqlExpressionCopyCount.Eight"),
      8);

  private final String code;
  private final String description;
  private final int copyCount;

  BvScd2SqlExpressionCopyCount(String code, String description, int copyCount) {
    this.code = code;
    this.description = description;
    this.copyCount = copyCount;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(BvScd2SqlExpressionCopyCount.class);
  }

  public static BvScd2SqlExpressionCopyCount lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        BvScd2SqlExpressionCopyCount.class, description, ONE);
  }

  public static BvScd2SqlExpressionCopyCount lookupCode(String code) {
    return IEnumHasCode.lookupCode(BvScd2SqlExpressionCopyCount.class, code, ONE);
  }
}
