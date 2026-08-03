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
package org.apache.hop.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmDimensionalDdlTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    PluginRegistry.init();
  }

  @Test
  void type2VersionFieldMapsToIntegerInPostgresqlDdl() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("d_order");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("order_id"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    IValueMeta versionMeta =
        DmLayoutSupport.buildDimensionTargetTableLayout(dimension, config, new Variables())
            .searchValueMeta("version");

    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setDatabaseType("POSTGRESQL");
    String fieldDefinition =
        databaseMeta.getFieldDefinition(versionMeta, null, null, false, true, false);

    assertTrue(
        fieldDefinition.toUpperCase().contains("INTEGER"),
        "Expected INTEGER DDL for version field but got: " + fieldDefinition);
    assertFalse(
        fieldDefinition.toUpperCase().contains("DOUBLE"),
        "Version field must not map to DOUBLE PRECISION: " + fieldDefinition);
  }
}
