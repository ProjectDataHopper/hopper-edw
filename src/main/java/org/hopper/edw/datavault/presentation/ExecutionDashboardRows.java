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
package org.hopper.edw.datavault.presentation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.execution.Execution;
import org.apache.hop.execution.ExecutionInfoLocation;
import org.apache.hop.execution.ExecutionState;
import org.apache.hop.execution.ExecutionType;
import org.apache.hop.execution.IExecutionInfoLocation;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Maps Hop execution-info records to rows for generated dashboards. */
public final class ExecutionDashboardRows {

  public static final String COL_NAME = "name";
  public static final String COL_TYPE = "type";
  public static final String COL_STATUS = "status";
  public static final String COL_DURATION_MS = "duration_ms";
  public static final String COL_STARTED = "started";
  public static final String COL_COUNT = "count";
  public static final String COL_FILENAME = "filename";

  private static final SimpleDateFormat STARTED_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  private ExecutionDashboardRows() {}

  public static RowMeta rowMeta() {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString(COL_NAME));
    meta.addValueMeta(new ValueMetaString(COL_TYPE));
    meta.addValueMeta(new ValueMetaString(COL_STATUS));
    meta.addValueMeta(new ValueMetaInteger(COL_DURATION_MS));
    meta.addValueMeta(new ValueMetaString(COL_STARTED));
    meta.addValueMeta(new ValueMetaInteger(COL_COUNT));
    meta.addValueMeta(new ValueMetaString(COL_FILENAME));
    return meta;
  }

  public static RowMetaAndData toRow(Execution execution, ExecutionState state) {
    String status = statusOf(state);
    long duration = durationMs(execution, state);
    String started = formatStarted(execution == null ? null : execution.getExecutionStartDate());
    String type =
        execution != null && execution.getExecutionType() != null
            ? execution.getExecutionType().name()
            : "";
    String name = execution != null ? StringUtils.defaultString(execution.getName()) : "";
    String filename = execution != null ? StringUtils.defaultString(execution.getFilename()) : "";
    return new RowMetaAndData(rowMeta(), name, type, status, duration, started, 1L, filename);
  }

  public static List<RowMetaAndData> collect(
      IHopMetadataProvider hopMetadata,
      IVariables variables,
      String filenameOrNameFilter,
      int limit)
      throws Exception {
    List<RowMetaAndData> rows = new ArrayList<>();
    if (hopMetadata == null) {
      return rows;
    }
    List<String> locationNames;
    try {
      locationNames = hopMetadata.getSerializer(ExecutionInfoLocation.class).listObjectNames();
    } catch (Exception e) {
      return rows;
    }
    if (locationNames == null || locationNames.isEmpty()) {
      return rows;
    }
    int cap = Math.max(1, limit);
    for (String locationName : locationNames) {
      if (rows.size() >= cap) {
        break;
      }
      ExecutionInfoLocation meta =
          hopMetadata.getSerializer(ExecutionInfoLocation.class).load(locationName);
      if (meta == null || meta.getExecutionInfoLocation() == null) {
        continue;
      }
      IExecutionInfoLocation location = meta.getExecutionInfoLocation();
      try {
        location.initialize(variables, hopMetadata);
        List<String> ids = location.getExecutionIds(false, cap);
        if (ids == null) {
          continue;
        }
        for (String id : ids) {
          if (rows.size() >= cap) {
            break;
          }
          Execution execution = location.getExecution(id);
          if (execution == null) {
            continue;
          }
          ExecutionType type = execution.getExecutionType();
          if (type != ExecutionType.Pipeline && type != ExecutionType.Workflow) {
            continue;
          }
          if (StringUtils.isNotBlank(filenameOrNameFilter)
              && !matchesFilter(execution, filenameOrNameFilter)) {
            continue;
          }
          ExecutionState state = location.getExecutionState(id, false);
          rows.add(toRow(execution, state));
        }
      } catch (Exception ignored) {
        // Skip locations that cannot be read.
      } finally {
        try {
          location.close();
        } catch (Exception ignored) {
          // best effort
        }
      }
    }
    return rows;
  }

  static boolean matchesFilter(Execution execution, String filter) {
    if (execution == null || StringUtils.isBlank(filter)) {
      return true;
    }
    String needle = filter.trim();
    String filename = StringUtils.defaultString(execution.getFilename());
    String name = StringUtils.defaultString(execution.getName());
    return filename.contains(needle)
        || filename.endsWith(needle)
        || name.equalsIgnoreCase(needle)
        || filename.equalsIgnoreCase(needle);
  }

  static String statusOf(ExecutionState state) {
    if (state == null) {
      return "UNKNOWN";
    }
    if (state.isFailed()) {
      return "FAILED";
    }
    if (state.isRunning()) {
      return "RUNNING";
    }
    if (state.isFinished()) {
      return "SUCCESS";
    }
    if (StringUtils.isNotBlank(state.getStatusDescription())) {
      return state.getStatusDescription();
    }
    return "UNKNOWN";
  }

  static long durationMs(Execution execution, ExecutionState state) {
    Date start = execution == null ? null : execution.getExecutionStartDate();
    Date end = state == null ? null : state.getExecutionEndDate();
    if (start == null) {
      return 0L;
    }
    if (end == null) {
      end = new Date();
    }
    long ms = end.getTime() - start.getTime();
    return Math.max(0L, ms);
  }

  static String formatStarted(Date started) {
    if (started == null) {
      return "";
    }
    synchronized (STARTED_FORMAT) {
      return STARTED_FORMAT.format(started);
    }
  }
}
