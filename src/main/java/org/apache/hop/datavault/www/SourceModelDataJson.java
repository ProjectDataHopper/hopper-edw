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
package org.apache.hop.datavault.www;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.datavault.virtualization.jdbc.HopSourceModelJdbcResultSet;

/** Minimal JSON encoder for the hop-hsm wire protocol (no Jackson dependency required). */
public final class SourceModelDataJson {

  private SourceModelDataJson() {}

  public static String error(String message) {
    StringBuilder sb = new StringBuilder(128);
    sb.append("{\"ok\":false,\"v\":").append(SourceModelDataProtocol.VERSION);
    sb.append(",\"error\":");
    appendString(sb, message != null ? message : "error");
    sb.append('}');
    return sb.toString();
  }

  public static String ping(String modelName) {
    StringBuilder sb = new StringBuilder(96);
    sb.append("{\"ok\":true,\"v\":").append(SourceModelDataProtocol.VERSION);
    sb.append(",\"action\":\"ping\"");
    if (modelName != null && !modelName.isEmpty()) {
      sb.append(",\"schema\":");
      appendString(sb, modelName);
    }
    sb.append('}');
    return sb.toString();
  }

  /**
   * JDBC schemas = enabled {@code Source model service} metadata names.
   *
   * @param schemas list of {@code {n, remarks}} entries
   */
  public static String schemas(List<SchemaInfo> schemas) {
    StringBuilder sb = new StringBuilder(128);
    sb.append("{\"ok\":true,\"v\":").append(SourceModelDataProtocol.VERSION);
    sb.append(",\"schemas\":[");
    for (int i = 0; i < schemas.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      SchemaInfo s = schemas.get(i);
      sb.append("{\"n\":");
      appendString(sb, s.name());
      sb.append(",\"remarks\":");
      appendString(sb, s.remarks());
      sb.append('}');
    }
    sb.append("]}");
    return sb.toString();
  }

  public static String tables(List<TableInfo> tables) {
    StringBuilder sb = new StringBuilder(256);
    sb.append("{\"ok\":true,\"v\":").append(SourceModelDataProtocol.VERSION);
    sb.append(",\"tables\":[");
    for (int i = 0; i < tables.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      TableInfo t = tables.get(i);
      sb.append("{\"schema\":");
      appendString(sb, t.schema());
      sb.append(",\"n\":");
      appendString(sb, t.name());
      sb.append(",\"type\":");
      appendString(sb, t.type());
      sb.append(",\"remarks\":");
      appendString(sb, t.remarks());
      sb.append('}');
    }
    sb.append("]}");
    return sb.toString();
  }

  public static String columns(List<ColumnInfo> columns) {
    StringBuilder sb = new StringBuilder(512);
    sb.append("{\"ok\":true,\"v\":").append(SourceModelDataProtocol.VERSION);
    sb.append(",\"columns\":[");
    for (int i = 0; i < columns.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      ColumnInfo c = columns.get(i);
      sb.append("{\"schema\":");
      appendString(sb, c.schema());
      sb.append(",\"table\":");
      appendString(sb, c.table());
      sb.append(",\"n\":");
      appendString(sb, c.name());
      sb.append(",\"t\":");
      appendString(sb, c.typeName());
      sb.append(",\"j\":").append(c.sqlType());
      sb.append(",\"pos\":").append(c.position());
      sb.append(",\"pk\":").append(c.primaryKey());
      sb.append('}');
    }
    sb.append("]}");
    return sb.toString();
  }

