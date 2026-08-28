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
package org.hopper.edw.datavault.metadata.businessvault;

import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.HashKeyDataType;
import org.hopper.edw.datavault.metadata.businessvault.BvPitSnapshotSpineSupport.PitSqlDialect;

/**
 * Dialect-specific {@code first-byte(hash_key) % PARTITION_COUNT = PARTITION_NUMBER} predicates for
 * partitioned SCD2 satellite reads. Values are Hop variables substituted by Table Input.
 */
public final class BvScd2HashPartitionSqlSupport {

  public static final String PARTITION_COUNT_VARIABLE = "PARTITION_COUNT";
  public static final String PARTITION_NUMBER_VARIABLE = "PARTITION_NUMBER";

  static final String PARTITION_COUNT_REF = "${" + PARTITION_COUNT_VARIABLE + "}";
  static final String PARTITION_NUMBER_REF = "${" + PARTITION_NUMBER_VARIABLE + "}";

  private BvScd2HashPartitionSqlSupport() {}

  /**
   * Boolean SQL predicate using the quoted hash-key column. Empty column or metadata yields {@code
   * null} (caller skips the filter).
   */
  public static String buildPredicate(
      DatabaseMeta databaseMeta, HashKeyDataType hashKeyDataType, String quotedHashKeyColumn) {
    if (Utils.isEmpty(quotedHashKeyColumn)) {
      return null;
    }
    HashKeyDataType type = hashKeyDataType != null ? hashKeyDataType : HashKeyDataType.HEX;
    PitSqlDialect dialect = BvPitSnapshotSpineSupport.resolveDialect(databaseMeta);
    String firstByte = firstByteExpression(dialect, type, quotedHashKeyColumn);
    return firstByte + " % " + PARTITION_COUNT_REF + " = " + PARTITION_NUMBER_REF;
  }

  static String firstByteExpression(
      PitSqlDialect dialect, HashKeyDataType type, String quotedHashKeyColumn) {
    return switch (type) {
      case BINARY -> binaryFirstByte(dialect, quotedHashKeyColumn);
      case STRING -> stringFirstByte(dialect, quotedHashKeyColumn);
      default -> hexFirstByte(dialect, quotedHashKeyColumn);
    };
  }

  private static String binaryFirstByte(PitSqlDialect dialect, String hk) {
    return switch (dialect) {
      case MYSQL, SINGLESTORE -> "CONV(HEX(SUBSTRING(" + hk + ", 1, 1)), 16, 10)";
      case SQL_SERVER -> "CONVERT(int, SUBSTRING(" + hk + ", 1, 1))";
      case SNOWFLAKE -> "TO_NUMBER(SUBSTR(HEX_ENCODE(" + hk + "), 1, 2), 'XX')";
      case POSTGRES -> "get_byte(" + hk + ", 0)";
    };
  }

  private static String hexFirstByte(PitSqlDialect dialect, String hk) {
    return switch (dialect) {
      case MYSQL, SINGLESTORE -> "CONV(SUBSTRING(" + hk + ", 1, 2), 16, 10)";
      case SQL_SERVER -> "CONVERT(int, CONVERT(varbinary(1), LEFT(" + hk + ", 2), 2))";
      case SNOWFLAKE -> "TO_NUMBER(SUBSTR(" + hk + ", 1, 2), 'XX')";
      case POSTGRES -> "('x' || substr(" + hk + ", 1, 2))::bit(8)::int";
    };
  }

  private static String stringFirstByte(PitSqlDialect dialect, String hk) {
    return switch (dialect) {
      case MYSQL, SINGLESTORE -> "SUBSTRING_INDEX(" + hk + ", '-', 1)";
      case SQL_SERVER ->
          "TRY_CONVERT(int, LEFT(" + hk + " + '-', CHARINDEX('-', " + hk + " + '-') - 1))";
      case SNOWFLAKE -> "TRY_TO_NUMBER(SPLIT_PART(" + hk + ", '-', 1))";
      case POSTGRES -> "split_part(" + hk + ", '-', 1)::int";
    };
  }
}
