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
package org.hopper.edw.catalog.discovery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class HopVariableResolutionSupportTest {

  @Test
  void containsUnresolvedVariables_detectsPlaceholders() {
    assertTrue(HopVariableResolutionSupport.containsUnresolvedVariables("${ICEBERG_CATALOG_URI}"));
    assertFalse(HopVariableResolutionSupport.containsUnresolvedVariables("http://localhost:8181"));
  }

  @Test
  void requireResolved_rejectsUnresolvedPlaceholders() {
    Variables variables = new Variables();

    assertThrows(
        HopException.class,
        () ->
            HopVariableResolutionSupport.requireResolved(
                variables, "${ICEBERG_CATALOG_URI}", "Iceberg catalog URI"));
  }

  @Test
  void requireResolved_acceptsResolvedValues() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("ICEBERG_CATALOG_URI", "http://iceberg-rest:8181");

    HopVariableResolutionSupport.requireResolved(
        variables, "${ICEBERG_CATALOG_URI}", "Iceberg catalog URI");
  }
}
