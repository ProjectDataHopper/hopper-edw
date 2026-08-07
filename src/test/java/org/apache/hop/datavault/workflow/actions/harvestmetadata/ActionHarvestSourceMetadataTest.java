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
package org.apache.hop.datavault.workflow.actions.harvestmetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.catalog.harvest.SchemaHarvestModels.BaselineMode;
import org.junit.jupiter.api.Test;

class ActionHarvestSourceMetadataTest {

  @Test
  void defaults() {
    ActionHarvestSourceMetadata action = new ActionHarvestSourceMetadata();
    assertEquals(BaselineMode.WORKING_CATALOG.name(), action.getBaselineMode());
    assertTrue(action.isPersistHistory());
    assertTrue(action.isAutoCreateTables());
    assertTrue(action.isFailOnPersistError());
    assertTrue(action.isEvaluation());
  }

  @Test
  void cloneCopiesFields() {
    ActionHarvestSourceMetadata original = new ActionHarvestSourceMetadata("harvest");
    original.setResourceDefinitionGroup("retail-sources");
    original.setCatalogConnection("local-catalog");
    original.setHistoryDatabase("OPS");
    original.setBaselineMode(BaselineMode.CATALOG_VERSION.name());
    original.setBaselineVersionTag("v1.0.0");
    original.setFailOnDiscoveryErrors(true);

    ActionHarvestSourceMetadata copy = (ActionHarvestSourceMetadata) original.clone();
    assertEquals("retail-sources", copy.getResourceDefinitionGroup());
    assertEquals("local-catalog", copy.getCatalogConnection());
    assertEquals("OPS", copy.getHistoryDatabase());
    assertEquals(BaselineMode.CATALOG_VERSION.name(), copy.getBaselineMode());
    assertEquals("v1.0.0", copy.getBaselineVersionTag());
    assertTrue(copy.isFailOnDiscoveryErrors());
  }

  @Test
  void baselineModeOptions() {
    ActionHarvestSourceMetadata action = new ActionHarvestSourceMetadata();
    // Hop GUI requires (ILogChannel, IHopMetadataProvider) signature.
    var options = action.getBaselineModeOptions(null, null);
    assertEquals(2, options.size());
    assertEquals(BaselineMode.WORKING_CATALOG.name(), options.get(0));
  }
}
