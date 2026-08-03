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
package org.apache.hop.datavault.metadata.iceberg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hop.catalog.discovery.PhysicalSourceRef;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/** Discovers Iceberg table layout from a REST catalog and S3-compatible warehouse. */
public final class IcebergTableMetadataDiscovery {

  private static final Class<?> PKG = IcebergTableMetadataDiscovery.class;

  private IcebergTableMetadataDiscovery() {}

  public record DiscoveryResult(List<SourceField> fields, Long schemaId) {}

  public record SchemaMetadata(Schema schema, long schemaId) {}

  public static DiscoveryResult discover(PhysicalSourceRef physicalRef, IVariables variables)
      throws HopException {
    if (physicalRef == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "IcebergTableMetadataDiscovery.Error.MissingLocation"));
    }
    physicalRef.validateIcebergLocation(variables);
    return discover(IcebergConnectionSettings.from(physicalRef, variables));
  }

  public static DiscoveryResult discover(IcebergConnectionSettings settings) throws HopException {
    settings.validate();
    SchemaMetadata schemaMetadata = loadSchemaMetadata(settings);
    List<SourceField> fields = fieldsFromSchema(schemaMetadata.schema());
    if (fields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "IcebergTableMetadataDiscovery.Error.NoFields"));
    }
    return new DiscoveryResult(fields, schemaMetadata.schemaId());
  }

  public static SchemaMetadata loadSchemaMetadata(IcebergConnectionSettings settings)
      throws HopException {
    settings.validate();
    return loadTableSchemaMetadata(settings);
  }

  public static List<SourceField> fieldsFromSchema(Schema schema) throws HopException {
    if (schema == null || schema.columns() == null || schema.columns().isEmpty()) {
      return List.of();
    }

    List<SourceField> fields = new ArrayList<>();
    for (Types.NestedField column : schema.columns()) {
      if (column == null || Utils.isEmpty(column.name())) {
        continue;
      }
      fields.add(toSourceField(column.name(), column.type()));
    }
    return fields;
  }

  private static SourceField toSourceField(String name, Type icebergType) throws HopException {
    IValueMeta valueMeta =
        IcebergTypeMapping.valueMetaForField(name, icebergType, "IcebergDiscovery");
    SourceField field = new SourceField(name);
    field.setDescription("");
    field.setSourceDataType(valueMeta.getTypeDesc());
    field.setHopType(valueMeta.getType());
    field.setLength(valueMeta.getLength() > 0 ? String.valueOf(valueMeta.getLength()) : "");
    field.setPrecision(
        valueMeta.getPrecision() > 0 ? String.valueOf(valueMeta.getPrecision()) : "");
    return field;
  }

  private static SchemaMetadata loadTableSchemaMetadata(IcebergConnectionSettings settings)
      throws HopException {
    ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread()
          .setContextClassLoader(IcebergTableMetadataDiscovery.class.getClassLoader());

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
      Schema schema = table.schema();
      return new SchemaMetadata(schema, schema.schemaId());
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "IcebergTableMetadataDiscovery.Error.LoadTable",
              settings.namespace(),
              settings.tableName()),
          e);
    } finally {
      Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }
  }
}
