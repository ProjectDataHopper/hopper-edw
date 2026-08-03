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
package org.apache.hop.quality.profile;

import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.quality.model.QualityEvaluationMode;
import org.apache.hop.quality.model.QualityLifecycle;

/** Builds a {@link DataProfileSnapshot} from in-memory rows. */
public final class RowProfileCollector {

  public static final int DEFAULT_MAX_DISTINCT = 200;

  private RowProfileCollector() {}

  public static DataProfileSnapshot collect(
      String subjectKey,
      IRowMeta rowMeta,
      List<Object[]> rows,
      QualityEvaluationMode mode,
      QualityLifecycle lifecycle) {
    return collect(subjectKey, rowMeta, rows, mode, lifecycle, DEFAULT_MAX_DISTINCT);
  }

  public static DataProfileSnapshot collect(
      String subjectKey,
      IRowMeta rowMeta,
      List<Object[]> rows,
      QualityEvaluationMode mode,
      QualityLifecycle lifecycle,
      int maxDistinct) {
    DataProfileSnapshot snapshot = new DataProfileSnapshot();
    snapshot.setSubjectKey(subjectKey);
    snapshot.setEvaluationMode(mode != null ? mode : QualityEvaluationMode.SAMPLE);
    snapshot.setLifecycle(lifecycle != null ? lifecycle : QualityLifecycle.AD_HOC);
    if (rows == null) {
      snapshot.setRowCount(0);
      return snapshot;
    }
    snapshot.setRowCount(rows.size());
    snapshot.setRowCountExact(
        mode != QualityEvaluationMode.SAMPLE); // sample row counts are lower bounds unless empty

    if (rowMeta == null) {
      return snapshot;
    }

    for (Object[] row : rows) {
      if (row == null) {
        continue;
      }
      for (int i = 0; i < rowMeta.size(); i++) {
        IValueMeta valueMeta = rowMeta.getValueMeta(i);
        if (valueMeta == null) {
          continue;
        }
        String name = valueMeta.getName();
        FieldProfile field = snapshot.field(name);
        Object value = i < row.length ? row[i] : null;
        boolean isNull;
        try {
          isNull = value == null || valueMeta.isNull(value);
        } catch (Exception e) {
          isNull = value == null;
        }
        if (isNull) {
          field.observeNull();
          continue;
        }
        if (valueMeta.isString()) {
          String text;
          try {
            text = valueMeta.getString(value);
          } catch (Exception e) {
            text = String.valueOf(value);
          }
          if (text != null && text.isEmpty()) {
            // Include "" in valueCounts/distinct so REGEX and exactDistinctCount match SQL
            field.observeEmptyString(maxDistinct);
            continue;
          }
          field.observeValue(text, text, maxDistinct);
          if (text != null) {
            field.observeStringLength(text.length());
          }
        } else {
          String display;
          try {
            display = valueMeta.getString(value);
          } catch (Exception e) {
            display = String.valueOf(value);
          }
          field.observeValue(value, display, maxDistinct);
          if (display != null) {
            field.observeStringLength(display.length());
          }
        }
      }
    }

    // Full-scan (non-sample) without truncation: treat observed distinct size as exact.
    if (mode == QualityEvaluationMode.FULL_SCAN || mode == QualityEvaluationMode.SQL_PUSHDOWN) {
      for (FieldProfile field : snapshot.getFields().values()) {
        if (!field.isDistinctTruncated()) {
          field.setExactDistinctCount((long) field.getDistinctValues().size());
        }
      }
    }
    return snapshot;
  }
}
