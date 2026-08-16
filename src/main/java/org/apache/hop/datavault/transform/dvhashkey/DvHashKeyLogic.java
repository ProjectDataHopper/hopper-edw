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
package org.apache.hop.datavault.transform.dvhashkey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.binary.Hex;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.HashAlgorithm;
import org.apache.hop.datavault.metadata.HashContentCasing;
import org.apache.hop.datavault.metadata.HashKeyDataType;

/** Shared hash-key input construction and digest formatting for the DvHashKey transform. */
public final class DvHashKeyLogic {

  private DvHashKeyLogic() {}

  public static String digestAlgorithmName(HashAlgorithm algorithm) throws HopException {
    HashAlgorithm algo = algorithm != null ? algorithm : HashAlgorithm.MD5;
    return switch (algo) {
      case MD5 -> "MD5";
      case SHA1 -> "SHA-1";
      case SHA256 -> "SHA-256";
      case SHA512 -> "SHA-512";
    };
  }

  public static MessageDigest createDigest(HashAlgorithm algorithm) throws HopException {
    try {
      return MessageDigest.getInstance(digestAlgorithmName(algorithm));
    } catch (NoSuchAlgorithmException e) {
      throw new HopException("Unsupported hash algorithm: " + algorithm, e);
    }
  }

  /**
   * Builds the byte sequence that is hashed. Returns {@code null} when no content was contributed
   * (all-null fields with no prefix/suffix).
   */
  public static byte[] buildHashInput(
      Object[] row, IRowMeta rowMeta, int[] fieldIndexes, DvHashKeyMeta meta, IVariables variables)
      throws HopException {
    if (row == null || rowMeta == null || fieldIndexes == null || fieldIndexes.length == 0) {
      return null;
    }

    String prefix = resolve(meta.getHashContentPrefix(), variables);
    String suffix = resolve(meta.getHashContentSuffix(), variables);
    String delimiter = resolve(meta.getBusinessKeyDelimiter(), variables);
    String nullPlaceholder = resolve(meta.getNullPlaceholder(), variables);
    boolean trim = meta.isTrimBusinessKeys();
    HashContentCasing casing =
        meta.getHashContentCasing() != null ? meta.getHashContentCasing() : HashContentCasing.UPPER;

    StringBuilder builder = new StringBuilder();
    if (!Utils.isEmpty(prefix)) {
      builder.append(prefix);
    }

    boolean valueAdded = !builder.isEmpty();
    for (int i = 0; i < fieldIndexes.length; i++) {
      int fieldIndex = fieldIndexes[i];
      IValueMeta valueMeta = rowMeta.getValueMeta(fieldIndex);
      Object value = row[fieldIndex];

      String part;
      if (value == null) {
        if (Utils.isEmpty(nullPlaceholder)) {
          continue;
        }
        part = nullPlaceholder;
      } else if (valueMeta.isBinary()) {
        byte[] bytes = rowMeta.getBinary(row, fieldIndex);
        if (bytes == null || bytes.length == 0) {
          if (Utils.isEmpty(nullPlaceholder)) {
            continue;
          }
          part = nullPlaceholder;
        } else {
          part = new String(bytes, StandardCharsets.ISO_8859_1);
        }
      } else {
        // Compatible string, not getString(): Integer/Number length + DecimalFormat would
        // otherwise hash padded values such as " 000001001" while JDBC/hub loads hash "1001".
        part = valueMeta.getCompatibleString(value);
        if (Utils.isEmpty(part)) {
          if (Utils.isEmpty(nullPlaceholder)) {
            continue;
          }
          part = nullPlaceholder;
        }
      }

      if (trim && part != null) {
        part = part.trim();
      }
      part = applyCasing(part, casing);
      if (Utils.isEmpty(part)) {
        continue;
      }

      if (valueAdded && !Utils.isEmpty(delimiter)) {
        builder.append(delimiter);
      }
      builder.append(part);
      valueAdded = true;
    }

    if (!Utils.isEmpty(suffix)) {
      builder.append(suffix);
      valueAdded = true;
    }

    if (!valueAdded) {
      return null;
    }
    return builder.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Computes a hash key from explicit field values using the same rules as the DvHashKey transform.
   */
  public static Object computeHashFromValues(
      List<Object> values,
      List<Boolean> binaryFlags,
      DataVaultConfiguration config,
      IVariables variables)
      throws HopException {
    if (values == null || values.isEmpty()) {
      return null;
    }
    DvHashKeyMeta meta = DvHashKeyMetaFactory.create(config, new ArrayList<>(), "hash");
    RowMeta rowMeta = new RowMeta();
    Object[] row = new Object[values.size()];
    int[] fieldIndexes = new int[values.size()];
    for (int i = 0; i < values.size(); i++) {
      boolean binary =
          binaryFlags != null && i < binaryFlags.size() && Boolean.TRUE.equals(binaryFlags.get(i));
      IValueMeta valueMeta = binary ? new ValueMetaBinary("f" + i) : new ValueMetaString("f" + i);
      rowMeta.addValueMeta(valueMeta);
      fieldIndexes[i] = i;
      row[i] = values.get(i);
    }
    byte[] inputBytes = buildHashInput(row, rowMeta, fieldIndexes, meta, variables);
    if (inputBytes == null) {
      return null;
    }
    MessageDigest digest = createDigest(meta.getHashAlgorithm());
    digest.update(inputBytes);
    return formatHashResult(digest.digest(), meta.getHashKeyDataType());
  }

  public static Object formatHashResult(byte[] digestBytes, HashKeyDataType dataType) {
    if (digestBytes == null) {
      return null;
    }
    HashKeyDataType type = dataType != null ? dataType : HashKeyDataType.HEX;
    return switch (type) {
      case BINARY -> digestBytes;
      case HEX -> Hex.encodeHexString(digestBytes);
      default -> bytesToDecimalDashString(digestBytes);
    };
  }

  public static int resultValueMetaType(HashKeyDataType dataType) {
    HashKeyDataType type = dataType != null ? dataType : HashKeyDataType.HEX;
    return type == HashKeyDataType.BINARY ? IValueMeta.TYPE_BINARY : IValueMeta.TYPE_STRING;
  }

  public static int resultValueMetaLength(HashAlgorithm algorithm, HashKeyDataType dataType) {
    HashAlgorithm algo = algorithm != null ? algorithm : HashAlgorithm.MD5;
    int digestBytes = algo.getDigestLength();
    HashKeyDataType type = dataType != null ? dataType : HashKeyDataType.HEX;
    return switch (type) {
      case BINARY -> digestBytes;
      case HEX -> digestBytes * 2;
      default -> digestBytes * 3 + (digestBytes > 0 ? digestBytes - 1 : 0);
    };
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static String applyCasing(String value, HashContentCasing casing) {
    if (value == null || casing == null || casing == HashContentCasing.NONE) {
      return value;
    }
    return casing == HashContentCasing.LOWER ? value.toLowerCase() : value.toUpperCase();
  }

  private static String bytesToDecimalDashString(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < bytes.length; i++) {
      if (i > 0) {
        sb.append('-');
      }
      sb.append(bytes[i] & 0xFF);
    }
    return sb.toString();
  }
}
