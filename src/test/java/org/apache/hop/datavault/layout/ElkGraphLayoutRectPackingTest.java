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
package org.apache.hop.datavault.layout;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.apache.hop.core.gui.Point;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.Test;

class ElkGraphLayoutRectPackingTest {

  @Test
  void targetWidthChangesRowWrapping() throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    for (int i = 0; i < 12; i++) {
      TransformMeta transform = new TransformMeta();
      transform.setName("Transform_" + i);
      transform.setLocation(0, 0);
      pipelineMeta.addTransform(transform);
    }

    int narrowRows = countRows(layoutPipeline(pipelineMeta, 320));
    int wideRows = countRows(layoutPipeline(pipelineMeta, 1600));

    assertTrue(narrowRows > wideRows, "narrow width should produce more rows");
  }

  private static int countRows(Point[] locations) {
    Set<Integer> rows = new HashSet<>();
    for (Point location : locations) {
      rows.add(location.y);
    }
    return rows.size();
  }

  private static Point[] layoutPipeline(PipelineMeta pipelineMeta, int targetWidth)
      throws Exception {
    ElkLayout layout = new ElkLayout();
    layout.setAlgorithm(ElkLayoutAlgorithm.RECT_PACKING);
    layout.setTargetWidth(targetWidth);
    layout.setOriginX(0);
    layout.setOriginY(0);

    ElkGraphLayout.fromPipeline(pipelineMeta).layout(layout);

    Point[] locations = new Point[pipelineMeta.getTransforms().size()];
    for (int i = 0; i < pipelineMeta.getTransforms().size(); i++) {
      locations[i] = pipelineMeta.getTransform(i).getLocation();
    }
    return locations;
  }
}
