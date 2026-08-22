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
package org.hopper.edw.datavault.transform.datedimensiongenerator;

import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** One configurable output field for the date dimension generator. */
public class DateDimensionGeneratorField {

  @HopMetadataProperty(
      key = "name",
      injectionKey = "FIELD_NAME",
      injectionKeyDescription = "DateDimensionGenerator.Injection.FIELD_NAME")
  private String name;

  @HopMetadataProperty(
      key = "hopType",
      injectionKey = "FIELD_TYPE",
      injectionKeyDescription = "DateDimensionGenerator.Injection.FIELD_TYPE")
  private int hopType = IValueMeta.TYPE_STRING;

  @HopMetadataProperty(
      key = "length",
      injectionKey = "FIELD_LENGTH",
      injectionKeyDescription = "DateDimensionGenerator.Injection.FIELD_LENGTH")
  private String length = "";

  @HopMetadataProperty(
      key = "precision",
      injectionKey = "FIELD_PRECISION",
      injectionKeyDescription = "DateDimensionGenerator.Injection.FIELD_PRECISION")
  private String precision = "";

  @HopMetadataProperty(
      key = "formatMask",
      injectionKey = "FIELD_FORMAT_MASK",
      injectionKeyDescription = "DateDimensionGenerator.Injection.FIELD_FORMAT_MASK")
  private String formatMask;

  @HopMetadataProperty(
      key = "locale",
      injectionKey = "FIELD_LOCALE",
      injectionKeyDescription = "DateDimensionGenerator.Injection.FIELD_LOCALE")
  private String locale = "";

  public DateDimensionGeneratorField() {}

  public DateDimensionGeneratorField(
      String name, int hopType, String length, String precision, String formatMask, String locale) {
    this.name = name;
    this.hopType = hopType;
    this.length = length;
    this.precision = precision;
    this.formatMask = formatMask;
    this.locale = locale;
  }

  public DateDimensionGeneratorField(DateDimensionGeneratorField other) {
    this.name = other.name;
    this.hopType = other.hopType;
    this.length = other.length;
    this.precision = other.precision;
    this.formatMask = other.formatMask;
    this.locale = other.locale;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getHopType() {
    return hopType;
  }

  public void setHopType(int hopType) {
    this.hopType = hopType;
  }

  public String getLength() {
    return length;
  }

  public void setLength(String length) {
    this.length = length;
  }

  public String getPrecision() {
    return precision;
  }

  public void setPrecision(String precision) {
    this.precision = precision;
  }

  public String getFormatMask() {
    return formatMask;
  }

  public void setFormatMask(String formatMask) {
    this.formatMask = formatMask;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }
}
