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
package org.apache.hop.catalog.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceDefinitionGroupModelDiscoverySupportTest {

  @Test
  void findProjectModelFilesReturnsProjectRelativePaths(@TempDir Path projectHome)
      throws Exception {
    Path modelsDir = projectHome.resolve("models");
    Files.createDirectories(modelsDir);
    Path dvModel = modelsDir.resolve("retail-360.hdv");
    Path bvModel = modelsDir.resolve("customer-360.hbv");
    Path dmModel = modelsDir.resolve("retail-f-orders.hdm");
    Files.writeString(dvModel, "<model/>");
    Files.writeString(bvModel, "<model/>");
    Files.writeString(dmModel, "<model/>");
    Files.writeString(modelsDir.resolve("readme.txt"), "ignore");

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", projectHome.toString());

    var dvFiles =
        ResourceDefinitionGroupModelDiscoverySupport.findProjectModelFiles(variables, ".hdv");
    assertEquals(1, dvFiles.size());
    assertEquals("${PROJECT_HOME}/models/retail-360.hdv", dvFiles.get(0));

    var bvFiles =
        ResourceDefinitionGroupModelDiscoverySupport.findProjectModelFiles(variables, "hbv");
    assertEquals(1, bvFiles.size());
    assertEquals("${PROJECT_HOME}/models/customer-360.hbv", bvFiles.get(0));

    var dmFiles =
        ResourceDefinitionGroupModelDiscoverySupport.findProjectModelFiles(
            variables, HopDimensionalFileType.DIMENSIONAL_FILE_EXTENSION);
    assertEquals(1, dmFiles.size());
    assertTrue(dmFiles.get(0).startsWith("${PROJECT_HOME}/"));
  }

  @Test
  void findProjectModelFilesReturnsEmptyWhenProjectHomeUnresolved() {
    Variables variables = new Variables();
    assertTrue(
        ResourceDefinitionGroupModelDiscoverySupport.findProjectModelFiles(variables, ".hdv")
            .isEmpty());
  }

  @Test
  void toProjectRelativePathReturnsNullForPathsOutsideProjectHome(@TempDir Path projectHome) {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", projectHome.toString());

    String relative =
        ResourceDefinitionGroupModelDiscoverySupport.toProjectRelativePath(
            "/tmp/other/model.hdv", variables);

    assertEquals(null, relative);
  }
}
