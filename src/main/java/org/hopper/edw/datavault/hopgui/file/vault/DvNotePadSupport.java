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

import org.apache.hop.core.NotePadMeta;
import org.apache.hop.core.NotePadType;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DvNote;
import org.hopper.edw.datavault.metadata.DvNoteType;

/**
 * Maps model {@link DvNote}s to Hop {@link NotePadMeta} for Markdown canvas rendering and the
 * shared note editor dialog.
 */
public final class DvNotePadSupport {

  private DvNotePadSupport() {}

  /**
   * Returns a stable {@link NotePadMeta} for the note (same instance across paints so link hover
   * identity works) and synchronizes fields from the model note.
   */
  public static NotePadMeta forCanvas(DvNote note) {
    if (note == null) {
      return null;
    }
    NotePadMeta pad = note.getCanvasNotePad();
    if (pad == null) {
      pad = new NotePadMeta();
      note.setCanvasNotePad(pad);
    }
    syncToNotePadMeta(note, pad);
    return pad;
  }

  /** Copy model fields onto an existing or new NotePadMeta (does not attach canvas cache). */
  public static NotePadMeta toNotePadMeta(DvNote note) {
    NotePadMeta pad = new NotePadMeta();
    if (note != null) {
      syncToNotePadMeta(note, pad);
    }
    return pad;
  }

  public static void syncToNotePadMeta(DvNote note, NotePadMeta pad) {
    if (note == null || pad == null) {
      return;
    }
    pad.setNote(note.getText());
    pad.setMarkdown(true);
    pad.setNoteType(toNotePadType(note.getNoteType()));
    if (note.getLocation() != null) {
      pad.setLocation(new Point(note.getLocation().x, note.getLocation().y));
    }
    pad.setWidth(note.getWidth());
    pad.setHeight(note.getHeight());
    pad.setSelected(note.isSelected());
    pad.setMinimumWidth(note.getMinimumWidth());
    pad.setMinimumHeight(note.getMinimumHeight());
  }

  /**
   * Apply dialog/editor results back onto the model note (text, type). Location and size are left
   * unchanged unless explicitly present and positive on the pad.
   */
  public static void applyFromNotePadMeta(DvNote note, NotePadMeta pad) {
    if (note == null || pad == null) {
      return;
    }
    note.setText(pad.getNote());
    note.setNoteType(toDvNoteType(pad.getNoteType()));
    // Keep canvas adapter identity in sync for the next paint.
    NotePadMeta canvas = note.getCanvasNotePad();
    if (canvas != null) {
      syncToNotePadMeta(note, canvas);
    }
  }

  public static NotePadType toNotePadType(DvNoteType type) {
    if (type == null) {
      return NotePadType.GENERAL;
    }
    return NotePadType.lookupCode(type.getCode());
  }

  public static DvNoteType toDvNoteType(NotePadType type) {
    if (type == null) {
      return DvNoteType.GENERAL;
    }
    return DvNoteType.lookupCode(type.getCode());
  }

  /**
   * Heuristic: bare identifiers are model table names; paths/extensions are Hop files or URLs
   * handled by {@link org.apache.hop.ui.hopgui.file.delegates.HopGuiNoteLinkSupport}.
   */
  public static boolean looksLikeFileOrUrlTarget(String target) {
    if (Utils.isEmpty(target)) {
      return false;
    }
    String t = target.trim();
    if (t.regionMatches(true, 0, "http://", 0, 7)
        || t.regionMatches(true, 0, "https://", 0, 8)
        || t.regionMatches(true, 0, "file:", 0, 5)) {
      return true;
    }
    if (t.contains("/") || t.contains("\\") || t.contains("://")) {
      return true;
    }
    int dot = t.lastIndexOf('.');
    if (dot > 0 && dot < t.length() - 1) {
      String ext = t.substring(dot + 1).toLowerCase();
      return switch (ext) {
        case "hpl",
                "hwf",
                "hdv",
                "hbv",
                "hdm",
                "hem",
                "json",
                "xml",
                "txt",
                "md",
                "csv",
                "svg",
                "png",
                "jpg",
                "jpeg",
                "gif" ->
            true;
        default -> false;
      };
    }
    return false;
  }
}
