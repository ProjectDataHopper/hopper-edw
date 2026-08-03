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
 */
package org.apache.hop.catalog.hopgui.perspective;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionRef;

/** Builds grouped or flat catalog tree structures for the data catalog perspective. */
public final class DataCatalogTreeBuilder {

  private DataCatalogTreeBuilder() {}

  public static List<RecordDefinitionRef> sortRefs(List<RecordDefinitionRef> refs) {
    if (refs == null || refs.isEmpty()) {
      return List.of();
    }
    return refs.stream()
        .sorted(
            Comparator.comparing(
                    (RecordDefinitionRef ref) ->
                        ref.getKey() != null ? ref.getKey().getNamespace() : "",
                    String.CASE_INSENSITIVE_ORDER)
                .thenComparing(
                    ref -> ref.getKey() != null ? ref.getKey().getName() : "",
                    String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  public static Map<String, List<RecordDefinitionRef>> groupByNamespace(
      List<RecordDefinitionRef> refs) {
    Map<String, List<RecordDefinitionRef>> grouped = new LinkedHashMap<>();
    for (RecordDefinitionRef ref : sortRefs(refs)) {
      String namespace = namespaceOf(ref);
      grouped.computeIfAbsent(namespace, key -> new ArrayList<>()).add(ref);
    }
    return grouped;
  }

  public static String displayRecordName(RecordDefinitionRef ref, boolean groupByNamespace) {
    if (ref == null || ref.getKey() == null) {
      return "";
    }
    if (groupByNamespace) {
      return ref.getKey().getName();
    }
    return ref.getKey().toString();
  }

  private static String namespaceOf(RecordDefinitionRef ref) {
    RecordDefinitionKey key = ref.getKey();
    if (key == null || key.getNamespace() == null) {
      return "";
    }
    return key.getNamespace();
  }
}
