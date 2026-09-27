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
package org.hopper.edw.datavault.transform.hashkeypartition;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.hopper.edw.datavault.metadata.HashKeyDataType;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2HashPartitionSqlSupport;
import org.hopper.edw.datavault.metadata.businessvault.HashKeyPartitionLogic;

/** Drops rows that do not belong to this hash-key partition. */
public class HashKeyPartition extends BaseTransform<HashKeyPartitionMeta, HashKeyPartitionData> {

  public HashKeyPartition(
      TransformMeta transformMeta,
      HashKeyPartitionMeta meta,
      HashKeyPartitionData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    if (row == null) {
      setOutputDone();
      return false;
    }
    if (first) {
      first = false;
      data.outputRowMeta = getInputRowMeta().clone();
      data.keyIndex = getInputRowMeta().indexOfValue(resolve(meta.getKeyField()));
      data.hashKeyDataType = HashKeyDataType.lookupCode(meta.getHashKeyDataType());
      data.count = parse(resolve("${" + BvScd2HashPartitionSqlSupport.PARTITION_COUNT_VARIABLE + "}"), 1);
      data.number =
          parse(resolve("${" + BvScd2HashPartitionSqlSupport.PARTITION_NUMBER_VARIABLE + "}"), 0);
    }
    Object key = data.keyIndex < 0 ? null : row[data.keyIndex];
    if (!HashKeyPartitionLogic.inPartition(key, data.hashKeyDataType, data.count, data.number)) {
      return true;
    }
    putRow(data.outputRowMeta, row);
    return true;
  }

  private static int parse(String text, int fallback) {
    if (text == null || text.isBlank() || text.startsWith("${")) {
      return fallback;
    }
    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
