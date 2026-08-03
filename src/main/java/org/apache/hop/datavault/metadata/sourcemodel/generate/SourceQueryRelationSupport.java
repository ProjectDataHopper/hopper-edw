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
package org.apache.hop.datavault.metadata.sourcemodel.generate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryJoin;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;

/** Graph helpers for source-query join UX (related tables, resolved key labels). */
public final class SourceQueryRelationSupport {

  private SourceQueryRelationSupport() {}

  /** Tables related by a model edge to any of {@code inScopeTables}. */
  public static List<String> relatedTableNames(SourceModel model, Set<String> inScopeTables) {
    Set<String> related = new LinkedHashSet<>();
    if (model == null || inScopeTables == null || inScopeTables.isEmpty()) {
      return List.of();
    }
    for (SourceRelationship relationship : model.getRelationships()) {
      if (relationship == null || !relationship.isValid()) {
        continue;
      }
      String child = relationship.getChildTableName();
      String parent = relationship.getParentTableName();
      if (inScopeTables.contains(child) && !inScopeTables.contains(parent)) {
        related.add(parent);
      }
      if (inScopeTables.contains(parent) && !inScopeTables.contains(child)) {
        related.add(child);
      }
    }
    return new ArrayList<>(related);
  }

  public static Set<String> inScopeFromQuery(SourceQuery query) {
    return new LinkedHashSet<>(SourceQueryGenerationSupport.participantTableNames(query));
  }

  public static Set<String> inScopeFromDrivingAndJoins(
      String drivingTable, List<SourceQueryJoin> joins) {
    Set<String> inScope = new LinkedHashSet<>();
    if (!Utils.isEmpty(drivingTable)) {
      inScope.add(drivingTable.trim());
    }
    if (joins != null) {
      for (SourceQueryJoin join : joins) {
        if (join != null && !Utils.isEmpty(join.getTableName())) {
          inScope.add(join.getTableName().trim());
        }
      }
    }
    return inScope;
  }

  /**
   * Human-readable ON clause for a join using the same resolver as SQL generation. Returns a short
   * status message when resolution fails.
   */
  public static String formatResolvedKeys(
      SourceModel model, SourceQueryJoin join, Set<String> inScopeBeforeJoin) {
    if (model == null || join == null || Utils.isEmpty(join.getTableName())) {
      return "";
    }
    try {
      SourceQueryJoinKeyResolver.ResolvedJoinKeys keys =
          SourceQueryJoinKeyResolver.resolve(model, join, inScopeBeforeJoin);
      if (!keys.isValid()) {
        return "(unresolved)";
      }
      StringBuilder out = new StringBuilder();
      for (int i = 0; i < keys.leftColumns().size(); i++) {
        if (i > 0) {
          out.append(" AND ");
        }
        out.append(keys.leftTables().get(i))
            .append('.')
            .append(keys.leftColumns().get(i))
            .append(" = ")
            .append(join.getTableName().trim())
            .append('.')
            .append(keys.rightColumns().get(i));
      }
      return out.toString();
    } catch (Exception e) {
      String message = e.getMessage();
      if (Utils.isEmpty(message)) {
        return "(unresolved)";
      }
      if (message.length() > 80) {
        return message.substring(0, 77) + "...";
      }
      return message;
    }
  }

  /** Relationships that connect {@code rightTable} to any table in {@code inScope}. */
  public static List<SourceRelationship> relationshipsTo(
      SourceModel model, String rightTable, Set<String> inScope) {
    List<SourceRelationship> matches = new ArrayList<>();
    if (model == null || Utils.isEmpty(rightTable) || inScope == null) {
      return matches;
    }
    String right = rightTable.trim();
    for (SourceRelationship relationship : model.getRelationships()) {
      if (relationship == null || !relationship.isValid()) {
        continue;
      }
      boolean childIsRight = right.equals(relationship.getChildTableName());
      boolean parentIsRight = right.equals(relationship.getParentTableName());
      boolean parentIn = inScope.contains(relationship.getParentTableName());
      boolean childIn = inScope.contains(relationship.getChildTableName());
      if ((childIsRight && parentIn) || (parentIsRight && childIn)) {
        matches.add(relationship);
      }
    }
    return matches;
  }
}
