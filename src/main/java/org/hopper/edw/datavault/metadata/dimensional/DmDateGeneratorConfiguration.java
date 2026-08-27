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
package org.hopper.edw.datavault.metadata.dimensional;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.edw.datavault.transform.datedimensiongenerator.DateDimensionGeneratorField;
import org.hopper.edw.datavault.transform.datedimensiongenerator.DateDimensionGeneratorMeta;
import org.hopper.edw.datavault.transform.datedimensiongenerator.DateDimensionGeneratorMetaFactory;

/**
 * Calendar generator options stored on a dimensional table with {@link
 * DmSourceType#DATE_GENERATOR}. Mirrors {@link DateDimensionGeneratorMeta} so generated pipelines
 * can embed the transform without an external {@code .hpl}.
 */
@Getter
@Setter
public class DmDateGeneratorConfiguration {

  @HopMetadataProperty
  private String startDate = DateDimensionGeneratorMetaFactory.DEFAULT_START_DATE;

  @HopMetadataProperty private String endDate = DateDimensionGeneratorMetaFactory.DEFAULT_END_DATE;

  @HopMetadataProperty private String referenceDate = "";

  @HopMetadataProperty private String dayOffset = "0";

  @HopMetadataProperty private String weekOffset = "0";

  @HopMetadataProperty private String monthOffset = "0";

  @HopMetadataProperty(groupKey = "fields", key = "field")
  private List<DateDimensionGeneratorField> fields = new ArrayList<>();

  public DmDateGeneratorConfiguration() {}

  public DmDateGeneratorConfiguration(DmDateGeneratorConfiguration other) {
    if (other == null) {
      return;
    }
    this.startDate = other.startDate;
    this.endDate = other.endDate;
    this.referenceDate = other.referenceDate;
    this.dayOffset = other.dayOffset;
    this.weekOffset = other.weekOffset;
    this.monthOffset = other.monthOffset;
    if (other.fields != null) {
      for (DateDimensionGeneratorField field : other.fields) {
        this.fields.add(new DateDimensionGeneratorField(field));
      }
    }
  }

  public static DmDateGeneratorConfiguration createDefault() {
    DmDateGeneratorConfiguration config = new DmDateGeneratorConfiguration();
    config.setStartDate(DateDimensionGeneratorMetaFactory.DEFAULT_START_DATE);
    config.setEndDate(DateDimensionGeneratorMetaFactory.DEFAULT_END_DATE);
    config.setReferenceDate("");
    config.setDayOffset("0");
    config.setWeekOffset("0");
    config.setMonthOffset("0");
    config.setFields(new ArrayList<>(DateDimensionGeneratorMetaFactory.defaultFields()));
    return config;
  }

  public List<DateDimensionGeneratorField> getFieldsOrEmpty() {
    return fields != null ? fields : List.of();
  }

  public DateDimensionGeneratorMeta toTransformMeta() {
    return toTransformMeta(null);
  }

  /**
   * Maps this configuration to a transform meta. When {@code loadTimestampField} is not empty and
   * not already present in the field table, a {@code @now} Timestamp field is appended so Type 1
   * loads can populate the model load timestamp column.
   */
  public DateDimensionGeneratorMeta toTransformMeta(String loadTimestampField) {
    DateDimensionGeneratorMeta meta = new DateDimensionGeneratorMeta();
    meta.setStartDate(startDate);
    meta.setEndDate(endDate);
    meta.setReferenceDate(referenceDate);
    meta.setDayOffset(dayOffset);
    meta.setWeekOffset(weekOffset);
    meta.setMonthOffset(monthOffset);
    List<DateDimensionGeneratorField> copy = new ArrayList<>();
    for (DateDimensionGeneratorField field : getFieldsOrEmpty()) {
      copy.add(new DateDimensionGeneratorField(field));
    }
    DateDimensionGeneratorMetaFactory.ensureLoadTimestampField(copy, loadTimestampField);
    meta.setFields(copy);
    return meta;
  }

  public void applyFromTransformMeta(DateDimensionGeneratorMeta meta) {
    if (meta == null) {
      return;
    }
    this.startDate = meta.getStartDate();
    this.endDate = meta.getEndDate();
    this.referenceDate = meta.getReferenceDate();
    this.dayOffset = meta.getDayOffset();
    this.weekOffset = meta.getWeekOffset();
    this.monthOffset = meta.getMonthOffset();
    this.fields = new ArrayList<>();
    if (meta.getFields() != null) {
      for (DateDimensionGeneratorField field : meta.getFields()) {
        this.fields.add(new DateDimensionGeneratorField(field));
      }
    }
  }
}
