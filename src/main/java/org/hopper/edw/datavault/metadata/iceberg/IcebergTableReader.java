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
package org.hopper.edw.datavault.metadata.iceberg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SnapshotRef;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.CloseableIterable;

/** Reads rows from an Iceberg table via the REST catalog and S3-compatible warehouse. */
public final class IcebergTableReader implements AutoCloseable {

  private final IcebergConnectionSettings settings;
  private final String[] fieldNames;
  private CloseableIterable<Record> records;
  private Iterator<Record> iterator;
  private Schema schema;
  private IRowMeta outputRowMeta;

  public IcebergTableReader(IcebergConnectionSettings settings, List<String> selectedFieldNames)
      throws HopException {
    this.settings = settings;
    settings.validate();
    openTable();
    this.fieldNames = resolveFieldNames(selectedFieldNames);
    buildOutputRowMeta("IcebergTableInput");
  }

  private void openTable() throws HopException {
    ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(IcebergTableReader.class.getClassLoader());

      Map<String, String> properties = new HashMap<>();
      properties.put("type", "rest");
      properties.put(CatalogProperties.URI, settings.catalogUri());
      if (!Utils.isEmpty(settings.warehouse())) {
        properties.put(CatalogProperties.WAREHOUSE_LOCATION, settings.warehouse());
      }
      if (!Utils.isEmpty(settings.s3Endpoint())) {
        properties.put("s3.endpoint", settings.s3Endpoint());
        properties.put("s3.path-style-access", "true");
      }
      if (!Utils.isEmpty(settings.s3AccessKey())) {
        properties.put("s3.access-key-id", settings.s3AccessKey());
      }
      if (!Utils.isEmpty(settings.s3SecretKey())) {
        properties.put("s3.secret-access-key", settings.s3SecretKey());
      }
      properties.put("client.region", "us-east-1");

      Catalog catalog = CatalogUtil.buildIcebergCatalog("rest", properties, new Configuration());
      Table table =
          catalog.loadTable(TableIdentifier.of(settings.namespace(), settings.tableName()));
      schema = table.schema();

      var readBuilder = IcebergGenerics.read(table);
      if (!Utils.isEmpty(settings.snapshotId())) {
        readBuilder = readBuilder.useSnapshot(Long.parseLong(settings.snapshotId()));
      } else if (!Utils.isEmpty(settings.branch())) {
        SnapshotRef ref = table.refs().get(settings.branch());
        if (ref == null) {
          throw new HopException(
              "Iceberg table "
                  + settings.namespace()
                  + "."
                  + settings.tableName()
                  + " does not contain branch '"
                  + settings.branch()
                  + "'");
        }
        readBuilder = readBuilder.useSnapshot(ref.snapshotId());
      }
      records = readBuilder.build();
      iterator = records.iterator();
    } catch (Exception e) {
      throw new HopException(
          "Unable to read Iceberg table " + settings.namespace() + "." + settings.tableName(), e);
    } finally {
      Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }
  }

  private String[] resolveFieldNames(List<String> selectedFieldNames) throws HopException {
    if (selectedFieldNames != null && !selectedFieldNames.isEmpty()) {
      return selectedFieldNames.stream()
          .filter(name -> !Utils.isEmpty(name))
          .map(String::trim)
          .toArray(String[]::new);
    }
    List<String> names = new ArrayList<>();
    for (org.apache.iceberg.types.Types.NestedField field : schema.columns()) {
      names.add(field.name());
    }
    if (names.isEmpty()) {
      throw new HopException("Iceberg table schema does not contain any columns");
    }
    return names.toArray(String[]::new);
  }

  private void buildOutputRowMeta(String origin) throws HopException {
    RowMeta rowMeta = new RowMeta();
    for (String fieldName : fieldNames) {
      org.apache.iceberg.types.Types.NestedField field = schema.findField(fieldName);
      if (field == null) {
        throw new HopException("Iceberg table does not contain field '" + fieldName + "'");
      }
      rowMeta.addValueMeta(IcebergTypeMapping.valueMetaForField(fieldName, field.type(), origin));
    }
    outputRowMeta = rowMeta;
  }

  public IRowMeta getOutputRowMeta() {
    return outputRowMeta;
  }

  public String[] getFieldNames() {
    return fieldNames;
  }

  public boolean hasNext() {
    return iterator != null && iterator.hasNext();
  }

  public Object[] nextRow() {
    Record record = iterator.next();
    return IcebergTypeMapping.toHopRow(record, schema, fieldNames);
  }

  public List<RowMetaAndData> readAll(int rowLimit) throws HopException {
    List<RowMetaAndData> rows = new ArrayList<>();
    while (hasNext()) {
      rows.add(new RowMetaAndData(outputRowMeta, nextRow()));
      if (rowLimit > 0 && rows.size() >= rowLimit) {
        break;
      }
    }
    return rows;
  }

  @Override
  public void close() throws IOException {
    if (records != null) {
      records.close();
    }
  }
}
