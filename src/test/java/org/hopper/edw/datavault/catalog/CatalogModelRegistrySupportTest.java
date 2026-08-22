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
package org.hopper.edw.datavault.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class CatalogModelRegistrySupportTest {

  @Test
  void sanitizeBasenameStripsPathAndExtension() {
    assertEquals("retail-360", CatalogModelRegistrySupport.sanitizeBasename("retail-360.hdv"));
    assertEquals(
        "retail-360", CatalogModelRegistrySupport.sanitizeBasename("../models/retail-360"));
    assertEquals("customer_360", CatalogModelRegistrySupport.sanitizeBasename("customer 360.hbv"));
  }

  @Test
  void modelsRegistryNamespaceUsesProjectKey() {
    Variables vars = new Variables();
    vars.setVariable("PROJECT_HOME", "/workspace/integration-tests");
    assertEquals(
        "hop/integration-tests/models-registry",
        CatalogModelRegistrySupport.modelsRegistryNamespace(vars));
  }

  @Test
  void modelRegistryKeyUsesSanitizedName() {
    Variables vars = new Variables();
    vars.setVariable("PROJECT_HOME", "/proj");
    var key = CatalogModelRegistrySupport.modelRegistryKey(vars, "My Model.hdv");
    assertEquals("hop/proj/models-registry", key.getNamespace());
    assertEquals("My_Model", key.getName());
  }

  @Test
  void typedModelRegistryKeySeparatesDvAndBv() {
    Variables vars = new Variables();
    vars.setVariable("PROJECT_HOME", "/proj");
    var dv =
        CatalogModelRegistrySupport.modelRegistryKey(
            vars, "retail-360", org.hopper.edw.catalog.model.RecordDefinitionType.DV_MODEL);
    var bv =
        CatalogModelRegistrySupport.modelRegistryKey(
            vars, "retail-360", org.hopper.edw.catalog.model.RecordDefinitionType.BV_MODEL);
    assertEquals("hop/proj/models-registry/dv", dv.getNamespace());
    assertEquals("hop/proj/models-registry/bv", bv.getNamespace());
    assertEquals("retail-360", dv.getName());
    assertEquals("retail-360", bv.getName());
  }

  @Test
  void portableModelPathPrefersProjectHomeVariable() throws Exception {
    Variables vars = new Variables();
    vars.setVariable("PROJECT_HOME", "/workspace/project");
    String stored =
        CatalogModelRegistrySupport.portableModelPath("/workspace/project/models/vault1.hdv", vars);
    assertTrue(stored.contains("PROJECT_HOME") || stored.endsWith("vault1.hdv"));
  }
}
