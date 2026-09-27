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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Point-in-time survivorship for one grain key. Lower rank wins. {@code inherit} skips a null and
 * tries the next rank. {@code apply} writes that null. A seed fills a gap and does not replace a
 * value already held by an upsert or delete source. A delete withdraws that source. A present flag
 * distinguishes a cleared null from a field left out of the message. A key that never arrives is
 * not deleted.
 */
public final class SurvivorshipMergeLogic {

  private SurvivorshipMergeLogic() {}

  /** One source's contribution to one target field. */
  public static final class Rule {
    public final String field;
    public final String sourceId;
    public final int rank;
    public final BvNullPolicy nullPolicy;
    public final BvLegOperation operation;
    public final String presentFlagField;

    public Rule(
        String field,
        String sourceId,
        int rank,
        BvNullPolicy nullPolicy,
        BvLegOperation operation) {
      this(field, sourceId, rank, nullPolicy, operation, null);
    }

    public Rule(
        String field,
        String sourceId,
        int rank,
        BvNullPolicy nullPolicy,
        BvLegOperation operation,
        String presentFlagField) {
      this.field = field;
      this.sourceId = sourceId;
      this.rank = rank;
      this.nullPolicy = nullPolicy == null ? BvNullPolicy.APPLY : nullPolicy;
      this.operation = operation == null ? BvLegOperation.UPSERT : operation;
      this.presentFlagField = presentFlagField;
    }
  }

  /** Latest contribution of one source. Null map values are stored nulls, not missing fields. */
  public static final class Snapshot {
    public boolean seen;
    public boolean deleted;
    public BvLegOperation operation = BvLegOperation.UPSERT;
    public final Map<String, Object> values = new LinkedHashMap<>();
    public final Set<String> cleared = new LinkedHashSet<>();
  }

  /** One source row already ordered by grain key and timestamp. */
  public static final class Event {
    public final Object key;
    public final Date timestamp;
    public final String sourceId;
    public final BvLegOperation operation;
    public final Map<String, Object> values;
    public final Set<String> cleared;

    public Event(
        Object key,
        Date timestamp,
        String sourceId,
        BvLegOperation operation,
        Map<String, Object> values) {
      this(key, timestamp, sourceId, operation, values, Set.of());
    }

    public Event(
        Object key,
        Date timestamp,
        String sourceId,
        BvLegOperation operation,
        Map<String, Object> values,
        Set<String> cleared) {
      this.key = key;
      this.timestamp = timestamp;
      this.sourceId = sourceId;
      this.operation = operation == null ? BvLegOperation.UPSERT : operation;
      this.values = values == null ? Map.of() : values;
      this.cleared = cleared == null ? Set.of() : cleared;
    }
  }

  /** A surviving vector that differs from the previous one for the same key. */
  public static final class Emit {
    public final Object key;
    public final Date timestamp;
    public final String sourceId;
    public final Map<String, Object> values;

    public Emit(Object key, Date timestamp, String sourceId, Map<String, Object> values) {
      this.key = key;
      this.timestamp = timestamp;
      this.sourceId = sourceId;
      this.values = values;
    }
  }

  public static final class Vector {
    public final Map<String, Object> values;
    public final String sourceId;

    public Vector(Map<String, Object> values, String sourceId) {
      this.values = values;
      this.sourceId = sourceId;
    }
  }

  public static void observe(
      Map<String, Snapshot> state,
      String sourceId,
      BvLegOperation operation,
      Map<String, Object> values) {
    observe(state, sourceId, operation, values, Set.of());
  }

  public static void observe(
      Map<String, Snapshot> state,
      String sourceId,
      BvLegOperation operation,
      Map<String, Object> values,
      Set<String> cleared) {
    if (state == null || sourceId == null) {
      return;
    }
    Snapshot snapshot = state.computeIfAbsent(sourceId, key -> new Snapshot());
    snapshot.seen = true;
    snapshot.operation = operation == null ? BvLegOperation.UPSERT : operation;
    if (snapshot.operation == BvLegOperation.DELETE) {
      snapshot.deleted = true;
      snapshot.values.clear();
      snapshot.cleared.clear();
      return;
    }
    snapshot.deleted = false;
    if (values != null) {
      for (Map.Entry<String, Object> entry : values.entrySet()) {
        snapshot.values.put(entry.getKey(), entry.getValue());
        if (entry.getValue() != null) {
          snapshot.cleared.remove(entry.getKey());
        }
      }
    }
    if (cleared != null) {
      for (String field : cleared) {
        snapshot.cleared.add(field);
        snapshot.values.put(field, null);
      }
    }
  }

