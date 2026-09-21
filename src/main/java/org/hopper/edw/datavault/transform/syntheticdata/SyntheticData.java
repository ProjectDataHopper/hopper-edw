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
package org.hopper.edw.datavault.transform.syntheticdata;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.core.IRowSet;

/** Input transform that emits synthetic rows and optionally writes a nested XML document. */
public class SyntheticData extends BaseTransform<SyntheticDataMeta, SyntheticDataData> {

  private static final Class<?> PKG = SyntheticData.class;

  private final SyntheticDataEngine engine = new SyntheticDataEngine();

  public SyntheticData(
      TransformMeta transformMeta,
      SyntheticDataMeta meta,
      SyntheticDataData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (!data.isStarted()) {
      data.setStarted(true);
      openCursor();
    }
    if (data.getRows() == null || !data.getRows().hasNext()) {
      writeDocument();
      setOutputDone();
      return false;
    }
    Map<String, Object> generated;
    try {
      generated = data.getRows().next();
    } catch (IllegalStateException e) {
      if (e.getCause() instanceof HopException hopException) {
        throw hopException;
      }
      throw new HopException(e.getMessage(), e);
    }
    if (data.getDocumentRows() != null) {
      data.getDocumentRows().add(generated);
    }
    Object[] output = RowDataUtil.allocateRowData(data.getOutputRowMeta().size());
    List<SyntheticField> published = data.getPublishedFields();
    for (int i = 0; i < published.size(); i++) {
      output[i] = generated.get(published.get(i).getName());
    }
    putRow(data.getOutputRowMeta(), output);
    return true;
  }

  private void openCursor() throws HopException {
    List<String> sources = new ArrayList<>();
    addSource(sources, meta.getParentsTransform());
    addSource(sources, meta.getChildrenTransform());
    addSource(sources, meta.getPairsLeftTransform());
    addSource(sources, meta.getPairsRightTransform());
    Map<String, List<Map<String, Object>>> loaded = drain(sources);

    IRowMeta outputRowMeta = new RowMeta();
    meta.getFields(outputRowMeta, getTransformName(), null, null, this, metadataProvider);
    data.setOutputRowMeta(outputRowMeta);
    List<SyntheticField> published = new ArrayList<>();
    for (SyntheticField field : meta.getFields()) {
      if (field != null && field.isPublish() && !Utils.isEmpty(field.getName())) {
        published.add(field);
      }
    }
    data.setPublishedFields(published);
    if ("XML".equalsIgnoreCase(meta.getDocumentFormat())) {
      data.setDocumentRows(new ArrayList<>());
    }
    data.setRows(
        engine.iterate(
            meta,
            this,
            loaded.getOrDefault(meta.getParentsTransform(), List.of()),
            loaded.getOrDefault(meta.getChildrenTransform(), List.of()),
            column(loaded, meta.getPairsLeftTransform(), meta.getPairLeftField()),
            column(loaded, meta.getPairsRightTransform(), meta.getPairRightField())));
  }

  private static void addSource(List<String> sources, String name) {
    if (!Utils.isEmpty(name) && !sources.contains(name)) {
      sources.add(name);
    }
  }

  /**
   * Reads every info rowset together. Draining one stream to completion before the next deadlocks
   * when a shared upstream transform is copied to more than one of them.
   */
  private Map<String, List<Map<String, Object>>> drain(List<String> sources) throws HopException {
    Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
    Map<String, IRowSet> sets = new LinkedHashMap<>();
    for (String source : sources) {
      rows.put(source, new ArrayList<>());
      sets.put(source, findInputRowSet(source));
    }
    Set<String> open = new HashSet<>(sources);
    while (!open.isEmpty()) {
      if (isStopped()) {
        break;
      }
      boolean progress = false;
      for (String source : new ArrayList<>(open)) {
        IRowSet rowSet = sets.get(source);
        Object[] row = rowSet.getRowImmediate();
        if (row != null) {
          IRowMeta rowMeta = rowSet.getRowMeta();
          rows.get(source).add(toMap(rowMeta, row));
          progress = true;
          continue;
        }
        if (rowSet.isDone()) {
          open.remove(source);
        }
      }
      if (!progress && !open.isEmpty()) {
        try {
          Thread.sleep(5L);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new HopException("Interrupted while reading synthetic info streams", e);
        }
      }
    }
    return rows;
  }

  private static List<Object> column(
      Map<String, List<Map<String, Object>>> loaded, String transform, String field) {
    if (Utils.isEmpty(transform) || Utils.isEmpty(field)) {
      return List.of();
    }
    List<Object> values = new ArrayList<>();
    for (Map<String, Object> row : loaded.getOrDefault(transform, List.of())) {
      values.add(row.get(field));
    }
    return values;
  }

  private static Map<String, Object> toMap(IRowMeta rowMeta, Object[] row) {
    Map<String, Object> values = new LinkedHashMap<>();
    if (rowMeta == null || row == null) {
      return values;
    }
    for (int i = 0; i < rowMeta.size() && i < row.length; i++) {
      values.put(rowMeta.getValueMeta(i).getName(), row[i]);
    }
    return values;
  }

  private void writeDocument() throws HopException {
    if (data.getDocumentRows() == null || Utils.isEmpty(meta.getDocumentFilename())) {
      return;
    }
    String filename = resolve(meta.getDocumentFilename());
    try {
      FileObject file = HopVfs.getFileObject(filename);
      if (file.getParent() != null && !file.getParent().exists()) {
        file.getParent().createFolder();
      }
      try (OutputStream out = HopVfs.getOutputStream(file, false)) {
        NestedXmlWriter.write(out, meta, this, data.getDocumentRows());
      }
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "SyntheticData.Error.Document", filename), e);
    }
  }
}
