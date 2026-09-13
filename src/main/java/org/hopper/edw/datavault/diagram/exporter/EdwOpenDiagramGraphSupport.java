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
package org.hopper.edw.datavault.diagram.exporter;

import java.util.List;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.IHopPerspective;
import org.apache.hop.ui.hopgui.perspective.TabItemHandler;
import org.hopper.edw.datavault.hopgui.file.executionmap.HopGuiExecutionMapGraph;
import org.hopper.edw.datavault.hopgui.file.lineageview.HopGuiLineageViewGraph;
import org.hopper.edw.datavault.lineageview.HopLineageViewDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;

/** Finds an open EDW graph whose subject is the given artifact (identity). */
public final class EdwOpenDiagramGraphSupport {

  private EdwOpenDiagramGraphSupport() {}

  public static HopGuiLineageViewGraph findLineageView(HopLineageViewDocument document) {
    IHopFileTypeHandler handler = findHandler(document);
    return handler instanceof HopGuiLineageViewGraph graph ? graph : null;
  }

  public static HopGuiExecutionMapGraph findExecutionMap(ExecutionMapDocument document) {
    IHopFileTypeHandler handler = findHandler(document);
    return handler instanceof HopGuiExecutionMapGraph graph ? graph : null;
  }

  private static IHopFileTypeHandler findHandler(Object artifact) {
    HopGui hopGui;
    try {
      hopGui = HopGui.peekInstance();
    } catch (Throwable ignored) {
      return null;
    }
    if (hopGui == null || artifact == null) {
      return null;
    }
    IHopFileTypeHandler active = hopGui.getActiveFileTypeHandler();
    if (active != null && active.getSubject() == artifact) {
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
        if (item == null || item.getTypeHandler() == null) {
          continue;
        }
        if (item.getTypeHandler().getSubject() == artifact) {
          return item.getTypeHandler();
        }
      }
    }
    return null;
  }
}
