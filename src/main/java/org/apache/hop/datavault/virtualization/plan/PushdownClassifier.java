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
package org.apache.hop.datavault.virtualization.plan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelVisitor;
import org.apache.calcite.rel.core.TableScan;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.virtualization.calcite.SourceModelJsonTable;
import org.apache.hop.datavault.virtualization.calcite.SourceModelPipelineTable;
import org.apache.hop.datavault.virtualization.calcite.SourceModelQueryTable;
import org.apache.hop.datavault.virtualization.calcite.SourceModelTable;

/**
 * Classifies whether a planned RelNode tree can be fully pushed to a single database connection.
 */
public final class PushdownClassifier {

  private PushdownClassifier() {}

  public record Classification(
      boolean fullDatabasePushdown,
      String sharedDatabaseName,
      List<SourceModelTable> databaseTables,
      List<String> reasons) {}

  public static Classification classify(RelNode root) {
    List<SourceModelTable> databaseTables = new ArrayList<>();
    Set<String> connections = new LinkedHashSet<>();
    List<String> reasons = new ArrayList<>();
    int[] scanCount = new int[1];

    new RelVisitor() {
      @Override
      public void visit(RelNode node, int ordinal, RelNode parent) {
        if (node instanceof TableScan scan) {
          scanCount[0]++;
          SourceModelTable smt = scan.getTable().unwrap(SourceModelTable.class);
          if (smt != null) {
            databaseTables.add(smt);
            if (smt.physicalType() != DvSourceType.DATABASE) {
              reasons.add(
                  "Table '"
                      + smt.logicalName()
                      + "' is not a DATABASE source (got "
                      + smt.physicalType()
                      + ")");
            } else if (Utils.isEmpty(smt.databaseName())) {
              reasons.add("Table '" + smt.logicalName() + "' has no database connection");
            } else {
              connections.add(smt.databaseName().trim());
            }
          } else if (scan.getTable().unwrap(SourceModelQueryTable.class) != null) {
            SourceModelQueryTable qt = scan.getTable().unwrap(SourceModelQueryTable.class);
            reasons.add(
                "Table '" + qt.logicalName() + "' is a named Source Query (expanded residually)");
          } else if (scan.getTable().unwrap(SourceModelJsonTable.class) != null) {
            SourceModelJsonTable json = scan.getTable().unwrap(SourceModelJsonTable.class);
            reasons.add("Table '" + json.logicalName() + "' is a JSON source (residual path)");
          } else if (scan.getTable().unwrap(SourceModelPipelineTable.class) != null) {
            SourceModelPipelineTable pipe = scan.getTable().unwrap(SourceModelPipelineTable.class);
            reasons.add("Table '" + pipe.logicalName() + "' is a PIPELINE source (residual path)");
          } else {
            reasons.add("Table scan is not a known source-model table");
          }
        }
        super.visit(node, ordinal, parent);
      }
    }.go(root);

    if (scanCount[0] == 0) {
      reasons.add("Query references no tables");
      return new Classification(false, null, databaseTables, reasons);
    }
    if (!reasons.isEmpty()) {
      String shared = connections.size() == 1 ? connections.iterator().next() : null;
      return new Classification(false, shared, databaseTables, reasons);
    }
    if (connections.size() != 1) {
      if (connections.isEmpty()) {
        reasons.add("No DATABASE tables with a connection");
      } else {
        reasons.add("Tables span multiple database connections: " + connections);
      }
      return new Classification(false, null, databaseTables, reasons);
    }
    return new Classification(true, connections.iterator().next(), databaseTables, reasons);
  }
}
