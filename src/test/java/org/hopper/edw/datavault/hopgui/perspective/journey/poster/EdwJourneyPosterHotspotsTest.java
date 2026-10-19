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
package org.hopper.edw.datavault.hopgui.perspective.journey.poster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hopper.edw.datavault.hopgui.perspective.journey.poster.EdwJourneyPosterHotspots.Hotspot;
import org.junit.jupiter.api.Test;

class EdwJourneyPosterHotspotsTest {

  private static final Pattern ATTR = Pattern.compile("(?:^|[\\s])(x|y|width|height)=\"(-?\\d+)\"");

  @Test
  void hitRectanglesMatchTheExplorerSvg() throws Exception {
    String html = Files.readString(Path.of("docs/presentations/edw-component-explorer.html"));
    List<Hotspot> fromHtml = new ArrayList<>();
    Matcher blocks = Pattern.compile("<g class=\"comp\" ([\\s\\S]*?)</g>").matcher(html);
    while (blocks.find()) {
      String block = blocks.group(1);
      Matcher id = Pattern.compile("data-comp=\"([a-z0-9-]+)\"").matcher(block);
      assertTrue(id.find(), block);
      Matcher hits = Pattern.compile("<rect class=\"hit\"([^>]*)/?>").matcher(block);
      while (hits.find()) {
        fromHtml.add(rect(id.group(1), hits.group(1)));
      }
    }
    assertEquals(fromHtml, EdwJourneyPosterHotspots.hotspots());
    assertTrue(html.contains("data-comp=\"publish\""));
    assertTrue(EdwJourneyPosterHotspots.ids().contains("publish"));
  }

  @Test
  void laterRectangleWinsAndOutsideMisses() {
    assertEquals("gates", EdwJourneyPosterHotspots.hit(250, 520));
    assertEquals("landing", EdwJourneyPosterHotspots.hit(60, 200));
    assertNull(EdwJourneyPosterHotspots.hit(-1, 10));
    assertNull(EdwJourneyPosterHotspots.hit(10, 10));
  }

  private static Hotspot rect(String id, String attrs) {
    int x = 0;
    int y = 0;
    int width = 0;
    int height = 0;
    Matcher matcher = ATTR.matcher(attrs);
    while (matcher.find()) {
      int value = Integer.parseInt(matcher.group(2));
      switch (matcher.group(1)) {
        case "x" -> x = value;
        case "y" -> y = value;
        case "width" -> width = value;
        case "height" -> height = value;
        default -> {}
      }
    }
    return new Hotspot(id, x, y, width, height);
  }
}
