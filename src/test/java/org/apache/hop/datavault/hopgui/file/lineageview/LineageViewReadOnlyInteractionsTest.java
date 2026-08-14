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
package org.apache.hop.datavault.hopgui.file.lineageview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.datavault.lineageview.backend.LineageNode;
import org.apache.hop.datavault.lineageview.backend.LineageNodeKind;
import org.junit.jupiter.api.Test;

class LineageViewReadOnlyInteractionsTest {

  @Test
  void nodeOfReadsOwnerThenParent() {
    LineageNode node =
        LineageNode.builder().id("dataset:ns:a").kind(LineageNodeKind.DATASET).name("a").build();
    AreaOwner icon =
        new AreaOwner(
            AreaOwner.AreaType.TRANSFORM_ICON,
            0,
            0,
            10,
            10,
            new DPoint(0, 0),
            "dataset:ns:a",
            node);
    AreaOwner name =
        new AreaOwner(AreaOwner.AreaType.TRANSFORM_NAME, 0, 0, 10, 10, new DPoint(0, 0), node, "a");
    assertSame(node, LineageViewReadOnlyInteractions.nodeOf(icon));
    assertSame(node, LineageViewReadOnlyInteractions.nodeOf(name));
    assertEquals("a", ((LineageNode) name.getParent()).getName());
    assertNull(LineageViewReadOnlyInteractions.nodeOf(null));
  }
}
