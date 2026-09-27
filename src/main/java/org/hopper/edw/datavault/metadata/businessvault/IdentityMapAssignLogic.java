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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Freezes {@code hk_raw → hk_durable} for a star-shaped same-as link. An existing raw key keeps its
 * durable key. A new member of a known cluster inherits that cluster's earliest durable key. A new
 * cluster hashes the MDM id when one is present, otherwise the current master raw key. {@code
 * preferred_bk} may change without changing {@code hk_durable}.
 */
public final class IdentityMapAssignLogic {

  private IdentityMapAssignLogic() {}

  /** One open map row already stored. */
  public static final class Existing {
    public final Object raw;
    public final Object durable;
    public final Object preferredBk;
    public final Object master;
    public final Date validFrom;
    public final Date validTo;
    public final int ruleVersion;

    public Existing(
        Object raw,
        Object durable,
        Object preferredBk,
        Object master,
        Date validFrom,
        Date validTo,
        int ruleVersion) {
      this.raw = raw;
      this.durable = durable;
      this.preferredBk = preferredBk;
      this.master = master;
      this.validFrom = validFrom;
      this.validTo = validTo;
      this.ruleVersion = ruleVersion;
    }
  }

  /** One same-as edge. The master raw key is itself a member of the cluster. */
  public static final class Edge {
    public final Object master;
    public final Object duplicate;
    public final Object preferredBk;
    public final Object mdmId;
    public final Date validFrom;
    public final Date validTo;

    public Edge(
        Object master,
        Object duplicate,
        Object preferredBk,
        Object mdmId,
        Date validFrom,
        Date validTo) {
      this.master = master;
      this.duplicate = duplicate;
      this.preferredBk = preferredBk;
      this.mdmId = mdmId;
      this.validFrom = validFrom;
      this.validTo = validTo;
    }
  }

  /** A row to insert, or an in-place update of the open row. Durable key is never rewritten. */
  public static final class Assignment {
    public final Object raw;
    public final Object durable;
    public final Object preferredBk;
    public final Object master;
    public final Date validFrom;
    public final Date validTo;
    public final int ruleVersion;
    public final boolean update;

    public Assignment(
        Object raw,
        Object durable,
        Object preferredBk,
        Object master,
        Date validFrom,
        Date validTo,
        int ruleVersion,
        boolean update) {
      this.raw = raw;
      this.durable = durable;
      this.preferredBk = preferredBk;
      this.master = master;
      this.validFrom = validFrom;
      this.validTo = validTo;
      this.ruleVersion = ruleVersion;
      this.update = update;
    }
  }

  @FunctionalInterface
  public interface DurableHasher {
    Object hash(Object value);
  }

  public static List<Assignment> assign(
      List<Existing> existing, List<Edge> edges, DurableHasher hasher, Date openEnd) {
    Map<KeyRef, Existing> stored = new LinkedHashMap<>();
    if (existing != null) {
      for (Existing row : existing) {
        if (row == null || row.raw == null || row.durable == null) {
          continue;
        }
        stored.putIfAbsent(new KeyRef(row.raw), row);
      }
    }

    Map<KeyRef, List<Edge>> clusters = new LinkedHashMap<>();
    if (edges != null) {
      for (Edge edge : edges) {
        if (edge == null || edge.master == null) {
          continue;
        }
        clusters.computeIfAbsent(new KeyRef(edge.master), key -> new ArrayList<>()).add(edge);
      }
    }

    List<Assignment> changes = new ArrayList<>();
    for (Map.Entry<KeyRef, List<Edge>> cluster : clusters.entrySet()) {
      Object master = cluster.getKey().value;
      List<Edge> clusterEdges = cluster.getValue();
      Set<KeyRef> members = new LinkedHashSet<>();
      members.add(new KeyRef(master));
      Object mdmId = null;
      for (Edge edge : clusterEdges) {
        if (edge.duplicate != null) {
          members.add(new KeyRef(edge.duplicate));
        }
        if (mdmId == null && edge.mdmId != null && !"".equals(edge.mdmId)) {
          mdmId = edge.mdmId;
        }
      }

      Object inherited = earliestDurable(members, stored);
      Object clusterDurable = inherited != null ? inherited : hashNewCluster(hasher, mdmId, master);
      if (clusterDurable == null) {
        continue;
      }

      Object preferred = latestPreferred(clusterEdges, stored.get(new KeyRef(master)));
      for (KeyRef member : members) {
        Existing prior = stored.get(member);
        Date memberFrom = earliestFrom(member.value, master, clusterEdges);
        Date memberTo = latestClosedTo(member.value, master, clusterEdges, openEnd);
        if (prior == null) {
          changes.add(
              new Assignment(
                  member.value,
                  clusterDurable,
                  preferred,
                  master,
                  memberFrom,
                  memberTo,
                  1,
                  false));
          continue;
        }
        boolean preferredChanged = !BvIdentityKeys.same(prior.preferredBk, preferred);
        boolean masterChanged = !BvIdentityKeys.same(prior.master, master);
        boolean closed = memberTo != null && !BvIdentityKeys.same(prior.validTo, memberTo);
        if (preferredChanged || masterChanged || closed) {
          changes.add(
              new Assignment(
                  prior.raw,
                  prior.durable,
                  preferred,
                  master,
                  prior.validFrom,
                  memberTo == null ? prior.validTo : memberTo,
                  prior.ruleVersion + 1,
                  true));
        }
      }
    }
    return changes;
  }

