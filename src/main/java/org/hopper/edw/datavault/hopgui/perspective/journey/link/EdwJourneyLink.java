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
package org.hopper.edw.datavault.hopgui.perspective.journey.link;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelId;

/**
 * Application link carried by a presentation {@code OPEN_LINK}. The scheme is {@code hop-edw}.
 *
 * <p>Examples: {@code
 * hop-edw://open?layer=dv&file=models/vault.hdv&element=hub_customer&verb=select}, {@code
 * hop-edw://act?name=validate-sources}, {@code hop-edw://panel?id=GATE_DESK}.
 */
public record EdwJourneyLink(
    Kind kind,
    String layer,
    String file,
    String element,
    String verb,
    String action,
    String panelId) {

  public static final String SCHEME = "hop-edw://";

  public enum Kind {
    OPEN,
    ACT,
    PANEL
  }

  public static String open(String layer, String file, String element, String verb) {
    return SCHEME
        + "open?layer="
        + enc(layer)
        + "&file="
        + enc(file)
        + "&element="
        + enc(element)
        + "&verb="
        + enc(verb == null || verb.isBlank() ? "select" : verb);
  }

  public static String action(String name) {
    return SCHEME + "act?name=" + enc(name);
  }

  public static String panel(EdwJourneyPanelId id) {
    return SCHEME + "panel?id=" + enc(id == null ? "" : id.name());
  }

  public static EdwJourneyLink parse(String url) {
    if (url == null || url.isBlank() || !url.startsWith(SCHEME)) {
      return null;
    }
    String rest = url.substring(SCHEME.length());
    int query = rest.indexOf('?');
    String command = query < 0 ? rest : rest.substring(0, query);
    Map<String, String> params = query < 0 ? Map.of() : query(rest.substring(query + 1));
    return switch (command) {
      case "open" ->
          new EdwJourneyLink(
              Kind.OPEN,
              params.get("layer"),
              params.get("file"),
              params.get("element"),
              blank(params.get("verb")) ? "select" : params.get("verb"),
              null,
              null);
      case "act" -> new EdwJourneyLink(Kind.ACT, null, null, null, null, params.get("name"), null);
      case "panel" ->
          new EdwJourneyLink(Kind.PANEL, null, null, null, null, null, params.get("id"));
      default -> null;
    };
  }

  private static Map<String, String> query(String raw) {
    Map<String, String> params = new LinkedHashMap<>();
    if (raw == null || raw.isEmpty()) {
      return params;
    }
    for (String part : raw.split("&")) {
      int eq = part.indexOf('=');
      String key = eq < 0 ? part : part.substring(0, eq);
      String value = eq < 0 ? "" : part.substring(eq + 1);
      params.put(dec(key), dec(value));
    }
    return params;
  }

  private static String enc(String value) {
    try {
      return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      return "";
    }
  }

  private static String dec(String value) {
    try {
      return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      return value;
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
