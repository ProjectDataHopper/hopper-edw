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
package org.hopper.edw.datavault.transform.identitylookup;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.hopper.edw.datavault.metadata.businessvault.IdentityLookupLogic;

/**
 * Replaces the raw hub hash with the durable hash in effect at the row timestamp. The identity map
 * is read from the named input. Rows are consumed round-robin so neither input fills the rowset
 * buffer while the other waits.
 */
public class IdentityLookup extends BaseTransform<IdentityLookupMeta, IdentityLookupData> {

  public IdentityLookup(
      TransformMeta transformMeta,
      IdentityLookupMeta meta,
      IdentityLookupData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (first) {
      first = false;
      if (!load()) {
        setOutputDone();
        return false;
      }
    }
    if (data.index >= data.hits.size()) {
      setOutputDone();
      return false;
    }
    IdentityLookupLogic.Hit hit = data.hits.get(data.index);
    Object[] source = data.mainRows.get(data.index);
    data.index++;
    if (!hit.emit) {
      if (hit.quarantine) {
        setLinesRejected(getLinesRejected() + 1);
      }
      return true;
    }
    Object[] output = new Object[data.outputRowMeta.size()];
    System.arraycopy(source, 0, output, 0, Math.min(source.length, output.length));
    if (data.rawIndex >= 0) {
      output[data.rawIndex] = hit.key;
    }
    if (data.rawPayloadIndex >= 0) {
      output[data.rawPayloadIndex] = hit.raw;
    }
    if (data.preferredIndex >= 0) {
      output[data.preferredIndex] = hit.preferredBk;
    }
    if (data.ruleVersionIndex >= 0) {
      output[data.ruleVersionIndex] = hit.ruleVersion == null ? null : hit.ruleVersion.longValue();
    }
    putRow(data.outputRowMeta, output);
    return true;
  }

  private boolean load() throws HopException {
    IRowSet mainSet = findMainRowSet();
    IRowSet mapSet = findInputRowSet(field(meta.getMapTransform()));
    if (mainSet == null) {
      logError("Identity lookup has no main input");
      return false;
    }
    List<Object[]> mapRows = new ArrayList<>();
    IRowMeta mapMeta = null;
    IRowMeta mainMeta = null;
    boolean mainDone = false;
    boolean mapDone = mapSet == null;
    while (!mainDone || !mapDone) {
      if (!mainDone) {
        Object[] row = getRowFrom(mainSet);
        if (row == null) {
          mainDone = true;
        } else {
          if (mainMeta == null) {
            mainMeta = mainSet.getRowMeta();
          }
          data.mainRows.add(row);
        }
      }
      if (!mapDone) {
        Object[] row = getRowFrom(mapSet);
        if (row == null) {
          mapDone = true;
        } else {
          if (mapMeta == null) {
            mapMeta = mapSet.getRowMeta();
          }
          mapRows.add(row);
        }
      }
    }
    if (mainMeta == null) {
      return true;
    }
    data.outputRowMeta = mainMeta.clone();
    meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);
    data.rawIndex = mainMeta.indexOfValue(field(meta.getRawKeyField()));
    data.timestampIndex = mainMeta.indexOfValue(field(meta.getTimestampField()));
    data.rawPayloadIndex = data.outputRowMeta.indexOfValue(field(meta.getRawPayloadField()));
    data.preferredIndex = data.outputRowMeta.indexOfValue(field(meta.getPreferredPayloadField()));
    data.ruleVersionIndex = data.outputRowMeta.indexOfValue(field(meta.getRuleVersionPayloadField()));

    List<IdentityLookupLogic.MainRow> mains = new ArrayList<>();
    for (Object[] row : data.mainRows) {
      Object raw = data.rawIndex < 0 ? null : row[data.rawIndex];
      Date timestamp = null;
      if (data.timestampIndex >= 0) {
        timestamp = mainMeta.getDate(row, data.timestampIndex);
      }
      mains.add(new IdentityLookupLogic.MainRow(raw, timestamp));
    }
    List<IdentityLookupLogic.Interval> intervals = new ArrayList<>();
    if (mapMeta != null) {
      int rawIndex = mapMeta.indexOfValue(field(meta.getMapRawField()));
      int durableIndex = mapMeta.indexOfValue(field(meta.getMapDurableField()));
      int preferredIndex = mapMeta.indexOfValue(field(meta.getMapPreferredField()));
      int fromIndex = mapMeta.indexOfValue(field(meta.getMapValidFromField()));
      int toIndex = mapMeta.indexOfValue(field(meta.getMapValidToField()));
      int versionIndex = mapMeta.indexOfValue(field(meta.getMapRuleVersionField()));
      for (Object[] row : mapRows) {
        Integer ruleVersion = null;
        if (versionIndex >= 0 && row[versionIndex] instanceof Number number) {
          ruleVersion = number.intValue();
        }
        intervals.add(
            new IdentityLookupLogic.Interval(
                rawIndex < 0 ? null : row[rawIndex],
                durableIndex < 0 ? null : row[durableIndex],
                preferredIndex < 0 ? null : row[preferredIndex],
                fromIndex < 0 ? null : mapMeta.getDate(row, fromIndex),
                toIndex < 0 ? null : mapMeta.getDate(row, toIndex),
                ruleVersion));
      }
    }
    data.hits =
        IdentityLookupLogic.lookupAll(mains, intervals, meta.unmappedPolicyOrDefault());
    return true;
  }

  private IRowSet findMainRowSet() {
    String mapName = field(meta.getMapTransform());
    for (IRowSet rowSet : getInputRowSets()) {
      if (rowSet == null) {
        continue;
      }
      if (mapName != null && mapName.equals(rowSet.getOriginTransformName())) {
        continue;
      }
      return rowSet;
    }
    return null;
  }

  private String field(String value) {
    if (value == null) {
      return null;
    }
    return super.resolve(value);
  }
}
