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
package org.apache.hop.datavault.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.apache.hop.catalog.model.CatalogSourceField;
import org.apache.hop.datavault.metadata.CsvFieldOptions;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.SourceFieldInputOptions;
import org.junit.jupiter.api.Test;

class DvSourceFieldSupportTest {

  @Test
  void roundTripsCsvInputOptionsBetweenCatalogAndSourceFields() {
    SourceField sourceField = new SourceField("amount");
    sourceField.setHopType(2);
    CsvFieldOptions csv = new CsvFieldOptions();
    csv.setFormat("#,##0.00");
    csv.setDecimalSymbol(",");
    csv.setGroupingSymbol(".");
    SourceFieldInputOptions inputOptions = new SourceFieldInputOptions();
    inputOptions.setCsv(csv);
    sourceField.setInputOptions(inputOptions);

    List<CatalogSourceField> catalogFields =
        DvSourceFieldSupport.toCatalogFields(List.of(sourceField));
    assertEquals(1, catalogFields.size());
    CatalogSourceField catalogField = catalogFields.getFirst();
    assertNotNull(catalogField.getInputOptions());
    assertNotNull(catalogField.getInputOptions().getCsv());
    assertEquals("#,##0.00", catalogField.getInputOptions().getCsv().getFormat());
    assertEquals(",", catalogField.getInputOptions().getCsv().getDecimalSymbol());
    assertEquals(".", catalogField.getInputOptions().getCsv().getGroupingSymbol());

    List<SourceField> restored = DvSourceFieldSupport.fromCatalogFields(catalogFields);
    assertEquals(1, restored.size());
    SourceField restoredField = restored.getFirst();
    assertNotNull(restoredField.getInputOptions());
    assertNotNull(restoredField.getInputOptions().getCsv());
    assertEquals("#,##0.00", restoredField.getInputOptions().getCsv().getFormat());
    assertEquals(",", restoredField.getInputOptions().getCsv().getDecimalSymbol());
    assertEquals(".", restoredField.getInputOptions().getCsv().getGroupingSymbol());
  }

  @Test
  void roundTripsPrimaryKeyPositionBetweenCatalogAndSourceFields() {
    SourceField sourceField = new SourceField("customer_id");
    sourceField.setPrimaryKeyPosition(2);

    List<CatalogSourceField> catalogFields =
        DvSourceFieldSupport.toCatalogFields(List.of(sourceField));
    assertEquals(2, catalogFields.getFirst().getPrimaryKeyPosition());

    List<SourceField> restored = DvSourceFieldSupport.fromCatalogFields(catalogFields);
    assertEquals(2, restored.getFirst().getPrimaryKeyPosition());
  }

  @Test
  void preservesNullInputOptions() {
    SourceField sourceField = new SourceField("name");
    List<CatalogSourceField> catalogFields =
        DvSourceFieldSupport.toCatalogFields(List.of(sourceField));
    assertNull(catalogFields.getFirst().getInputOptions());

    List<SourceField> restored = DvSourceFieldSupport.fromCatalogFields(catalogFields);
    assertNull(restored.getFirst().getInputOptions());
  }
}
