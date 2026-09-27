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
package org.hopper.edw.datavault.transform.survivorshipmerge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.hopper.edw.datavault.metadata.businessvault.BvLegOperation;
import org.hopper.edw.datavault.metadata.businessvault.BvNullPolicy;
import org.hopper.edw.datavault.metadata.businessvault.BvSurvivorshipSupport;
import org.hopper.edw.datavault.metadata.businessvault.SurvivorshipMergeLogic;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeLogic;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeRow;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeSortKey;

/**
 * Merges pre-sorted legs and emits a row when the surviving field vector changes. Inputs are read
 * round-robin from their row sets so one leg cannot fill the buffer while another waits.
 */
public class SurvivorshipMerge extends BaseTransform<SurvivorshipMergeMeta, SurvivorshipMergeData> {

  private static final Class<?> PKG = SurvivorshipMergeMeta.class;

  private final List<Held> group = new ArrayList<>();

  public SurvivorshipMerge(
      TransformMeta transformMeta,
      SurvivorshipMergeMeta meta,
      SurvivorshipMergeData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (first) {
      first = false;
      if (!initialize()) {
        setOutputDone();
        return false;
      }
    }
    while (data.pending.isEmpty()) {
      if (data.finished) {
        setOutputDone();
        return false;
      }
      SortedSchemaMergeRow next = pull();
      if (next == null) {
        flush();
        data.finished = true;
        if (data.pending.isEmpty()) {
          setOutputDone();
          return false;
        }
        break;
      }
      accept(next);
    }
    putRow(data.outputRowMeta, data.pending.remove(0));
    return true;
  }

  private void accept(SortedSchemaMergeRow next) throws HopException {
    boolean newKey = !group.isEmpty() && !sameKey(group.get(0).row, next);
    boolean newTime = !group.isEmpty() && (newKey || !sameTime(group.get(0).row, next));
    if (newTime) {
      flush();
    }
    if (newKey) {
      data.state = new LinkedHashMap<>();
      data.previous = null;
    }
    group.add(new Held(next, event(next)));
  }

  private void flush() throws HopException {
    if (group.isEmpty()) {
      return;
    }
    List<SurvivorshipMergeLogic.Event> events = new ArrayList<>();
    for (Held held : group) {
      events.add(held.event);
    }
    SurvivorshipMergeLogic.Vector vector =
        SurvivorshipMergeLogic.applyGroup(data.state, data.previous, events, data.rules);
    if (vector != null) {
      Held basis = group.get(group.size() - 1);
      Object[] output =
          data.schemaMapping.mapRow(basis.row.getStreamIndex(), basis.row.getRowData());
      for (Map.Entry<String, Object> entry : vector.values.entrySet()) {
        int index = data.outputRowMeta.indexOfValue(entry.getKey());
        if (index >= 0 && index < output.length) {
          output[index] = entry.getValue();
        }
      }
      if (vector.sourceId != null) {
        int sourceIndex = data.outputRowMeta.indexOfValue(field(meta.getSourceField()));
        if (sourceIndex >= 0 && sourceIndex < output.length) {
          output[sourceIndex] = vector.sourceId;
        }
      }
      data.previous = vector.values;
      data.pending.add(output);
    }
    group.clear();
  }

  private boolean initialize() throws HopException {
    if (meta.getKeys() == null || meta.getKeys().isEmpty()) {
      logError(BaseMessages.getString(PKG, "SurvivorshipMerge.Error.NoKeys"));
      return false;
    }
    if (meta.getTimestampField() == null || meta.getTimestampField().isBlank()) {
      logError(BaseMessages.getString(PKG, "SurvivorshipMerge.Error.NoTimestamp"));
      return false;
    }
    data.sortKeys = new ArrayList<>();
    for (SurvivorshipMergeKey key : meta.getKeys()) {
      if (key != null && key.getFieldName() != null && !key.getFieldName().isBlank()) {
        data.sortKeys.add(new SortedSchemaMergeSortKey(field(key.getFieldName()), true));
      }
    }
    data.keyCount = data.sortKeys.size();
    if (data.keyCount == 0) {
      logError(BaseMessages.getString(PKG, "SurvivorshipMerge.Error.NoKeys"));
      return false;
    }
    data.sortKeys.add(new SortedSchemaMergeSortKey(field(meta.getTimestampField()), true));
    data.rules = new ArrayList<>();
    if (meta.getRules() != null) {
      for (SurvivorshipMergeRule rule : meta.getRules()) {
        if (rule == null || rule.getFieldName() == null || rule.getSourceId() == null) {
          continue;
        }
        Integer rank = BvSurvivorshipSupport.parseRank(field(rule.getRank()));
        if (rank == null) {
          continue;
        }
        data.rules.add(
            new SurvivorshipMergeLogic.Rule(
                field(rule.getFieldName()),
                field(rule.getSourceId()),
                rank,
                BvNullPolicy.lookupCode(field(rule.getNullPolicy())),
                BvLegOperation.lookupCode(field(rule.getOperation())),
                field(rule.getPresentFlagField())));
      }
    }
    List<IRowSet> inputRowSets = getInputRowSets();
    if (inputRowSets == null || inputRowSets.isEmpty()) {
      logError(BaseMessages.getString(PKG, "SurvivorshipMerge.Error.NoInputs"));
      return false;
    }
    data.sortedBuffer = new ArrayList<>();
    List<IRowMeta> inputLayouts = new ArrayList<>();
    for (int i = inputRowSets.size() - 1; i >= 0 && !isStopped(); i--) {
      IRowSet rowSet = inputRowSets.get(i);
      Object[] row = getRowFrom(rowSet);
      if (row != null) {
        int streamIndex = inputLayouts.size();
        inputLayouts.add(rowSet.getRowMeta());
        data.sortedBuffer.add(new SortedSchemaMergeRow(streamIndex, rowSet, rowSet.getRowMeta(), row));
      }
    }
    if (data.sortedBuffer.isEmpty()) {
      data.finished = true;
      return true;
    }
    IRowMeta[] inputRowMetas = inputLayouts.toArray(new IRowMeta[0]);
    data.schemaMapping = SortedSchemaMergeLogic.buildSchemaMapping(inputRowMetas);
    data.outputRowMeta = data.schemaMapping.getOutputRowMeta().clone();
    for (int i = 0; i < data.outputRowMeta.size(); i++) {
      data.outputRowMeta.getValueMeta(i).setOrigin(getTransformName());
    }
    data.sortKeyIndices = SortedSchemaMergeLogic.resolveSortKeyIndices(inputRowMetas, data.sortKeys);
    data.comparator =
        (left, right) -> {
          try {
            return SortedSchemaMergeLogic.compareRows(
                left, right, data.sortKeyIndices, data.sortKeys);
          } catch (HopValueException e) {
            throw new IllegalStateException("Error comparing survivorship rows", e);
          }
        };
    data.sortedBuffer.sort(data.comparator);
    return true;
  }

