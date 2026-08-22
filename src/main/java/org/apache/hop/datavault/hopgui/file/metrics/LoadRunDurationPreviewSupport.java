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
package org.apache.hop.datavault.hopgui.file.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.dialog.ShowRowsDialog;
import org.apache.hop.datavault.metrics.LoadRunDurationRun;
import org.apache.hop.datavault.metrics.LoadRunDurationSnapshot;
import org.apache.hop.i18n.BaseMessages;
import org.eclipse.swt.widgets.Shell;

/** Builds tabular previews of load duration metrics for {@link ShowRowsDialog}. */
public final class LoadRunDurationPreviewSupport {

  private static final Class<?> PKG = LoadRunDurationPreviewSupport.class;

  public static final String COL_MODEL_NAME = "model-name";
  public static final String COL_TABLE_NAME = "table-name";
  public static final String COL_TIMESTAMP = "timestamp";
  public static final String COL_DURATION_SECONDS = "duration in seconds";

  private LoadRunDurationPreviewSupport() {}

  public static boolean hasPreviewRows(LoadRunDurationSnapshot snapshot) {
    return !buildPreviewRows("", snapshot).isEmpty();
  }

  public static IRowMeta createPreviewRowMeta() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString(COL_MODEL_NAME));
    rowMeta.addValueMeta(new ValueMetaString(COL_TABLE_NAME));
    rowMeta.addValueMeta(new ValueMetaDate(COL_TIMESTAMP));
    ValueMetaNumber durationSeconds = new ValueMetaNumber(COL_DURATION_SECONDS);
    durationSeconds.setPrecision(3);
    rowMeta.addValueMeta(durationSeconds);
    return rowMeta;
  }

  public static List<Object[]> buildPreviewRows(
      String modelName, LoadRunDurationSnapshot snapshot) {
    if (snapshot == null || snapshot.getStatus() != LoadRunDurationSnapshot.Status.LOADED) {
      return List.of();
    }

    List<LoadRunDurationRun> runs = snapshot.getRuns();
    List<String> tableNames = snapshot.getTableNames();
    if (runs.isEmpty() || tableNames.isEmpty()) {
      return List.of();
    }

    String resolvedModelName = Const.NVL(modelName, "");
    List<PreviewRow> rows = new ArrayList<>();
    for (String tableName : tableNames) {
      for (int runIndex = 0; runIndex < runs.size(); runIndex++) {
        long durationMs = snapshot.durationMs(tableName, runIndex);
        if (durationMs <= 0L) {
          continue;
        }
        LoadRunDurationRun run = runs.get(runIndex);
        rows.add(
            new PreviewRow(
                resolvedModelName,
                Const.NVL(tableName, ""),
                run != null ? run.getFinishedAt() : null,
                durationMs / 1000.0));
      }
    }

    rows.sort(
        Comparator.comparing(PreviewRow::timestamp, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(PreviewRow::tableName));

    List<Object[]> previewRows = new ArrayList<>(rows.size());
    for (PreviewRow row : rows) {
      previewRows.add(
          new Object[] {row.modelName(), row.tableName(), row.timestamp(), row.durationSeconds()});
    }
    return previewRows;
  }

  public static void openPreviewDialog(
      Shell shell, IVariables variables, String modelName, LoadRunDurationSnapshot snapshot) {
    List<Object[]> previewRows = buildPreviewRows(modelName, snapshot);
    if (previewRows.isEmpty()) {
      return;
    }
    new ShowRowsDialog(
            shell,
            variables,
            BaseMessages.getString(PKG, "LoadRunDurationPreviewSupport.Preview.Title"),
            BaseMessages.getString(PKG, "LoadRunDurationPreviewSupport.Preview.Message", modelName),
            createPreviewRowMeta(),
            previewRows)
        .open();
  }

  private record PreviewRow(
      String modelName, String tableName, Date timestamp, double durationSeconds) {}
}
