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
package org.hopper.edw.datavault.hopgui.file.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.metadata.DvIntegrationMode;
import org.apache.hop.i18n.BaseMessages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvCustomPipelinesTabI18nTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void customPipelinesModeEnablesTabControls() {
    assertTrue(
        DvCustomPipelinesTabSupport.isCustomPipelinesIntegrationMode(
            DvIntegrationMode.CUSTOM_PIPELINES.getDescription()));
    assertFalse(
        DvCustomPipelinesTabSupport.isCustomPipelinesIntegrationMode(
            DvIntegrationMode.HOP_MANAGED.getDescription()));
    assertFalse(
        DvCustomPipelinesTabSupport.isCustomPipelinesIntegrationMode(
            DvIntegrationMode.EXTERNAL_READ.getDescription()));
  }

  @Test
  void customPipelinesTabMessagesResolve() {
    assertResolved(
        DvCustomPipelinesTabSupport.class, "DvCustomPipelinesTab.Tab.Label", "Custom pipelines");
    assertResolved(
        DvCustomPipelinesTabSupport.class,
        "DvCustomPipelinesTab.SelectPipelines.Button",
        "Select pipelines");
    assertResolved(
        DvCustomPipelinesTabSupport.class,
        "DvCustomPipelinesTab.OpenSelected.Button",
        "Open selected");
  }

  private static void assertResolved(Class<?> pkg, String key, String expected) {
    String label = BaseMessages.getString(pkg, key);
    assertEquals(expected, label);
    assertFalse(label.startsWith("!"));
    assertFalse(label.endsWith("!"));
  }
}
