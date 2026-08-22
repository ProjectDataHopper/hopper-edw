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
package org.hopper.edw.datavault.hopgui.file.executionmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.hopgui.executionmap.ExecutionMapGuiPlugin;
import org.apache.hop.i18n.BaseMessages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExecutionMapI18nTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void graphMessagesResolveFromFileExecutionMapPackage() {
    String label =
        BaseMessages.getString(
            HopGuiExecutionMapGraph.class, "HopGuiExecutionMapGraph.Context.OpenArtifact.Name");
    assertEquals("Open artifact", label);
    assertFalse(label.startsWith("!"));
    assertFalse(label.endsWith("!"));

    String catalogLabel =
        BaseMessages.getString(
            HopGuiExecutionMapGraph.class, "HopGuiExecutionMapGraph.Context.OpenInCatalog.Name");
    assertEquals("Open in Data Catalog", catalogLabel);
    assertFalse(catalogLabel.startsWith("!"));

    String previewLabel =
        BaseMessages.getString(
            HopGuiExecutionMapGraph.class, "HopGuiExecutionMapGraph.Context.PreviewDataset.Name");
    assertEquals("Preview data", previewLabel);
    assertFalse(previewLabel.startsWith("!"));

    String previewTooltip =
        BaseMessages.getString(
            HopGuiExecutionMapGraph.class, "ExecutionMapNavigationSupport.Tooltip.PreviewDataset");
    assertEquals("Right-click and choose Preview data", previewTooltip);
    assertFalse(previewTooltip.startsWith("!"));
  }

  @Test
  void generationPluginMessagesResolveFromExecutionMapPluginPackage() {
    String label =
        BaseMessages.getString(ExecutionMapGuiPlugin.class, "ExecutionMapGuiPlugin.Action.Name");
    assertEquals("Ad-hoc generate execution map", label);
  }
}
