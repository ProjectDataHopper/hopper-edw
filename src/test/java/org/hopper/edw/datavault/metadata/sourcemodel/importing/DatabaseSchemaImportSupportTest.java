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
package org.hopper.edw.datavault.metadata.sourcemodel.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.IProgressMonitor;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.database.DiscoveredForeignKey;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJoinType;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DatabaseSchemaImportSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void layoutPointUsesGrid() {
    Point first = DatabaseSchemaImportSupport.layoutPoint(0);
    assertEquals(DatabaseSchemaImportSupport.LAYOUT_ORIGIN_X, first.x);
    assertEquals(DatabaseSchemaImportSupport.LAYOUT_ORIGIN_Y, first.y);

    Point fifth = DatabaseSchemaImportSupport.layoutPoint(4);
    assertEquals(DatabaseSchemaImportSupport.LAYOUT_ORIGIN_X, fifth.x);
    assertEquals(
        DatabaseSchemaImportSupport.LAYOUT_ORIGIN_Y + DatabaseSchemaImportSupport.LAYOUT_STEP_Y,
        fifth.y);
  }

  @Test
  void toSourceColumnsCopiesPkAndTypes() {
    SourceField id = new SourceField("product_id");
    id.setPrimaryKeyPosition(1);
    id.setHopType(5);
    id.setSourceDataType("int4");
    id.setLength("10");
    SourceField name = new SourceField("name");
    name.setHopType(2);

    List<SourceColumn> columns = DatabaseSchemaImportSupport.toSourceColumns(List.of(id, name));
    assertEquals(2, columns.size());
    assertEquals(1, columns.get(0).getPrimaryKeyPosition());
    assertEquals(5, columns.get(0).getHopType());
    assertEquals("int4", columns.get(0).getSourceDataType());
    assertEquals(0, columns.get(1).getPrimaryKeyPosition());
  }

  @Test
  void buildSourceTablePopulatesPhysicalAndColumns() {
    SourceField pk = new SourceField("id");
    pk.setPrimaryKeyPosition(1);
    SourceTable table =
        DatabaseSchemaImportSupport.buildSourceTable(
            "product", "CRM", "public", "product", List.of(pk), 1);
    assertEquals("product", table.getName());
    assertEquals("CRM", table.getDatabaseName());
    assertEquals("public", table.getSchemaName());
    assertEquals("product", table.getTableName());
    assertEquals(1, table.getColumns().size());
    assertEquals(
        DatabaseSchemaImportSupport.LAYOUT_ORIGIN_X + DatabaseSchemaImportSupport.LAYOUT_STEP_X,
        table.getLocation().x);
  }

  @Test
  void buildRelationshipsFromForeignKeysCreatesLeftJoinEdges() {
    Map<String, String> physicalToLogical = new HashMap<>();
    physicalToLogical.put(
        DatabaseSchemaImportSupport.normalizePhysicalKey("public", "product"), "product");
    physicalToLogical.put(
        DatabaseSchemaImportSupport.normalizePhysicalKey("public", "product_type"), "product_type");

    DiscoveredForeignKey fk = new DiscoveredForeignKey();
    fk.setConstraintName("fk_product_type");
    fk.setChildSchema("public");
    fk.setChildTable("product");
    fk.setParentSchema("public");
    fk.setParentTable("product_type");
    fk.addColumnPair("type_id", "type_id");

    SourceModel model = new SourceModel();
    List<String> warnings = new ArrayList<>();
    List<SourceRelationship> relationships =
        DatabaseSchemaImportSupport.buildRelationshipsFromForeignKeys(
            List.of(fk), physicalToLogical, model, List.of(), warnings);

    assertEquals(1, relationships.size());
    SourceRelationship rel = relationships.get(0);
    assertEquals("product", rel.getChildTableName());
    assertEquals("product_type", rel.getParentTableName());
    assertEquals(List.of("type_id"), rel.getChildColumns());
    assertEquals(List.of("type_id"), rel.getParentColumns());
    assertEquals(SourceJoinType.LEFT, rel.resolveDefaultJoinType());
    // Import uses crow's-foot style free-text cardinality (optional child side).
    assertEquals("0..N:1", rel.getCardinality());
    assertTrue(warnings.isEmpty());
  }

  @Test
  void buildRelationshipsSkipsMissingEndpointsAndDuplicates() {
    Map<String, String> physicalToLogical = new HashMap<>();
    physicalToLogical.put(
        DatabaseSchemaImportSupport.normalizePhysicalKey(null, "product"), "product");

    DiscoveredForeignKey missingParent = new DiscoveredForeignKey();
    missingParent.setChildTable("product");
    missingParent.setParentTable("missing");
    missingParent.addColumnPair("type_id", "type_id");

    DiscoveredForeignKey ok = new DiscoveredForeignKey();
    ok.setChildTable("product");
    ok.setParentTable("product_type");
    ok.addColumnPair("type_id", "type_id");
    physicalToLogical.put(
        DatabaseSchemaImportSupport.normalizePhysicalKey(null, "product_type"), "product_type");

    SourceModel model = new SourceModel();
    SourceRelationship existing = new SourceRelationship("existing");
    existing.setChildTableName("product");
    existing.setParentTableName("product_type");
    existing.setChildColumns(List.of("type_id"));
    existing.setParentColumns(List.of("type_id"));
    model.getRelationships().add(existing);

    List<String> warnings = new ArrayList<>();
    List<SourceRelationship> relationships =
        DatabaseSchemaImportSupport.buildRelationshipsFromForeignKeys(
            List.of(missingParent, ok), physicalToLogical, model, List.of(), warnings);

    assertTrue(relationships.isEmpty());
    assertEquals(2, warnings.size());
  }

  @Test
  void applyImportResultAddsTablesAndRelationships() {
    SourceModel model = new SourceModel();
    SourceTable table = new SourceTable("product");
    SourceRelationship rel = new SourceRelationship("fk1");
    rel.setChildTableName("product");
    rel.setParentTableName("product_type");
    rel.setChildColumns(List.of("type_id"));
    rel.setParentColumns(List.of("type_id"));

    SourceSchemaImportResult result =
        new SourceSchemaImportResult(
            List.of(table), List.of(rel), List.of("product"), List.of(), List.of());
    DatabaseSchemaImportSupport.applyImportResult(model, result);

    assertEquals(1, model.getTables().size());
    assertEquals(1, model.getRelationships().size());
    assertEquals("product", model.findTable("product").getName());
  }

  @Test
  void uniqueLogicalNameAddsSuffix() {
    SourceModel model = new SourceModel();
    model.getTables().add(new SourceTable("product"));
    String unique = DatabaseSchemaImportSupport.uniqueLogicalName(model, List.of(), "product");
    assertEquals("product_2", unique);
  }

  @Test
  void uniqueNameHandlesCollisions() {
    Set<String> used = new HashSet<>();
    used.add("fk_a");
    assertEquals("fk_a_2", DatabaseSchemaImportSupport.uniqueName(used, "fk_a"));
    assertEquals("fk_b", DatabaseSchemaImportSupport.uniqueName(used, "fk_b"));
  }

  @Test
  void sanitizeNameRemovesOddCharacters() {
    assertEquals("order_header", DatabaseSchemaImportSupport.sanitizeName("order header"));
    assertFalse(DatabaseSchemaImportSupport.sanitizeName("!!!").isEmpty());
  }

  @Test
  void importTablesEmptyListReturnsEmptyWithoutProgress() throws Exception {
    SourceModel model = new SourceModel();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("crm");
    CountingMonitor monitor = new CountingMonitor();

    SourceSchemaImportResult result =
        DatabaseSchemaImportSupport.importTables(
            model,
            databaseMeta,
            null,
            List.of(),
            new Variables(),
            new MemoryMetadataProvider(),
            monitor);

    assertTrue(result.getImportedTablesOrEmpty().isEmpty());
    assertEquals(0, monitor.beginTaskWork);
  }

  private static final class CountingMonitor implements IProgressMonitor {
    int beginTaskWork;

    @Override
    public void beginTask(String message, int nrWorks) {
      beginTaskWork = nrWorks;
    }

    @Override
    public void subTask(String message) {}

    @Override
    public boolean isCanceled() {
      return false;
    }

    @Override
    public void worked(int nrWorks) {}

    @Override
    public void done() {}

    @Override
    public void setTaskName(String taskName) {}
  }
}
