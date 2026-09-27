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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * As-of lookup from a raw hub hash to the durable hash in effect at a row timestamp. Intervals are
 * half-open: {@code validFrom} inclusive, {@code validTo} exclusive. A null end is open.
 */
public final class IdentityLookupLogic {

  private IdentityLookupLogic() {}

  public static final class Interval {
    public final Object raw;
    public final Object durable;
    public final Object preferredBk;
    public final Date validFrom;
    public final Date validTo;
    public final Integer ruleVersion;

    public Interval(
        Object raw, Object durable, Object preferredBk, Date validFrom, Date validTo) {
      this(raw, durable, preferredBk, validFrom, validTo, null);
    }

    public Interval(
        Object raw,
        Object durable,
        Object preferredBk,
        Date validFrom,
        Date validTo,
        Integer ruleVersion) {
      this.raw = raw;
      this.durable = durable;
      this.preferredBk = preferredBk;
      this.validFrom = validFrom;
      this.validTo = validTo;
      this.ruleVersion = ruleVersion;
    }
  }

  public static final class Hit {
    public final boolean emit;
    public final boolean quarantine;
    public final Object key;
    public final Object raw;
    public final Object preferredBk;
    public final Integer ruleVersion;

    private Hit(
        boolean emit,
        boolean quarantine,
        Object key,
        Object raw,
        Object preferredBk,
        Integer ruleVersion) {
      this.emit = emit;
      this.quarantine = quarantine;
      this.key = key;
      this.raw = raw;
      this.preferredBk = preferredBk;
      this.ruleVersion = ruleVersion;
    }
  }

  public static Hit lookup(
      Object raw, Date timestamp, List<Interval> intervals, BvIdentityUnmappedPolicy policy) {
    Interval match = match(raw, timestamp, intervals);
    if (match != null) {
      return new Hit(true, false, match.durable, raw, match.preferredBk, match.ruleVersion);
    }
    BvIdentityUnmappedPolicy effective =
        policy != null ? policy : BvIdentityUnmappedPolicy.SELF;
    return switch (effective) {
      case DROP -> new Hit(false, false, null, raw, null, null);
      case QUARANTINE -> new Hit(false, true, null, raw, null, null);
      case SELF -> new Hit(true, false, raw, raw, null, null);
    };
  }

  /**
   * Indexes map intervals by raw-key equality, so binary hashes match by content. Order of {@code
   * mainRows} is preserved. Several intervals for one raw key are tried in list order.
   */
  public static List<Hit> lookupAll(
      List<MainRow> mainRows, List<Interval> mapRows, BvIdentityUnmappedPolicy policy) {
    List<Hit> hits = new ArrayList<>();
    if (mainRows == null || mainRows.isEmpty()) {
      return hits;
    }
    Map<KeyRef, List<Interval>> byRaw = new LinkedHashMap<>();
    if (mapRows != null) {
      for (Interval interval : mapRows) {
        if (interval == null || interval.raw == null) {
          continue;
        }
        byRaw.computeIfAbsent(new KeyRef(interval.raw), key -> new ArrayList<>()).add(interval);
      }
    }
    for (MainRow row : mainRows) {
      if (row == null) {
        hits.add(lookup(null, null, List.of(), policy));
        continue;
      }
      List<Interval> intervals = byRaw.getOrDefault(new KeyRef(row.raw), List.of());
      hits.add(lookup(row.raw, row.timestamp, intervals, policy));
    }
    return hits;
  }

  static Interval match(Object raw, Date timestamp, List<Interval> intervals) {
    if (raw == null || timestamp == null || intervals == null) {
      return null;
    }
    for (Interval interval : intervals) {
      if (interval == null || !BvIdentityKeys.same(interval.raw, raw) || interval.durable == null) {
        continue;
      }
      if (interval.validFrom != null && timestamp.before(interval.validFrom)) {
        continue;
      }
      if (interval.validTo != null && !timestamp.before(interval.validTo)) {
        continue;
      }
      return interval;
    }
    return null;
  }

  public static final class MainRow {
    public final Object raw;
    public final Date timestamp;

    public MainRow(Object raw, Date timestamp) {
      this.raw = raw;
      this.timestamp = timestamp;
    }
  }

  private static final class KeyRef {
    private final Object value;

    private KeyRef(Object value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof KeyRef ref && BvIdentityKeys.same(value, ref.value);
    }

    @Override
    public int hashCode() {
      if (value instanceof byte[] bytes) {
        return java.util.Arrays.hashCode(bytes);
      }
      return value == null ? 0 : value.hashCode();
    }
  }
}
