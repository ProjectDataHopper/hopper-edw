/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.hopgui.coaching;

import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.coaching.CoachingSourceRef;
import org.apache.hop.datavault.metadata.coaching.CoachingSourceResolver;
import org.apache.hop.datavault.metadata.coaching.ICoachingModelAdapter;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.shared.HopGuiAbstractGraph;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Canvas;

public final class CoachingCanvasDropSupport {

  private CoachingCanvasDropSupport() {}

  public static void register(
      Canvas canvas,
      HopGui hopGui,
      ICoachableModelGraph graph,
      HopGuiAbstractGraph graphView,
      IVariables variables) {
    DropTarget dropTarget = new DropTarget(canvas, DND.DROP_COPY | DND.DROP_MOVE);
    dropTarget.setTransfer(new Transfer[] {TextTransfer.getInstance()});
    dropTarget.addDropListener(
        new DropTargetAdapter() {
          @Override
          public void drop(DropTargetEvent event) {
            if (!(event.data instanceof String identityKey)) {
              return;
            }
            ICoachingModelAdapter adapter = graph.createCoachingModelAdapter();
            if (adapter == null) {
              return;
            }
            CoachingSourceRef sourceRef = findSource(adapter, identityKey, variables, hopGui);
            if (sourceRef == null) {
              return;
            }
            Point location =
                ModelGraphDropCoordinates.toModelLocation(canvas, graphView, event.x, event.y);
            CoachingTableTypeDialog typeDialog =
                new CoachingTableTypeDialog(hopGui.getShell(), adapter, sourceRef);
            if (!typeDialog.open()) {
              return;
            }
            String createdTableName =
                graph.createTableFromCoachSource(
                    sourceRef,
                    typeDialog.getSelectedTableType(),
                    typeDialog.getSelectedTableName(),
                    location);
            if (createdTableName != null) {
              graph.refreshCoachPanel();
              CoachingMappingDialogSupport.openMapDialogForTable(
                  hopGui, adapter, sourceRef, variables, createdTableName);
            }
          }
        });
  }

  private static CoachingSourceRef findSource(
      ICoachingModelAdapter adapter, String identityKey, IVariables variables, HopGui hopGui) {
    try {
      return CoachingSourceResolver.resolve(adapter, variables, hopGui.getMetadataProvider())
          .stream()
          .map(node -> node.getSourceRef())
          .filter(ref -> ref != null && identityKey.equals(ref.identityKey()))
          .findFirst()
          .orElse(null);
    } catch (Exception ignored) {
      return null;
    }
  }
}
