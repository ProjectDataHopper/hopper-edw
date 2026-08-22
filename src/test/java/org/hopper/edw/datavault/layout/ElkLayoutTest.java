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
package org.hopper.edw.datavault.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.alg.rectpacking.options.RectPackingOptions;
import org.eclipse.elk.alg.rectpacking.p1widthapproximation.WidthApproximationStrategy;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.util.ElkGraphUtil;
import org.junit.jupiter.api.Test;

class ElkLayoutTest {

  @Test
  void copyConstructorCopiesAlgorithmAndTargetWidth() {
    ElkLayout source = new ElkLayout();
    source.setAlgorithm(ElkLayoutAlgorithm.RECT_PACKING);
    source.setTargetWidth(800);

    ElkLayout copy = new ElkLayout(source);

    assertEquals(ElkLayoutAlgorithm.RECT_PACKING, copy.getAlgorithm());
    assertEquals(800, copy.getTargetWidth());
  }

  @Test
  void applyToUsesRectPackingAlgorithmAndTargetWidth() {
    ElkLayout layout = new ElkLayout();
    layout.setAlgorithm(ElkLayoutAlgorithm.RECT_PACKING);
    layout.setTargetWidth(640);

    ElkNode root = ElkGraphUtil.createGraph();
    layout.applyTo(root);

    assertEquals(RectPackingOptions.ALGORITHM_ID, root.getProperty(CoreOptions.ALGORITHM));
    assertEquals(
        WidthApproximationStrategy.TARGET_WIDTH,
        root.getProperty(RectPackingOptions.WIDTH_APPROXIMATION_STRATEGY));
    assertEquals(640.0, root.getProperty(RectPackingOptions.WIDTH_APPROXIMATION_TARGET_WIDTH));
    assertNotNull(root.getProperty(RectPackingOptions.CONTENT_ALIGNMENT));
    assertFalse(root.getProperty(CoreOptions.EXPAND_NODES));
    assertTrue(root.getProperty(RectPackingOptions.NODE_SIZE_FIXED_GRAPH_SIZE));
  }

  @Test
  void createDefaultMatchesPreferredSettings() {
    ElkLayout layout = ElkLayout.createDefault();

    assertEquals(ElkLayoutAlgorithm.LAYERED, layout.getAlgorithm());
    assertEquals(1000, layout.getTargetWidth());
    assertEquals(ElkLayoutDirection.RIGHT, layout.getDirection());
    assertEquals(48, layout.getSpacingWithinLayer());
    assertEquals(16, layout.getSpacingBetweenLayers());
    assertEquals(16, layout.getSpacingEdgeNode());
    assertEquals(48, layout.getOriginX());
    assertEquals(16, layout.getOriginY());
    assertEquals(16, layout.getGridSize());
    assertEquals(120, layout.getMinNodeWidth());
    assertEquals(48, layout.getNodeHeight());
  }

  @Test
  void applyToUsesLayeredAlgorithmByDefault() {
    ElkLayout layout = new ElkLayout();

    ElkNode root = ElkGraphUtil.createGraph();
    layout.applyTo(root);

    assertEquals(LayeredOptions.ALGORITHM_ID, root.getProperty(CoreOptions.ALGORITHM));
    assertEquals(
        ElkLayoutDirection.RIGHT.toElkDirection(), root.getProperty(CoreOptions.DIRECTION));
  }
}