  /**
   * True when a present-flag value says the attribute was in the message. Null, false, 0, and
   * N/NO/FALSE are absent.
   */
  public static boolean fieldIsPresent(Object flag) {
    if (flag == null) {
      return false;
    }
    if (flag instanceof Boolean present) {
      return present;
    }
    if (flag instanceof Number number) {
      return number.doubleValue() != 0d;
    }
    String text = flag.toString().trim();
    if (text.isEmpty()
        || text.equalsIgnoreCase("N")
        || text.equalsIgnoreCase("NO")
        || text.equalsIgnoreCase("FALSE")
        || text.equalsIgnoreCase("F")
        || text.equalsIgnoreCase("0")) {
      return false;
    }
    return true;
  }

  /** Copies one source field into the observation, honoring a present flag when one is named. */
  public static void readField(
      Map<String, Object> values,
      Set<String> cleared,
      String field,
      Object value,
      String presentFlagField,
      Object flag) {
    if (field == null || values == null) {
      return;
    }
    if (presentFlagField != null && !presentFlagField.isBlank() && !fieldIsPresent(flag)) {
      return;
    }
    values.put(field, value);
    if (presentFlagField != null && !presentFlagField.isBlank() && value == null && cleared != null) {
      cleared.add(field);
    }
  }

  public static Vector survivors(Map<String, Snapshot> state, List<Rule> rules) {
    Map<String, Object> values = new LinkedHashMap<>();
    String dominant = null;
    int dominantRank = Integer.MAX_VALUE;
    if (rules != null) {
      for (String field : fields(rules)) {
        Winner winner = winner(field, state, rules);
        values.put(field, winner.value);
        if (winner.sourceId != null && winner.rank < dominantRank) {
          dominant = winner.sourceId;
          dominantRank = winner.rank;
        }
      }
    }
    return new Vector(values, dominant);
  }

  /**
   * Applies every observation at one timestamp, then reports the new vector. {@code previous} null
   * means this is the first vector for the key.
   */
  public static Vector applyGroup(
      Map<String, Snapshot> state,
      Map<String, Object> previous,
      List<Event> observations,
      List<Rule> rules) {
    if (observations != null) {
      for (Event event : observations) {
        if (event == null) {
          continue;
        }
        observe(state, event.sourceId, event.operation, event.values, event.cleared);
      }
    }
    Vector vector = survivors(state, rules);
    return sameVector(previous, vector.values) ? null : vector;
  }

  /** Events must already be ordered by key, then timestamp. Same-timestamp rows collapse. */
  public static List<Emit> replay(List<Event> events, List<Rule> rules) {
    List<Emit> emits = new ArrayList<>();
    if (events == null || events.isEmpty()) {
      return emits;
    }
    Map<String, Snapshot> state = new LinkedHashMap<>();
    Map<String, Object> previous = null;
    Object currentKey = null;
    Date currentTimestamp = null;
    List<Event> group = new ArrayList<>();
    boolean open = false;
    for (Event event : events) {
      if (event == null) {
        continue;
      }
      boolean newKey = !open || !same(currentKey, event.key);
      boolean newTime = newKey || !same(currentTimestamp, event.timestamp);
      if (open && newTime) {
        emitGroup(emits, state, previous, group, rules, currentKey, currentTimestamp);
        previous = lastValues(emits, currentKey, previous);
        group.clear();
      }
      if (newKey) {
        state = new LinkedHashMap<>();
        previous = null;
        currentKey = event.key;
      }
      currentTimestamp = event.timestamp;
      group.add(event);
      open = true;
    }
    if (open) {
      emitGroup(emits, state, previous, group, rules, currentKey, currentTimestamp);
    }
    return emits;
  }

