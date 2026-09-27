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
package org.hopper.edw.datavault.transform.identitymapassign;

import java.sql.Timestamp;
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
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BvIdentityKeys;
import org.hopper.edw.datavault.metadata.businessvault.IdentityMapAssignLogic;
import org.hopper.edw.datavault.transform.dvhashkey.DvHashKeyLogic;

/** Freezes durable keys from a same-as link and the identity map rows already stored. */
public class IdentityMapAssign extends BaseTransform<IdentityMapAssignMeta, IdentityMapAssignData> {

  public IdentityMapAssign(
      TransformMeta transformMeta,
      IdentityMapAssignMeta meta,
      IdentityMapAssignData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (first) {
      first = false;
      load();
    }
    if (data.index >= data.changes.size()) {
      setOutputDone();
      return false;
    }
    IdentityMapAssignLogic.Assignment change = data.changes.get(data.index++);
    Object[] row = new Object[data.outputRowMeta.size()];
    row[data.outputRowMeta.indexOfValue(BvIdentityKeys.HK_RAW)] = change.raw;
    row[data.outputRowMeta.indexOfValue(BvIdentityKeys.HK_DURABLE)] = change.durable;
    row[data.outputRowMeta.indexOfValue(BvIdentityKeys.PREFERRED_BK)] = change.preferredBk;
    row[data.outputRowMeta.indexOfValue(BvIdentityKeys.HK_MASTER)] = change.master;
    row[data.outputRowMeta.indexOfValue(BvIdentityKeys.VALID_FROM)] = change.validFrom;
    row[data.outputRowMeta.indexOfValue(BvIdentityKeys.VALID_TO)] = change.validTo;
    row[data.outputRowMeta.indexOfValue(BvIdentityKeys.RECORD_SOURCE)] =
        resolve(meta.getRecordSource());
    row[data.outputRowMeta.indexOfValue(BvIdentityKeys.RULE_VERSION)] =
        (long) change.ruleVersion;
    putRow(data.outputRowMeta, row);
    return true;
  }

  private void load() throws HopException {
    data.outputRowMeta = new org.apache.hop.core.row.RowMeta();
    meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);
    List<Object[]> edgeRows = new ArrayList<>();
    List<Object[]> existingRows = new ArrayList<>();
    IRowMeta edgeMeta = null;
    IRowMeta existingMeta = null;
    IRowSet edges = findInputRowSet(resolve(meta.getEdgeTransform()));
    IRowSet existing = findInputRowSet(resolve(meta.getExistingTransform()));
    boolean edgeDone = edges == null;
    boolean existingDone = existing == null;
    while (!edgeDone || !existingDone) {
      if (!edgeDone) {
        Object[] row = getRowFrom(edges);
        if (row == null) {
          edgeDone = true;
        } else {
          if (edgeMeta == null) {
            edgeMeta = edges.getRowMeta();
          }
          edgeRows.add(row);
        }
      }
      if (!existingDone) {
        Object[] row = getRowFrom(existing);
        if (row == null) {
          existingDone = true;
        } else {
          if (existingMeta == null) {
            existingMeta = existing.getRowMeta();
          }
          existingRows.add(row);
        }
      }
    }
    List<IdentityMapAssignLogic.Edge> logicEdges = new ArrayList<>();
    if (edgeMeta != null) {
      for (Object[] row : edgeRows) {
        logicEdges.add(
            new IdentityMapAssignLogic.Edge(
                value(edgeMeta, row, meta.getMasterField()),
                value(edgeMeta, row, meta.getDuplicateField()),
                value(edgeMeta, row, meta.getPreferredField()),
                value(edgeMeta, row, meta.getMdmField()),
                date(edgeMeta, row, meta.getEdgeFromField()),
                date(edgeMeta, row, meta.getEdgeToField())));
      }
    }
    List<IdentityMapAssignLogic.Existing> logicExisting = new ArrayList<>();
    if (existingMeta != null) {
      for (Object[] row : existingRows) {
        Object version = value(existingMeta, row, BvIdentityKeys.RULE_VERSION);
        int ruleVersion = version instanceof Number number ? number.intValue() : 1;
        logicExisting.add(
            new IdentityMapAssignLogic.Existing(
                value(existingMeta, row, BvIdentityKeys.HK_RAW),
                value(existingMeta, row, BvIdentityKeys.HK_DURABLE),
                value(existingMeta, row, BvIdentityKeys.PREFERRED_BK),
                value(existingMeta, row, BvIdentityKeys.HK_MASTER),
                date(existingMeta, row, BvIdentityKeys.VALID_FROM),
                date(existingMeta, row, BvIdentityKeys.VALID_TO),
                ruleVersion));
      }
    }
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setHashAlgorithm(meta.getHashAlgorithm());
    config.setHashKeyDataType(meta.getHashKeyDataType());
    data.changes =
        IdentityMapAssignLogic.assign(
            logicExisting,
            logicEdges,
            value -> hash(config, value),
            Timestamp.valueOf(BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL));
  }

  private Object hash(DataVaultConfiguration config, Object value) {
    try {
      boolean binary = value instanceof byte[];
      return DvHashKeyLogic.computeHashFromValues(
          List.of(value), List.of(binary), config, this);
    } catch (HopException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Object value(IRowMeta rowMeta, Object[] row, String field) {
    if (rowMeta == null || row == null || field == null) {
      return null;
    }
    int index = rowMeta.indexOfValue(field);
    if (index < 0 || index >= row.length) {
      return null;
    }
    return row[index];
  }

  private static Date date(IRowMeta rowMeta, Object[] row, String field) throws HopException {
    if (rowMeta == null || row == null || field == null) {
      return null;
    }
    int index = rowMeta.indexOfValue(field);
    if (index < 0) {
      return null;
    }
    return rowMeta.getDate(row, index);
  }
}
