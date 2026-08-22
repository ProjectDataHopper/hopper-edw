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
package org.hopper.edw.datavault.metadata.sourcemodel.generate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryJoin;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;

/** Resolves left/right join key columns for a {@link SourceQueryJoin}. */
public final class SourceQueryJoinKeyResolver {

  private SourceQueryJoinKeyResolver() {}

  public record ResolvedJoinKeys(
      List<String> leftTables, List<String> leftColumns, List<String> rightColumns) {

    public boolean isValid() {
      return leftColumns != null
          && rightColumns != null
          && !leftColumns.isEmpty()
          && leftColumns.size() == rightColumns.size()
          && leftTables != null
          && leftTables.size() == leftColumns.size();
    }
  }

  /**
   * @param inScopeTables tables already present in the FROM/JOIN so far (includes driving table)
   */
  public static ResolvedJoinKeys resolve(
      SourceModel model, SourceQueryJoin join, Set<String> inScopeTables) throws HopException {
    if (join == null || Utils.isEmpty(join.getTableName())) {
      throw new HopException("Join is missing a table name");
    }
    String rightTable = join.getTableName().trim();

    if (!join.getLeftColumns().isEmpty() && !join.getRightColumns().isEmpty()) {
      List<String> leftTables = new ArrayList<>();
      List<String> leftCols = new ArrayList<>(join.getLeftColumns());
      List<String> rightCols = new ArrayList<>(join.getRightColumns());
      if (leftCols.size() != rightCols.size()) {
        throw new HopException(
            "Join to '" + rightTable + "' has mismatched left/right column counts");
      }
      for (int i = 0; i < leftCols.size(); i++) {
        String leftTable =
            i < join.getLeftTableNames().size() && !Utils.isEmpty(join.getLeftTableNames().get(i))
                ? join.getLeftTableNames().get(i).trim()
                : pickDefaultLeftTable(inScopeTables, rightTable);
        leftTables.add(leftTable);
      }
      return new ResolvedJoinKeys(leftTables, leftCols, rightCols);
    }

    if (!Utils.isEmpty(join.getRelationshipName())) {
      SourceRelationship relationship = model.findRelationship(join.getRelationshipName());
      if (relationship == null) {
        throw new HopException(
            "Relationship '"
                + join.getRelationshipName()
                + "' not found for join to "
                + rightTable);
      }
      return fromRelationship(relationship, rightTable, inScopeTables);
    }

    // Infer from any relationship between rightTable and an in-scope table.
    for (SourceRelationship relationship : model.getRelationships()) {
      if (relationship == null || !relationship.isValid()) {
        continue;
      }
      boolean childIn = inScopeTables.contains(relationship.getChildTableName());
      boolean parentIn = inScopeTables.contains(relationship.getParentTableName());
      boolean childIsRight = rightTable.equals(relationship.getChildTableName());
      boolean parentIsRight = rightTable.equals(relationship.getParentTableName());
      if ((childIsRight && parentIn) || (parentIsRight && childIn)) {
        return fromRelationship(relationship, rightTable, inScopeTables);
      }
    }

    throw new HopException(
        "Cannot resolve join keys for table '"
            + rightTable
            + "': set join columns or a relationship name");
  }

  private static ResolvedJoinKeys fromRelationship(
      SourceRelationship relationship, String rightTable, Set<String> inScopeTables)
      throws HopException {
    String child = relationship.getChildTableName();
    String parent = relationship.getParentTableName();
    List<String> childCols = new ArrayList<>(relationship.getChildColumns());
    List<String> parentCols = new ArrayList<>(relationship.getParentColumns());

    if (rightTable.equals(child)) {
      // Joining child: left is parent (must be in scope), right is child FK cols.
      if (!inScopeTables.contains(parent)) {
        throw new HopException(
            "Parent table '"
                + parent
                + "' must already be in the query before joining child '"
                + child
                + "'");
      }
      List<String> leftTables = new ArrayList<>();
      for (int i = 0; i < parentCols.size(); i++) {
        leftTables.add(parent);
      }
      return new ResolvedJoinKeys(leftTables, parentCols, childCols);
    }
    if (rightTable.equals(parent)) {
      if (!inScopeTables.contains(child)) {
        throw new HopException(
            "Child table '"
                + child
                + "' must already be in the query before joining parent '"
                + parent
                + "'");
      }
      List<String> leftTables = new ArrayList<>();
      for (int i = 0; i < childCols.size(); i++) {
        leftTables.add(child);
      }
      return new ResolvedJoinKeys(leftTables, childCols, parentCols);
    }
    throw new HopException(
        "Relationship '"
            + relationship.getName()
            + "' does not involve join table '"
            + rightTable
            + "'");
  }

  private static String pickDefaultLeftTable(Set<String> inScopeTables, String rightTable) {
    for (String name : inScopeTables) {
      if (!rightTable.equals(name)) {
        return name;
      }
    }
    return rightTable;
  }
}
