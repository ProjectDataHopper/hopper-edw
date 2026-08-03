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
package org.apache.hop.datavault.hopgui.file.vault.delegates;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopXmlException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.xml.XmlFormatter;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.vault.HopGuiVaultGraph;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.DvNoteType;
import org.apache.hop.datavault.metadata.DvReferenceTable;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvLinkedTable;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
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
 * Clipboard support for the Data Vault model graph. Selected tables and notes are serialized to a
 * custom XML format ({@value #XML_TAG_CLIPBOARD}) for copy/cut/paste.
 */
public class HopGuiVaultClipboardDelegate {
  private static final Class<?> PKG = HopGui.class;

  public static final String XML_TAG_CLIPBOARD = "data-vault-clipboard";
  public static final String XML_TAG_TABLES = "tables";
  public static final String XML_TAG_TABLE = "table";
  public static final String XML_TAG_NOTES = "notes";
  public static final String XML_TAG_NOTE = "note";

  private final HopGui hopGui;
  private final HopGuiVaultGraph vaultGraph;

  public HopGuiVaultClipboardDelegate(HopGui hopGui, HopGuiVaultGraph vaultGraph) {
    this.hopGui = hopGui;
    this.vaultGraph = vaultGraph;
  }

  public void copySelected(List<IDvTable> tables, List<DvNote> notes) {
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

  public HopGuiVaultGraph getVaultGraph() {
    return vaultGraph;
  }

  public boolean pasteXml(DataVaultModel model, String clipboardContent, Point location) {
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
      List<IDvTable> tables = deserializeTables(clipboardNode, metadataProvider);
      List<DvNote> notes = deserializeNotes(clipboardNode, metadataProvider);
      if (tables.isEmpty() && notes.isEmpty()) {
        return false;
      }

      DataVaultClipboardPasteSupport.applyLocationOffset(tables, notes, location);
      Map<String, String> nameMap =
          DataVaultClipboardPasteSupport.assignUniqueTableNames(model, tables);
      DataVaultClipboardPasteSupport.remapNamesInPastedTables(tables, nameMap);
      clearChangedOnPasteResult(tables, notes);
      vaultGraph.applyPasteResult(tables, notes);
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

  private static void serializeTablesToXml(List<IDvTable> tables, StringBuilder xml)
      throws HopException {
    xml.append(XmlHandler.openTag(XML_TAG_TABLES)).append(Const.CR);
    if (tables != null) {
      for (IDvTable table : tables) {
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

  private List<IDvTable> deserializeTables(
      Node clipboardNode, IHopMetadataProvider metadataProvider) throws HopXmlException {
    List<IDvTable> tables = new ArrayList<>();
    Node tablesNode = XmlHandler.getSubNode(clipboardNode, XML_TAG_TABLES);
    if (tablesNode == null) {
      return tables;
    }
    List<Node> tableNodes = XmlHandler.getNodes(tablesNode, XML_TAG_TABLE);
    for (Node tableNode : tableNodes) {
      IDvTable table = deserializeTable(tableNode, metadataProvider);
      if (table != null) {
        table.setSelected(false);
        tables.add(table);
      }
    }
    return tables;
  }

  private IDvTable deserializeTable(Node tableNode, IHopMetadataProvider metadataProvider)
      throws HopXmlException {
    String tableTypeName = XmlHandler.getTagValue(tableNode, "tableType");
    if (Utils.isEmpty(tableTypeName)) {
      return null;
    }
    // Dual-read: legacy TABLE_REFERENCE → LINKED_TABLE
    DvTableType tableType = DvTableType.parsePersisted(tableTypeName);
    return switch (tableType) {
      case HUB -> XmlMetadataUtil.deSerializeFromXml(tableNode, DvHub.class, metadataProvider);
      case LINK -> XmlMetadataUtil.deSerializeFromXml(tableNode, DvLink.class, metadataProvider);
      case SATELLITE ->
          XmlMetadataUtil.deSerializeFromXml(tableNode, DvSatellite.class, metadataProvider);
      case REFERENCE ->
          XmlMetadataUtil.deSerializeFromXml(tableNode, DvReferenceTable.class, metadataProvider);
      case LINKED_TABLE, TABLE_REFERENCE ->
          XmlMetadataUtil.deSerializeFromXml(tableNode, DvLinkedTable.class, metadataProvider);
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

  private boolean pastePlainTextNote(DataVaultModel model, String text, Point location) {
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
      vaultGraph.applyPasteResult(List.of(), List.of(note));
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

  private static void clearChangedOnPasteResult(List<IDvTable> tables, List<DvNote> notes) {
    if (tables != null) {
      for (IDvTable table : tables) {
        if (table != null) {
          table.clearChanged();
        }
      }
    }
  }
}
