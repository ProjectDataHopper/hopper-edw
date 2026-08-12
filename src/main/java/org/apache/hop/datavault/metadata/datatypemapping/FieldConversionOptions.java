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
package org.apache.hop.datavault.metadata.datatypemapping;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Shared Hop value-meta conversion attributes used by data type mapping rules, per-source field
 * overrides, catalog publish, and Select Values meta changes.
 */
@Getter
@Setter
@NoArgsConstructor
public class FieldConversionOptions {

  @HopMetadataProperty private String conversionMask;
  @HopMetadataProperty private String decimalSymbol;
  @HopMetadataProperty private String groupingSymbol;
  @HopMetadataProperty private String currencySymbol;
  @HopMetadataProperty private String dateFormatLocale;
  @HopMetadataProperty private String dateFormatTimeZone;
  @HopMetadataProperty private boolean dateFormatLenient;
  @HopMetadataProperty private boolean lenientStringToNumber;
  @HopMetadataProperty private String encoding;
  @HopMetadataProperty private String roundingType;
  @HopMetadataProperty private String storageType;

  /** Hop trim type name or empty when unset. */
  @HopMetadataProperty private String trimType;

  public FieldConversionOptions(FieldConversionOptions other) {
    if (other == null) {
      return;
    }
    this.conversionMask = other.conversionMask;
    this.decimalSymbol = other.decimalSymbol;
    this.groupingSymbol = other.groupingSymbol;
    this.currencySymbol = other.currencySymbol;
    this.dateFormatLocale = other.dateFormatLocale;
    this.dateFormatTimeZone = other.dateFormatTimeZone;
    this.dateFormatLenient = other.dateFormatLenient;
    this.lenientStringToNumber = other.lenientStringToNumber;
    this.encoding = other.encoding;
    this.roundingType = other.roundingType;
    this.storageType = other.storageType;
    this.trimType = other.trimType;
  }

  /**
   * True when any conversion attribute is set (including boolean flags left at false only count if
   * mask/symbols set).
   */
  public boolean hasAnyAttribute() {
    return !Utils.isEmpty(conversionMask)
        || !Utils.isEmpty(decimalSymbol)
        || !Utils.isEmpty(groupingSymbol)
        || !Utils.isEmpty(currencySymbol)
        || !Utils.isEmpty(dateFormatLocale)
        || !Utils.isEmpty(dateFormatTimeZone)
        || dateFormatLenient
        || lenientStringToNumber
        || !Utils.isEmpty(encoding)
        || !Utils.isEmpty(roundingType)
        || !Utils.isEmpty(storageType)
        || !Utils.isEmpty(trimType);
  }

  /**
   * Overlay non-empty attributes from {@code overlay} onto this instance (attribute-level merge).
   * Boolean flags from overlay win when overlay has any conversion attribute or the flag is true.
   */
  public void mergeFrom(FieldConversionOptions overlay) {
    if (overlay == null) {
      return;
    }
    if (!Utils.isEmpty(overlay.conversionMask)) {
      this.conversionMask = overlay.conversionMask;
    }
    if (!Utils.isEmpty(overlay.decimalSymbol)) {
      this.decimalSymbol = overlay.decimalSymbol;
    }
    if (!Utils.isEmpty(overlay.groupingSymbol)) {
      this.groupingSymbol = overlay.groupingSymbol;
    }
    if (!Utils.isEmpty(overlay.currencySymbol)) {
      this.currencySymbol = overlay.currencySymbol;
    }
    if (!Utils.isEmpty(overlay.dateFormatLocale)) {
      this.dateFormatLocale = overlay.dateFormatLocale;
    }
    if (!Utils.isEmpty(overlay.dateFormatTimeZone)) {
      this.dateFormatTimeZone = overlay.dateFormatTimeZone;
    }
    if (overlay.dateFormatLenient) {
      this.dateFormatLenient = true;
    }
    if (overlay.lenientStringToNumber) {
      this.lenientStringToNumber = true;
    }
    if (!Utils.isEmpty(overlay.encoding)) {
      this.encoding = overlay.encoding;
    }
    if (!Utils.isEmpty(overlay.roundingType)) {
      this.roundingType = overlay.roundingType;
    }
    if (!Utils.isEmpty(overlay.storageType)) {
      this.storageType = overlay.storageType;
    }
    if (!Utils.isEmpty(overlay.trimType)) {
      this.trimType = overlay.trimType;
    }
  }

  public boolean isEmpty() {
    return !hasAnyAttribute();
  }
}
