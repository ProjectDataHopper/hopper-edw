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
package org.apache.hop.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultModel;

/**
 * Dependency graph and topological ordering for Business Vault tables that include SQL business
 * tables with {@code ref()} edges.
 */
public final class BvSqlDependencySupport {

  private BvSqlDependencySupport() {}

  /**
   * Orders pipeline-executable tables: respects BV→BV sqlRefs among business tables, keeps SCD2
   * before PIT when there is no stronger edge, and places dependents after their prerequisites.
   *
   * <p>Base priority when no ref edge applies: SCD2 (0), PIT (1), BUSINESS_TABLE (2), then model
   * order as a stable tie-break.
   */
  public static List<IBvTable> orderTablesForPipelineExecution(
      List<IBvTable> tables, BusinessVaultModel bvModel, DataVaultModel dvModel) {
    if (tables == null || tables.isEmpty()) {
      return List.of();
    }

    // Sync refs for business tables so edges are current.
    for (IBvTable table : tables) {
      if (table instanceof BvBusinessTable businessTable) {
        BvSqlRefResolver.syncRefsFromSql(businessTable, bvModel, dvModel, null, null);
      }
    }

    Map<String, IBvTable> byKey = new LinkedHashMap<>();
    List<IBvTable> executable = new ArrayList<>();
    for (IBvTable table : tables) {
      if (table == null
          || table.getTableType() == null
          || !BusinessVaultUpdateExecutionSupport.isPipelineExecutableTableType(
              table.getTableType())) {
        continue;
      }
      executable.add(table);
      String key = tableKey(table);
      if (!Utils.isEmpty(key)) {
        byKey.put(key.toLowerCase(), table);
      }
    }

    // adjacency: dependency -> list of dependents (edge dep -> table that needs dep first)
    Map<String, Set<String>> dependents = new HashMap<>();
    Map<String, Integer> indegree = new HashMap<>();
    for (IBvTable table : executable) {
      indegree.put(tableKey(table).toLowerCase(), 0);
    }

    for (IBvTable table : executable) {
      if (!(table instanceof BvBusinessTable businessTable)) {
        continue;
      }
      String tableKey = tableKey(table).toLowerCase();
      for (BvSqlRef ref : businessTable.getSqlRefs()) {
        if (ref == null
            || ref.getResolvedKind() != BvSqlResolvedKind.BV_TABLE
            || Utils.isEmpty(ref.getObjectName())) {
          continue;
        }
        IBvTable dep = findExecutableByName(byKey, ref.getObjectName());
        if (dep == null || dep == table) {
          continue;
        }
        String depKey = tableKey(dep).toLowerCase();
        dependents.computeIfAbsent(depKey, k -> new HashSet<>()).add(tableKey);
      }
    }

    for (Set<String> deps : dependents.values()) {
      for (String dependent : deps) {
        indegree.merge(dependent, 1, Integer::sum);
      }
    }

    // Kahn with priority: lower type rank first, then original order.
    Map<String, Integer> originalIndex = new HashMap<>();
    for (int i = 0; i < executable.size(); i++) {
      originalIndex.put(tableKey(executable.get(i)).toLowerCase(), i);
    }

    List<IBvTable> ordered = new ArrayList<>();
    Set<String> remaining = new HashSet<>(indegree.keySet());

    while (!remaining.isEmpty()) {
      String best = null;
      int bestRank = Integer.MAX_VALUE;
      int bestIndex = Integer.MAX_VALUE;
      for (String key : remaining) {
        if (indegree.getOrDefault(key, 0) > 0) {
          continue;
        }
        IBvTable candidate = byKey.get(key);
        int rank = typeRank(candidate);
        int index = originalIndex.getOrDefault(key, Integer.MAX_VALUE);
        if (rank < bestRank || (rank == bestRank && index < bestIndex)) {
          best = key;
          bestRank = rank;
          bestIndex = index;
        }
      }
      if (best == null) {
        // Cycle: append remaining by type rank / original order and stop.
        List<String> cycleKeys = new ArrayList<>(remaining);
        cycleKeys.sort(
            (a, b) -> {
              int cmp = Integer.compare(typeRank(byKey.get(a)), typeRank(byKey.get(b)));
              if (cmp != 0) {
                return cmp;
              }
              return Integer.compare(
                  originalIndex.getOrDefault(a, 0), originalIndex.getOrDefault(b, 0));
            });
        for (String key : cycleKeys) {
          ordered.add(byKey.get(key));
        }
        break;
      }
      remaining.remove(best);
      ordered.add(byKey.get(best));
      for (String dependent : dependents.getOrDefault(best, Set.of())) {
        indegree.merge(dependent, -1, Integer::sum);
      }
    }

    return ordered;
  }

