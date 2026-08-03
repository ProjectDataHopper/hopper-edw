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
package org.apache.hop.datavault.workflow.actions.updateresourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class ActionUpdateResourceDefinitionGroupReferencedObjectTest {

  @Test
  void toDisplayRelativePathStripsProjectHomeToken() {
    Variables vars = new Variables();
    vars.setVariable("PROJECT_HOME", "/home/user/retail-example");

    assertEquals(
        "models/retail-360.hdv",
        ActionUpdateResourceDefinitionGroup.toDisplayRelativePath(
            "${PROJECT_HOME}/models/retail-360.hdv", vars));
    assertEquals(
        "models/retail-360.hbv",
        ActionUpdateResourceDefinitionGroup.toDisplayRelativePath(
            "${PROJECT_HOME}/models/retail-360.hbv", vars));
    assertEquals(
        ".", ActionUpdateResourceDefinitionGroup.toDisplayRelativePath("${PROJECT_HOME}", vars));
  }

  @Test
  void toDisplayRelativePathRelativizesAbsolutePathsUnderProjectHome() {
    Variables vars = new Variables();
    vars.setVariable("PROJECT_HOME", "/home/user/retail-example");

    assertEquals(
        "models/retail-360.hdv",
        ActionUpdateResourceDefinitionGroup.toDisplayRelativePath(
            "/home/user/retail-example/models/retail-360.hdv", vars));
  }

  @Test
  void toDisplayRelativePathKeepsAlreadyRelativePaths() {
    Variables vars = new Variables();
    vars.setVariable("PROJECT_HOME", "/home/user/retail-example");

    assertEquals(
        "models/foo.hdv",
        ActionUpdateResourceDefinitionGroup.toDisplayRelativePath("models/foo.hdv", vars));
  }

  @Test
  void toDisplayRelativePathNormalizesBackslashes() {
    Variables vars = new Variables();
    assertEquals(
        "models/foo.hdv",
        ActionUpdateResourceDefinitionGroup.toDisplayRelativePath(
            "${PROJECT_HOME}\\models\\foo.hdv", vars));
  }
}
