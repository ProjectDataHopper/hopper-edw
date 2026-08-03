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

import org.apache.hop.catalog.discovery.HopVariableResolutionSupport;
import org.apache.hop.catalog.discovery.PhysicalSourceRef;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.transform.iceberginput.IcebergTableInputMeta;

/** Resolved Iceberg REST catalog and warehouse connection settings. */
public record IcebergConnectionSettings(
    String catalogUri,
    String warehouse,
    String namespace,
    String tableName,
    String snapshotId,
    String branch,
    String s3Endpoint,
    String s3AccessKey,
    String s3SecretKey) {

  public static IcebergConnectionSettings from(
      PhysicalSourceRef physicalRef, IVariables variables) {
    return new IcebergConnectionSettings(
        resolve(variables, physicalRef.getCatalogUri()),
        resolve(variables, physicalRef.getWarehouse()),
        resolve(variables, physicalRef.getIcebergNamespace()),
        resolve(variables, physicalRef.getIcebergTableName()),
        resolve(variables, physicalRef.getSnapshotId()),
        resolve(variables, physicalRef.getBranch()),
        resolve(variables, physicalRef.getS3Endpoint()),
        resolve(variables, physicalRef.getS3AccessKey()),
        resolve(variables, physicalRef.getS3SecretKey()));
  }

  public static IcebergConnectionSettings from(DvIcebergSource source, IVariables variables) {
    return new IcebergConnectionSettings(
        resolve(variables, source.getCatalogUri()),
        resolve(variables, source.getWarehouse()),
        resolve(variables, source.getNamespace()),
        resolve(variables, source.getTableName()),
        resolve(variables, source.getSnapshotId()),
        resolve(variables, source.getBranch()),
        resolve(variables, source.getS3Endpoint()),
        resolve(variables, source.getS3AccessKey()),
        resolve(variables, source.getS3SecretKey()));
  }

  public static IcebergConnectionSettings from(IcebergTableInputMeta meta, IVariables variables) {
    return new IcebergConnectionSettings(
        resolve(variables, meta.getCatalogUri()),
        resolve(variables, meta.getWarehouse()),
        resolve(variables, meta.getNamespace()),
        resolve(variables, meta.getTableName()),
        resolve(variables, meta.getSnapshotId()),
        resolve(variables, meta.getBranch()),
        resolve(variables, meta.getS3Endpoint()),
        resolve(variables, meta.getS3AccessKey()),
        resolve(variables, meta.getS3SecretKey()));
  }

  public void validate() {
    try {
      HopVariableResolutionSupport.requireResolved(null, catalogUri, "Iceberg catalog URI");
      HopVariableResolutionSupport.requireResolved(null, namespace, "Iceberg namespace");
      HopVariableResolutionSupport.requireResolved(null, tableName, "Iceberg table name");
    } catch (HopException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    if (Utils.isEmpty(catalogUri)) {
      throw new IllegalArgumentException("Iceberg catalog URI is required");
    }
    if (Utils.isEmpty(namespace)) {
      throw new IllegalArgumentException("Iceberg namespace is required");
    }
    if (Utils.isEmpty(tableName)) {
      throw new IllegalArgumentException("Iceberg table name is required");
    }
  }

  private static String resolve(IVariables variables, String value) {
    if (variables == null || value == null) {
      return value;
    }
    return variables.resolve(value);
  }
}
