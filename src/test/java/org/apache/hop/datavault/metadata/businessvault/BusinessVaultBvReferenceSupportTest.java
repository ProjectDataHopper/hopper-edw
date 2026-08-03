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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.gui.Point;
import org.junit.jupiter.api.Test;

class BusinessVaultBvReferenceSupportTest {

  @Test
  void hasBvReferenceNameOnlyIgnoresPath() {
    BusinessVaultModel model = new BusinessVaultModel();
    BvBvTableReference external =
        new BvBvTableReference(
            "satb_product_hb", BvTableType.BUSINESS_TABLE, "${PROJECT_HOME}/models/a.hbv");
    model.getBvReferences().add(external);

    assertTrue(BusinessVaultBvReferenceSupport.hasBvReference(model, "satb_product_hb"));
  }

  @Test
  void hasBvReferenceMatchesModelPath() {
    BusinessVaultModel model = new BusinessVaultModel();
    model
        .getBvReferences()
        .add(
            new BvBvTableReference(
                "satb_product_hb", BvTableType.BUSINESS_TABLE, "${PROJECT_HOME}/models/a.hbv"));
    model
        .getBvReferences()
        .add(
            new BvBvTableReference(
                "satb_product_hb", BvTableType.BUSINESS_TABLE, "${PROJECT_HOME}/models/b.hbv"));

    assertTrue(
        BusinessVaultBvReferenceSupport.hasBvReference(
            model, "satb_product_hb", "${PROJECT_HOME}/models/a.hbv"));
    assertTrue(
        BusinessVaultBvReferenceSupport.hasBvReference(
            model, "satb_product_hb", "a.hbv")); // basename match
    assertFalse(
        BusinessVaultBvReferenceSupport.hasBvReference(
            model, "satb_product_hb", "${PROJECT_HOME}/models/c.hbv"));
  }

  @Test
  void listAvailableSkipsAlreadyReferenced() {
    BusinessVaultModel source = new BusinessVaultModel();
    source.setFilename("${PROJECT_HOME}/models/bv-historize.hbv");
    BvBusinessTable table = new BvBusinessTable();
    table.setName("satb_product_hb");
    table.setTableName("satb_product_hb");
    source.getTables().add(table);

    BusinessVaultModel current = new BusinessVaultModel();
    current.setFilename("${PROJECT_HOME}/models/bv-harmonize.hbv");
    current
        .getBvReferences()
        .add(
            new BvBvTableReference(
                "satb_product_hb",
                BvTableType.BUSINESS_TABLE,
                "${PROJECT_HOME}/models/bv-historize.hbv"));

    List<String> available =
        BusinessVaultBvReferenceSupport.listAvailableBvTableNames(
            source, current, "${PROJECT_HOME}/models/bv-historize.hbv");
    assertTrue(available.isEmpty());
  }

  @Test
  void createReferenceCopiesPhysicalNameAndLocation() {
    BvBusinessTable table = new BvBusinessTable();
    table.setName("satb_product_hb");
    table.setTableName("satb_product_hb");
    table.setDescription("Historized product");

    BvBvTableReference ref =
        BusinessVaultBvReferenceSupport.createReference(
            table, new Point(10, 20), "${PROJECT_HOME}/models/bv-historize.hbv");

    assertEquals("satb_product_hb", ref.getBvTableName());
    assertEquals("satb_product_hb", ref.getPhysicalTableName());
    assertEquals(BvTableType.BUSINESS_TABLE, ref.getBvTableType());
    assertEquals("${PROJECT_HOME}/models/bv-historize.hbv", ref.getReferencedModelFilename());
    assertEquals(10, ref.getLocation().x);
    assertEquals(20, ref.getLocation().y);
  }

  @Test
  void ensureBvCanvasAliasesDoesNotDuplicate() {
    BusinessVaultModel model = new BusinessVaultModel();
    model.setFilename("${PROJECT_HOME}/models/bv-harmonize.hbv");
    BvBvTableReference existing =
        new BvBvTableReference(
            "satb_product_hb",
            BvTableType.BUSINESS_TABLE,
            "${PROJECT_HOME}/models/bv-historize.hbv");
    existing.setLocation(new Point(100, 100));
    model.getBvReferences().add(existing);

    BvSqlRef sqlRef = new BvSqlRef();
    sqlRef.setObjectName("satb_product_hb");
    sqlRef.setResolvedKind(BvSqlResolvedKind.BV_TABLE);
    sqlRef.setResolvedModelFilename("${PROJECT_HOME}/models/bv-historize.hbv");
    sqlRef.setResolvedTableName("satb_product_hb");

    BvSqlRefResolver.ensureBvCanvasAliases(model, List.of(sqlRef));
    assertEquals(1, model.getBvReferences().size());
    assertEquals(100, model.getBvReferences().get(0).getLocation().x);
  }

  @Test
  void ensureBvCanvasAliasesCreatesExternalAlias() {
    BusinessVaultModel model = new BusinessVaultModel();
    model.setFilename("${PROJECT_HOME}/models/bv-harmonize.hbv");

    BvSqlRef sqlRef = new BvSqlRef();
    sqlRef.setObjectName("satb_product_hb");
    sqlRef.setResolvedKind(BvSqlResolvedKind.BV_TABLE);
    sqlRef.setResolvedModelFilename("${PROJECT_HOME}/models/bv-historize.hbv");
    sqlRef.setResolvedTableName("satb_product_hb");

    BvSqlRefResolver.ensureBvCanvasAliases(model, List.of(sqlRef));
    assertEquals(1, model.getBvReferences().size());
    BvBvTableReference alias = model.getBvReferences().get(0);
    assertEquals("satb_product_hb", alias.getBvTableName());
    assertEquals("${PROJECT_HOME}/models/bv-historize.hbv", alias.getReferencedModelFilename());
  }

  @Test
  void ensureBvCanvasAliasesSkipsSameModel() {
    BusinessVaultModel model = new BusinessVaultModel();
    model.setFilename("${PROJECT_HOME}/models/bv-harmonize.hbv");

    BvSqlRef sqlRef = new BvSqlRef();
    sqlRef.setObjectName("local_table");
    sqlRef.setResolvedKind(BvSqlResolvedKind.BV_TABLE);
    sqlRef.setResolvedModelFilename("${PROJECT_HOME}/models/bv-harmonize.hbv");

    BvSqlRefResolver.ensureBvCanvasAliases(model, List.of(sqlRef));
    assertTrue(model.getBvReferences().isEmpty());
  }

  @Test
  void listBvModelSourcesAlwaysIncludesBrowse() {
    BusinessVaultModel model = new BusinessVaultModel();
    model.setName("current-bv");
    List<BusinessVaultBvReferenceSupport.BvModelSource> sources =
        BusinessVaultBvReferenceSupport.listBvModelSources(model, null, null);
    assertEquals(1, sources.size());
    assertTrue(sources.get(0).browseFile());
  }
}
