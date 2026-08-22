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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.util.Utils;

/** Splits a dbt {@code macros/*.sql} file into named {@code {% macro %}} units. */
public final class DbtMacroFileParser {

  private static final Pattern MACRO =
      Pattern.compile(
          "\\{%-?\\s*macro\\s+([A-Za-z_][\\w.]*)\\s*\\([^)]*\\)\\s*-?%\\}.*?\\{%-?\\s*endmacro\\s*-?%\\}",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private DbtMacroFileParser() {}

  public static List<DbtMacroDraft> parse(String sql, String originRelativePath) {
    List<DbtMacroDraft> macros = new ArrayList<>();
    if (Utils.isEmpty(sql)) {
      return macros;
    }
    Matcher matcher = MACRO.matcher(sql);
    while (matcher.find()) {
      DbtMacroDraft draft = new DbtMacroDraft();
      draft.setName(matcher.group(1));
      draft.setJinjaSource(matcher.group().trim());
      draft.setOriginRelativePath(originRelativePath);
      macros.add(draft);
    }
    return macros;
  }
}
