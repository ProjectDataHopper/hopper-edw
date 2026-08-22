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
package org.apache.hop.datavault.hopgui.file.vault.delegates;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphSnapshotUndo;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Gzip-compressed XML snapshots of a {@link DataVaultModel} for undo/redo. */
public class HopGuiVaultSnapshotUndo {

  private final ModelGraphSnapshotUndo<DataVaultModel> delegate =
      new ModelGraphSnapshotUndo<>(
          DataVaultModel.class, HopVaultFileType.XML_TAG, DataVaultModel::new);

  public ModelGraphSnapshotUndo<DataVaultModel> getDelegate() {
    return delegate;
  }

  public void clear() {
    delegate.clear();
  }

  public boolean canUndo() {
    return delegate.canUndo();
  }

  public boolean canRedo() {
    return delegate.canRedo();
  }

  public boolean isApplyingSnapshot() {
    return delegate.isApplyingSnapshot();
  }

  public void markChange(DataVaultModel model, IHopMetadataProvider metadataProvider)
      throws HopException {
    delegate.markChange(model, metadataProvider);
  }

  public void pushSnapshot(byte[] snapshot) {
    delegate.pushSnapshot(snapshot);
  }

  public byte[] captureSnapshot(DataVaultModel model, IHopMetadataProvider metadataProvider)
      throws HopException {
    return delegate.captureSnapshot(model, metadataProvider);
  }

  public DataVaultModel undo(
      DataVaultModel model, IHopMetadataProvider metadataProvider, String preserveFilename)
      throws HopException {
    return delegate.undo(model, metadataProvider, preserveFilename);
  }

  public DataVaultModel redo(
      DataVaultModel model, IHopMetadataProvider metadataProvider, String preserveFilename)
      throws HopException {
    return delegate.redo(model, metadataProvider, preserveFilename);
  }
}
