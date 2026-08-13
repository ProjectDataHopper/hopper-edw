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
package org.apache.hop.datavault.hopgui.file.modelgraph;

import org.apache.hop.core.gui.Point;

/**
 * Click-versus-drag geometry for model canvases.
 *
 * <p>Hop Web arms icon-drag on mouse-down (RAP does not stream move-while-held). Mouse-up must
 * still treat an unmoved pointer as a left-click so the table context dialog can open.
 */
public final class ModelGraphClickSupport {

  private ModelGraphClickSupport() {}

  /**
   * True when {@code real} is within {@code thresholdPx} of {@code lastClick} (same units as the
   * two points). {@code thresholdPx} of 0 means exact equality.
   */
  public static boolean isUnmovedClick(Point lastClick, Point real, int thresholdPx) {
    if (lastClick == null || real == null) {
      return false;
    }
    int dx = real.x - lastClick.x;
    int dy = real.y - lastClick.y;
    int threshold = Math.max(thresholdPx, 0);
    return dx * dx + dy * dy <= threshold * threshold;
  }
}
