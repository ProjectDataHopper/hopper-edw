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
package org.hopper.edw.datavault.metadata.businessvault;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.database.IDatabase;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/**
 * Compiles a source calendar into a half-open predicate on the leg functional timestamp. No
 * predicate when the SCD2 table does not name a calendar or the leg has no source id.
 */
public final class BvSourceCalendarSqlSupport {

  static final String TIMESTAMP_MASK = "yyyy-MM-dd HH:mm:ss";

  private BvSourceCalendarSqlSupport() {}

  public static String predicateForLeg(
      BvScd2PipelineSupport.Scd2BuildContext ctx,
      BvScd2PipelineSupport.SatelliteLeg leg,
      DatabaseMeta databaseMeta,
      String quotedTimestampColumn) {
    if (ctx == null || ctx.scd2Table == null || leg == null || Utils.isEmpty(quotedTimestampColumn)) {
      return null;
    }
    if (Utils.isEmpty(ctx.scd2Table.getSourceCalendarName())) {
      return null;
    }
    IVariables variables = ctx.variables;
    String sourceId = legSourceId(ctx.scd2Table, leg, variables);
    if (Utils.isEmpty(sourceId)) {
      return null;
    }
    BvSourceCalendar calendar =
        BvSourceCalendarSupport.find(ctx.bvModel, ctx.scd2Table.getSourceCalendarName(), variables);
    List<BvSourceCalendarEntry> windows =
        BvSourceCalendarSupport.windowsFor(calendar, sourceId, variables);
    if (windows.isEmpty()) {
      return "1 = 0";
    }
    return windowPredicate(databaseMeta, quotedTimestampColumn, windows, variables);
  }

  static String legSourceId(BvScd2Table scd2Table, BvScd2PipelineSupport.SatelliteLeg leg, IVariables variables) {
    if (scd2Table == null || leg == null) {
      return null;
    }
    if (leg.isSourceQuery()) {
      for (BvSourceQueryRef ref : scd2Table.getSourceQueryRefs()) {
        if (ref == null || Utils.isEmpty(ref.getSourceQueryName())) {
          continue;
        }
        String name = variables == null ? ref.getSourceQueryName() : variables.resolve(ref.getSourceQueryName());
        if (leg.sourceName().equals(name)) {
          return ref.getSourceId();
        }
      }
      return null;
    }
    BvScd2SatelliteConfig config =
        BvScd2FieldMappingValidationSupport.findSatelliteConfig(
            scd2Table, leg.sourceName(), variables);
    return config == null ? null : config.getSourceId();
  }

  /**
   * Half-open windows OR-ed together. {@code effectiveTo} empty means no upper bound. Returns
   * {@code null} when there is nothing to apply.
   */
  public static String windowPredicate(
      DatabaseMeta databaseMeta,
      String quotedTimestampColumn,
      List<BvSourceCalendarEntry> windows,
      IVariables variables) {
    if (Utils.isEmpty(quotedTimestampColumn) || windows == null || windows.isEmpty()) {
      return null;
    }
    List<String> parts = new ArrayList<>();
    for (BvSourceCalendarEntry window : windows) {
      if (window == null) {
        continue;
      }
      Date from = BvSourceCalendarSupport.parseTimestamp(window.getEffectiveFrom(), variables);
      Date to =
          Utils.isEmpty(window.getEffectiveTo())
              ? null
              : BvSourceCalendarSupport.parseTimestamp(window.getEffectiveTo(), variables);
      if (from == null) {
        continue;
      }
      StringBuilder part = new StringBuilder();
      part.append(quotedTimestampColumn).append(" >= ").append(timestampLiteral(databaseMeta, from));
      if (to != null) {
        part.append(" AND ");
        part.append(quotedTimestampColumn).append(" < ").append(timestampLiteral(databaseMeta, to));
      }
      parts.add(part.toString());
    }
    if (parts.isEmpty()) {
      return "1 = 0";
    }
    if (parts.size() == 1) {
      return "(" + parts.get(0) + ")";
    }
    return "(" + String.join(" OR ", parts) + ")";
  }

  static String timestampLiteral(DatabaseMeta databaseMeta, Date date) {
    String formatted = new SimpleDateFormat(TIMESTAMP_MASK).format(date);
    String quoted = "'" + formatted + "'";
    if (databaseMeta == null) {
      return quoted;
    }
    IDatabase database = databaseMeta.getIDatabase();
    if (database == null) {
      return quoted;
    }
    try {
      return database.getSqlValue(new ValueMetaDate("bound"), date, TIMESTAMP_MASK);
    } catch (HopValueException e) {
      return quoted;
    }
  }
}
