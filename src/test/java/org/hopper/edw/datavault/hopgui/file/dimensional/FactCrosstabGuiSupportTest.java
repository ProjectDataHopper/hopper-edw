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
package org.hopper.edw.datavault.hopgui.file.dimensional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.DmFactlessFact;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.junit.jupiter.api.Test;

class FactCrosstabGuiSupportTest {

  @Test
  void actionIsOfferedOnFactLikeTablesOnly() {
    assertTrue(FactCrosstabGuiSupport.isFactLikeTable(new DmFact()));
    assertTrue(FactCrosstabGuiSupport.isFactLikeTable(new DmFactlessFact()));
    assertFalse(FactCrosstabGuiSupport.isFactLikeTable(new DmDimension()));
    assertFalse(FactCrosstabGuiSupport.isFactLikeTable(new DmJunkDimension()));
    assertFalse(FactCrosstabGuiSupport.isFactLikeTable(null));
  }
}
