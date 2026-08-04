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
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.datavault.config.DataVaultConfigSingleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression: reverse-import of an existing DV 2.0 schema must not raise type/length errors when
 * physical source and target columns are the same (SingleStore JDBC metadata noise).
 */
class DvFieldMappingValidationFalsePositiveTest {

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
  void datetimeStoredAsStringMatchesTimestampTarget() throws Exception {
    // JDBC often maps DATETIME → String while the model attribute is Timestamp / DATETIME(6).
    IValueMeta source = new ValueMetaString("load_dts");
    source.setOriginalColumnTypeName("DATETIME(6)");
    IValueMeta target =
        ValueMetaFactory.createValueMeta("load_dts", IValueMeta.TYPE_TIMESTAMP, 6, 6);
    target.setOriginalColumnTypeName("DATETIME(6)");

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateMapping(
        source, target, "load_dts", null, null, remarks);

    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> "Unexpected errors: " + remarks);
  }

  @Test
  void identicalDatetime6DoesNotWarnAboutNanoseconds() throws Exception {
    // Bogus JDBC scale 9 with TYPE_NAME DATETIME(6) on both sides.
    IValueMeta source =
        ValueMetaFactory.createValueMeta("load_dts", IValueMeta.TYPE_TIMESTAMP, 9, -1);
    source.setOriginalColumnTypeName("DATETIME(6)");
    IValueMeta target =
        ValueMetaFactory.createValueMeta("load_dts", IValueMeta.TYPE_TIMESTAMP, 6, 6);
    target.setOriginalColumnTypeName("DATETIME(6)");

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateMapping(
        source, target, "load_dts", singleStoreMeta(), null, remarks);

    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_WARNING),
        () -> "Unexpected warnings: " + remarks);
    assertEquals(6, DvFieldMappingValidationSupport.temporalFractionalDigits(source));
  }

  @Test
  void identicalVarchar150DoesNotErrorWhenSourceDisplayIs255() throws Exception {
    IValueMeta source = ValueMetaFactory.createValueMeta("name", IValueMeta.TYPE_STRING, 255, -1);
    source.setOriginalPrecision(150);
    source.setOriginalColumnTypeName("VARCHAR");
    IValueMeta target = ValueMetaFactory.createValueMeta("name", IValueMeta.TYPE_STRING, 150, -1);
    target.setOriginalColumnTypeName("VARCHAR");

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateMapping(source, target, "name", null, null, remarks);

    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> "Unexpected errors: " + remarks);
  }

  @Test
  void identicalLongtextDoesNotErrorOnDisplaySize255() throws Exception {
    IValueMeta source = ValueMetaFactory.createValueMeta("notes", IValueMeta.TYPE_STRING, 255, -1);
    source.setOriginalColumnTypeName("LONGTEXT");
    IValueMeta target = ValueMetaFactory.createValueMeta("notes", IValueMeta.TYPE_STRING);
    target.setLength(DvSqlStringTypeSupport.CLOB_LENGTH);
    target.setOriginalColumnTypeName("LONGTEXT");

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateMapping(source, target, "notes", null, null, remarks);

    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> "Unexpected errors: " + remarks);
  }

  @Test
  void valueMetaFromSourceFieldFixesDatetimeStringHopType() throws Exception {
    SourceField sf = new SourceField("load_dts");
    sf.setSourceDataType("DATETIME(6)");
    sf.setHopType(IValueMeta.TYPE_STRING); // wrong JDBC hop type
    sf.setLength("9");
    sf.setPrecision("9");

    IValueMeta vm = DvFieldMappingValidationSupport.valueMetaFromSourceField(sf, null);
    assertEquals(IValueMeta.TYPE_TIMESTAMP, vm.getType());
    assertEquals(6, DvFieldMappingValidationSupport.temporalFractionalDigits(vm));
  }

  @Test
  void stillErrorsWhenSourceStringExceedsTargetVarchar() throws Exception {
    IValueMeta source = ValueMetaFactory.createValueMeta("code", IValueMeta.TYPE_STRING, 500, -1);
    IValueMeta target = ValueMetaFactory.createValueMeta("code", IValueMeta.TYPE_STRING, 50, -1);
    target.setOriginalColumnTypeName("VARCHAR");

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateMapping(source, target, "code", null, null, remarks);

    assertTrue(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> "Expected length overflow error, got: " + remarks);
  }

  private static DatabaseMeta singleStoreMeta() {
    return new DatabaseMeta() {
      @Override
      public String getPluginId() {
        return DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID;
      }
    };
  }
}
