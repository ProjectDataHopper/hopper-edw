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
package org.hopper.edw.datavault.hopgui.file.sourcemodel.delegates;

import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.xml.XmlFormatter;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.hopgui.file.sourcemodel.HopGuiSourceModelGraph;
import org.hopper.edw.datavault.metadata.DvNote;
import org.hopper.edw.datavault.metadata.DvNoteType;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.hopgui.HopGui;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Clipboard support for the source model graph. */
public class HopGuiSourceModelClipboardDelegate {

  private static final Class<?> PKG = HopGui.class;

  public static final String XML_TAG_CLIPBOARD = "source-model-clipboard";
  public static final String XML_TAG_TABLES = "tables";
  public static final String XML_TAG_TABLE = "table";
  public static final String XML_TAG_RELATIONSHIPS = "relationships";
  public static final String XML_TAG_RELATIONSHIP = "relationship";
  public static final String XML_TAG_NOTES = "notes";
  public static final String XML_TAG_NOTE = "note";

  private final HopGui hopGui;
  private final HopGuiSourceModelGraph sourceModelGraph;

  public HopGuiSourceModelClipboardDelegate(
      HopGui hopGui, HopGuiSourceModelGraph sourceModelGraph) {
    this.hopGui = hopGui;
    this.sourceModelGraph = sourceModelGraph;
  }

  public void copySelected(
      List<SourceTable> tables, List<SourceRelationship> relationships, List<DvNote> notes) {
    boolean hasTables = tables != null && !tables.isEmpty();
    boolean hasRels = relationships != null && !relationships.isEmpty();
    boolean hasNotes = notes != null && !notes.isEmpty();
    if (!hasTables && !hasRels && !hasNotes) {
      return;
    }

    StringBuilder xml = new StringBuilder(5000).append(XmlHandler.getXmlHeader());
    try {
      xml.append(XmlHandler.openTag(XML_TAG_CLIPBOARD)).append(Const.CR);
      serializeTables(tables, xml);
      serializeRelationships(relationships, xml);
      serializeNotes(notes, xml);
      xml.append(XmlHandler.closeTag(XML_TAG_CLIPBOARD)).append(Const.CR);
      GuiResource.getInstance().toClipboard(XmlFormatter.format(xml.toString()));
    } catch (Exception ex) {
      new ErrorDialog(
          hopGui.getActiveShell(),
          BaseMessages.getString(PKG, "HopGui.Dialog.ExceptionCopyToClipboard.Title"),
          BaseMessages.getString(PKG, "HopGui.Dialog.ExceptionCopyToClipboard.Message"),
          ex);
    }
  }