  private static Object earliestDurable(Set<KeyRef> members, Map<KeyRef, Existing> stored) {
    Existing earliest = null;
    for (KeyRef member : members) {
      Existing row = stored.get(member);
      if (row == null || row.durable == null) {
        continue;
      }
      if (earliest == null || before(row.validFrom, earliest.validFrom)) {
        earliest = row;
      }
    }
    return earliest == null ? null : earliest.durable;
  }

  private static Object hashNewCluster(DurableHasher hasher, Object mdmId, Object master) {
    if (hasher == null) {
      return null;
    }
    if (mdmId != null && !"".equals(mdmId)) {
      return hasher.hash(mdmId);
    }
    return hasher.hash(master);
  }

  private static Object latestPreferred(List<Edge> edges, Existing masterRow) {
    Edge latest = null;
    for (Edge edge : edges) {
      if (edge.preferredBk == null || "".equals(edge.preferredBk)) {
        continue;
      }
      if (latest == null || before(latest.validFrom, edge.validFrom)) {
        latest = edge;
      }
    }
    if (latest != null) {
      return latest.preferredBk;
    }
    return masterRow == null ? null : masterRow.preferredBk;
  }

  private static Date earliestFrom(Object member, Object master, List<Edge> edges) {
    Date earliest = null;
    for (Edge edge : edges) {
      if (!mentions(edge, member, master) || edge.validFrom == null) {
        continue;
      }
      if (earliest == null || edge.validFrom.before(earliest)) {
        earliest = edge.validFrom;
      }
    }
    return earliest;
  }

  /**
   * Open end when any covering edge is open. Otherwise the latest closed {@code validTo}. {@code
   * openEnd} is used when a member is only the master and no edge carries a bound.
   */
  private static Date latestClosedTo(
      Object member, Object master, List<Edge> edges, Date openEnd) {
    boolean saw = false;
    Date latest = null;
    for (Edge edge : edges) {
      if (!mentions(edge, member, master)) {
        continue;
      }
      saw = true;
      if (edge.validTo == null) {
        return openEnd;
      }
      if (latest == null || edge.validTo.after(latest)) {
        latest = edge.validTo;
      }
    }
    if (!saw) {
      return openEnd;
    }
    return latest == null ? openEnd : latest;
  }

  private static boolean mentions(Edge edge, Object member, Object master) {
    if (BvIdentityKeys.same(member, master)) {
      return true;
    }
    return BvIdentityKeys.same(edge.duplicate, member);
  }

  private static boolean before(Date left, Date right) {
    if (left == null) {
      return right != null;
    }
    if (right == null) {
      return false;
    }
    return left.before(right);
  }

  /** Map key that compares hash bytes by content. */
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
