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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.metadata.DvTableType;

/** Lookup and Check model rules for {@link BvSourceCalendar}. */
public final class BvSourceCalendarSupport {

  private static final Class<?> PKG = BvSourceCalendarSupport.class;

  static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

  private BvSourceCalendarSupport() {}

  public static BvSourceCalendar find(
      BusinessVaultModel model, String calendarName, IVariables variables) {
    if (model == null || Utils.isEmpty(calendarName)) {
      return null;
    }
    String resolved = resolve(calendarName, variables);
    IBvTable table = model.findTable(resolved);
    if (table instanceof BvSourceCalendar calendar) {
      return calendar;
    }
    return null;
  }

  public static void validateEntries(
      List<ICheckResult> remarks, BvSourceCalendar calendar, IVariables variables) {
    if (calendar == null) {
      return;
    }
    if (calendar.getEntries().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "BvSourceCalendar.CheckResult.Empty", calendar.getName()),
              calendar));
      return;
    }

    Map<String, List<ParsedWindow>> bySource = new LinkedHashMap<>();
    for (BvSourceCalendarEntry entry : calendar.getEntries()) {
      if (entry == null) {
        continue;
      }
      String sourceId = resolve(entry.getSourceId(), variables);
      if (Utils.isEmpty(sourceId)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "BvSourceCalendar.CheckResult.MissingSourceId", calendar.getName()),
                calendar));
        continue;
      }
      if (!Utils.isEmpty(entry.getPriority())
          && !isInteger(resolve(entry.getPriority(), variables))) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvSourceCalendar.CheckResult.InvalidPriority",
                    calendar.getName(),
                    sourceId,
                    entry.getPriority()),
                calendar));
      }
      Date from = parseTimestamp(entry.getEffectiveFrom(), variables);
      if (Utils.isEmpty(entry.getEffectiveFrom()) || from == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvSourceCalendar.CheckResult.InvalidFrom",
                    calendar.getName(),
                    sourceId,
                    Const.NVL(entry.getEffectiveFrom(), "")),
                calendar));
        continue;
      }
      Date to = null;
      if (!Utils.isEmpty(entry.getEffectiveTo())) {
        to = parseTimestamp(entry.getEffectiveTo(), variables);
        if (to == null) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvSourceCalendar.CheckResult.InvalidTo",
                      calendar.getName(),
                      sourceId,
                      entry.getEffectiveTo()),
                  calendar));
          continue;
        }
        if (!from.before(to)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvSourceCalendar.CheckResult.InvertedWindow",
                      calendar.getName(),
                      sourceId),
                  calendar));
          continue;
        }
      }
      bySource.computeIfAbsent(sourceId, key -> new ArrayList<>()).add(new ParsedWindow(from, to));
    }

    for (Map.Entry<String, List<ParsedWindow>> group : bySource.entrySet()) {
      List<ParsedWindow> windows = group.getValue();
      windows.sort(Comparator.comparing(window -> window.from));
      for (int i = 1; i < windows.size(); i++) {
        ParsedWindow previous = windows.get(i - 1);
        ParsedWindow next = windows.get(i);
        if (previous.to == null || next.from.before(previous.to)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvSourceCalendar.CheckResult.Overlap",
                      calendar.getName(),
                      group.getKey()),
                  calendar));
          break;
        }
      }
    }
  }

  /**
   * An SCD2 table may name one calendar. Each satellite and source-query leg then needs a source
   * id that exists on that calendar. Blank calendar keeps today's unfiltered pipeline.
   */
  public static void validateScd2(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      BusinessVaultModel model,
      IVariables variables) {
    if (scd2Table == null) {
      return;
    }
    String calendarName = resolve(scd2Table.getSourceCalendarName(), variables);
    BvSourceCalendar calendar = find(model, calendarName, variables);
    if (!Utils.isEmpty(scd2Table.getSourceCalendarName())) {
      if (model == null || calendar == null) {
        IBvTable named = model == null ? null : model.findTable(calendarName);
        String key =
            named == null
                ? "BvScd2Table.CheckResult.UnknownSourceCalendar"
                : "BvScd2Table.CheckResult.SourceCalendarWrongType";
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    BvScd2Table.class, key, scd2Table.getName(), calendarName),
                scd2Table));
      }
    }

    for (BvScd2SatelliteConfig config : scd2Table.getSatelliteConfigs()) {
      if (config == null || Utils.isEmpty(config.getSatelliteName())) {
        continue;
      }
      validateLeg(
          remarks,
          scd2Table,
          calendar,
          calendarName,
          config.getSatelliteName(),
          false,
          config.getSourceId(),
          config.getOp(),
          config.getPriorityOverride(),
          variables);
    }
    for (BvSourceQueryRef ref : scd2Table.getSourceQueryRefs()) {
      if (ref == null || Utils.isEmpty(ref.getSourceQueryName())) {
        continue;
      }
      validateLeg(
          remarks,
          scd2Table,
          calendar,
          calendarName,
          ref.getSourceQueryName(),
          true,
          ref.getSourceId(),
          ref.getOp(),
          ref.getPriorityOverride(),
          variables);
    }

    if (calendar == null) {
      return;
    }
    for (BvDerivativeRef derivative : scd2Table.getDerivatives()) {
      if (!isSatelliteLeg(derivative)) {
        continue;
      }
      BvScd2SatelliteConfig config =
          BvScd2FieldMappingValidationSupport.findSatelliteConfig(
              scd2Table, derivative.getDvTableName(), variables);
      String sourceId = config == null ? null : resolve(config.getSourceId(), variables);
      if (Utils.isEmpty(sourceId)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    BvScd2Table.class,
                    "BvScd2Table.CheckResult.MissingLegSourceId",
                    scd2Table.getName(),
                    derivative.getDvTableName(),
                    calendarName),
                scd2Table));
      }
    }
    for (BvSourceQueryRef ref : scd2Table.getSourceQueryRefs()) {
      if (ref == null || Utils.isEmpty(ref.getSourceQueryName())) {
        continue;
      }
      if (Utils.isEmpty(resolve(ref.getSourceId(), variables))) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    BvScd2Table.class,
                    "BvScd2Table.CheckResult.MissingQueryCalendarSystem",
                    scd2Table.getName(),
                    ref.getSourceQueryName(),
                    calendarName),
                scd2Table));
      }
    }
  }

  public static List<BvSourceCalendarEntry> windowsFor(
      BvSourceCalendar calendar, String sourceId, IVariables variables) {
    List<BvSourceCalendarEntry> windows = new ArrayList<>();
    if (calendar == null || Utils.isEmpty(sourceId)) {
      return windows;
    }
    String resolvedId = resolve(sourceId, variables);
    for (BvSourceCalendarEntry entry : calendar.getEntries()) {
      if (entry == null) {
        continue;
      }
      if (resolvedId.equals(resolve(entry.getSourceId(), variables))) {
        windows.add(entry);
      }
    }
    return windows;
  }

  /**
   * @return the timestamp, or {@code null} when {@code text} is empty or not a supported bound
   */
  public static Date parseTimestamp(String text, IVariables variables) {
    if (Utils.isEmpty(text)) {
      return null;
    }
    String resolved = resolve(text, variables);
    if (resolved.length() == 10) {
      resolved = resolved + " 00:00:00";
    }
    try {
      LocalDateTime dateTime = LocalDateTime.parse(resolved, DATE_TIME);
      return Timestamp.valueOf(dateTime);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  static boolean isSatelliteLeg(BvDerivativeRef derivative) {
    if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
      return false;
    }
    return derivative.getDvTableType() == DvTableType.SATELLITE
        || (derivative.getDvTableType() != null && derivative.getDvTableType().isLinkedTable());
  }

  private static void validateLeg(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      BvSourceCalendar calendar,
      String calendarName,
      String legName,
      boolean sourceQuery,
      String sourceId,
      BvLegOperation op,
      String priorityOverride,
      IVariables variables) {
    String resolvedSourceId = resolve(sourceId, variables);
    if (!Utils.isEmpty(sourceId) && Utils.isEmpty(calendarName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  BvScd2Table.class,
                  "BvScd2Table.CheckResult.DanglingSourceId",
                  scd2Table.getName(),
                  legName,
                  resolvedSourceId),
              scd2Table));
    } else if (!Utils.isEmpty(resolvedSourceId) && calendar != null) {
      if (windowsFor(calendar, resolvedSourceId, variables).isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    BvScd2Table.class,
                    "BvScd2Table.CheckResult.UnknownLegSourceId",
                    scd2Table.getName(),
                    legName,
                    resolvedSourceId,
                    calendarName),
                scd2Table));
      }
    }
    if (op == BvLegOperation.SEED && !sourceQuery) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  BvScd2Table.class,
                  "BvScd2Table.CheckResult.SeedRequiresSourceQuery",
                  scd2Table.getName(),
                  legName),
              scd2Table));
    }
    if (!Utils.isEmpty(priorityOverride) && !isInteger(resolve(priorityOverride, variables))) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  BvScd2Table.class,
                  "BvScd2Table.CheckResult.InvalidPriorityOverride",
                  scd2Table.getName(),
                  legName,
                  priorityOverride),
              scd2Table));
    }
    if (!Utils.isEmpty(priorityOverride) && Utils.isEmpty(calendarName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  BvScd2Table.class,
                  "BvScd2Table.CheckResult.DanglingPriorityOverride",
                  scd2Table.getName(),
                  legName),
              scd2Table));
    }
  }

  private static boolean isInteger(String text) {
    if (Utils.isEmpty(text)) {
      return false;
    }
    try {
      Integer.parseInt(text.trim());
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static String resolve(String text, IVariables variables) {
    if (text == null) {
      return "";
    }
    String trimmed = text.trim();
    if (variables == null || Utils.isEmpty(trimmed)) {
      return trimmed;
    }
    return variables.resolve(trimmed).trim();
  }

  private record ParsedWindow(Date from, Date to) {}
}