  public static boolean sameVector(Map<String, Object> left, Map<String, Object> right) {
    if (left == null || right == null) {
      return left == right;
    }
    if (left.size() != right.size()) {
      return false;
    }
    for (Map.Entry<String, Object> entry : right.entrySet()) {
      if (!same(left.get(entry.getKey()), entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  private static void emitGroup(
      List<Emit> emits,
      Map<String, Snapshot> state,
      Map<String, Object> previous,
      List<Event> group,
      List<Rule> rules,
      Object key,
      Date timestamp) {
    Vector vector = applyGroup(state, previous, group, rules);
    if (vector != null) {
      emits.add(new Emit(key, timestamp, vector.sourceId, vector.values));
    }
  }

  private static Map<String, Object> lastValues(
      List<Emit> emits, Object key, Map<String, Object> previous) {
    if (emits.isEmpty()) {
      return previous;
    }
    Emit last = emits.get(emits.size() - 1);
    if (!same(last.key, key)) {
      return previous;
    }
    return last.values;
  }

  private static Winner winner(String field, Map<String, Snapshot> state, List<Rule> rules) {
    List<Rule> ranked = new ArrayList<>();
    for (Rule rule : rules) {
      if (rule != null && field.equals(rule.field)) {
        ranked.add(rule);
      }
    }
    ranked.sort(
        (left, right) -> {
          int compare = Integer.compare(left.rank, right.rank);
          if (compare != 0) {
            return compare;
          }
          return Objects.toString(left.sourceId, "").compareTo(Objects.toString(right.sourceId, ""));
        });
    for (Rule rule : ranked) {
      Snapshot snapshot = state == null ? null : state.get(rule.sourceId);
      if (snapshot == null || !snapshot.seen || snapshot.deleted) {
        continue;
      }
      if (!snapshot.values.containsKey(field)) {
        continue;
      }
      Object value = snapshot.values.get(field);
      if (value == null && snapshot.cleared.contains(field)) {
        return new Winner(null, rule.sourceId, rule.rank);
      }
      boolean seed = rule.operation == BvLegOperation.SEED || snapshot.operation == BvLegOperation.SEED;
      if (seed) {
        if (operationalValuePresent(field, state, rules)) {
          continue;
        }
        if (value == null) {
          continue;
        }
        return new Winner(value, rule.sourceId, rule.rank);
      }
      if (value == null && rule.nullPolicy == BvNullPolicy.INHERIT) {
        continue;
      }
      return new Winner(value, rule.sourceId, rule.rank);
    }
    return new Winner(null, null, Integer.MAX_VALUE);
  }

  private static boolean operationalValuePresent(
      String field, Map<String, Snapshot> state, List<Rule> rules) {
    for (Rule rule : rules) {
      if (rule == null || !field.equals(rule.field) || rule.operation == BvLegOperation.SEED) {
        continue;
      }
      Snapshot snapshot = state == null ? null : state.get(rule.sourceId);
      if (snapshot == null
          || !snapshot.seen
          || snapshot.deleted
          || snapshot.operation == BvLegOperation.SEED) {
        continue;
      }
      if (snapshot.values.containsKey(field) && snapshot.values.get(field) != null) {
        return true;
      }
    }
    return false;
  }

  private static List<String> fields(List<Rule> rules) {
    List<String> fields = new ArrayList<>();
    for (Rule rule : rules) {
      if (rule != null && rule.field != null && !fields.contains(rule.field)) {
        fields.add(rule.field);
      }
    }
    return fields;
  }

  private static boolean same(Object left, Object right) {
    if (left instanceof byte[] leftBytes && right instanceof byte[] rightBytes) {
      return Arrays.equals(leftBytes, rightBytes);
    }
    if (left instanceof Date leftDate && right instanceof Date rightDate) {
      return leftDate.getTime() == rightDate.getTime();
    }
    return Objects.equals(left, right);
  }

  private static final class Winner {
    private final Object value;
    private final String sourceId;
    private final int rank;

    private Winner(Object value, String sourceId, int rank) {
      this.value = value;
      this.sourceId = sourceId;
      this.rank = rank;
    }
  }
}
