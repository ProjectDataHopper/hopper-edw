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
package org.apache.hop.datavault.hopgui.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.DPoint;
import org.junit.jupiter.api.Test;

class UpdateRunLiveWorkflowMouseSupportTest {

  @Test
  void badgeAreaUsesParentForRunIdAndOwnerForTooltip() {
    AreaOwner areaOwner =
        new AreaOwner(
            AreaType.CUSTOM,
            0,
            0,
            20,
            20,
            new DPoint(0, 0),
            new UpdateRunLiveAreaOwnerData("run-1"),
            "Updating model");

    assertTrue(UpdateRunLiveSnapshotTooltipSupport.isLiveBadgeOwner(areaOwner.getParent()));
    assertTrue(areaOwner.getOwner() instanceof String);
  }

  @Test
  void rejectsNullExtension() {
    assertFalse(UpdateRunLiveWorkflowMouseSupport.openDialogIfBadgeClicked(null));
  }
}
