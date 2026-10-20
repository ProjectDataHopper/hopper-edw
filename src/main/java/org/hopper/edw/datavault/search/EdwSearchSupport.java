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
package org.hopper.edw.datavault.search;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.SearchResult;

/**
 * Field search for model objects. Hop's {@code StringSearcher} only walks types under {@code
 * org.apache.hop}, so after this plugin moved to {@code org.hopper} a search found the model name
 * and missed the tables, columns, and record sources stored on those objects.
 */
public final class EdwSearchSupport {

  private static final int MAX_DEPTH = 5;

  private EdwSearchSupport() {}

  public static <T> void matchObjectFields(
      ISearchable<T> searchable,
      List<ISearchResult> searchResults,
      ISearchQuery searchQuery,
      Object object,
      String descriptionPrefix,
      String component) {
    if (object == null || searchQuery == null) {
      return;
    }
    List<FieldValue> values = new ArrayList<>();
    collect(
        object,
        1,
        values,
        Collections.newSetFromMap(new IdentityHashMap<>()));
    for (FieldValue value : values) {
      if (StringUtils.isNotEmpty(value.value) && searchQuery.matches(value.value)) {
        searchResults.add(
            new SearchResult(
                searchable,
                value.value,
                descriptionPrefix + " : " + value.fieldName,
                component,
                value.value));
      }
    }
  }

  private static void collect(Object object, int depth, List<FieldValue> values, Set<Object> seen) {
    if (object == null || depth > MAX_DEPTH || !seen.add(object)) {
      return;
    }
    for (Class<?> type = object.getClass();
        type != null && type != Object.class && !type.getName().startsWith("java.");
        type = type.getSuperclass()) {
      for (Field field : type.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
          continue;
        }
        if (!sanctioned(field)) {
          continue;
        }
        Object raw;
        try {
          field.setAccessible(true);
          raw = field.get(object);
        } catch (IllegalAccessException e) {
          continue;
        }
        accept(raw, field.getName(), depth, values, seen);
      }
    }
  }

  private static boolean sanctioned(Field field) {
    String signature = field.toString();
    return signature.contains("org.hopper")
        || signature.contains("org.apache.hop")
        || signature.contains("java.util");
  }

  private static void accept(
      Object raw, String fieldName, int depth, List<FieldValue> values, Set<Object> seen) {
    if (raw == null) {
      return;
    }
    if (raw instanceof String string) {
      values.add(new FieldValue(fieldName, string));
      return;
    }
    if (raw instanceof String[] array) {
      for (int i = 0; i < array.length; i++) {
        if (array[i] != null) {
          values.add(new FieldValue(fieldName + " #" + (i + 1), array[i]));
        }
      }
      return;
    }
    if (raw instanceof Boolean bool) {
      values.add(new FieldValue(fieldName + " (Boolean)", bool.toString()));
      return;
    }
    if (raw instanceof Enum<?> enumeration) {
      values.add(new FieldValue(fieldName, enumeration.name()));
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() != null) {
          values.add(new FieldValue(fieldName + " (Map key)", entry.getKey().toString()));
        }
        accept(entry.getValue(), fieldName, depth, values, seen);
      }
      return;
    }
    if (raw instanceof Iterable<?> iterable) {
      for (Object element : iterable) {
        accept(element, fieldName, depth, values, seen);
      }
      return;
    }
    if (raw.getClass().isArray()) {
      return;
    }
    collect(raw, depth + 1, values, seen);
  }

  private static final class FieldValue {
    private final String fieldName;
    private final String value;

    private FieldValue(String fieldName, String value) {
      this.fieldName = fieldName;
      this.value = value;
    }
  }
}
