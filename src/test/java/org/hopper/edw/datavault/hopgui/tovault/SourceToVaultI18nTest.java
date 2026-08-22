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
package org.hopper.edw.datavault.hopgui.tovault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.hopgui.file.sourcemodel.HopGuiSourceModelGraph;
import org.hopper.edw.datavault.hopgui.file.vault.HopGuiVaultGraph;
import org.apache.hop.i18n.BaseMessages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SourceToVaultI18nTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void reviewAndToolbarMessagesResolve() {
    assertResolved(
        SourceToVaultReviewDialog.class,
        "SourceToVaultReviewDialog.Shell.Title",
        "Generate Data Vault from source model");
    assertResolved(
        SourceToVaultReviewDialog.class, "SourceToVaultReviewDialog.Apply.Label", "Apply selected");
    assertResolved(
        SourceToVaultReviewDialog.class,
        "SourceToVaultReviewDialog.IncludeNonTableSources.Label",
        "Include source queries, JSON extractions, and pipelines");
    assertResolved(
        SourceToVaultReviewDialog.class,
        "SourceToVaultReviewDialog.CreateReferenceTables.Label",
        "Classify lookup tables as reference tables");
    assertResolved(
        SourceToVaultGenerationSupport.class,
        "SourceToVaultGenerationSupport.Success.Title",
        "Generate Data Vault");
    assertResolved(
        HopGuiSourceModelGraph.class,
        "HopGuiSourceModelGraph.Toolbar.GenerateVault.Tooltip",
        "Generate Data Vault hubs, links, satellites, and reference tables from selected source tables, queries, JSON extractions, and pipelines");
    assertResolved(
        HopGuiVaultGraph.class,
        "HopGuiVaultGraph.Toolbar.GenerateFromSource.Tooltip",
        "Generate hubs, links, and satellites from a source model");
  }

  private static void assertResolved(Class<?> pkg, String key, String expected) {
    String label = BaseMessages.getString(pkg, key);
    assertEquals(expected, label);
    assertFalse(label.startsWith("!"));
    assertFalse(label.endsWith("!"));
  }
}
