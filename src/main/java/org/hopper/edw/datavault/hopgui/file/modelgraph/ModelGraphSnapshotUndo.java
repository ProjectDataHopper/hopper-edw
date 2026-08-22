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
package org.hopper.edw.datavault.hopgui.file.modelgraph;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.config.DataVaultConfigSingleton;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Gzip-compressed XML snapshots of a warehouse model document for undo/redo. */
public class ModelGraphSnapshotUndo<M> {

  private final Class<M> modelClass;
  private final String xmlRootTag;
  private final Supplier<M> modelFactory;

  private final List<byte[]> undoStack = new ArrayList<>();
  private final List<byte[]> redoStack = new ArrayList<>();
  private boolean applyingSnapshot;

  public ModelGraphSnapshotUndo(Class<M> modelClass, String xmlRootTag, Supplier<M> modelFactory) {
    this.modelClass = modelClass;
    this.xmlRootTag = xmlRootTag;
    this.modelFactory = modelFactory;
  }

  public void clear() {
    undoStack.clear();
    redoStack.clear();
  }

  public boolean canUndo() {
    return !undoStack.isEmpty();
  }

  public boolean canRedo() {
    return !redoStack.isEmpty();
  }

  public boolean isApplyingSnapshot() {
    return applyingSnapshot;
  }

  public void markChange(M model, IHopMetadataProvider metadataProvider) throws HopException {
    if (applyingSnapshot || model == null) {
      return;
    }
    pushSnapshot(captureSnapshot(model, metadataProvider));
  }

  @SuppressWarnings("unchecked")
  public void markChangeObject(Object model, IHopMetadataProvider metadataProvider)
      throws HopException {
    markChange((M) model, metadataProvider);
  }

  public void pushSnapshot(byte[] snapshot) {
    if (applyingSnapshot || snapshot == null) {
      return;
    }
    undoStack.add(snapshot);
    trimStack(undoStack);
    redoStack.clear();
  }

  public byte[] captureSnapshot(M model, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null) {
      throw new HopException("Cannot capture snapshot of a null model");
    }
    try {
      String xml = XmlHandler.aroundTag(xmlRootTag, XmlMetadataUtil.serializeObjectToXml(model));
      return compress(xml);
    } catch (Exception e) {
      throw new HopException("Error capturing model snapshot", e);
    }
  }

  @SuppressWarnings("unchecked")
  public byte[] captureSnapshotObject(Object model, IHopMetadataProvider metadataProvider)
      throws HopException {
    return captureSnapshot((M) model, metadataProvider);
  }

  public M undo(M model, IHopMetadataProvider metadataProvider, String preserveFilename)
      throws HopException {
    if (!canUndo() || model == null) {
      return null;
    }
    applyingSnapshot = true;
    try {
      redoStack.add(captureSnapshot(model, metadataProvider));
      trimStack(redoStack);
      byte[] previous = undoStack.remove(undoStack.size() - 1);
      return restoreSnapshot(previous, metadataProvider, preserveFilename);
    } finally {
      applyingSnapshot = false;
    }
  }

  public M redo(M model, IHopMetadataProvider metadataProvider, String preserveFilename)
      throws HopException {
    if (!canRedo() || model == null) {
      return null;
    }
    applyingSnapshot = true;
    try {
      undoStack.add(captureSnapshot(model, metadataProvider));
      trimStack(undoStack);
      byte[] next = redoStack.remove(redoStack.size() - 1);
      return restoreSnapshot(next, metadataProvider, preserveFilename);
    } finally {
      applyingSnapshot = false;
    }
  }

  @SuppressWarnings("unchecked")
  public Object undoObject(
      Object model, IHopMetadataProvider metadataProvider, String preserveFilename)
      throws HopException {
    return undo((M) model, metadataProvider, preserveFilename);
  }

  @SuppressWarnings("unchecked")
  public Object redoObject(
      Object model, IHopMetadataProvider metadataProvider, String preserveFilename)
      throws HopException {
    return redo((M) model, metadataProvider, preserveFilename);
  }

  private M restoreSnapshot(
      byte[] snapshot, IHopMetadataProvider metadataProvider, String preserveFilename)
      throws HopException {
    try {
      String xml = decompress(snapshot);
      Document document = XmlHandler.loadXmlString(xml);
      Node rootNode = XmlHandler.getSubNode(document, xmlRootTag);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      M restored = modelFactory.get();
      XmlMetadataUtil.deSerializeFromXml(rootNode, modelClass, restored, metadataProvider);
      preserveFilename(restored, preserveFilename);
      if (restored instanceof org.apache.hop.core.changed.IChanged changed) {
        changed.clearChanged();
      }
      return restored;
    } catch (Exception e) {
      throw new HopException("Error restoring model snapshot", e);
    }
  }

  private static byte[] compress(String xml) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(xml.length());
    try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
      gzip.write(xml.getBytes(StandardCharsets.UTF_8));
    }
    return baos.toByteArray();
  }

  private static String decompress(byte[] compressed) throws IOException {
    try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      gzip.transferTo(baos);
      return baos.toString(StandardCharsets.UTF_8);
    }
  }

  private static void preserveFilename(Object restored, String preserveFilename) {
    if (preserveFilename == null || restored == null) {
      return;
    }
    if (restored instanceof DataVaultModel dataVaultModel) {
      dataVaultModel.setFilename(preserveFilename);
    } else if (restored instanceof BusinessVaultModel businessVaultModel) {
      businessVaultModel.setFilename(preserveFilename);
    }
  }

  private static void trimStack(List<byte[]> stack) {
    int max = DataVaultConfigSingleton.getConfig().getMaxUndoOperations();
    while (stack.size() > max) {
      stack.remove(0);
    }
  }
}
