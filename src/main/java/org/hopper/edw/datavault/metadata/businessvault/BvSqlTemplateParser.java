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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.util.Utils;

/**
 * Parses dbt-style SQL template macros for Business Vault SQL business tables.
 *
 * <p>Supported forms (style {@link BvSqlReferenceStyle#DBT}):
 *
 * <ul>
 *   <li>{@code {{ ref('object') }}}
 *   <li>{@code {{ ref('model', 'object') }}}
 *   <li>{@code {{ source('source', 'table') }}}
 * </ul>
 *
 * <p>Not a full Jinja engine — only these macros with single-quoted string arguments.
 */
public final class BvSqlTemplateParser {

  private static final Pattern REF_PATTERN =
      Pattern.compile(
          "\\{\\{\\s*ref\\s*\\(\\s*'([^']+)'\\s*(?:,\\s*'([^']+)'\\s*)?\\)\\s*\\}\\}",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern SOURCE_PATTERN =
      Pattern.compile(
          "\\{\\{\\s*source\\s*\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*\\)\\s*\\}\\}",
          Pattern.CASE_INSENSITIVE);

  private BvSqlTemplateParser() {}

  public enum MacroKind {
    REF,
    SOURCE
  }

  /**
   * A single macro occurrence in authoring SQL.
   *
   * @param kind ref or source
   * @param fullMatch exact substring matched (for replacement)
   * @param start inclusive start index in the original SQL
   * @param end exclusive end index
   * @param arg1 first argument (model or source name for two-arg; object for one-arg ref)
   * @param arg2 second argument (object/table); null for one-arg ref
   */
  public record MacroOccurrence(
      MacroKind kind, String fullMatch, int start, int end, String arg1, String arg2) {

    public boolean isOneArgRef() {
      return kind == MacroKind.REF && Utils.isEmpty(arg2);
    }

    public String refModelName() {
      if (kind != MacroKind.REF) {
        return null;
      }
      return isOneArgRef() ? null : arg1;
    }

    public String refObjectName() {
      if (kind != MacroKind.REF) {
        return null;
      }
      return isOneArgRef() ? arg1 : arg2;
    }

    public String sourceName() {
      return kind == MacroKind.SOURCE ? arg1 : null;
    }

    public String sourceTableName() {
      return kind == MacroKind.SOURCE ? arg2 : null;
    }
  }

  public static List<MacroOccurrence> parse(String sql) {
    if (Utils.isEmpty(sql)) {
      return List.of();
    }
    List<MacroOccurrence> all = new ArrayList<>();
    Matcher refMatcher = REF_PATTERN.matcher(sql);
    while (refMatcher.find()) {
      all.add(
          new MacroOccurrence(
              MacroKind.REF,
              refMatcher.group(),
              refMatcher.start(),
              refMatcher.end(),
              refMatcher.group(1),
              refMatcher.group(2)));
    }
    Matcher sourceMatcher = SOURCE_PATTERN.matcher(sql);
    while (sourceMatcher.find()) {
      all.add(
          new MacroOccurrence(
              MacroKind.SOURCE,
              sourceMatcher.group(),
              sourceMatcher.start(),
              sourceMatcher.end(),
              sourceMatcher.group(1),
              sourceMatcher.group(2)));
    }
    all.sort((a, b) -> Integer.compare(a.start(), b.start()));
    return all;
  }

  /** Distinct {@link BvSqlRef} entries from {@code ref()} macros (order of first appearance). */
  public static List<BvSqlRef> extractRefs(String sql) {
    Set<String> seen = new LinkedHashSet<>();
    List<BvSqlRef> refs = new ArrayList<>();
    for (MacroOccurrence macro : parse(sql)) {
      if (macro.kind() != MacroKind.REF) {
        continue;
      }
      String model = macro.refModelName();
      String object = macro.refObjectName();
      if (Utils.isEmpty(object)) {
        continue;
      }
      String key =
          (model == null ? "" : model.trim().toLowerCase()) + "\0" + object.trim().toLowerCase();
      if (!seen.add(key)) {
        continue;
      }
      refs.add(new BvSqlRef(Utils.isEmpty(model) ? null : model.trim(), object.trim()));
    }
    return refs;
  }

  /**
   * Distinct source name + table pairs from {@code source()} macros (order of first appearance).
   */
  public static List<BvSqlSource> extractSourceUsages(String sql) {
    Set<String> seen = new LinkedHashSet<>();
    List<BvSqlSource> sources = new ArrayList<>();
    for (MacroOccurrence macro : parse(sql)) {
      if (macro.kind() != MacroKind.SOURCE) {
        continue;
      }
      String sourceName = macro.sourceName();
      String tableName = macro.sourceTableName();
      if (Utils.isEmpty(sourceName) || Utils.isEmpty(tableName)) {
        continue;
      }
      String key = sourceName.trim().toLowerCase() + "\0" + tableName.trim().toLowerCase();
      if (!seen.add(key)) {
        continue;
      }
      sources.add(new BvSqlSource(sourceName.trim(), tableName.trim()));
    }
    return sources;
  }

  /**
   * Rewrites SQL by replacing each macro occurrence with a provided qualified name.
   *
   * @param sql authoring SQL
   * @param replacement lookup key: for REF one-arg {@code object}, two-arg {@code model\0object};
   *     for SOURCE {@code source\0table}
   */
  public static String rewrite(
      String sql, java.util.function.Function<MacroOccurrence, String> replacementFn) {
    if (Utils.isEmpty(sql) || replacementFn == null) {
      return sql;
    }
    List<MacroOccurrence> macros = parse(sql);
    if (macros.isEmpty()) {
      return sql;
    }
    StringBuilder out = new StringBuilder(sql.length() + 64);
    int cursor = 0;
    for (MacroOccurrence macro : macros) {
      out.append(sql, cursor, macro.start());
      String replacement = replacementFn.apply(macro);
      out.append(Objects.requireNonNullElse(replacement, macro.fullMatch()));
      cursor = macro.end();
    }
    out.append(sql, cursor, sql.length());
    return out.toString();
  }
}
