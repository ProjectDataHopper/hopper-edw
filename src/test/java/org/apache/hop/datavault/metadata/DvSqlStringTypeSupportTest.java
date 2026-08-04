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
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvSqlStringTypeSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void longtextWithDisplaySize255UsesClobCapacity() {
    assertEquals(
        DvSqlStringTypeSupport.CLOB_LENGTH,
        DvSqlStringTypeSupport.capacityForSqlStringType("LONGTEXT", 255));
  }

  @Test
  void normalizeStringLengthFixesLongtextDisplaySizeBug() {
    IValueMeta meta = new ValueMetaString("notes");
    meta.setLength(255);
    meta.setOriginalColumnTypeName("LONGTEXT");
    DvSqlStringTypeSupport.normalizeStringLength(meta);
    assertEquals(DvSqlStringTypeSupport.CLOB_LENGTH, meta.getLength());
  }

  @Test
  void normalizeStringLengthPrefersVarcharColumnSizeOverDisplay255() {
    IValueMeta meta = new ValueMetaString("name");
    meta.setLength(255);
    meta.setOriginalPrecision(150);
    meta.setOriginalColumnTypeName("VARCHAR");
    DvSqlStringTypeSupport.normalizeStringLength(meta);
    assertEquals(150, meta.getLength());
  }

  @Test
  void identicalVarchar150DoesNotErrorOnLength() throws Exception {
    IValueMeta source = ValueMetaFactory.createValueMeta("name", IValueMeta.TYPE_STRING, 150, -1);
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
  void bogusSourceDisplay255VsTarget150DoesNotErrorAfterNormalize() throws Exception {
    // Simulates SingleStore getColumnDisplaySize=255 for VARCHAR(150) on source,
    // with model/target correctly at 150 — after normalize using originalPrecision.
    IValueMeta source = ValueMetaFactory.createValueMeta("name", IValueMeta.TYPE_STRING, 255, -1);
    source.setOriginalPrecision(150);
    source.setOriginalColumnTypeName("VARCHAR");
    IValueMeta target = ValueMetaFactory.createValueMeta("name", IValueMeta.TYPE_STRING, 150, -1);
    target.setOriginalColumnTypeName("VARCHAR");

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateMapping(source, target, "name", null, null, remarks);
    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("length")),
        () -> "Unexpected length errors: " + remarks);
  }

  @Test
  void skipOverflowWhenTargetIsLongtext() throws Exception {
    IValueMeta source = ValueMetaFactory.createValueMeta("notes", IValueMeta.TYPE_STRING);
    source.setLength(Integer.MAX_VALUE);
    source.setOriginalColumnTypeName("LONGTEXT");
    IValueMeta target = ValueMetaFactory.createValueMeta("notes", IValueMeta.TYPE_STRING);
    target.setLength(255);
    target.setOriginalColumnTypeName("LONGTEXT");
    assertTrue(DvSqlStringTypeSupport.skipStringLengthOverflowCheck(source, target));
  }

  @Test
  void doesNotSkipOverflowWhenTargetIsVarchar255() throws Exception {
    IValueMeta source = ValueMetaFactory.createValueMeta("code", IValueMeta.TYPE_STRING, 500, -1);
    IValueMeta target = ValueMetaFactory.createValueMeta("code", IValueMeta.TYPE_STRING, 255, -1);
    target.setOriginalColumnTypeName("VARCHAR");
    assertFalse(DvSqlStringTypeSupport.skipStringLengthOverflowCheck(source, target));
  }
}
