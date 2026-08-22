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
package org.hopper.edw.datavault.metadata;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.NotePadMeta;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.IGuiSize;
import org.apache.hop.core.gui.Point;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * A typed annotation note on the Data Vault model canvas. Notes are documentation only; they do not
 * affect DV Update or DDL generation.
 *
 * <p>Canvas painting reuses Hop 2.19 {@link NotePadMeta} / {@code MarkdownNoteRenderer} via a
 * stable transient adapter ({@link #canvasNotePad}) so link hover identity matches Hop's
 * pipeline/workflow notes.
 */
@Getter
@Setter
public class DvNote implements IGuiPosition, IGuiSize, Cloneable {

  @HopMetadataProperty private String text;

  @HopMetadataProperty(storeWithCode = true)
  private DvNoteType noteType = DvNoteType.GENERAL;

  @HopMetadataProperty(inline = true)
  private Point location = new Point(50, 50);

  @HopMetadataProperty private int width = -1;

  @HopMetadataProperty private int height = -1;

  /** Transient selection state for the visual editor. */
  private boolean selected;

  /** Minimum size computed during last paint (for resize operations). */
  private int minimumWidth;

  /** Minimum size computed during last paint (for resize operations). */
  private int minimumHeight;

  /**
   * Stable Hop note adapter used while painting and for {@code NoteLinkHit} identity. Not
   * serialized; recreated on demand.
   */
  private transient NotePadMeta canvasNotePad;

  public DvNote() {}

  public DvNote(DvNote other) {
    this.text = other.text;
    this.noteType = other.noteType;
    this.location =
        other.location != null ? new Point(other.location.x, other.location.y) : new Point(50, 50);
    this.width = other.width;
    this.height = other.height;
    this.selected = other.selected;
    this.minimumWidth = other.minimumWidth;
    this.minimumHeight = other.minimumHeight;
  }

  @Override
  public void setLocation(int x, int y) {
    if (location == null) {
      location = new Point(x, y);
    } else {
      location.x = x;
      location.y = y;
    }
  }

  @Override
  public void setLocation(Point point) {
    if (point != null) {
      setLocation(point.x, point.y);
    } else {
      location = null;
    }
  }

  @Override
  public DvNote clone() {
    return new DvNote(this);
  }
}
