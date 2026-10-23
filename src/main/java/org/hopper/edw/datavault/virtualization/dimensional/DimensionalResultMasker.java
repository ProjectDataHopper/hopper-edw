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
package org.hopper.edw.datavault.virtualization.dimensional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.transforms.maskfields.MaskingEngine;
import org.apache.hop.pipeline.transforms.maskfields.MaskingKey;
import org.apache.hop.pipeline.transforms.maskfields.MaskingPattern;
import org.apache.hop.pipeline.transforms.maskfields.MaskingRuntime;
import org.apache.hop.pipeline.transforms.maskfields.MaskingStorage;
import org.apache.hop.pipeline.transforms.maskfields.MaskingToken;
import org.apache.hop.pipeline.transforms.maskfields.MaskingValueSource;
import org.apache.hop.pipeline.transforms.maskfields.store.IMaskingStore;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalFieldMasking;

/**
 * Applies Hop masking patterns to result cells. This is the same {@link MaskingEngine} the Mask
 * fields transform uses, run after the warehouse statement so bound parameters stay on that
 * statement.
 */
public final class DimensionalResultMasker {

  private DimensionalResultMasker() {}

  public static void apply(
      List<RowMetaAndData> rows,
      String[] outputPatterns,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (rows == null || rows.isEmpty() || outputPatterns == null) {
      return;
    }
    IRowMeta rowMeta = null;
    for (RowMetaAndData row : rows) {
      if (row != null && row.getRowMeta() != null) {
        rowMeta = row.getRowMeta();
        break;
      }
    }
    if (rowMeta == null) {
      return;
    }
    if (outputPatterns.length != rowMeta.size()) {
      throw new HopException(
          "Refusing to return rows: the warehouse result does not match the masked projection.");
    }
    MaskingRuntime.Lease lease =
        MaskingRuntime.getInstance().acquire("dimensional-jdbc-" + UUID.randomUUID());
    try {
      List<MaskingEngine.Binding> bindings = new ArrayList<>();
      for (int i = 0; i < outputPatterns.length; i++) {
        if (outputPatterns[i] == null || outputPatterns[i].isBlank()) {
          continue;
        }
        MaskingPattern pattern = DimensionalFieldMasking.load(metadataProvider, outputPatterns[i]);
        if (pattern == null) {
          throw new HopException(
              "Masking pattern '"
                  + outputPatterns[i]
                  + "' was not found. The column is not returned in the clear.");
        }
        if (StringUtils.isEmpty(pattern.getName())) {
          pattern.setName(outputPatterns[i]);
        }
        String fieldName = rowMeta.getValueMeta(i).getName();
        bindings.add(binding(fieldName, pattern, lease, variables, metadataProvider));
      }
      if (bindings.isEmpty()) {
        return;
      }
      MaskingEngine engine = new MaskingEngine(bindings);
      for (RowMetaAndData row : rows) {
        if (row == null || row.getData() == null) {
          continue;
        }
        engine.apply(row.getRowMeta() != null ? row.getRowMeta() : rowMeta, row.getData());
      }
    } finally {
      lease.release();
    }
  }

  /** Fails closed when a projected pattern cannot be loaded, before the warehouse is queried. */
  public static void requirePatterns(String[] outputPatterns, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (outputPatterns == null) {
      return;
    }
    for (String patternName : outputPatterns) {
      if (patternName == null || patternName.isBlank()) {
        continue;
      }
      if (DimensionalFieldMasking.load(metadataProvider, patternName) == null) {
        throw new HopException(
            "Masking pattern '"
                + patternName
                + "' was not found. The column is not returned in the clear.");
      }
    }
  }

  private static MaskingEngine.Binding binding(
      String fieldName,
      MaskingPattern pattern,
      MaskingRuntime.Lease lease,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    long start = parseStart(pattern, variables);
    IMaskingStore store = storeFor(pattern, lease, variables, metadataProvider);
    AtomicLong shared = null;
    if (!pattern.remembers()
        && pattern.getValueSource() == MaskingValueSource.SYNTHETIC
        && pattern.getToken() != MaskingToken.UUID) {
      shared = lease.sequence("dimensional-jdbc", fieldName, start);
    }
    String prefix = resolve(pattern.getPrefix(), variables);
    String suffix = resolve(pattern.getSuffix(), variables);
    return new MaskingEngine.Binding(
        fieldName,
        pattern,
        prefix,
        suffix,
        start,
        store,
        shared,
        MaskingKey.forPattern(pattern, variables));
  }

  private static IMaskingStore storeFor(
      MaskingPattern pattern,
      MaskingRuntime.Lease lease,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (!pattern.remembers()
        || pattern.getValueSource() == MaskingValueSource.SET_NULL
        || pattern.getValueSource() == MaskingValueSource.SET_EMPTY) {
      return null;
    }
    if (pattern.getStorage() == MaskingStorage.MEMORY) {
      return lease.memory();
    }
    String connectionName = resolve(pattern.getConnection(), variables);
    String schema = resolve(pattern.getSchemaName(), variables);
    String table = resolve(pattern.getTableName(), variables);
    if (StringUtils.isEmpty(connectionName) || StringUtils.isEmpty(table)) {
      throw new HopException(
          "Masking pattern '" + pattern.getName() + "' has no database map table.");
    }
    DatabaseMeta databaseMeta =
        metadataProvider == null
            ? null
            : metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    if (databaseMeta == null) {
      throw new HopException(
          "Masking pattern '" + pattern.getName() + "' connection '" + connectionName + "' was not found.");
    }
    return lease.database(
        new SimpleLoggingObject("dimensional-jdbc-mask", LoggingObjectType.GENERAL, null),
        variables,
        databaseMeta,
        schema,
        table);
  }

  private static long parseStart(MaskingPattern pattern, IVariables variables) throws HopException {
    String text = resolve(pattern.getSequenceStart(), variables);
    if (StringUtils.isBlank(text)) {
      return 1L;
    }
    try {
      return Long.parseLong(text.trim());
    } catch (NumberFormatException e) {
      throw new HopException(
          "Masking pattern '" + pattern.getName() + "' has an invalid sequence start.");
    }
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return "";
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
