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
package org.hopper.edw.datavault.hopgui.ai;

import java.util.List;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.IHopPerspective;
import org.apache.hop.ui.hopgui.perspective.TabItemHandler;
import org.hopper.edw.datavault.hopgui.file.modelgraph.HopGuiModelGraphBase;

/** Finds the open EDW graph whose {@code getSubject()} is the advisor artifact (identity). */
public final class EdwAiAdvisorGraphSupport {

  private EdwAiAdvisorGraphSupport() {}

  public static HopGui hopGuiFrom(AiAdvisorRequest request) {
    if (request == null || request.getAttributes() == null) {
      return null;
    }
    Object value = request.getAttributes().get(AiAdvisorRequest.ATTR_HOP_GUI);
    return value instanceof HopGui hopGui ? hopGui : null;
  }

  public static void markUndoPoint(AiAdvisorRequest request) {
    HopGuiModelGraphBase graph =
        findGraph(hopGuiFrom(request), request != null ? request.getArtifact() : null);
    if (graph != null) {
      graph.markAiAdvisorUndoPoint();
    }
  }

  public static void afterApply(AiAdvisorRequest request) {
    HopGuiModelGraphBase graph =
        findGraph(hopGuiFrom(request), request != null ? request.getArtifact() : null);
    if (graph != null) {
      graph.afterAiAdvisorApply();
    }
  }

  public static HopGuiModelGraphBase findGraph(HopGui hopGui, Object artifact) {
    if (hopGui == null || artifact == null) {
      return null;
    }
    HopGuiModelGraphBase match = match(hopGui.getActiveFileTypeHandler(), artifact);
    if (match != null) {
      return match;
    }
    if (hopGui.getPerspectiveManager() == null) {
      return null;
    }
    for (IHopPerspective perspective : hopGui.getPerspectiveManager().getPerspectives()) {
      List<TabItemHandler> items = perspective.getItems();
      if (items == null) {
        continue;
      }
      for (TabItemHandler item : items) {
        if (item == null) {
          continue;
        }
        match = match(item.getTypeHandler(), artifact);
        if (match != null) {
          return match;
        }
      }
    }
    return null;
  }

  private static HopGuiModelGraphBase match(IHopFileTypeHandler handler, Object artifact) {
    if (handler instanceof HopGuiModelGraphBase graph && handler.getSubject() == artifact) {
      return graph;
    }
    return null;
  }
}
