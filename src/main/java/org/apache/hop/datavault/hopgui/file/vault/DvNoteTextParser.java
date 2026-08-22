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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.util.Utils;

/** Parses standard Markdown hyperlinks in Data Vault canvas note text. */
public final class DvNoteTextParser {

  private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]*)\\]\\(([^)]*)\\)");

  private DvNoteTextParser() {}

  /** A plain-text or hyperlink segment within a single line of note text. */
  public record Segment(boolean link, String label, String target) {

    public String displayText() {
      if (!link) {
        return label != null ? label : "";
      }
      if (!Utils.isEmpty(label)) {
        return label;
      }
      return target != null ? target : "";
    }
  }

  public static boolean isUrlTarget(String target) {
    if (Utils.isEmpty(target)) {
      return false;
    }
    String trimmed = target.trim();
    return trimmed.regionMatches(true, 0, "http", 0, 4);
  }

  public static List<Segment> parseLine(String line) {
    List<Segment> segments = new ArrayList<>();
    if (line == null) {
      return segments;
    }
    Matcher matcher = LINK_PATTERN.matcher(line);
    int lastEnd = 0;
    while (matcher.find()) {
      if (matcher.start() > lastEnd) {
        segments.add(new Segment(false, line.substring(lastEnd, matcher.start()), null));
      }
      segments.add(new Segment(true, matcher.group(1), matcher.group(2)));
      lastEnd = matcher.end();
    }
    if (lastEnd < line.length()) {
      segments.add(new Segment(false, line.substring(lastEnd), null));
    }
    return segments;
  }

  /** Text as drawn on the canvas (link labels replace Markdown syntax). */
  public static String displayLine(String line) {
    List<Segment> segments = parseLine(line);
    if (segments.isEmpty()) {
      return line != null ? line : "";
    }
    StringBuilder builder = new StringBuilder();
    for (Segment segment : segments) {
      builder.append(segment.displayText());
    }
    return builder.toString();
  }
}