  /**
   * Detects a cycle among BV business tables connected by resolved BV {@code ref()} edges. Returns
   * a short description of the cycle or null when acyclic.
   */
  public static String findCycleDescription(List<IBvTable> tables) {
    if (tables == null || tables.isEmpty()) {
      return null;
    }
    Map<String, IBvTable> byKey = new HashMap<>();
    Map<String, List<String>> edges = new HashMap<>();
    for (IBvTable table : tables) {
      if (!(table instanceof BvBusinessTable businessTable)) {
        continue;
      }
      String key = tableKey(table).toLowerCase();
      if (Utils.isEmpty(key)) {
        continue;
      }
      byKey.put(key, table);
      edges.putIfAbsent(key, new ArrayList<>());
      for (BvSqlRef ref : businessTable.getSqlRefs()) {
        if (ref == null
            || ref.getResolvedKind() != BvSqlResolvedKind.BV_TABLE
            || Utils.isEmpty(ref.getObjectName())) {
          continue;
        }
        IBvTable dep = findBvInList(tables, ref.getObjectName());
        if (dep instanceof BvBusinessTable && dep != table) {
          String depKey = tableKey(dep).toLowerCase();
          // edge: table depends on dep → for cycle detection walk table -> dep
          edges.computeIfAbsent(key, k -> new ArrayList<>()).add(depKey);
          byKey.putIfAbsent(depKey, dep);
        }
      }
    }

    Set<String> visiting = new HashSet<>();
    Set<String> visited = new HashSet<>();
    List<String> path = new ArrayList<>();
    for (String start : byKey.keySet()) {
      String cycle = dfsCycle(start, edges, visiting, visited, path);
      if (cycle != null) {
        return cycle;
      }
    }
    return null;
  }

  private static String dfsCycle(
      String node,
      Map<String, List<String>> edges,
      Set<String> visiting,
      Set<String> visited,
      List<String> path) {
    if (visited.contains(node)) {
      return null;
    }
    if (visiting.contains(node)) {
      int idx = path.indexOf(node);
      List<String> cycle = new ArrayList<>(path.subList(Math.max(0, idx), path.size()));
      cycle.add(node);
      return String.join(" -> ", cycle);
    }
    visiting.add(node);
    path.add(node);
    for (String next : edges.getOrDefault(node, List.of())) {
      String cycle = dfsCycle(next, edges, visiting, visited, path);
      if (cycle != null) {
        return cycle;
      }
    }
    path.remove(path.size() - 1);
    visiting.remove(node);
    visited.add(node);
    return null;
  }

  private static IBvTable findExecutableByName(Map<String, IBvTable> byKey, String name) {
    if (Utils.isEmpty(name)) {
      return null;
    }
    IBvTable direct = byKey.get(name.toLowerCase());
    if (direct != null) {
      return direct;
    }
    for (IBvTable table : byKey.values()) {
      if (table == null) {
        continue;
      }
      if (name.equalsIgnoreCase(table.getName()) || name.equalsIgnoreCase(table.getTableName())) {
        return table;
      }
    }
    return null;
  }

  private static IBvTable findBvInList(List<IBvTable> tables, String name) {
    for (IBvTable table : tables) {
      if (table == null) {
        continue;
      }
      if (name.equalsIgnoreCase(table.getName()) || name.equalsIgnoreCase(table.getTableName())) {
        return table;
      }
    }
    return null;
  }

  static String tableKey(IBvTable table) {
    if (table == null) {
      return "";
    }
    if (!Utils.isEmpty(table.getName())) {
      return table.getName();
    }
    return Utils.isEmpty(table.getTableName()) ? "" : table.getTableName();
  }

  static int typeRank(IBvTable table) {
    if (table == null || table.getTableType() == null) {
      return 99;
    }
    return switch (table.getTableType()) {
      case SCD2 -> 0;
      case PIT -> 1;
      case BUSINESS_TABLE -> 2;
    };
  }
}
