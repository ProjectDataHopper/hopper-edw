/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;

/** Helpers for source primary-key metadata stored on {@link SourceField}. */
public final class SourceFieldPrimaryKeySupport {

  public static final int NOT_PRIMARY_KEY = 0;

  private SourceFieldPrimaryKeySupport() {}

  public static boolean isPrimaryKey(SourceField field) {
    return field != null && field.getPrimaryKeyPosition() > NOT_PRIMARY_KEY;
  }

  public static List<SourceField> primaryKeyFields(List<SourceField> fields) {
    if (fields == null || fields.isEmpty()) {
      return List.of();
    }
    List<SourceField> primaryKeys = new ArrayList<>();
    for (SourceField field : fields) {
      if (isPrimaryKey(field)) {
        primaryKeys.add(field);
      }
    }
    primaryKeys.sort(Comparator.comparingInt(SourceField::getPrimaryKeyPosition));
    return primaryKeys;
  }

  public static List<String> primaryKeyFieldNames(List<SourceField> fields) {
    return primaryKeyFields(fields).stream()
        .map(SourceField::getName)
        .filter(name -> !Utils.isEmpty(name))
        .map(String::trim)
        .collect(Collectors.toList());
  }

  public static void clearPrimaryKeyPositions(List<SourceField> fields) {
    if (fields == null) {
      return;
    }
    for (SourceField field : fields) {
      if (field != null) {
        field.setPrimaryKeyPosition(NOT_PRIMARY_KEY);
      }
    }
  }

  public static void applyPrimaryKeyFieldNames(List<SourceField> fields, List<String> orderedNames) {
    clearPrimaryKeyPositions(fields);
    if (fields == null || orderedNames == null || orderedNames.isEmpty()) {
      return;
    }
    Map<String, SourceField> fieldsByName = indexByName(fields);
    int position = 1;
    for (String rawName : orderedNames) {
      if (Utils.isEmpty(rawName)) {
        continue;
      }
      SourceField field = fieldsByName.get(rawName.trim());
      if (field != null) {
        field.setPrimaryKeyPosition(position++);
      }
    }
  }

  public static boolean hasPrimaryKeyMetadata(List<SourceField> fields) {
    return !primaryKeyFieldNames(fields).isEmpty();
  }

  public static boolean primaryKeyCompositionEquals(
      List<SourceField> storedFields, List<SourceField> discoveredFields) {
    return primaryKeyFieldNames(storedFields).equals(primaryKeyFieldNames(discoveredFields));
  }

  public static String describePrimaryKeyCompositionDifference(
      List<SourceField> storedFields, List<SourceField> discoveredFields) {
    List<String> stored = primaryKeyFieldNames(storedFields);
    List<String> discovered = primaryKeyFieldNames(discoveredFields);
    if (stored.equals(discovered)) {
      return null;
    }
    return formatComposition(stored) + " -> " + formatComposition(discovered);
  }

  public static List<BusinessKey> businessKeysFromPrimaryKeyFields(
      List<SourceField> fields, String recordSourceName) {
    List<BusinessKey> businessKeys = new ArrayList<>();
    for (SourceField field : primaryKeyFields(fields)) {
      BusinessKey businessKey = new BusinessKey(Const.NVL(field.getName(), ""));
      businessKey.setDescription(Const.NVL(field.getDescription(), ""));
      businessKey.setDataType(DvDataTypeSupport.preferredDataTypeLabel(field));
      businessKey.setLength(Const.NVL(field.getLength(), ""));
      businessKey.setPrecision(Const.NVL(field.getPrecision(), ""));
      businessKey.setSourceFieldName(Const.NVL(field.getName(), ""));
      businessKey.setRecordSourceName(Const.NVL(recordSourceName, ""));
      businessKeys.add(businessKey);
    }
    return businessKeys;
  }

  private static Map<String, SourceField> indexByName(List<SourceField> fields) {
    Map<String, SourceField> indexed = new LinkedHashMap<>();
    if (fields == null) {
      return indexed;
    }
    for (SourceField field : fields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      indexed.put(field.getName().trim(), field);
    }
    return indexed;
  }

  private static String formatComposition(List<String> fieldNames) {
    if (fieldNames == null || fieldNames.isEmpty()) {
      return "(none)";
    }
    return "[" + String.join(", ", fieldNames) + "]";
  }
}