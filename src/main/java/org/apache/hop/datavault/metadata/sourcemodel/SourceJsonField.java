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
package org.apache.hop.datavault.metadata.sourcemodel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Projected field from a {@link SourceJson} extraction (JsonPath or parent pass-through).
 *
 * <p>Pass-through fields carry a parent column name and no path. Extracted fields use JsonPath
 * expressions compatible with Hop {@code JsonInput} (top-level leaves plus at most one array
 * expansion per {@link SourceJson} step).
 */
@Getter
@Setter
@NoArgsConstructor
public class SourceJsonField {

  /** Output field name for the composed feed. */
  @HopMetadataProperty private String name;

  /**
   * JsonPath expression (e.g. {@code $.id}, {@code $.lines[*].sku}). Empty for pass-through fields.
   */
  @HopMetadataProperty private String path;

  /**
   * When true, the field is taken from the parent row layout instead of JsonPath extraction. Use
   * {@link #parentFieldName} for the parent column name.
   */
  @HopMetadataProperty private boolean passThrough;

  /** Parent column name when {@link #passThrough} is true. */
  @HopMetadataProperty private String parentFieldName;

  /** Hop {@code ValueMetaInterface} type code. */
  @HopMetadataProperty(intCodeConverter = ValueMetaBase.ValueTypeCodeConverter.class)
  private int hopType;

  @HopMetadataProperty private int length = -1;
  @HopMetadataProperty private int precision = -1;
  @HopMetadataProperty private String format;
  @HopMetadataProperty private String decimalSymbol;
  @HopMetadataProperty private String groupSymbol;
  @HopMetadataProperty private String currencySymbol;

  @HopMetadataProperty(intCodeConverter = ValueMetaBase.TrimTypeCodeConverter.class)
  private int trimType;

  /**
   * Logical primary-key position of this field for the composed feed (1-based). Zero means the
   * field is not part of the feed grain.
   */
  @HopMetadataProperty private int primaryKeyPosition;

  /** JsonInput "repeat" flag for sparse multi-value paths. */
  @HopMetadataProperty private boolean repeated;

  public SourceJsonField(String name, String path) {
    this.name = name;
    this.path = path;
  }

  public static SourceJsonField passThroughField(String parentFieldName) {
    SourceJsonField field = new SourceJsonField();
    field.setPassThrough(true);
    field.setParentFieldName(parentFieldName);
    field.setName(parentFieldName);
    return field;
  }

  public String resolveName() {
    if (!Utils.isEmpty(name)) {
      return name.trim();
    }
    if (passThrough && !Utils.isEmpty(parentFieldName)) {
      return parentFieldName.trim();
    }
    if (!Utils.isEmpty(path)) {
      String p = path.trim();
      int lastDot = p.lastIndexOf('.');
      if (lastDot >= 0 && lastDot < p.length() - 1) {
        String leaf =
            p.substring(lastDot + 1)
                .replace("[*]", "")
                .replace("'", "")
                .replace("[", "")
                .replace("]", "");
        if (!Utils.isEmpty(leaf) && !"*".equals(leaf)) {
          return leaf;
        }
      }
    }
    return "";
  }

  public boolean isPrimaryKey() {
    return primaryKeyPosition > 0;
  }

  /**
   * Whether this field's path expands an array (JsonInput allows at most one such path per step).
   *
   * <p>Accepts both Hop sample-dialog style ({@code $.lines.*.sku}) and JsonPath style ({@code
   * $.lines[*].sku}).
   */
  public boolean isArrayExpandingPath() {
    if (passThrough || Utils.isEmpty(path)) {
      return false;
    }
    return path.contains("[*]") || path.contains(".*") || path.matches(".*\\[\\s*\\*\\s*\\].*");
  }
}
