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
package org.apache.hop.datavault.transform.datedimensiongenerator;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorLogic.DateRange;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Generates one row per day between a start and end date with configurable calendar fields. */
public class DateDimensionGenerator
    extends BaseTransform<DateDimensionGeneratorMeta, DateDimensionGeneratorData> {

  private static final Class<?> PKG = DateDimensionGeneratorMeta.class;

  public DateDimensionGenerator(
      TransformMeta transformMeta,
      DateDimensionGeneratorMeta meta,
      DateDimensionGeneratorData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean init() {
    if (!super.init()) {
      return false;
    }
    try {
      DateRange range =
          DateDimensionGeneratorLogic.resolveDateRange(
              meta.getStartDate(), meta.getEndDate(), this);
      data.currentDate = range.startDate();
      data.endDate = range.endDate();
      data.outputRowMeta =
          DateDimensionGeneratorLogic.buildOutputRowMeta(
              meta.getFields(), getTransformName(), this);
      data.preparedFields =
          DateDimensionGeneratorLogic.prepareFields(meta.getFields(), getTransformName(), this);
      return true;
    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "DateDimensionGenerator.Log.UnableToInitialize"), e);
      return false;
    }
  }

  @Override
  public boolean processRow() throws HopException {
    if (data.currentDate == null || data.currentDate.isAfter(data.endDate)) {
      setOutputDone();
      return false;
    }

    Object[] row = DateDimensionGeneratorLogic.buildRow(data.currentDate, data.preparedFields);
    putRow(data.outputRowMeta, row);
    data.currentDate = data.currentDate.plusDays(1);

    if (checkFeedback(getLinesWritten()) && isDetailed()) {
      logDetailed(
          BaseMessages.getString(
              PKG, "DateDimensionGenerator.Log.LineNumber", Long.toString(getLinesWritten())));
    }
    return true;
  }
}
