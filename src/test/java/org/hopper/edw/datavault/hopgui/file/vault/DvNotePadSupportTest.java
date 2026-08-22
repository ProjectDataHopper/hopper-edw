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
package org.hopper.edw.datavault.hopgui.file.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.NotePadMeta;
import org.apache.hop.core.NotePadType;
import org.apache.hop.core.gui.Point;
import org.hopper.edw.datavault.metadata.DvNote;
import org.hopper.edw.datavault.metadata.DvNoteType;
import org.junit.jupiter.api.Test;

class DvNotePadSupportTest {

  @Test
  void forCanvas_isStableIdentityAndSyncsFields() {
    DvNote note = new DvNote();
    note.setText("# Hello\n[hub](hub_customer)");
    note.setNoteType(DvNoteType.IMPORTANT);
    note.setLocation(new Point(10, 20));
    note.setWidth(200);
    note.setHeight(100);

    NotePadMeta first = DvNotePadSupport.forCanvas(note);
    NotePadMeta second = DvNotePadSupport.forCanvas(note);
    assertSame(first, second);
    assertTrue(first.isMarkdown());
    assertEquals("# Hello\n[hub](hub_customer)", first.getNote());
    assertEquals(NotePadType.IMPORTANT, first.getNoteType());
    assertEquals(10, first.getLocation().x);
    assertEquals(20, first.getLocation().y);
    assertEquals(200, first.getWidth());
  }

  @Test
  void applyFromNotePadMeta_mapsTextAndType() {
    DvNote note = new DvNote();
    note.setText("old");
    note.setNoteType(DvNoteType.GENERAL);

    NotePadMeta pad = new NotePadMeta();
    pad.setNote("**new** body");
    pad.setNoteType(NotePadType.WARNING);
    pad.setMarkdown(true);

    DvNotePadSupport.applyFromNotePadMeta(note, pad);
    assertEquals("**new** body", note.getText());
    assertEquals(DvNoteType.WARNING, note.getNoteType());
  }

  @Test
  void typeMapping_roundTrips() {
    for (DvNoteType type : DvNoteType.values()) {
      NotePadType pad = DvNotePadSupport.toNotePadType(type);
      assertEquals(type, DvNotePadSupport.toDvNoteType(pad));
      assertEquals(type.getCode(), pad.getCode());
    }
  }

  @Test
  void looksLikeFileOrUrlTarget() {
    assertTrue(DvNotePadSupport.looksLikeFileOrUrlTarget("https://example.org"));
    assertTrue(DvNotePadSupport.looksLikeFileOrUrlTarget("http://example.org/a"));
    assertTrue(DvNotePadSupport.looksLikeFileOrUrlTarget("../pipelines/load.hpl"));
    assertTrue(DvNotePadSupport.looksLikeFileOrUrlTarget("folder/file.hwf"));
    assertTrue(DvNotePadSupport.looksLikeFileOrUrlTarget("file:///tmp/a.hpl"));
    assertFalse(DvNotePadSupport.looksLikeFileOrUrlTarget("hub_customer"));
    assertFalse(DvNotePadSupport.looksLikeFileOrUrlTarget("sat_product"));
    assertFalse(DvNotePadSupport.looksLikeFileOrUrlTarget(null));
  }
}
