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
package org.apache.hop.datavault.hopgui.file.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.hopgui.file.dimensional.HopGuiDmTableDialog;
import org.apache.hop.i18n.BaseMessages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoadFromSourceI18nTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void vaultDialogLoadFromSourceMessagesResolve() {
    assertResolvedLabel(
        DvHubDialog.class, "DvHubDialog.GetKeys.Button", "Import keys from sources");
    assertResolvedLabel(
        DvHubDialog.class,
        "DvHubDialog.GetKeys.ToolTip",
        "Import business keys from record sources not yet mapped on the Keys tab");
    assertResolvedLabel(
        DvSatelliteDialog.class, "DvSatelliteDialog.GetAttributes.Button", "Load from source");
    assertResolvedLabel(
        DvSatelliteDialog.class,
        "DvSatelliteDialog.GetAttributes.ToolTip",
        "Select source fields to add as satellite attributes");
  }

  @Test
  void dimensionalDialogLoadFromSourceMessagesResolve() {
    assertResolvedLabel(
        HopGuiDmTableDialog.class,
        "HopGuiDmTableDialog.NaturalKeys.GetKeys.Label",
        "&Load from source");
    assertResolvedLabel(
        HopGuiDmTableDialog.class,
        "HopGuiDmTableDialog.NaturalKeys.GetKeys.ToolTip",
        "Load field names from the source into the natural keys table");
    assertResolvedLabel(
        HopGuiDmTableDialog.class,
        "HopGuiDmTableDialog.Attributes.GetAttributes.Label",
        "&Load from source");
    assertResolvedLabel(
        HopGuiDmTableDialog.class,
        "HopGuiDmTableDialog.Attributes.GetAttributes.ToolTip",
        "Load source fields not already used as natural keys into the attributes table");
  }

  private static void assertResolvedLabel(Class<?> pkg, String key, String expected) {
    String label = BaseMessages.getString(pkg, key);
    assertEquals(expected, label);
    assertFalse(label.startsWith("!"));
    assertFalse(label.endsWith("!"));
  }
}
