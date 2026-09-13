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
package org.hopper.edw.datavault.diagram.schema;

import org.apache.hop.core.util.Utils;

/** Safe identifiers for PlantUML aliases and Mermaid entity names. */
public final class SchemaIdentifiers {

  private SchemaIdentifiers() {}

  public static String alias(String name) {
    if (Utils.isEmpty(name)) {
      return "unnamed";
    }
    StringBuilder sb = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
        sb.append(c);
      } else {
        sb.append('_');
      }
    }
    String alias = sb.toString();
    if (alias.isEmpty() || Character.isDigit(alias.charAt(0))) {
      alias = "t_" + alias;
    }
    return alias;
  }

  public static String display(String name) {
    if (name == null) {
      return "";
    }
    return name.replace("\"", "'");
  }
}
