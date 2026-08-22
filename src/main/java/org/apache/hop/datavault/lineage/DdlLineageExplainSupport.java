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
package org.apache.hop.datavault.lineage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Explains generated DDL using a {@link LineageSnapshot}: which model tables/fields (and source
 * mappings) drive each structural change.
 */
public final class DdlLineageExplainSupport {

  private static final Class<?> PKG = DdlLineageExplainSupport.class;

  private DdlLineageExplainSupport() {}

  /**
   * Builds a multi-line explanation for the given DDL statements and model. Always returns a
   * non-empty string when {@code ddlStatements} has content.
   */
  public static String explain(
      List<String> ddlStatements,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (ddlStatements == null || ddlStatements.isEmpty()) {
      return "";
    }
    LineageSnapshot snapshot =
        DvModelLineageCollector.collect(model, variables, metadataProvider, null);
    return explain(ddlStatements, snapshot);
  }

  public static String explain(
      List<String> ddlStatements, BusinessVaultModel model, IVariables variables) {
    if (ddlStatements == null || ddlStatements.isEmpty()) {
      return "";
    }
    return explain(ddlStatements, BvModelLineageCollector.collect(model, variables));
  }

  public static String explain(
      List<String> ddlStatements, DimensionalModel model, IVariables variables) {
    if (ddlStatements == null || ddlStatements.isEmpty()) {
      return "";
    }
    return explain(ddlStatements, DmModelLineageCollector.collect(model, variables));
  }

  public static String explain(List<String> ddlStatements, LineageSnapshot snapshot) {
    if (ddlStatements == null || ddlStatements.isEmpty()) {
      return "";
    }
    List<DdlDelta> deltas = DdlDeltaClassifier.classify(ddlStatements);
    return format(deltas, snapshot, ddlStatements.size());
  }

  public static String format(
      List<DdlDelta> deltas, LineageSnapshot snapshot, int rawStatementCount) {
    StringBuilder sb = new StringBuilder();
    int count = deltas != null ? deltas.size() : 0;
    sb.append(
        BaseMessages.getString(
            PKG,
            "DdlLineageExplain.Header",
            Integer.toString(count > 0 ? count : rawStatementCount)));
    sb.append('\n');

    if (deltas == null || deltas.isEmpty()) {
      sb.append(BaseMessages.getString(PKG, "DdlLineageExplain.Unclassified"));
      sb.append('\n');
      return sb.toString();
    }

    int index = 1;
    for (DdlDelta delta : deltas) {
      sb.append(index++).append(") ").append(nvl(delta.getSummary())).append('\n');
      Optional<TableLineage> table =
          snapshot != null && !Utils.isEmpty(delta.getTableName())
              ? findTable(snapshot, delta.getTableName())
              : Optional.empty();

      if (table.isPresent()) {
        TableLineage tl = table.get();
        sb.append("   ")
            .append(
                BaseMessages.getString(
                    PKG,
                    "DdlLineageExplain.TableWhy",
                    nvl(tl.getLogicalName()),
                    nvl(tl.getTableType()),
                    nvl(tl.getModelName())))
            .append('\n');
        for (LineageReason reason : tl.getReasons()) {
          sb.append("   - [")
              .append(reason.getCode())
              .append("] ")
              .append(reason.getMessage())
              .append('\n');
        }
        if (!tl.getSources().isEmpty()) {
          String sources =
              tl.getSources().stream()
                  .map(
                      s -> nvl(s.getName()) + (s.getRole() != null ? " (" + s.getRole() + ")" : ""))
                  .collect(Collectors.joining(", "));
          sb.append("   ")
              .append(BaseMessages.getString(PKG, "DdlLineageExplain.Sources", sources))
              .append('\n');
        }

        if (delta.getType() == DdlDeltaType.CREATE_TABLE) {
          for (FieldLineage field : tl.getFields()) {
            sb.append("   · ").append(formatFieldLine(field)).append('\n');
          }
        } else if (!Utils.isEmpty(delta.getColumnName())) {
          Optional<FieldLineage> field = tl.findField(delta.getColumnName());
          if (field.isPresent()) {
            sb.append("   ")
                .append(BaseMessages.getString(PKG, "DdlLineageExplain.ColumnLineage"))
                .append('\n');
            sb.append("   · ").append(formatFieldLine(field.get())).append('\n');
          } else {
            sb.append("   ")
                .append(
                    BaseMessages.getString(
                        PKG, "DdlLineageExplain.ColumnNoLineage", delta.getColumnName()))
                .append('\n');
          }
        }
      } else {
        sb.append("   ")
            .append(
                BaseMessages.getString(
                    PKG, "DdlLineageExplain.NoTableLineage", nvl(delta.getTableName())))
            .append('\n');
      }
      sb.append('\n');
    }
    return sb.toString().trim() + "\n";
  }

  private static Optional<TableLineage> findTable(LineageSnapshot snapshot, String tableName) {
    Optional<TableLineage> byPhysical = snapshot.findTableByPhysicalName(tableName);
    if (byPhysical.isPresent()) {
      return byPhysical;
    }
    return snapshot.findTableByLogicalName(tableName);
  }

  private static String formatFieldLine(FieldLineage field) {
    StringBuilder line = new StringBuilder();
    line.append(nvl(field.getTargetFieldName()));
    if (field.isTechnical()) {
      line.append(" [technical]");
    }
    List<String> parts = new ArrayList<>();
    for (FieldContribution c : field.getContributions()) {
      StringBuilder part = new StringBuilder();
      if (!Utils.isEmpty(c.getSourceName()) || !Utils.isEmpty(c.getSourceFieldName())) {
        part.append(nvl(c.getSourceName()));
        if (!Utils.isEmpty(c.getSourceFieldName())) {
          part.append('.').append(c.getSourceFieldName());
        }
      }
      if (!c.getReasons().isEmpty()) {
        String codes =
            c.getReasons().stream()
                .map(r -> r.getCode().name())
                .distinct()
                .collect(Collectors.joining("/"));
        if (part.length() > 0) {
          part.append(' ');
        }
        part.append('[').append(codes).append(']');
        part.append(' ').append(c.getReasons().get(0).getMessage());
      }
      if (part.length() > 0) {
        parts.add(part.toString());
      }
    }
    if (!parts.isEmpty()) {
      line.append(" ← ").append(String.join("; ", parts));
    }
    return line.toString();
  }

  private static String nvl(String value) {
    return value != null ? value : "";
  }
}
