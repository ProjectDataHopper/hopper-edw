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

import java.util.Set;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelId;

/** Turns a parsed {@link EdwJourneyLink} into one navigator call. Does not open Hop GUI itself. */
public final class EdwJourneyLinkExecutor {

  public static final Set<String> ACTIONS =
      Set.of(
          "harvest-history",
          "validate-sources",
          "list-versions",
          "browse-lineage",
          "edit-group",
          "create-group",
          "create-source-model",
          "generate-dv",
          "create-ops-analytics",
          "open-docs");

  private EdwJourneyLinkExecutor() {}

  public static void execute(
      EdwJourneyLink link, EdwJourneySnapshot snapshot, EdwJourneyNavigator navigator) {
    if (navigator == null) {
      return;
    }
    if (link == null) {
      navigator.fail("This row has no link.");
      return;
    }
    switch (link.kind()) {
      case PANEL -> {
        if (EdwJourneyPanelId.fromWire(link.panelId()) == null) {
          navigator.fail("Unknown panel " + link.panelId());
          return;
        }
        navigator.showPanel(link.panelId());
      }
      case ACT -> {
        if (link.action() == null || !ACTIONS.contains(link.action())) {
          navigator.fail("Unknown action " + link.action());
          return;
        }
        navigator.runAction(link.action());
      }
      case OPEN -> open(link, snapshot, navigator);
    }
  }

  private static void open(
      EdwJourneyLink link, EdwJourneySnapshot snapshot, EdwJourneyNavigator navigator) {
    String layer = link.layer() == null ? "" : link.layer();
    String file = blank(link.file()) ? null : link.file();
    String element = link.element();
    if (blank(file) && blank(layer) && blank(element)) {
      navigator.fail("No model file for this bar");
      return;
    }
    if (file == null && !"catalog".equals(layer) && !"workflow".equals(layer)) {
      file = EdwJourneyModelLookup.file(snapshot, layer, element);
    }
    boolean needsFile =
        switch (layer) {
          case "hsm", "dv", "bv", "dm", "workflow", "hsl", "hlv" -> true;
          default -> blank(layer) && !blank(element);
        };
    if (needsFile && blank(file)) {
      EdwJourneyModelLookup.LocateResult found =
          EdwJourneyModelLookup.locate(snapshot, layer, element);
      if (found.matches() > 1) {
        navigator.fail("Several models are named " + element);
        return;
      }
      if (found.located() == null) {
        navigator.fail("No model file for " + (blank(layer) ? "" : layer + " ") + element);
        return;
      }
      layer = found.located().layer();
      file = found.located().file();
      if (found.located().model() == null
          || found.located().model().tableNames() == null
          || !found.located().model().tableNames().contains(element)) {
        element = "";
      }
    }
    if (needsFile && blank(file)) {
      navigator.fail("No model file for " + layer + " " + element);
      return;
    }
    navigator.openLayer(layer, file, element, link.verb() == null ? "select" : link.verb());
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  /** Side effects the Journey host performs. Tests use a fake. */
  public interface EdwJourneyNavigator {
    void openLayer(String layer, String file, String element, String verb);

    void runAction(String action);

    void showPanel(String panelId);

    void fail(String message);
  }
}
