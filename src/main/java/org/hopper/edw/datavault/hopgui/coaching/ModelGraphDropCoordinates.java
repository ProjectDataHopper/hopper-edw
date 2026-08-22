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
package org.hopper.edw.datavault.hopgui.coaching;

import org.apache.hop.core.gui.Point;
import org.apache.hop.ui.hopgui.file.shared.HopGuiAbstractGraph;
import org.eclipse.swt.widgets.Canvas;

/** Converts SWT drop coordinates to model graph locations. */
public final class ModelGraphDropCoordinates {

  private ModelGraphDropCoordinates() {}

  /**
   * Maps a {@link org.eclipse.swt.dnd.DropTargetEvent} position to model coordinates.
   *
   * <p>Drop events report display-relative coordinates; mouse interactions on the canvas use
   * canvas-relative coordinates. {@link HopGuiAbstractGraph#screen2real(int, int)} also applies
   * canvas magnification and native zoom.
   */
  public static Point toModelLocation(
      Canvas canvas, HopGuiAbstractGraph graphView, int displayX, int displayY) {
    org.eclipse.swt.graphics.Point canvasPoint =
        canvas.getDisplay().map(null, canvas, displayX, displayY);
    return graphView.screen2real(canvasPoint.x, canvasPoint.y);
  }
}
