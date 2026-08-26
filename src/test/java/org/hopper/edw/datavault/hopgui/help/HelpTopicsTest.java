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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.hopgui.help.HelpTopics.HelpPage;
import org.junit.jupiter.api.Test;

class HelpTopicsTest {

  @Test
  void everyTopicMapsToAnExistingAdocPage() {
    Path docs = Path.of("docs");
    assertTrue(Files.isDirectory(docs), "tests run from the module directory");
    Set<String> ids = new HashSet<>();
    for (HelpPage page : HelpTopics.pages()) {
      assertTrue(ids.add(page.topicId()), "duplicate topic id " + page.topicId());
      assertNotNull(page.htmlPage());
      assertTrue(page.htmlPage().endsWith(".html"), page.topicId());
      Path adoc = docs.resolve(page.adocRelative());
      assertTrue(Files.isRegularFile(adoc), "missing AsciiDoc for " + page.topicId() + ": " + adoc);
    }
    assertFalse(ids.isEmpty());
  }

  @Test
  void hubTopicPointsAtDialogHelpPage() {
    HelpPage page = HelpTopics.page(HelpTopics.DV_HUB);
    assertEquals("help/dv-hub-dialog.html", page.htmlPage());
    assertEquals("HelpTopics.DvHubDialog.Title", HelpTopics.titleKey(HelpTopics.DV_HUB));
  }

  @Test
  void unknownTopicThrows() {
    assertNull(HelpTopics.page("missing-topic-xyz"));
    HopException ex =
        assertThrows(HopException.class, () -> HelpTopics.requirePage("missing-topic-xyz"));
    assertTrue(ex.getMessage().contains("missing-topic-xyz"));
  }
}
