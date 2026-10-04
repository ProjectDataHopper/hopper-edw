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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;

/** Copies a parent card's output columns onto a {@link SourceMasking} field list. */
public final class SourceMaskingParentSupport {

  private SourceMaskingParentSupport() {}

  /**
   * Parent projection as masking fields. Existing pattern names are kept when the output name
   * matches.
   */
  public static List<SourceMaskingField> copyFields(
      SourceModel model, SourceMasking masking, List<SourceMaskingField> existing) {
    Map<String, String> patterns = new HashMap<>();
    if (existing != null) {
      for (SourceMaskingField field : existing) {
        if (field != null && !Utils.isEmpty(field.resolveName()) && field.hasPattern()) {
          patterns.put(field.resolveName(), field.getPatternName().trim());
        }
      }
    }
    List<SourceMaskingField> copied = new ArrayList<>();
    if (model == null || masking == null || Utils.isEmpty(masking.getParentSourceName())) {
      return copied;
    }
    String parentName = masking.getParentSourceName().trim();
    switch (masking.resolveParentSourceKind()) {
      case TABLE -> copyTable(model.findTable(parentName), patterns, copied);
      case QUERY -> copyQuery(model, model.findQuery(parentName), patterns, copied);
      case JSON -> copyJson(model.findJsonSource(parentName), patterns, copied);
      case PIPELINE -> copyPipeline(model.findPipelineSource(parentName), patterns, copied);
      case MASKING -> copyMasking(model.findMaskingSource(parentName), patterns, copied);
    }
    return copied;
  }

  public static boolean parentExists(SourceModel model, SourceMasking masking) {
    if (model == null || masking == null || Utils.isEmpty(masking.getParentSourceName())) {
      return false;
    }
    String parentName = masking.getParentSourceName().trim();
    return switch (masking.resolveParentSourceKind()) {
      case TABLE -> model.findTable(parentName) != null;
      case QUERY -> model.findQuery(parentName) != null;
      case JSON -> model.findJsonSource(parentName) != null;
      case PIPELINE -> model.findPipelineSource(parentName) != null;
      case MASKING -> model.findMaskingSource(parentName) != null;
    };
  }

  private static void copyTable(
      SourceTable table, Map<String, String> patterns, List<SourceMaskingField> copied) {
    if (table == null) {
      return;
    }
    for (SourceColumn column : table.getColumns()) {
      if (column == null || Utils.isEmpty(column.getName())) {
        continue;
      }
      SourceMaskingField field = new SourceMaskingField(column.getName().trim());
      field.setDescription(column.getDescription());
      field.setHopType(column.getHopType());
      field.setLength(parseInt(column.getLength(), -1));
      field.setPrecision(parseInt(column.getPrecision(), -1));
      field.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
      applyPattern(field, patterns);
      copied.add(field);
    }
  }

  private static void copyQuery(
      SourceModel model,
      SourceQuery query,
      Map<String, String> patterns,
      List<SourceMaskingField> copied) {
    if (query == null) {
      return;
    }
    for (SourceQueryColumn column : query.getColumns()) {
      if (column == null || Utils.isEmpty(column.resolveAlias())) {
        continue;
      }
      SourceMaskingField field = new SourceMaskingField(column.resolveAlias());
      field.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
      SourceColumn physical = lookupQueryColumn(model, column);
      if (physical != null) {
        field.setDescription(physical.getDescription());
        field.setHopType(physical.getHopType());
        field.setLength(parseInt(physical.getLength(), -1));
        field.setPrecision(parseInt(physical.getPrecision(), -1));
      } else {
        field.setHopType(IValueMeta.TYPE_STRING);
      }
      applyPattern(field, patterns);
      copied.add(field);
    }
  }

  private static SourceColumn lookupQueryColumn(SourceModel model, SourceQueryColumn column) {
    if (model == null || column == null || Utils.isEmpty(column.getTableName())) {
      return null;
    }
    SourceTable table = model.findTable(column.getTableName());
    if (table == null) {
      return null;
    }
    return table.findColumn(column.getColumnName());
  }

  private static void copyJson(
      SourceJson json, Map<String, String> patterns, List<SourceMaskingField> copied) {
    if (json == null) {
      return;
    }
    for (SourceJsonField source : json.getFields()) {
      if (source == null || Utils.isEmpty(source.resolveName())) {
        continue;
      }
      SourceMaskingField field = new SourceMaskingField(source.resolveName());
      field.setHopType(source.getHopType());
      field.setLength(source.getLength());
      field.setPrecision(source.getPrecision());
      field.setPrimaryKeyPosition(source.getPrimaryKeyPosition());
      applyPattern(field, patterns);
      copied.add(field);
    }
  }

  private static void copyPipeline(
      SourcePipeline pipeline, Map<String, String> patterns, List<SourceMaskingField> copied) {
    if (pipeline == null) {
      return;
    }
    for (SourceColumn column : pipeline.getFields()) {
      if (column == null || Utils.isEmpty(column.getName())) {
        continue;
      }
      SourceMaskingField field = new SourceMaskingField(column.getName().trim());
      field.setDescription(column.getDescription());
      field.setHopType(column.getHopType());
      field.setLength(parseInt(column.getLength(), -1));
      field.setPrecision(parseInt(column.getPrecision(), -1));
      field.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
      applyPattern(field, patterns);
      copied.add(field);
    }
  }

  private static void copyMasking(
      SourceMasking parent, Map<String, String> patterns, List<SourceMaskingField> copied) {
    if (parent == null) {
      return;
    }
    for (SourceMaskingField source : parent.getFields()) {
      if (source == null || Utils.isEmpty(source.resolveName())) {
        continue;
      }
      SourceMaskingField field = new SourceMaskingField(source.resolveName());
      field.setDescription(source.getDescription());
      field.setHopType(source.getHopType());
      field.setLength(source.getLength());
      field.setPrecision(source.getPrecision());
      field.setPrimaryKeyPosition(source.getPrimaryKeyPosition());
      applyPattern(field, patterns);
      copied.add(field);
    }
  }

  private static void applyPattern(SourceMaskingField field, Map<String, String> patterns) {
    String pattern = patterns.get(field.resolveName());
    if (!Utils.isEmpty(pattern)) {
      field.setPatternName(pattern);
    }
  }

  private static int parseInt(String value, int fallback) {
    if (Utils.isEmpty(value)) {
      return fallback;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