  public static String queryResult(List<RowMetaAndData> rows, boolean truncated) throws Exception {
    IRowMeta meta = null;
    if (rows != null) {
      for (RowMetaAndData row : rows) {
        if (row != null && row.getRowMeta() != null) {
          meta = row.getRowMeta();
          break;
        }
      }
    }
    StringBuilder sb = new StringBuilder(Math.max(256, (rows != null ? rows.size() : 0) * 32));
    sb.append("{\"ok\":true,\"v\":").append(SourceModelDataProtocol.VERSION);
    sb.append(",\"truncated\":").append(truncated);
    sb.append(",\"columns\":[");
    if (meta != null) {
      for (int i = 0; i < meta.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        IValueMeta vm = meta.getValueMeta(i);
        int hopType = vm != null ? vm.getType() : IValueMeta.TYPE_STRING;
        sb.append("{\"n\":");
        appendString(sb, vm != null ? vm.getName() : ("c" + i));
        sb.append(",\"t\":");
        appendString(sb, HopSourceModelJdbcResultSet.hopToTypeName(hopType));
        sb.append(",\"j\":").append(HopSourceModelJdbcResultSet.hopToSqlType(hopType));
        sb.append('}');
      }
    }
    sb.append("],\"rows\":[");
    if (rows != null && meta != null) {
      for (int r = 0; r < rows.size(); r++) {
        if (r > 0) {
          sb.append(',');
        }
        RowMetaAndData row = rows.get(r);
        Object[] data = row != null ? row.getData() : null;
        sb.append('[');
        for (int c = 0; c < meta.size(); c++) {
          if (c > 0) {
            sb.append(',');
          }
          Object value = data != null && c < data.length ? data[c] : null;
          if (value == null && row != null) {
            try {
              value = row.getRowMeta().getString(data, c);
              // keep null if truly null
              if (data != null && c < data.length) {
                value = data[c];
              }
            } catch (Exception ignored) {
              // fall through
            }
          }
          appendValue(sb, value);
        }
        sb.append(']');
      }
    }
    sb.append("]}");
    return sb.toString();
  }

  private static void appendValue(StringBuilder sb, Object value) {
    if (value == null) {
      sb.append("null");
      return;
    }
    if (value instanceof Boolean b) {
      sb.append(b);
      return;
    }
    if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long) {
      sb.append(((Number) value).longValue());
      return;
    }
    if (value instanceof Float || value instanceof Double) {
      double d = ((Number) value).doubleValue();
      if (Double.isFinite(d)) {
        sb.append(d);
      } else {
        appendString(sb, String.valueOf(d));
      }
      return;
    }
    if (value instanceof BigDecimal bd) {
      sb.append(bd.toPlainString());
      return;
    }
    if (value instanceof Number n) {
      sb.append(n);
      return;
    }
    if (value instanceof Date d) {
      // ISO-ish millis for client Timestamp conversion
      sb.append(d.getTime());
      return;
    }
    if (value instanceof byte[] bytes) {
      // base64 not required for v1 — hex-ish string
      appendString(sb, new String(java.util.Base64.getEncoder().encode(bytes)));
      return;
    }
    appendString(sb, String.valueOf(value));
  }

  static void appendString(StringBuilder sb, String s) {
    sb.append('"');
    if (s != null) {
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        switch (c) {
          case '"' -> sb.append("\\\"");
          case '\\' -> sb.append("\\\\");
          case '\b' -> sb.append("\\b");
          case '\f' -> sb.append("\\f");
          case '\n' -> sb.append("\\n");
          case '\r' -> sb.append("\\r");
          case '\t' -> sb.append("\\t");
          default -> {
            if (c < 0x20) {
              sb.append(String.format("\\u%04x", (int) c));
            } else {
              sb.append(c);
            }
          }
        }
      }
    }
    sb.append('"');
  }

  public record SchemaInfo(String name, String remarks) {}

  /** {@code schema} = Source model service name (JDBC schema). */
  public record TableInfo(String schema, String name, String type, String remarks) {}

  public record ColumnInfo(
      String schema,
      String table,
      String name,
      String typeName,
      int sqlType,
      int position,
      boolean primaryKey) {}

  /** Expose Types constants used only for documentation / tests. */
  public static int varcharType() {
    return Types.VARCHAR;
  }
}
