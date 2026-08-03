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
package org.apache.hop.datavault.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class DvCatalogNamespacesTest {

  @Test
  void projectSourcesNamespaceUsesProjectHomeFolderName() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/integration-tests");

    assertEquals(
        "hop/integration-tests/sources", DvCatalogNamespaces.projectSourcesNamespace(variables));
  }

  @Test
  void projectSourcesNamespaceDefaultsWhenProjectHomeUnset() {
    assertEquals(
        "hop/project/sources", DvCatalogNamespaces.projectSourcesNamespace(new Variables()));
  }
}
