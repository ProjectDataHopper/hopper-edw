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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.hopgui.file.executionmap.HopExecutionMapFileType;
import org.hopper.edw.datavault.hopgui.file.lineageview.HopLineageViewFileType;
import org.hopper.edw.datavault.hopgui.file.sourcemodel.HopSourceModelFileType;
import org.hopper.edw.datavault.lineageview.HopLineageViewDocument;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModelGraphSnapshotUndoTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void undoKeepsSourceModelFilename() throws Exception {
    SourceModel model = new SourceModel();
    model.setName("source-tables-crm");
    model.setFilename("/proj/models/source-tables-crm.hsm");
    ModelGraphSnapshotUndo<SourceModel> undo =
        new ModelGraphSnapshotUndo<>(
            SourceModel.class, HopSourceModelFileType.XML_TAG, SourceModel::new);
    undo.markChange(model, null);
    model.setDescription("edited");

    SourceModel restored = undo.undo(model, null, model.getFilename());

    assertEquals("/proj/models/source-tables-crm.hsm", restored.getFilename());
    assertNull(restored.getDescription());
  }

  @Test
  void undoKeepsDimensionalExecutionMapAndLineageFilenames() throws Exception {
    DimensionalModel dimensional = new DimensionalModel();
    dimensional.setName("orders");
    dimensional.setFilename("/proj/models/retail-f-orders.hdm");
    ModelGraphSnapshotUndo<DimensionalModel> dimensionalUndo =
        new ModelGraphSnapshotUndo<>(
            DimensionalModel.class, HopDimensionalFileType.XML_TAG, DimensionalModel::new);
    dimensionalUndo.markChange(dimensional, null);
    dimensional.setDescription("edited");
    assertEquals(
        "/proj/models/retail-f-orders.hdm",
        dimensionalUndo.undo(dimensional, null, dimensional.getFilename()).getFilename());

    ExecutionMapDocument executionMap = new ExecutionMapDocument();
    executionMap.setName("run");
    executionMap.setFilename("/proj/maps/run.hem");
    ModelGraphSnapshotUndo<ExecutionMapDocument> executionUndo =
        new ModelGraphSnapshotUndo<>(
            ExecutionMapDocument.class, HopExecutionMapFileType.XML_TAG, ExecutionMapDocument::new);
    executionUndo.markChange(executionMap, null);
    executionMap.setName("run-edited");
    assertEquals(
        "/proj/maps/run.hem",
        executionUndo.undo(executionMap, null, executionMap.getFilename()).getFilename());

    HopLineageViewDocument lineage = new HopLineageViewDocument();
    lineage.setName("orders");
    lineage.setFilename("/proj/lineage/orders.hlv");
    ModelGraphSnapshotUndo<HopLineageViewDocument> lineageUndo =
        new ModelGraphSnapshotUndo<>(
            HopLineageViewDocument.class,
            HopLineageViewFileType.XML_TAG,
            HopLineageViewDocument::new);
    lineageUndo.markChange(lineage, null);
    lineage.setDescription("edited");
    assertEquals(
        "/proj/lineage/orders.hlv",
        lineageUndo.undo(lineage, null, lineage.getFilename()).getFilename());
  }
}
