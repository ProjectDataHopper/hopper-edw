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
package org.hopper.edw.datavault.hopgui.file.businessvault.delegates;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopXmlException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.xml.XmlFormatter;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopGuiBusinessVaultGraph;
import org.hopper.edw.datavault.metadata.DvNote;
import org.hopper.edw.datavault.metadata.DvNoteType;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvPitTable;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.BvTableType;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.hopgui.HopGui;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Clipboard support for the Business Vault model graph. Selected tables and notes are serialized to
 * a custom XML format ({@value #XML_TAG_CLIPBOARD}) for copy/cut/paste.
 */
public class HopGuiBusinessVaultClipboardDelegate {
  private static final Class<?> PKG = HopGui.class;

  public static final String XML_TAG_CLIPBOARD = "business-vault-clipboard";
  public static final String XML_TAG_TABLES = "tables";
  public static final String XML_TAG_TABLE = "table";
  public static final String XML_TAG_NOTES = "notes";
  public static final String XML_TAG_NOTE = "note";

  private final HopGui hopGui;
  private final HopGuiBusinessVaultGraph businessVaultGraph;

  public HopGuiBusinessVaultClipboardDelegate(
      HopGui hopGui, HopGuiBusinessVaultGraph businessVaultGraph) {
    this.hopGui = hopGui;
    this.businessVaultGraph = businessVaultGraph;
  }

  public void copySelected(List<IBvTable> tables, List<DvNote> notes) {
    boolean hasTables = tables != null && !tables.isEmpty();
    boolean hasNotes = notes != null && !notes.isEmpty();
    if (!hasTables && !hasNotes) {
      return;
    }

    StringBuilder xml = new StringBuilder(5000).append(XmlHandler.getXmlHeader());
    try {
      xml.append(XmlHandler.openTag(XML_TAG_CLIPBOARD)).append(Const.CR);
      serializeTablesToXml(tables, xml);
      serializeNotesToXml(notes, xml);
      xml.append(XmlHandler.closeTag(XML_TAG_CLIPBOARD)).append(Const.CR);
      toClipboard(XmlFormatter.format(xml.toString()));
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

  public HopGuiBusinessVaultGraph getBusinessVaultGraph() {
    return businessVaultGraph;
  }

  public boolean pasteXml(BusinessVaultModel model, String clipboardContent, Point location) {
    if (model == null || Utils.isEmpty(clipboardContent)) {
      return false;
    }
    try {
      Document document = XmlHandler.loadXmlString(clipboardContent);
      Node clipboardNode = XmlHandler.getSubNode(document, XML_TAG_CLIPBOARD);
      if (clipboardNode == null) {
        return pastePlainTextNote(model, clipboardContent.trim(), location);
      }

      IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();
      List<IBvTable> tables = deserializeTables(clipboardNode, metadataProvider);
      List<DvNote> notes = deserializeNotes(clipboardNode, metadataProvider);
      if (tables.isEmpty() && notes.isEmpty()) {
        return false;
      }

      BusinessVaultClipboardPasteSupport.applyLocationOffset(tables, notes, location);
      BusinessVaultClipboardPasteSupport.assignUniqueTableNames(model, tables);
      clearChangedOnPasteResult(tables);
      businessVaultGraph.applyPasteResult(tables, notes);
      return true;
    } catch (HopException e) {
      return pastePlainTextNote(model, clipboardContent.trim(), location);
    }
  }

  private void toClipboard(String clipText) {
    try {
      GuiResource.getInstance().toClipboard(clipText);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getActiveShell(),
          BaseMessages.getString(PKG, "HopGui.Dialog.ExceptionCopyToClipboard.Title"),
          BaseMessages.getString(PKG, "HopGui.Dialog.ExceptionCopyToClipboard.Message"),
          e);
    }
  }

  private static void serializeTablesToXml(List<IBvTable> tables, StringBuilder xml)
      throws HopException {
    xml.append(XmlHandler.openTag(XML_TAG_TABLES)).append(Const.CR);
    if (tables != null) {
      for (IBvTable table : tables) {
        if (table == null) {
          continue;
        }
        xml.append(XmlHandler.openTag(XML_TAG_TABLE)).append(Const.CR);
        xml.append(XmlMetadataUtil.serializeObjectToXml(table));
        xml.append(XmlHandler.closeTag(XML_TAG_TABLE)).append(Const.CR);
      }
    }
    xml.append(XmlHandler.closeTag(XML_TAG_TABLES)).append(Const.CR);
  }

  private static void serializeNotesToXml(List<DvNote> notes, StringBuilder xml)
      throws HopException {
    xml.append(XmlHandler.openTag(XML_TAG_NOTES)).append(Const.CR);
    if (notes != null) {
      for (DvNote note : notes) {
        if (note == null) {
          continue;
        }
        DvNote copy = note.clone();
        copy.setSelected(false);
        xml.append(XmlHandler.openTag(XML_TAG_NOTE)).append(Const.CR);
        xml.append(XmlMetadataUtil.serializeObjectToXml(copy));
        xml.append(XmlHandler.closeTag(XML_TAG_NOTE)).append(Const.CR);
      }
    }
    xml.append(XmlHandler.closeTag(XML_TAG_NOTES)).append(Const.CR);
  }

  private List<IBvTable> deserializeTables(
      Node clipboardNode, IHopMetadataProvider metadataProvider) throws HopXmlException {
    List<IBvTable> tables = new ArrayList<>();
    Node tablesNode = XmlHandler.getSubNode(clipboardNode, XML_TAG_TABLES);
    if (tablesNode == null) {
      return tables;
    }
    List<Node> tableNodes = XmlHandler.getNodes(tablesNode, XML_TAG_TABLE);
    for (Node tableNode : tableNodes) {
      IBvTable table = deserializeTable(tableNode, metadataProvider);
      if (table != null) {
        table.setSelected(false);
        tables.add(table);
      }
    }
    return tables;
  }

  private IBvTable deserializeTable(Node tableNode, IHopMetadataProvider metadataProvider)
      throws HopXmlException {
    String tableTypeName = XmlHandler.getTagValue(tableNode, "tableType");
    if (Utils.isEmpty(tableTypeName)) {
      return null;
    }
    BvTableType tableType = BvTableType.valueOf(tableTypeName);
    return switch (tableType) {
      case SCD2 ->
          XmlMetadataUtil.deSerializeFromXml(tableNode, BvScd2Table.class, metadataProvider);
      case PIT -> XmlMetadataUtil.deSerializeFromXml(tableNode, BvPitTable.class, metadataProvider);
      case BUSINESS_TABLE ->
          XmlMetadataUtil.deSerializeFromXml(tableNode, BvBusinessTable.class, metadataProvider);
    };
  }

  private List<DvNote> deserializeNotes(Node clipboardNode, IHopMetadataProvider metadataProvider)
      throws HopXmlException {
    List<DvNote> notes = new ArrayList<>();
    Node notesNode = XmlHandler.getSubNode(clipboardNode, XML_TAG_NOTES);
    if (notesNode == null) {
      return notes;
    }
    List<Node> noteNodes = XmlHandler.getNodes(notesNode, XML_TAG_NOTE);
    for (Node noteNode : noteNodes) {
      DvNote note = XmlMetadataUtil.deSerializeFromXml(noteNode, DvNote.class, metadataProvider);
      if (note != null) {
        note.setSelected(false);
        notes.add(note);
      }
    }
    return notes;
  }

  private boolean pastePlainTextNote(BusinessVaultModel model, String text, Point location) {
    if (Utils.isEmpty(text)) {
      return false;
    }
    try {
      DvNote note = new DvNote();
      note.setText(text);
      note.setNoteType(DvNoteType.GENERAL);
      int x = location != null ? location.x : 50;
      int y = location != null ? location.y : 50;
      PropsUi.setLocation(note, x, y);
      PropsUi.setSize(note, ConstUi.NOTE_MIN_SIZE, ConstUi.NOTE_MIN_SIZE);
      note.setSelected(false);
      businessVaultGraph.applyPasteResult(List.of(), List.of(note));
      return true;
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getActiveShell(),
          BaseMessages.getString(PKG, "HopGui.Dialog.UnablePasteTransforms.Title"),
          BaseMessages.getString(PKG, "HopGui.Dialog.UnablePasteTransforms.Message"),
          e);
      return false;
    }
  }

  private static void clearChangedOnPasteResult(List<IBvTable> tables) {
    if (tables != null) {
      for (IBvTable table : tables) {
        if (table != null) {
          table.clearChanged();
        }
      }
    }
  }
}
