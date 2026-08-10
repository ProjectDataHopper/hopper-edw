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
package org.apache.hop.catalog.transform.recorddatainput;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DvSourcePreviewInputSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.pipeline.transform.RowAdapter;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Streams actual data rows from a catalog record definition using the same source pipeline builders
 * as catalog Preview data.
 */
public class RecordDefinitionDataInput
    extends BaseTransform<RecordDefinitionDataInputMeta, RecordDefinitionDataInputData> {

  private static final Class<?> PKG = RecordDefinitionDataInputMeta.class;
  private static final Object[] END_MARKER = new Object[0];
  private static final int QUEUE_CAPACITY = 1024;

  public RecordDefinitionDataInput(
      TransformMeta transformMeta,
      RecordDefinitionDataInputMeta meta,
      RecordDefinitionDataInputData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (meta.isSelectFromInput()) {
      return processSelectFromInput();
    }
    return processFixedDefinition();
  }

  private boolean processFixedDefinition() throws HopException {
    if (first) {
      first = false;
      data.outputRowMeta = new RowMeta();
      meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);
      if (data.outputRowMeta.isEmpty()) {
        // getFields may have been empty at design time; resolve now.
        RecordDefinition definition =
            RecordDefinitionDataInputSupport.loadDefinition(
                meta.getCatalogConnectionName(),
                meta.getNamespaceValue(),
                meta.getNameValue(),
                this,
                metadataProvider);
        data.outputRowMeta =
            RecordDefinitionDataInputSupport.resolveOutputRowMeta(
                definition, this, getTransformName());
      }
      startSourcePipeline(
          meta.getNamespaceValue(), meta.getNameValue(), data.outputRowMeta, parseRowLimit());
    }

    return emitFromQueue();
  }

  private boolean processSelectFromInput() throws HopException {
    Object[] row = getRow();
    if (row == null) {
      setOutputDone();
      return false;
    }

    if (first) {
      first = false;
      if (getInputRowMeta() == null) {
        throw new HopException(
            BaseMessages.getString(PKG, "RecordDefinitionDataInput.Error.NoInputRowMeta"));
      }
      String namespaceField = resolve(meta.getNamespaceField());
      String nameField = resolve(meta.getNameField());
      data.namespaceFieldIndex = getInputRowMeta().indexOfValue(namespaceField);
      data.nameFieldIndex = getInputRowMeta().indexOfValue(nameField);
      if (data.namespaceFieldIndex < 0) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "RecordDefinitionDataInput.Error.MissingInputField", namespaceField));
      }
      if (data.nameFieldIndex < 0) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "RecordDefinitionDataInput.Error.MissingInputField", nameField));
      }
      // First output row meta is established from the first definition we successfully load.
    }

    String namespace = getInputRowMeta().getString(row, data.namespaceFieldIndex);
    String name = getInputRowMeta().getString(row, data.nameFieldIndex);
    if (Utils.isEmpty(namespace) || Utils.isEmpty(name)) {
      logBasic(
          "Skipping input row with empty namespace/name for catalog data read (transform "
              + getTransformName()
              + ")");
      return true;
    }

    RecordDefinition definition =
        RecordDefinitionDataInputSupport.loadDefinition(
            meta.getCatalogConnectionName(), namespace, name, this, metadataProvider);
    IRowMeta defFields =
        RecordDefinitionDataInputSupport.resolveOutputRowMeta(definition, this, getTransformName());
    if (data.outputRowMeta == null) {
      data.outputRowMeta = defFields.clone();
    }
    startSourcePipeline(namespace, name, defFields, parseRowLimit());

    // Emit all rows for this definition before consuming the next input row.
    while (true) {
      if (!emitFromQueue()) {
        break;
      }
    }
    cleanupSourcePipeline();
    return true;
  }

  private boolean emitFromQueue() throws HopException {
    if (data.rowQueue == null) {
      setOutputDone();
      return false;
    }
    try {
      Object[] next = data.rowQueue.poll(100, TimeUnit.MILLISECONDS);
      while (next == null) {
        if (data.sourceError != null && data.sourceError.get() != null) {
          throw new HopException(
              BaseMessages.getString(PKG, "RecordDefinitionDataInput.Error.SourceFailed"),
              data.sourceError.get());
        }
        if (data.sourceFinished != null && data.sourceFinished.get() && data.rowQueue.isEmpty()) {
          if (!meta.isSelectFromInput()) {
            setOutputDone();
          }
          return false;
        }
        if (isStopped()) {
          stopSourcePipeline();
          setOutputDone();
          return false;
        }
        next = data.rowQueue.poll(100, TimeUnit.MILLISECONDS);
      }
      if (next == END_MARKER) {
        if (data.sourceError != null && data.sourceError.get() != null) {
          throw new HopException(
              BaseMessages.getString(PKG, "RecordDefinitionDataInput.Error.SourceFailed"),
              data.sourceError.get());
        }
        if (!meta.isSelectFromInput()) {
          setOutputDone();
        }
        return false;
      }
      putRow(data.outputRowMeta, next);
      data.rowsEmitted++;
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      stopSourcePipeline();
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionDataInput.Error.Interrupted"), e);
    }
  }

  private void startSourcePipeline(
      String namespace, String name, IRowMeta expectedFields, int rowLimit) throws HopException {
    RecordDefinition definition =
        RecordDefinitionDataInputSupport.loadDefinition(
            meta.getCatalogConnectionName(), namespace, name, this, metadataProvider);

    DvSourcePreviewInputSupport.PreviewPipeline preview =
        RecordDefinitionDataInputSupport.buildSourcePipeline(
            definition, this, metadataProvider, rowLimit);

    PipelineMeta sourceMeta = preview.pipelineMeta();
    String previewTransformName = preview.previewTransformName();
    sourceMeta.setName("record-definition-data-" + Const.NVL(name, "source"));

    data.rowQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    data.sourceFinished = new AtomicBoolean(false);
    data.sourceError = new AtomicReference<>();
    data.rowsEmitted = 0;
    data.sourceStarted = true;

    LocalPipelineEngine pipeline = new LocalPipelineEngine(sourceMeta, this, this);
    pipeline.setMetadataProvider(metadataProvider);
    pipeline.copyFrom(this);
    data.sourcePipeline = pipeline;

    try {
      pipeline.prepareExecution();
      ITransform runThread = pipeline.findRunThread(previewTransformName);
      if (runThread == null) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "RecordDefinitionDataInput.Error.PreviewTransformMissing",
                previewTransformName));
      }
      final IRowMeta outputMeta = expectedFields != null ? expectedFields : data.outputRowMeta;
      runThread.addRowListener(
          new RowAdapter() {
            @Override
            public void rowWrittenEvent(IRowMeta rowMeta, Object[] row) {
              try {
                Object[] out = mapToOutput(rowMeta, row, outputMeta);
                if (!data.rowQueue.offer(out, 30, TimeUnit.SECONDS)) {
                  data.sourceError.compareAndSet(
                      null,
                      new HopException(
                          BaseMessages.getString(
                              PKG, "RecordDefinitionDataInput.Error.QueueFull")));
                  try {
                    pipeline.stopAll();
                  } catch (Exception ignored) {
                    // best effort
                  }
                }
              } catch (Exception e) {
                data.sourceError.compareAndSet(null, e);
                try {
                  pipeline.stopAll();
                } catch (Exception ignored) {
                  // best effort
                }
              }
            }
          });

      data.sourceThread =
          new Thread(
              () -> {
                try {
                  pipeline.startThreads();
                  pipeline.waitUntilFinished();
                  if (pipeline.getErrors() > 0 && data.sourceError.get() == null) {
                    data.sourceError.compareAndSet(
                        null,
                        new HopException(
                            BaseMessages.getString(
                                PKG,
                                "RecordDefinitionDataInput.Error.SourceErrors",
                                Long.toString(pipeline.getErrors()))));
                  }
                } catch (Exception e) {
                  data.sourceError.compareAndSet(null, e);
                } finally {
                  data.sourceFinished.set(true);
                  data.rowQueue.offer(END_MARKER);
                  try {
                    pipeline.cleanup();
                  } catch (Exception ignored) {
                    // ignore
                  }
                }
              },
              "RecordDefinitionDataInput-" + getTransformName());
      data.sourceThread.setDaemon(true);
      data.sourceThread.start();
    } catch (HopException e) {
      cleanupSourcePipeline();
      throw e;
    } catch (Exception e) {
      cleanupSourcePipeline();
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionDataInput.Error.StartSourceFailed"), e);
    }
  }

  private static Object[] mapToOutput(IRowMeta sourceMeta, Object[] sourceRow, IRowMeta outputMeta)
      throws HopException {
    if (outputMeta == null) {
      return sourceMeta.cloneRow(sourceRow);
    }
    Object[] out = RowDataUtil.allocateRowData(outputMeta.size());
    for (int i = 0; i < outputMeta.size(); i++) {
      String fieldName = outputMeta.getValueMeta(i).getName();
      int idx = sourceMeta.indexOfValue(fieldName);
      if (idx >= 0) {
        out[i] =
            outputMeta.getValueMeta(i).convertData(sourceMeta.getValueMeta(idx), sourceRow[idx]);
      }
    }
    return out;
  }

  private int parseRowLimit() {
    String raw = resolve(meta.getRowLimit());
    if (Utils.isEmpty(raw)) {
      return 0;
    }
    try {
      return Math.max(0, Integer.parseInt(raw.trim()));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private void stopSourcePipeline() {
    if (data.sourcePipeline != null) {
      try {
        data.sourcePipeline.stopAll();
      } catch (Exception ignored) {
        // best effort
      }
    }
  }

  private void cleanupSourcePipeline() {
    stopSourcePipeline();
    if (data.sourceThread != null) {
      try {
        data.sourceThread.join(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    data.sourcePipeline = null;
    data.sourceThread = null;
    data.sourceStarted = false;
    data.rowQueue = null;
  }

  @Override
  public void dispose() {
    cleanupSourcePipeline();
    super.dispose();
  }
}
