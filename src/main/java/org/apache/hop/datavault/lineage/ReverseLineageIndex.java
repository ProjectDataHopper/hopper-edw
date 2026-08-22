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
package org.apache.hop.datavault.lineage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.core.util.Utils;

/** Reverse index: upstream source field → consumers across DV/BV/DM lineage snapshots. */
public final class ReverseLineageIndex {

  private final Map<ReverseLineageKey, List<ReverseLineageConsumer>> byKey = new LinkedHashMap<>();
  private final Set<String> sourceNames = new LinkedHashSet<>();

  public void add(ReverseLineageKey key, ReverseLineageConsumer consumer) {
    if (key == null || key.isEmpty() || consumer == null) {
      return;
    }
    byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(consumer);
    if (!Utils.isEmpty(key.sourceName())) {
      sourceNames.add(key.sourceName());
    }
  }

  public List<ReverseLineageConsumer> find(String sourceName, String sourceFieldName) {
    ReverseLineageKey key = new ReverseLineageKey(sourceName, sourceFieldName);
    List<ReverseLineageConsumer> direct = byKey.getOrDefault(key, List.of());
    if (direct.isEmpty() && !Utils.isEmpty(sourceFieldName) && Utils.isEmpty(sourceName)) {
      // Field-only search across all feeds
      return byKey.entrySet().stream()
          .filter(
              e ->
                  e.getKey().sourceFieldName() != null
                      && e.getKey().sourceFieldName().equalsIgnoreCase(sourceFieldName))
          .flatMap(e -> e.getValue().stream())
          .collect(Collectors.toCollection(ArrayList::new));
    }
    return List.copyOf(direct);
  }

  /**
   * Finds consumers matching optional feed and field filters (substring, case-insensitive). Empty
   * filters match all.
   */
  public List<ReverseLineageConsumer> search(String sourceNameFilter, String sourceFieldFilter) {
    String nameNeedle =
        Utils.isEmpty(sourceNameFilter) ? "" : sourceNameFilter.toLowerCase(Locale.ROOT);
    String fieldNeedle =
        Utils.isEmpty(sourceFieldFilter) ? "" : sourceFieldFilter.toLowerCase(Locale.ROOT);
    List<ReverseLineageConsumer> matches = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (Map.Entry<ReverseLineageKey, List<ReverseLineageConsumer>> entry : byKey.entrySet()) {
      ReverseLineageKey key = entry.getKey();
      if (!Utils.isEmpty(nameNeedle)
          && !key.sourceName().toLowerCase(Locale.ROOT).contains(nameNeedle)) {
        continue;
      }
      if (!Utils.isEmpty(fieldNeedle)
          && !key.sourceFieldName().toLowerCase(Locale.ROOT).contains(fieldNeedle)) {
        continue;
      }
      for (ReverseLineageConsumer consumer : entry.getValue()) {
        String dedupe =
            key.display()
                + "|"
                + consumer.getLayer()
                + "|"
                + consumer.getModelName()
                + "|"
                + consumer.getTableName()
                + "|"
                + consumer.getTargetField();
        if (seen.add(dedupe)) {
          matches.add(consumer);
        }
      }
    }
    return matches;
  }

  public List<String> sourceNames() {
    return List.copyOf(sourceNames);
  }

  public Collection<ReverseLineageKey> keys() {
    return Collections.unmodifiableCollection(byKey.keySet());
  }

  public int size() {
    return byKey.values().stream().mapToInt(List::size).sum();
  }

  public boolean isEmpty() {
    return byKey.isEmpty();
  }
}
