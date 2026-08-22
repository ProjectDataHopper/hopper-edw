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
package org.apache.hop.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmSourceTargetTypeValidationSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void reportsTypeMismatchBetweenIntegerSourceAndStringTarget() {
    RowMeta sourceRowMeta = new RowMeta();
    sourceRowMeta.addValueMeta(new ValueMetaInteger("date_key"));

    RowMeta targetLayout = new RowMeta();
    targetLayout.addValueMeta(new ValueMetaString("date_key"));

    List<ICheckResult> remarks = new ArrayList<>();
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_date");

    DmSourceTargetTypeValidationSupport.validateMappedField(
        remarks,
        sourceRowMeta,
        targetLayout,
        "date_key",
        "Dimension 'dim_date' natural key 'date_key'",
        dimension);

    assertEquals(1, remarks.size());
    assertEquals(ICheckResult.TYPE_RESULT_ERROR, remarks.get(0).getType());
    assertTrue(remarks.get(0).getText().contains("Integer"));
    assertTrue(remarks.get(0).getText().contains("String"));
  }

  @Test
  void acceptsMatchingIntegerTypes() {
    RowMeta sourceRowMeta = new RowMeta();
    sourceRowMeta.addValueMeta(new ValueMetaInteger("date_key"));

    RowMeta targetLayout = new RowMeta();
    targetLayout.addValueMeta(new ValueMetaInteger("date_key"));

    List<ICheckResult> remarks = new ArrayList<>();
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_date");

    DmSourceTargetTypeValidationSupport.validateMappedField(
        remarks,
        sourceRowMeta,
        targetLayout,
        "date_key",
        "Dimension 'dim_date' natural key 'date_key'",
        dimension);

    assertTrue(remarks.isEmpty());
  }

  @Test
  void typesCompatibleTreatsIntegerAndNumberAsCompatible() {
    assertTrue(
        DmSourceTargetTypeValidationSupport.typesCompatible(
            IValueMeta.TYPE_INTEGER, IValueMeta.TYPE_NUMBER));
    assertTrue(
        DmSourceTargetTypeValidationSupport.typesCompatible(
            IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_INTEGER));
  }
}
