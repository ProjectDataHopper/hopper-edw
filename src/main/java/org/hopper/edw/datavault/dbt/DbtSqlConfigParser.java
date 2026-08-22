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
package org.hopper.edw.datavault.dbt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import org.apache.hop.core.util.Utils;

/** Extracts {@code {{ config(...) }}} keyword arguments from a dbt model SQL file. */
public final class DbtSqlConfigParser {

  private static final Pattern CONFIG_BLOCK =
      Pattern.compile(
          "\\{\\{\\s*config\\s*\\((.*?)\\)\\s*\\}\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private static final Pattern KEYWORD =
      Pattern.compile("(\\w+)\\s*=\\s*(?:'([^']*)'|\"([^\"]*)\"|(\\w+))", Pattern.CASE_INSENSITIVE);

  private DbtSqlConfigParser() {}

  @Getter
  public static final class SqlConfig {
    private String materialized;
    private String alias;
    private String schema;
  }

  public static SqlConfig parse(String sql) {
    SqlConfig config = new SqlConfig();
    if (Utils.isEmpty(sql)) {
      return config;
    }
    Matcher block = CONFIG_BLOCK.matcher(sql);
    while (block.find()) {
      String body = block.group(1);
      if (Utils.isEmpty(body)) {
        continue;
      }
      Matcher kw = KEYWORD.matcher(body);
      while (kw.find()) {
        String key = kw.group(1);
        String value = firstNonEmpty(kw.group(2), kw.group(3), kw.group(4));
        if (Utils.isEmpty(key) || Utils.isEmpty(value)) {
          continue;
        }
        if ("materialized".equalsIgnoreCase(key)) {
          config.materialized = value;
        } else if ("alias".equalsIgnoreCase(key)) {
          config.alias = value;
        } else if ("schema".equalsIgnoreCase(key)) {
          config.schema = value;
        }
      }
    }
    return config;
  }

  private static String firstNonEmpty(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (!Utils.isEmpty(value)) {
        return value;
      }
    }
    return null;
  }
}
