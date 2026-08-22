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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.datavault.config.DataVaultConfigSingleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DvTimestampFractionalPrecisionValidationTest {

  private boolean originalWarnFlag;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void saveFlag() {
    originalWarnFlag =
        DataVaultConfigSingleton.getConfig().isWarnTimestampFractionalPrecisionLoss();
    DataVaultConfigSingleton.getConfig().setWarnTimestampFractionalPrecisionLoss(true);
  }

  @AfterEach
  void restoreFlag() {
    DataVaultConfigSingleton.getConfig().setWarnTimestampFractionalPrecisionLoss(originalWarnFlag);
  }

  @Test
  void singleStoreEngineMaxIsSix() {
    assertEquals(
        6,
        DvFieldMappingValidationSupport.maxTemporalFractionalDigits(
            databaseMeta(DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID)));
  }

  @Test
  void temporalFractionalDigitsUsesTimestampLengthAsScale() throws Exception {
    IValueMeta ts = ValueMetaFactory.createValueMeta("ts", IValueMeta.TYPE_TIMESTAMP, 9, -1);
    assertEquals(9, DvFieldMappingValidationSupport.temporalFractionalDigits(ts));
  }

  @Test
  void warnsWhenSourceNanosecondsExceedSingleStoreDatetime6() throws Exception {
    IValueMeta source =
        ValueMetaFactory.createValueMeta("event_ts", IValueMeta.TYPE_TIMESTAMP, 9, -1);
    IValueMeta target =
        ValueMetaFactory.createValueMeta("event_ts", IValueMeta.TYPE_TIMESTAMP, -1, -1);
    List<ICheckResult> remarks = new ArrayList<>();

    DvFieldMappingValidationSupport.validateTemporalFractionalPrecision(
        source,
        target,
        "event_ts",
        databaseMeta(DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID),
        null,
        remarks);

    assertEquals(1, remarks.size());
    assertEquals(ICheckResult.TYPE_RESULT_WARNING, remarks.get(0).getType());
    assertTrue(remarks.get(0).getText().contains("9"));
    assertTrue(remarks.get(0).getText().contains("6"));
  }

  @Test
  void noWarningWhenSourceWithinDatetime6() throws Exception {
    IValueMeta source =
        ValueMetaFactory.createValueMeta("event_ts", IValueMeta.TYPE_TIMESTAMP, 6, -1);
    IValueMeta target =
        ValueMetaFactory.createValueMeta("event_ts", IValueMeta.TYPE_TIMESTAMP, -1, -1);
    List<ICheckResult> remarks = new ArrayList<>();

    DvFieldMappingValidationSupport.validateTemporalFractionalPrecision(
        source,
        target,
        "event_ts",
        databaseMeta(DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID),
        null,
        remarks);

    assertTrue(remarks.isEmpty());
  }

  @Test
  void warningCanBeDisabledInConfig() throws Exception {
    DataVaultConfigSingleton.getConfig().setWarnTimestampFractionalPrecisionLoss(false);
    IValueMeta source =
        ValueMetaFactory.createValueMeta("event_ts", IValueMeta.TYPE_TIMESTAMP, 9, -1);
    IValueMeta target =
        ValueMetaFactory.createValueMeta("event_ts", IValueMeta.TYPE_TIMESTAMP, -1, -1);
    List<ICheckResult> remarks = new ArrayList<>();

    DvFieldMappingValidationSupport.validateTemporalFractionalPrecision(
        source,
        target,
        "event_ts",
        databaseMeta(DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID),
        null,
        remarks);

    assertTrue(remarks.isEmpty());
  }

  @Test
  void neverEmitsErrorSeverity() throws Exception {
    IValueMeta source =
        ValueMetaFactory.createValueMeta("event_ts", IValueMeta.TYPE_TIMESTAMP, 9, -1);
    IValueMeta target =
        ValueMetaFactory.createValueMeta("event_ts", IValueMeta.TYPE_TIMESTAMP, 3, -1);
    List<ICheckResult> remarks = new ArrayList<>();

    DvFieldMappingValidationSupport.validateTemporalFractionalPrecision(
        source,
        target,
        "event_ts",
        databaseMeta(DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID),
        null,
        remarks);

    assertFalse(remarks.isEmpty());
    assertTrue(remarks.stream().noneMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
  }

  private static DatabaseMeta databaseMeta(String pluginId) {
    return new DatabaseMeta() {
      @Override
      public String getPluginId() {
        return pluginId;
      }
    };
  }
}
