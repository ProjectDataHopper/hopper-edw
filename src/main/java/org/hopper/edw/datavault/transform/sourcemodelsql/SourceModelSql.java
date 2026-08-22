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
package org.hopper.edw.datavault.transform.sourcemodelsql;

import java.util.Collections;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Executes free SQL against a Source Model ({@code .hsm}) via the Calcite virtualisation engine and
 * outputs the resulting rows.
 */
public class SourceModelSql extends BaseTransform<SourceModelSqlMeta, SourceModelSqlData> {

  private static final Class<?> PKG = SourceModelSqlMeta.class;

  public SourceModelSql(
      TransformMeta transformMeta,
      SourceModelSqlMeta meta,
      SourceModelSqlData data,
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
    if (Utils.isEmpty(meta.getSourceModelFilename())) {
      logError(BaseMessages.getString(PKG, "SourceModelSql.Log.MissingModel"));
      return false;
    }
    if (Utils.isEmpty(meta.getSql())) {
      logError(BaseMessages.getString(PKG, "SourceModelSql.Log.MissingSql"));
      return false;
    }
    data.executed = false;
    data.bufferedRows = Collections.emptyList();
    data.rowIterator = null;
    data.outputRowMeta = null;
    return true;
  }

  @Override
  public boolean processRow() throws HopException {
    // Pure input transform: no read from previous hops (same style as Date Dimension Generator).
    if (!data.executed) {
      executeQuery();
      data.executed = true;
    }

    if (data.rowIterator == null || !data.rowIterator.hasNext()) {
      setOutputDone();
      return false;
    }

    RowMetaAndData next = data.rowIterator.next();
    putRow(data.outputRowMeta, next.getData());

    if (checkFeedback(getLinesWritten()) && isDetailed()) {
      logDetailed(
          BaseMessages.getString(
              PKG, "SourceModelSql.Log.LineNumber", Long.toString(getLinesWritten())));
    }
    return true;
  }

  private void executeQuery() throws HopException {
    int rowLimit = SourceModelSqlSupport.parseRowLimit(meta.getRowLimit(), this);
    if (isBasic()) {
      logBasic(
          BaseMessages.getString(
              PKG,
              "SourceModelSql.Log.Executing",
              resolve(meta.getSourceModelFilename()),
              Integer.toString(rowLimit)));
    }
    List<RowMetaAndData> rows =
        SourceModelSqlSupport.execute(
            meta.getSourceModelFilename(), meta.getSql(), this, getMetadataProvider(), rowLimit);
    data.bufferedRows = rows != null ? rows : List.of();
    data.rowIterator = data.bufferedRows.iterator();
    if (!data.bufferedRows.isEmpty()) {
      data.outputRowMeta = data.bufferedRows.get(0).getRowMeta().clone();
    } else {
      data.outputRowMeta =
          SourceModelSqlSupport.planOutputRowMeta(
              meta.getSourceModelFilename(), meta.getSql(), this, getMetadataProvider());
      if (data.outputRowMeta == null) {
        data.outputRowMeta = new org.apache.hop.core.row.RowMeta();
      }
    }
  }
}
