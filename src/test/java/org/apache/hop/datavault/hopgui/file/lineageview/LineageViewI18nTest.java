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
package org.apache.hop.datavault.hopgui.file.lineageview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.hopgui.lineageview.LineageViewGuiPlugin;
import org.apache.hop.i18n.BaseMessages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LineageViewI18nTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void fileTypeAndDialogMessagesResolve() {
    assertEquals(
        "Hop Lineage View",
        BaseMessages.getString(
            HopGuiLineageViewGraph.class, "HopLineageViewFileType.GuiAction.New.Name"));
    assertFalse(
        BaseMessages.getString(HopGuiLineageViewGraph.class, "LineageViewSettingsDialog.New.Title")
            .startsWith("!"));
    assertEquals(
        "Refresh lineage",
        BaseMessages.getString(
            HopGuiLineageViewGraph.class, "HopGuiLineageViewGraph.Toolbar.Refresh"));
    assertEquals(
        "Structure: demo",
        BaseMessages.getString(
            HopGuiLineageViewGraph.class, "HopGuiLineageViewGraph.Status.Ready", "demo"));
    assertEquals(
        "Open model",
        BaseMessages.getString(
            HopGuiLineageViewGraph.class, "HopGuiLineageViewGraph.Context.OpenModel.Name"));
    assertEquals(
        "Show update pipeline",
        BaseMessages.getString(
            HopGuiLineageViewGraph.class,
            "HopGuiLineageViewGraph.Context.ShowUpdatePipeline.Name"));
    assertEquals(
        "Show lineage",
        BaseMessages.getString(LineageViewGuiPlugin.class, "LineageViewGuiPlugin.Action.Name"));
    assertEquals(
        "View as HTML",
        BaseMessages.getString(
            HopGuiLineageViewGraph.class, "HopGuiLineageViewGraph.Details.ViewHtml"));
  }
}
