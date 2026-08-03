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
package org.apache.hop.datavault.hopgui.file.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.datavault.metadata.DvNoteType;
import org.junit.jupiter.api.Test;

/**
 * Headless checks for note palette fallbacks (no SWT GuiResource). Dark-mode paths that depend on
 * PropsUi are covered by runtime GUI use; here we assert non-null, opaque RGB fills for every type.
 */
class DvNoteStyleTest {

  @Test
  void everyNoteTypeHasOpaqueBackground() {
    for (DvNoteType type : DvNoteType.values()) {
      DvNoteStyle.RgbColor bg = DvNoteStyle.backgroundColor(type);
      assertTrue(bg.red() >= 0 && bg.red() <= 255, type + " red");
      assertTrue(bg.green() >= 0 && bg.green() <= 255, type + " green");
      assertTrue(bg.blue() >= 0 && bg.blue() <= 255, type + " blue");
    }
  }

  @Test
  void importantAndInformationFallbackFillsAreDistinctFromNearWhite() {
    // Without PropsUi dark mode, headless uses light fallbacks — ensure they stay defined.
    DvNoteStyle.RgbColor important = DvNoteStyle.backgroundColor(DvNoteType.IMPORTANT);
    DvNoteStyle.RgbColor information = DvNoteStyle.backgroundColor(DvNoteType.INFORMATION);
    assertEquals(255, important.red());
    assertTrue(important.green() < 240, "important should stay yellowish, not pure white");
    assertTrue(information.blue() > information.red(), "information light fill should be bluish");
  }

  @Test
  void textColorIsDefined() {
    DvNoteStyle.RgbColor text = DvNoteStyle.textColor(DvNoteType.INFORMATION);
    assertTrue(text.red() == 0 || text.red() >= 200);
  }
}
