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

import java.lang.reflect.Method;
import java.util.List;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.core.logging.LogChannel;
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
    invokeGraph(request, "markAiAdvisorUndoPoint");
  }

  public static void afterApply(AiAdvisorRequest request) {
    invokeGraph(request, "afterAiAdvisorApply");
  }

  /**
   * The modeler graph can be a different class-loader copy of {@link HopGuiModelGraphBase}. Match
   * the open file by identity and call the public method, reflecting when {@code instanceof} fails.
   */
  private static void invokeGraph(AiAdvisorRequest request, String methodName) {
    Object artifact = request == null ? null : request.getArtifact();
    IHopFileTypeHandler handler = findHandler(hopGui(request), artifact);
    if (handler instanceof HopGuiModelGraphBase graph) {
      if ("markAiAdvisorUndoPoint".equals(methodName)) {
        graph.markAiAdvisorUndoPoint();
      } else {
        graph.afterAiAdvisorApply();
      }
      return;
    }
    if (handler == null) {
      return;
    }
    try {
      Method method = handler.getClass().getMethod(methodName);
      method.invoke(handler);
    } catch (ReflectiveOperationException e) {
      LogChannel.UI.logError("Unable to refresh the modeler after AI advice", e);
    }
  }

  private static HopGui hopGui(AiAdvisorRequest request) {
    HopGui hopGui = hopGuiFrom(request);
    if (hopGui != null) {
      return hopGui;
    }
    try {
      return HopGui.peekInstance();
    } catch (Throwable e) {
      return null;
    }
  }

  public static HopGuiModelGraphBase findGraph(HopGui hopGui, Object artifact) {
    IHopFileTypeHandler handler = findHandler(hopGui, artifact);
    return handler instanceof HopGuiModelGraphBase graph ? graph : null;
  }

  private static IHopFileTypeHandler findHandler(HopGui hopGui, Object artifact) {
    if (hopGui == null || artifact == null) {
      return null;
    }
    IHopFileTypeHandler active = match(hopGui.getActiveFileTypeHandler(), artifact);
    if (active != null) {
      return active;
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
        IHopFileTypeHandler handler = match(item.getTypeHandler(), artifact);
        if (handler != null) {
          return handler;
        }
      }
    }
    return null;
  }

  private static IHopFileTypeHandler match(IHopFileTypeHandler handler, Object artifact) {
    if (handler != null && handler.getSubject() == artifact) {
      return handler;
    }
    return null;
  }
}
