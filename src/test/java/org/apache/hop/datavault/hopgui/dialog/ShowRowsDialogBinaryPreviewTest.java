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
package org.apache.hop.datavault.hopgui.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShowRowsDialogBinaryPreviewTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void binaryFieldsDefaultToHexEncoding() throws Exception {
    // Default HOP_BINARY_FIELDS_AVOID_HEX_PREVIEW is false → hex preview (same as Hop GUI).
    IValueMeta binary = new ValueMetaBinary("hk_customer");
    byte[] hash = new byte[] {0x0a, 0x1b, (byte) 0xef, 0x00};

    assertEquals("0a1bef00", ShowRowsDialog.formatCellValue(binary, hash));
  }

  @Test
  void nullBinaryIsNullDisplay() throws Exception {
    IValueMeta binary = new ValueMetaBinary("hk_customer");
    assertNull(ShowRowsDialog.formatCellValue(binary, null));
  }

  @Test
  void nonBinaryUsesGetString() throws Exception {
    IValueMeta string = new ValueMetaString("name");
    assertEquals("Acme", ShowRowsDialog.formatCellValue(string, "Acme"));
  }
}
