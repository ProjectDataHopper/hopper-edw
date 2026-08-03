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
package org.apache.hop.datavault.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LineageSnapshotDiffSupportTest {

  @Test
  void detectsPhysicalRenameAsBlockingWithoutExplicitName() {
    LineageSnapshot baseline =
        snapshotWithHub("hub_customer", "hub_customer", "CRM", "customer_id");
    LineageSnapshot current = snapshotWithHub("hub_customer", "hub_cust_new", "CRM", "customer_id");
    // Strip explicit name reason to simulate opaque rename
    current
        .getTables()
        .get(0)
        .getReasons()
        .removeIf(r -> r.getCode() == LineageReasonCode.USER_EXPLICIT_NAME);

    LineageDiffResult diff =
        LineageSnapshotDiffSupport.compare(baseline, current, "catalog:baseline");
    assertTrue(diff.hasBlocking(), "physical rename without USER_EXPLICIT_NAME should block");
    assertTrue(
        diff.getEntries().stream().anyMatch(e -> e.getType() == LineageDiffType.TABLE_RENAMED));
  }

  @Test
  void detectsFieldRenameViaSourceFingerprint() {
    LineageSnapshot baseline = new LineageSnapshot();
    baseline.setModelName("m");
    baseline.setModelLayer(LineageLayer.DV);
    TableLineage baseSat = new TableLineage();
    baseSat.setLogicalName("sat_x");
    baseSat.setPhysicalTableName("sat_x");
    FieldLineage baseField = new FieldLineage("old_name");
    FieldContribution baseC = new FieldContribution();
    baseC.setSourceName("SRC");
    baseC.setSourceFieldName("col");
    baseC.setTransform(FieldTransform.RENAME);
    baseField.addContribution(baseC);
    baseSat.addField(baseField);
    baseline.addTable(baseSat);

    LineageSnapshot current = new LineageSnapshot();
    current.setModelName("m");
    current.setModelLayer(LineageLayer.DV);
    TableLineage currSat = new TableLineage();
    currSat.setLogicalName("sat_x");
    currSat.setPhysicalTableName("sat_x");
    FieldLineage currField = new FieldLineage("new_name");
    FieldContribution currC = new FieldContribution();
    currC.setSourceName("SRC");
    currC.setSourceFieldName("col");
    currC.setTransform(FieldTransform.RENAME);
    currField.addContribution(currC);
    currSat.addField(currField);
    current.addTable(currSat);

    LineageDiffResult diff = LineageSnapshotDiffSupport.compare(baseline, current, "t");
    assertTrue(
        diff.getEntries().stream().anyMatch(e -> e.getType() == LineageDiffType.FIELD_RENAMED));
    assertEquals(
        "old_name",
        diff.getEntries().stream()
            .filter(e -> e.getType() == LineageDiffType.FIELD_RENAMED)
            .findFirst()
            .orElseThrow()
            .getBaselineValue());
  }

  @Test
  void detectsMappingChange() {
    LineageSnapshot baseline = snapshotWithHub("hub_a", "hub_a", "S1", "id");
    LineageSnapshot current = snapshotWithHub("hub_a", "hub_a", "S1", "id_new");

    LineageDiffResult diff = LineageSnapshotDiffSupport.compare(baseline, current, "t");
    assertTrue(
        diff.getEntries().stream().anyMatch(e -> e.getType() == LineageDiffType.MAPPING_CHANGED));
  }

  @Test
  void missingBaselineMarksAddedTablesInfo() {
    LineageSnapshot current = snapshotWithHub("hub_a", "hub_a", "S1", "id");
    LineageDiffResult diff = LineageSnapshotDiffSupport.compare(null, current, "none");
    assertTrue(diff.isBaselineMissing());
    assertFalse(diff.hasBlocking());
    assertTrue(
        diff.getEntries().stream().anyMatch(e -> e.getType() == LineageDiffType.TABLE_ADDED));
  }

  @Test
  void tableRenameByFingerprintAcrossLogicalNames() {
    LineageSnapshot baseline = snapshotWithHub("hub_old", "hub_old", "CRM", "cust_id");
    LineageSnapshot current = snapshotWithHub("hub_new", "hub_new", "CRM", "cust_id");
    current.getTables().get(0).getReasons().clear();

    LineageDiffResult diff = LineageSnapshotDiffSupport.compare(baseline, current, "t");
    assertTrue(
        diff.getEntries().stream().anyMatch(e -> e.getType() == LineageDiffType.TABLE_RENAMED));
    assertTrue(diff.hasBlocking());
  }

  private static LineageSnapshot snapshotWithHub(
      String logical, String physical, String source, String sourceField) {
    LineageSnapshot snapshot = new LineageSnapshot();
    snapshot.setModelName("unit");
    snapshot.setModelLayer(LineageLayer.DV);
    TableLineage hub = new TableLineage();
    hub.setLayer(LineageLayer.DV);
    hub.setLogicalName(logical);
    hub.setPhysicalTableName(physical);
    hub.setTableType("HUB");
    hub.addReason(LineageReasonFactory.userExplicitName(logical, physical));
    hub.addSource(
        new TableSourceRef(TableSourceKind.DV_SOURCE, source, TableSourceRole.RECORD_SOURCE));
    FieldLineage bk = new FieldLineage("id");
    FieldContribution contribution = new FieldContribution();
    contribution.setSourceKind(TableSourceKind.DV_SOURCE);
    contribution.setSourceName(source);
    contribution.setSourceFieldName(sourceField);
    contribution.setTransform(
        "id".equals(sourceField) ? FieldTransform.IDENTITY : FieldTransform.RENAME);
    contribution.addReason(LineageReasonFactory.userExplicitMapping("id", source, sourceField));
    bk.addContribution(contribution);
    hub.addField(bk);
    snapshot.addTable(hub);
    return snapshot;
  }
}
