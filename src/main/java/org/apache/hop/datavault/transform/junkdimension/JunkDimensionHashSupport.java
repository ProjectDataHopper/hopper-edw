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
package org.apache.hop.datavault.transform.junkdimension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.datavault.metadata.HashAlgorithm;
import org.apache.hop.datavault.metadata.HashKeyDataType;
import org.apache.hop.datavault.metadata.dimensional.DmJunkHashCodeStrategy;
import org.apache.hop.datavault.metadata.dimensional.DmJunkSurrogateKeyStrategy;
import org.apache.hop.datavault.transform.dvhashkey.DvHashKeyLogic;

/** Hash computation for the JunkDimension transform. */
public final class JunkDimensionHashSupport {

  private JunkDimensionHashSupport() {}

  public static DmJunkHashCodeStrategy resolveStrategy(JunkDimensionMeta meta) {
    if (meta == null || meta.getHashCodeStrategy() == null) {
      return meta != null && meta.isUseHash()
          ? DmJunkHashCodeStrategy.INTEGER_LEGACY
          : DmJunkHashCodeStrategy.NONE;
    }
    return DmJunkHashCodeStrategy.lookupCode(meta.getHashCodeStrategy());
  }

  public static Object computeHash(
      DmJunkHashCodeStrategy strategy, IRowMeta rowMeta, Object[] hashRow) throws HopException {
    if (strategy == null || strategy == DmJunkHashCodeStrategy.NONE) {
      return null;
    }
    if (strategy == DmJunkHashCodeStrategy.INTEGER_LEGACY) {
      return (long) rowMeta.hashCode(hashRow);
    }
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < rowMeta.size(); i++) {
      if (i > 0) {
        builder.append('|');
      }
      builder.append(rowMeta.getString(hashRow, i));
    }
    MessageDigest digest = DvHashKeyLogic.createDigest(toHashAlgorithm(strategy));
    digest.update(builder.toString().getBytes(StandardCharsets.UTF_8));
    return DvHashKeyLogic.formatHashResult(digest.digest(), HashKeyDataType.STRING);
  }

  public static Object computeTechnicalKey(
      DmJunkSurrogateKeyStrategy strategy,
      DmJunkHashCodeStrategy hashStrategy,
      IRowMeta rowMeta,
      Object[] row,
      int[] keyIndexes,
      String surrogateKeySourceField)
      throws HopException {
    if (strategy == DmJunkSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      int idx = rowMeta.indexOfValue(surrogateKeySourceField);
      if (idx < 0) {
        throw new HopException("Surrogate key source field not found: " + surrogateKeySourceField);
      }
      return row[idx];
    }
    if (strategy == DmJunkSurrogateKeyStrategy.COMPUTE_HASH_KEY) {
      Object[] hashRow = new Object[keyIndexes.length];
      IRowMeta hashRowMeta = new org.apache.hop.core.row.RowMeta();
      for (int i = 0; i < keyIndexes.length; i++) {
        hashRowMeta.addValueMeta(rowMeta.getValueMeta(keyIndexes[i]));
        hashRow[i] = row[keyIndexes[i]];
      }
      DmJunkHashCodeStrategy resolvedHashStrategy =
          hashStrategy != null ? hashStrategy : DmJunkHashCodeStrategy.MD5;
      return computeHash(resolvedHashStrategy, hashRowMeta, hashRow);
    }
    return null;
  }

  public static boolean usesStringHashColumn(DmJunkHashCodeStrategy strategy) {
    return strategy != null && strategy.usesHashColumn() && !strategy.usesIntegerHash();
  }

  private static HashAlgorithm toHashAlgorithm(DmJunkHashCodeStrategy strategy) {
    return switch (strategy) {
      case MD5 -> HashAlgorithm.MD5;
      case SHA1 -> HashAlgorithm.SHA1;
      case SHA256 -> HashAlgorithm.SHA256;
      case SHA512 -> HashAlgorithm.SHA512;
      default -> HashAlgorithm.MD5;
    };
  }
}
