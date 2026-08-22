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
package org.apache.hop.datavault.impact;

import org.apache.hop.core.util.Utils;

/** One node in the downstream blast-radius graph. */
public record ImpactNode(
    ImpactNodeKind kind,
    String modelType,
    String modelName,
    String modelFilename,
    String elementName,
    String fieldName,
    String sourceNamespace,
    String sourceName) {

  public String id() {
    return kind.name()
        + "|"
        + nvl(modelType)
        + "|"
        + nvl(modelFilename)
        + "|"
        + nvl(elementName)
        + "|"
        + nvl(fieldName)
        + "|"
        + nvl(sourceNamespace)
        + "|"
        + nvl(sourceName);
  }

  /** Human-readable label for reports and GUI. */
  public String displayLabel() {
    return switch (kind) {
      case SOURCE_OBJECT -> "source " + sourceKeyLabel();
      case SOURCE_FIELD -> "source " + sourceKeyLabel() + "." + fieldName;
      case DV_TABLE, BV_TABLE, DM_TABLE -> elementName;
      case DV_FIELD, BV_FIELD ->
          Utils.isEmpty(fieldName) ? elementName : elementName + "." + fieldName;
    };
  }

  public String sourceKeyLabel() {
    if (Utils.isEmpty(sourceNamespace)) {
      return nvl(sourceName);
    }
    if (Utils.isEmpty(sourceName)) {
      return sourceNamespace;
    }
    return sourceNamespace + "/" + sourceName;
  }

  private static String nvl(String value) {
    return value == null ? "" : value;
  }
}
