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
package org.hopper.edw.datavault.metadata.sourcemodel;

import org.apache.hop.core.Const;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;

/**
 * Resolves effective Hop types for {@link SourceJsonField} projections (especially parent
 * pass-through columns that often leave {@code hopType} unset).
 */
public final class SourceJsonFieldSupport {

  private SourceJsonFieldSupport() {}

  /**
   * Effective hop type for a projected field: explicit field type when set, otherwise the parent
   * table/query/JSON column type for pass-through fields.
   *
   * @return hop type id, or {@code 0} when still unknown
   */
  public static int resolveEffectiveHopType(
      SourceModel model, SourceJson jsonSource, SourceJsonField field) {
    if (field == null) {
      return 0;
    }
    if (field.getHopType() > 0) {
      return field.getHopType();
    }
    if (!field.isPassThrough()) {
      return 0;
    }
    return resolvePassThroughParentHopType(model, jsonSource, field);
  }

  /**
   * Fills unset {@code hopType} on pass-through fields from the parent source layout. Does not
   * invent types for JsonPath-extracted fields.
   */
  public static void applyMissingPassThroughTypes(SourceModel model, SourceJson jsonSource) {
    if (jsonSource == null) {
      return;
    }
    for (SourceJsonField field : jsonSource.getFields()) {
      if (field == null || field.getHopType() > 0) {
        continue;
      }
      int resolved = resolveEffectiveHopType(model, jsonSource, field);
      if (resolved > 0) {
        field.setHopType(resolved);
      }
    }
  }

  /** Hop type name for UI combo cells, or empty when unset/unknown. */
  public static String hopTypeLabel(int hopType) {
    if (hopType <= 0) {
      return "";
    }
    try {
      String name = ValueMetaFactory.getValueMetaName(hopType);
      if (Utils.isEmpty(name) || "-".equals(name) || "None".equalsIgnoreCase(name)) {
        return "";
      }
      return name;
    } catch (Exception e) {
      return "";
    }
  }

  public static int hopTypeIdFromLabel(String typeLabel) {
    if (Utils.isEmpty(typeLabel)) {
      return 0;
    }
    try {
      int id = ValueMetaFactory.getIdForValueMeta(typeLabel.trim());
      return id > 0 ? id : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  /**
   * Hop type of a named field/column on a parent table, query, or JSON source (for pass-through /
   * Add parent keys).
   */
  public static int resolveParentFieldHopType(
      SourceModel model, SourceJsonParentKind parentKind, String parentName, String fieldName) {
    if (model == null || Utils.isEmpty(parentName) || Utils.isEmpty(fieldName)) {
      return 0;
    }
    SourceJsonParentKind kind = parentKind != null ? parentKind : SourceJsonParentKind.TABLE;
    String parentField = fieldName.trim();
    return switch (kind) {
      case TABLE -> {
        SourceTable table = model.findTable(parentName);
        SourceColumn column = table != null ? table.findColumn(parentField) : null;
        yield column != null && column.getHopType() > 0 ? column.getHopType() : 0;
      }
      case QUERY -> {
        SourceQuery query = model.findQuery(parentName);
        if (query == null) {
          yield 0;
        }
        for (SourceQueryColumn col : query.getColumns()) {
          if (col == null) {
            continue;
          }
          String alias = col.resolveAlias();
          if (parentField.equals(alias) || parentField.equals(col.getColumnName())) {
            SourceTable table =
                !Utils.isEmpty(col.getTableName()) ? model.findTable(col.getTableName()) : null;
            SourceColumn sourceColumn =
                table != null ? table.findColumn(col.getColumnName()) : null;
            yield sourceColumn != null && sourceColumn.getHopType() > 0
                ? sourceColumn.getHopType()
                : 0;
          }
        }
        yield 0;
      }
      case JSON -> {
        SourceJson parentJson = model.findJsonSource(parentName);
        if (parentJson == null) {
          yield 0;
        }
        for (SourceJsonField parent : parentJson.getFields()) {
          if (parent != null && parentField.equals(parent.resolveName())) {
            int parentType = parent.getHopType();
            if (parentType <= 0) {
              parentType = resolveEffectiveHopType(model, parentJson, parent);
            }
            yield parentType;
          }
        }
        yield 0;
      }
    };
  }

  private static int resolvePassThroughParentHopType(
      SourceModel model, SourceJson jsonSource, SourceJsonField field) {
    if (model == null || jsonSource == null || field == null || !field.isPassThrough()) {
      return 0;
    }
    String parentField =
        !Utils.isEmpty(field.getParentFieldName())
            ? field.getParentFieldName().trim()
            : field.resolveName();
    if (Utils.isEmpty(parentField)) {
      return 0;
    }
    String parentName = Const.NVL(jsonSource.getParentSourceName(), "");
    if (Utils.isEmpty(parentName)) {
      return 0;
    }
    return resolveParentFieldHopType(
        model, jsonSource.resolveParentSourceKind(), parentName, parentField);
  }
}
