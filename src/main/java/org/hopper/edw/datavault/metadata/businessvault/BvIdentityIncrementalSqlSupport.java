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

/**
 * Satellite predicate for an incremental SCD2 load that also uses an identity map. Stable raw keys
 * stay on the watermark. A raw key whose map {@code rule_version} no longer matches the open SCD2
 * row is read again from the earlier of the map {@code valid_from} and the watermark.
 *
 * <p>Placeholders, in order, after the watermark comparison that the caller already added: open-end
 * sentinel, watermark, watermark.
 */
public final class BvIdentityIncrementalSqlSupport {

  private BvIdentityIncrementalSqlSupport() {}

  public static String remappedRawKeyPredicate(
      DatabaseMeta databaseMeta,
      String satelliteHashColumn,
      String satelliteTimestampColumn,
      String mapTable,
      String scd2Table,
      String scd2ValidToColumn) {
    String raw = databaseMeta.quoteField(BvIdentityKeys.HK_RAW);
    String mapVersion = databaseMeta.quoteField(BvIdentityKeys.RULE_VERSION);
    String storedVersion = databaseMeta.quoteField(BvIdentityKeys.MAP_RULE_VERSION);
    String validFrom = databaseMeta.quoteField(BvIdentityKeys.VALID_FROM);
    String validTo = databaseMeta.quoteField(scd2ValidToColumn);
    return "EXISTS (SELECT 1 FROM "
        + mapTable
        + " m INNER JOIN "
        + scd2Table
        + " s ON s."
        + raw
        + " = m."
        + raw
        + " AND s."
        + validTo
        + " = ? WHERE m."
        + raw
        + " = "
        + satelliteHashColumn
        + " AND m."
        + mapVersion
        + " <> s."
        + storedVersion
        + " AND "
        + satelliteTimestampColumn
        + " >= CASE WHEN m."
        + validFrom
        + " < ? THEN m."
        + validFrom
        + " ELSE ? END)";
  }

  /** Raw keys whose stored map version is stale. One {@code ?} for the open-end sentinel. */
  public static String remappedRawKeySelect(
      DatabaseMeta databaseMeta, String mapTable, String scd2Table, String scd2ValidToColumn) {
    String raw = databaseMeta.quoteField(BvIdentityKeys.HK_RAW);
    String mapVersion = databaseMeta.quoteField(BvIdentityKeys.RULE_VERSION);
    String storedVersion = databaseMeta.quoteField(BvIdentityKeys.MAP_RULE_VERSION);
    String validTo = databaseMeta.quoteField(scd2ValidToColumn);
    return "SELECT m."
        + raw
        + " FROM "
        + mapTable
        + " m INNER JOIN "
        + scd2Table
        + " s ON s."
        + raw
        + " = m."
        + raw
        + " AND s."
        + validTo
        + " = ? WHERE m."
        + databaseMeta.quoteField(BvIdentityKeys.RULE_VERSION)
        + " <> s."
        + storedVersion;
  }
}
