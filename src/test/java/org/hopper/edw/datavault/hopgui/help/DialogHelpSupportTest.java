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
package org.hopper.edw.datavault.hopgui.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.Test;

class DialogHelpSupportTest {

  @Test
  void requirePageRejectsUnknownTopic() {
    HopException ex =
        assertThrows(HopException.class, () -> HelpTopics.requirePage("missing-topic-xyz"));
    assertTrue(ex.getMessage().contains("missing-topic-xyz"));
  }

  @Test
  void titleKeyFallsBackForUnknownTopic() {
    assertEquals("HelpTopics.Default.Title", HelpTopics.titleKey("not-a-topic"));
    assertEquals("HelpTopics.DvHubDialog.Title", HelpTopics.titleKey(HelpTopics.DV_HUB));
  }
}
