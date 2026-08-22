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

import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.junit.jupiter.api.Test;

class DmCatalogNamespacesTest {

  @Test
  void projectDimensionalModelsNamespaceUsesProjectHomeAndModelName() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/integration-tests");

    DimensionalModel model = new DimensionalModel();
    model.setName("basic-star");

    assertEquals(
        "hop/integration-tests/dimensional/basic-star",
        DmCatalogNamespaces.projectDimensionalModelsNamespace(variables, model));
  }

  @Test
  void resolveModelBasenameUsesFilenameStemWhenNameMissing() {
    DimensionalModel model = new DimensionalModel();
    model.setFilename("${PROJECT_HOME}/models/retail-f-orders.hdm");

    assertEquals("retail-f-orders", DmCatalogNamespaces.resolveModelBasename(model));
  }

  @Test
  void projectDimensionalModelsNamespaceDefaultsWhenProjectHomeUnset() {
    DimensionalModel model = new DimensionalModel();
    model.setName("dm-model");

    assertEquals(
        "hop/project/dimensional/dm-model",
        DmCatalogNamespaces.projectDimensionalModelsNamespace(new Variables(), model));
  }
}
