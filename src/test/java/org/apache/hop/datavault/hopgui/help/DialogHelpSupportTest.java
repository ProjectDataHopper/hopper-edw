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
package org.apache.hop.datavault.hopgui.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DialogHelpSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void loadMarkdownReadsClasspathResource() throws HopException {
    String markdown = DialogHelpSupport.loadMarkdown("test-topic");
    assertTrue(markdown.contains("# Test topic"));
    assertTrue(markdown.contains("fixture"));
  }

  @Test
  void loadMarkdownThrowsForMissingTopic() {
    HopException ex =
        assertThrows(HopException.class, () -> DialogHelpSupport.loadMarkdown("missing-topic-xyz"));
    assertTrue(ex.getMessage().contains("missing-topic-xyz"));
  }

  @Test
  void hubHelpTopicIsRegistered() throws HopException {
    String markdown = DialogHelpSupport.loadMarkdown(HelpTopics.DV_HUB);
    assertFalse(markdown.startsWith("<!--"));
    assertTrue(markdown.contains("Hub editor"));
    MarkdownStyleRenderer.RenderedMarkdown rendered = MarkdownStyleRenderer.render(markdown);
    assertTrue(rendered.displayText().contains("Hub editor"));
    assertEquals("HelpTopics.DvHubDialog.Title", HelpTopics.titleKey(HelpTopics.DV_HUB));
  }
}
