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
package org.apache.hop.datavault.transform.datedimensiongenerator;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

@Transform(
    id = "DateDimensionGenerator",
    image = "date-dimension.svg",
    name = "i18n::DateDimensionGenerator.Name",
    description = "i18n::DateDimensionGenerator.Description",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Input",
    keywords = "i18n::DateDimensionGenerator.keyword",
    documentationUrl = "/pipeline/transforms/datedimensiongenerator.html")
public class DateDimensionGeneratorMeta
    extends BaseTransformMeta<DateDimensionGenerator, DateDimensionGeneratorData> {

  private static final Class<?> PKG = DateDimensionGeneratorMeta.class;

  @HopMetadataProperty(
      key = "startDate",
      injectionKey = "START_DATE",
      injectionKeyDescription = "DateDimensionGenerator.Injection.START_DATE")
  private String startDate = DateDimensionGeneratorMetaFactory.DEFAULT_START_DATE;

  @HopMetadataProperty(
      key = "endDate",
      injectionKey = "END_DATE",
      injectionKeyDescription = "DateDimensionGenerator.Injection.END_DATE")
  private String endDate = DateDimensionGeneratorMetaFactory.DEFAULT_END_DATE;

  /**
   * Optional reference date for relative attributes ({@code @rel_*} / YTD). Empty means the JVM
   * local date at runtime. Variables are supported.
   */
  @HopMetadataProperty(
      key = "referenceDate",
      injectionKey = "REFERENCE_DATE",
      injectionKeyDescription = "DateDimensionGenerator.Injection.REFERENCE_DATE")
  private String referenceDate = "";

  /**
   * Day offset applied when evaluating fiscal attributes ({@code @fiscal_*}). Variables supported.
   */
  @HopMetadataProperty(
      key = "dayOffset",
      injectionKey = "DAY_OFFSET",
      injectionKeyDescription = "DateDimensionGenerator.Injection.DAY_OFFSET")
  private String dayOffset = "0";

  /**
   * Week offset applied when evaluating fiscal attributes ({@code @fiscal_*}). Variables supported.
   */
  @HopMetadataProperty(
      key = "weekOffset",
      injectionKey = "WEEK_OFFSET",
      injectionKeyDescription = "DateDimensionGenerator.Injection.WEEK_OFFSET")
  private String weekOffset = "0";

  /**
   * Month offset applied when evaluating fiscal attributes ({@code @fiscal_*}). For example {@code
   * 6} shifts July onto January so a July-start fiscal year is easy to express.
   */
  @HopMetadataProperty(
      key = "monthOffset",
      injectionKey = "MONTH_OFFSET",
      injectionKeyDescription = "DateDimensionGenerator.Injection.MONTH_OFFSET")
  private String monthOffset = "0";

  @HopMetadataProperty(
      groupKey = "fields",
      key = "field",
      injectionGroupKey = "FIELDS",
      injectionKey = "FIELD",
      injectionKeyDescription = "DateDimensionGenerator.Injection.FIELD")
  private List<DateDimensionGeneratorField> fields = new ArrayList<>();

  public DateDimensionGeneratorMeta() {}

  @Override
  public DateDimensionGeneratorMeta clone() {
    DateDimensionGeneratorMeta meta = new DateDimensionGeneratorMeta();
    meta.startDate = startDate;
    meta.endDate = endDate;
    meta.referenceDate = referenceDate;
    meta.dayOffset = dayOffset;
    meta.weekOffset = weekOffset;
    meta.monthOffset = monthOffset;
    for (DateDimensionGeneratorField field : fields) {
      meta.fields.add(new DateDimensionGeneratorField(field));
    }
    return meta;
  }

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String name,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    try {
      DateDimensionGeneratorLogic.addFieldsToRowMeta(rowMeta, fields, name, variables);
    } catch (Exception e) {
      throw new HopTransformException(
          BaseMessages.getString(PKG, "DateDimensionGeneratorMeta.Error.GetFields"), e);
    }
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (Utils.isEmpty(startDate)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DateDimensionGeneratorMeta.CheckResult.StartDateMissing"),
              transformMeta));
    }
    if (Utils.isEmpty(endDate)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DateDimensionGeneratorMeta.CheckResult.EndDateMissing"),
              transformMeta));
    }
    try {
      DateDimensionGeneratorLogic.resolveDateRange(startDate, endDate, variables);
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DateDimensionGeneratorMeta.CheckResult.DateRangeOK"),
              transformMeta));
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transformMeta));
    }

    if (fields == null || fields.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DateDimensionGeneratorMeta.CheckResult.NoFields"),
              transformMeta));
      return;
    }

    int configuredFields = 0;
    for (DateDimensionGeneratorField field : fields) {
      if (field != null && !Utils.isEmpty(field.getName())) {
        configuredFields++;
      }
    }
    if (configuredFields == 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DateDimensionGeneratorMeta.CheckResult.NoFields"),
              transformMeta));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DateDimensionGeneratorMeta.CheckResult.FieldsOK", configuredFields),
              transformMeta));
    }

    if (input != null && input.length > 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(PKG, "DateDimensionGeneratorMeta.CheckResult.InputIgnored"),
              transformMeta));
    }
  }

  public String getStartDate() {
    return startDate;
  }

  public void setStartDate(String startDate) {
    this.startDate = startDate;
  }

  public String getEndDate() {
    return endDate;
  }

  public void setEndDate(String endDate) {
    this.endDate = endDate;
  }

  public String getReferenceDate() {
    return referenceDate;
  }

  public void setReferenceDate(String referenceDate) {
    this.referenceDate = referenceDate;
  }

  public String getDayOffset() {
    return dayOffset;
  }

  public void setDayOffset(String dayOffset) {
    this.dayOffset = dayOffset;
  }

  public String getWeekOffset() {
    return weekOffset;
  }

  public void setWeekOffset(String weekOffset) {
    this.weekOffset = weekOffset;
  }

  public String getMonthOffset() {
    return monthOffset;
  }

  public void setMonthOffset(String monthOffset) {
    this.monthOffset = monthOffset;
  }

  public List<DateDimensionGeneratorField> getFields() {
    return fields;
  }

  public void setFields(List<DateDimensionGeneratorField> fields) {
    this.fields = fields;
  }
}