  public String fromClipboard() {
    try {
      return GuiResource.getInstance().fromClipboard();
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getActiveShell(),
          BaseMessages.getString(PKG, "HopGui.Dialog.ExceptionPasteFromClipboard.Title"),
          BaseMessages.getString(PKG, "HopGui.Dialog.ExceptionPasteFromClipboard.Message"),
          e);
      return null;
    }
  }

  public boolean pasteXml(SourceModel model, String clipboardContent, Point location) {
    if (model == null || Utils.isEmpty(clipboardContent)) {
      return false;
    }
    try {
      Document document = XmlHandler.loadXmlString(clipboardContent);
      Node clipboardNode = XmlHandler.getSubNode(document, XML_TAG_CLIPBOARD);
      if (clipboardNode == null) {
        return pastePlainTextNote(model, clipboardContent.trim(), location);
      }
      IHopMetadataProvider provider = hopGui.getMetadataProvider();
      boolean changed = false;
      Point pasteAt = location != null ? location : new Point(50, 50);
      int offset = 0;

      Node tablesNode = XmlHandler.getSubNode(clipboardNode, XML_TAG_TABLES);
      List<Node> tableNodes =
          tablesNode != null ? XmlHandler.getNodes(tablesNode, XML_TAG_TABLE) : List.of();
      for (Node tableNode : tableNodes) {
        SourceTable table = new SourceTable();
        XmlMetadataUtil.deSerializeFromXml(tableNode, SourceTable.class, table, provider);
        table.setName(uniqueTableName(model, table.getName()));
        Point loc = table.getLocation();
        int x = pasteAt.x + offset;
        int y = pasteAt.y + offset;
        if (loc != null) {
          x = loc.x + 20;
          y = loc.y + 20;
        }
        PropsUi.setLocation(table, x, y);
        table.setSelected(true);
        model.getTables().add(table);
        offset += 20;
        changed = true;
      }

      Node relsNode = XmlHandler.getSubNode(clipboardNode, XML_TAG_RELATIONSHIPS);
      List<Node> relNodes =
          relsNode != null ? XmlHandler.getNodes(relsNode, XML_TAG_RELATIONSHIP) : List.of();
      for (Node relNode : relNodes) {
        SourceRelationship relationship = new SourceRelationship();
        XmlMetadataUtil.deSerializeFromXml(
            relNode, SourceRelationship.class, relationship, provider);
        if (model.findTable(relationship.getChildTableName()) == null
            || model.findTable(relationship.getParentTableName()) == null) {
          continue;
        }
        relationship.setName(uniqueRelationshipName(model, relationship.getName()));
        model.getRelationships().add(relationship);
        changed = true;
      }

      Node notesNode = XmlHandler.getSubNode(clipboardNode, XML_TAG_NOTES);
      List<Node> noteNodes =
          notesNode != null ? XmlHandler.getNodes(notesNode, XML_TAG_NOTE) : List.of();
      for (Node noteNode : noteNodes) {
        DvNote note = new DvNote();
        XmlMetadataUtil.deSerializeFromXml(noteNode, DvNote.class, note, provider);
        PropsUi.setLocation(note, pasteAt.x + offset, pasteAt.y + offset);
        note.setSelected(true);
        model.getNotes().add(note);
        offset += 20;
        changed = true;
      }
      return changed;
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getActiveShell(),
          BaseMessages.getString(PKG, "HopGui.Dialog.ExceptionPasteFromClipboard.Title"),
          BaseMessages.getString(PKG, "HopGui.Dialog.ExceptionPasteFromClipboard.Message"),
          e);
      return false;
    }
  }

  private boolean pastePlainTextNote(SourceModel model, String text, Point location) {
    if (Utils.isEmpty(text)) {
      return false;
    }
    DvNote note = new DvNote();
    note.setText(text);
    note.setNoteType(DvNoteType.GENERAL);
    PropsUi.setLocation(
        note, location != null ? location.x : 50, location != null ? location.y : 50);
    note.setSelected(true);
    model.getNotes().add(note);
    return true;
  }

  private void serializeTables(List<SourceTable> tables, StringBuilder xml) throws Exception {
    xml.append(XmlHandler.openTag(XML_TAG_TABLES)).append(Const.CR);
    if (tables != null) {
      for (SourceTable table : tables) {
        if (table == null) {
          continue;
        }
        xml.append(XmlHandler.openTag(XML_TAG_TABLE));
        xml.append(XmlMetadataUtil.serializeObjectToXml(table));
        xml.append(XmlHandler.closeTag(XML_TAG_TABLE)).append(Const.CR);
      }
    }
    xml.append(XmlHandler.closeTag(XML_TAG_TABLES)).append(Const.CR);
  }

  private void serializeRelationships(List<SourceRelationship> relationships, StringBuilder xml)
      throws Exception {
    xml.append(XmlHandler.openTag(XML_TAG_RELATIONSHIPS)).append(Const.CR);
    if (relationships != null) {
      for (SourceRelationship relationship : relationships) {
        if (relationship == null) {
          continue;
        }
        xml.append(XmlHandler.openTag(XML_TAG_RELATIONSHIP));
        xml.append(XmlMetadataUtil.serializeObjectToXml(relationship));
        xml.append(XmlHandler.closeTag(XML_TAG_RELATIONSHIP)).append(Const.CR);
      }
    }
    xml.append(XmlHandler.closeTag(XML_TAG_RELATIONSHIPS)).append(Const.CR);
  }

  private void serializeNotes(List<DvNote> notes, StringBuilder xml) throws Exception {
    xml.append(XmlHandler.openTag(XML_TAG_NOTES)).append(Const.CR);
    if (notes != null) {
      for (DvNote note : notes) {
        if (note == null) {
          continue;
        }
        xml.append(XmlHandler.openTag(XML_TAG_NOTE));
        xml.append(XmlMetadataUtil.serializeObjectToXml(note));
        xml.append(XmlHandler.closeTag(XML_TAG_NOTE)).append(Const.CR);
      }
    }
    xml.append(XmlHandler.closeTag(XML_TAG_NOTES)).append(Const.CR);
  }

  private static String uniqueTableName(SourceModel model, String base) {
    String name = Utils.isEmpty(base) ? "table" : base;
    if (model.findTable(name) == null) {
      return name;
    }
    int i = 2;
    while (model.findTable(name + "_" + i) != null) {
      i++;
    }
    return name + "_" + i;
  }

  private static String uniqueRelationshipName(SourceModel model, String base) {
    String name = Utils.isEmpty(base) ? "relationship" : base;
    if (model.findRelationship(name) == null) {
      return name;
    }
    int i = 2;
    while (model.findRelationship(name + "_" + i) != null) {
      i++;
    }
    return name + "_" + i;
  }
}