  private SortedSchemaMergeRow pull() throws HopException {
    if (data.sortedBuffer == null || data.sortedBuffer.isEmpty()) {
      return null;
    }
    SortedSchemaMergeRow smallest = data.sortedBuffer.remove(0);
    Object[] extra = getRowFrom(smallest.getRowSet());
    if (extra != null) {
      SortedSchemaMergeRow add =
          new SortedSchemaMergeRow(
              smallest.getStreamIndex(), smallest.getRowSet(), smallest.getRowSet().getRowMeta(), extra);
      int index = Collections.binarySearch(data.sortedBuffer, add, data.comparator);
      if (index < 0) {
        data.sortedBuffer.add(-index - 1, add);
      } else {
        data.sortedBuffer.add(index, add);
      }
    }
    return smallest;
  }

  private boolean sameKey(SortedSchemaMergeRow left, SortedSchemaMergeRow right)
      throws HopException {
    try {
      return SortedSchemaMergeLogic.compareRows(
              left, right, data.sortKeyIndices, data.sortKeys.subList(0, data.keyCount))
          == 0;
    } catch (HopValueException e) {
      throw new HopTransformException(e);
    }
  }

  private boolean sameTime(SortedSchemaMergeRow left, SortedSchemaMergeRow right)
      throws HopException {
    Date leftTime = date(left);
    Date rightTime = date(right);
    if (leftTime == null || rightTime == null) {
      return leftTime == rightTime;
    }
    return leftTime.getTime() == rightTime.getTime();
  }

  private SurvivorshipMergeLogic.Event event(SortedSchemaMergeRow row) throws HopException {
    String source = text(row, meta.getSourceField());
    BvLegOperation operation = BvLegOperation.lookupCode(text(row, meta.getOpField()));
    if (operation == null && source != null) {
      for (SurvivorshipMergeLogic.Rule rule : data.rules) {
        if (source.equals(rule.sourceId)) {
          operation = rule.operation;
          break;
        }
      }
    }
    Map<String, Object> values = new LinkedHashMap<>();
    Set<String> cleared = new LinkedHashSet<>();
    if (source != null) {
      for (SurvivorshipMergeLogic.Rule rule : data.rules) {
        if (!source.equals(rule.sourceId)) {
          continue;
        }
        int index = row.getRowMeta().indexOfValue(rule.field);
        if (index < 0 || index >= row.getRowData().length) {
          continue;
        }
        Object flag = null;
        if (rule.presentFlagField != null && !rule.presentFlagField.isBlank()) {
          int flagIndex = row.getRowMeta().indexOfValue(rule.presentFlagField);
          if (flagIndex >= 0 && flagIndex < row.getRowData().length) {
            flag = row.getRowData()[flagIndex];
          }
        }
        SurvivorshipMergeLogic.readField(
            values, cleared, rule.field, row.getRowData()[index], rule.presentFlagField, flag);
      }
    }
    return new SurvivorshipMergeLogic.Event(null, date(row), source, operation, values, cleared);
  }

  private Date date(SortedSchemaMergeRow row) throws HopException {
    int index = row.getRowMeta().indexOfValue(field(meta.getTimestampField()));
    if (index < 0) {
      return null;
    }
    return row.getRowMeta().getDate(row.getRowData(), index);
  }

  private String text(SortedSchemaMergeRow row, String name) throws HopException {
    int index = row.getRowMeta().indexOfValue(field(name));
    if (index < 0) {
      return null;
    }
    return row.getRowMeta().getString(row.getRowData(), index);
  }

  private String field(String value) {
    if (value == null) {
      return null;
    }
    return super.resolve(value);
  }

  private static final class Held {
    private final SortedSchemaMergeRow row;
    private final SurvivorshipMergeLogic.Event event;

    private Held(SortedSchemaMergeRow row, SurvivorshipMergeLogic.Event event) {
      this.row = row;
      this.event = event;
    }
  }
}
