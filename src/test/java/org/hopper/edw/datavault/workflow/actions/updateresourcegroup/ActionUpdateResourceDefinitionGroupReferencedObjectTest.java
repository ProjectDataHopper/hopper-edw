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
package org.hopper.edw.datavault.workflow.actions.updateresourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.xp.RegisterResourceDefinitionGroupMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionUpdateResourceDefinitionGroupReferencedObjectTest {

  private MemoryMetadataProvider metadata;
  private Variables variables;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    new RegisterResourceDefinitionGroupMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUp() throws Exception {
    metadata = new MemoryMetadataProvider();
    variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/home/user/retail-example");

    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("retail-sources");
    group.getDataVaultModelFiles().add("${PROJECT_HOME}/models/retail-360.hdv");
    group.getBusinessVaultModelFiles().add("${PROJECT_HOME}/models/retail-360.hbv");
    group.getBusinessVaultModelFiles().add("${PROJECT_HOME}/models/retail-sql.hbv");
    group.getDimensionalModelFiles().add("${PROJECT_HOME}/models/retail-f-orders.hdm");
    metadata.getSerializer(ResourceDefinitionGroupMeta.class).save(group);
  }

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

  @Test
  void loadReferencedObjectReturnsAllGroupModelsInLayerOrder() throws Exception {
    ActionUpdateResourceDefinitionGroup action = new ActionUpdateResourceDefinitionGroup();
    action.setResourceDefinitionGroup("retail-sources");
    action.setMetadataProvider(metadata);

    List<String> paths = loadAllReferencedPaths(action);
    assertEquals(
        List.of(
            "${PROJECT_HOME}/models/retail-360.hdv",
            "${PROJECT_HOME}/models/retail-360.hbv",
            "${PROJECT_HOME}/models/retail-sql.hbv",
            "${PROJECT_HOME}/models/retail-f-orders.hdm"),
        paths);
  }

  @Test
  void loadReferencedObjectHonorsIncludeLayerFlags() throws Exception {
    ActionUpdateResourceDefinitionGroup action = new ActionUpdateResourceDefinitionGroup();
    action.setResourceDefinitionGroup("retail-sources");
    action.setIncludeBusinessVault(false);
    action.setMetadataProvider(metadata);

    List<String> paths = loadAllReferencedPaths(action);
    assertEquals(
        List.of(
            "${PROJECT_HOME}/models/retail-360.hdv", "${PROJECT_HOME}/models/retail-f-orders.hdm"),
        paths);
    assertTrue(paths.stream().noneMatch(p -> p.endsWith(".hbv")));
  }

  @Test
  void getReferencedObjectDescriptionsUsesActionMetadataProvider() {
    ActionUpdateResourceDefinitionGroup action = new ActionUpdateResourceDefinitionGroup();
    action.setResourceDefinitionGroup("retail-sources");
    action.setMetadataProvider(metadata);

    String[] descriptions = action.getReferencedObjectDescriptions();
    assertEquals(4, descriptions.length);
    assertTrue(descriptions[0].contains("retail-360.hdv"));
    assertTrue(descriptions[1].contains("retail-360.hbv"));
    assertTrue(action.isReferencedObjectEnabled()[0]);
    assertFalse(action.isReferencedObjectEnabled().length == 0);
  }

  private List<String> loadAllReferencedPaths(ActionUpdateResourceDefinitionGroup action)
      throws Exception {
    List<String> paths = new ArrayList<>();
    for (int i = 0; i < 32; i++) {
      IHasFilename loaded = action.loadReferencedObject(i, metadata, variables);
      if (loaded == null || loaded.getFilename() == null || loaded.getFilename().isEmpty()) {
        break;
      }
      // Out-of-range falls through to super and typically returns null; stop when path repeats
      // unexpected types.
      String path = loaded.getFilename();
      if (!path.contains("models/")
          && !path.endsWith(".hdv")
          && !path.endsWith(".hbv")
          && !path.endsWith(".hdm")) {
        break;
      }
      paths.add(path);
    }
    return paths;
  }
}
