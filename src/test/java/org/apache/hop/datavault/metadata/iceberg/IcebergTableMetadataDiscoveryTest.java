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
package org.apache.hop.datavault.metadata.iceberg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.hop.catalog.discovery.PhysicalSourceRef;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

class IcebergTableMetadataDiscoveryTest {

  @Test
  void fieldsFromSchema_mapsIcebergColumnsToSourceFields() throws Exception {
    Schema schema =
        new Schema(
            Types.NestedField.required(1, "customer_id", Types.IntegerType.get()),
            Types.NestedField.optional(2, "name", Types.StringType.get()),
            Types.NestedField.optional(3, "amount", Types.DecimalType.of(10, 2)),
            Types.NestedField.optional(4, "load_date", Types.DateType.get()));

    var fields = IcebergTableMetadataDiscovery.fieldsFromSchema(schema);

    assertEquals(4, fields.size());
    assertField(fields.get(0), "customer_id", "Integer", IValueMeta.TYPE_INTEGER, "15", "");
    assertField(fields.get(1), "name", "String", IValueMeta.TYPE_STRING);
    assertField(fields.get(2), "amount", "BigNumber", IValueMeta.TYPE_BIGNUMBER, "10", "2");
    assertField(fields.get(3), "load_date", "Date", IValueMeta.TYPE_DATE);
  }

  @Test
  void discover_requiresCatalogNamespaceAndTable() {
    Variables variables = new Variables();
    PhysicalSourceRef physicalRef =
        PhysicalSourceRef.builder()
            .catalogUri("${ICEBERG_CATALOG_URI}")
            .icebergNamespace("${ICEBERG_NAMESPACE}")
            .build();

    assertThrows(
        HopException.class, () -> IcebergTableMetadataDiscovery.discover(physicalRef, variables));
  }

  private static void assertField(
      SourceField field, String name, String sourceDataType, int hopType) {
    assertField(field, name, sourceDataType, hopType, "", "");
  }

  private static void assertField(
      SourceField field,
      String name,
      String sourceDataType,
      int hopType,
      String length,
      String precision) {
    assertEquals(name, field.getName());
    assertEquals(sourceDataType, field.getSourceDataType());
    assertEquals(hopType, field.getHopType());
    assertEquals(length, field.getLength());
    assertEquals(precision, field.getPrecision());
  }
}
