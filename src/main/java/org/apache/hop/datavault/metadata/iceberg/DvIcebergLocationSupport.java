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

import org.apache.hop.catalog.model.PhysicalIcebergTableRef;

/** Shared helpers for Iceberg-based Data Vault source locations. */
public final class DvIcebergLocationSupport {

  private DvIcebergLocationSupport() {}

  public static void applyPhysicalIcebergTable(
      DvIcebergSource icebergSource, PhysicalIcebergTableRef physicalIcebergTable) {
    if (physicalIcebergTable == null || icebergSource == null) {
      return;
    }
    icebergSource.setCatalogUri(physicalIcebergTable.getCatalogUri());
    icebergSource.setWarehouse(physicalIcebergTable.getWarehouse());
    icebergSource.setNamespace(physicalIcebergTable.getNamespace());
    icebergSource.setTableName(physicalIcebergTable.getTableName());
    icebergSource.setSnapshotId(physicalIcebergTable.getSnapshotId());
    icebergSource.setBranch(physicalIcebergTable.getBranch());
    icebergSource.setS3Endpoint(physicalIcebergTable.getS3Endpoint());
    icebergSource.setS3AccessKey(physicalIcebergTable.getS3AccessKey());
    icebergSource.setS3SecretKey(physicalIcebergTable.getS3SecretKey());
  }

  public static PhysicalIcebergTableRef toPhysicalIcebergTableRef(DvIcebergSource icebergSource) {
    if (icebergSource == null) {
      return null;
    }
    PhysicalIcebergTableRef ref = new PhysicalIcebergTableRef();
    ref.setCatalogUri(icebergSource.getCatalogUri());
    ref.setWarehouse(icebergSource.getWarehouse());
    ref.setNamespace(icebergSource.getNamespace());
    ref.setTableName(icebergSource.getTableName());
    ref.setSnapshotId(icebergSource.getSnapshotId());
    ref.setBranch(icebergSource.getBranch());
    ref.setS3Endpoint(icebergSource.getS3Endpoint());
    ref.setS3AccessKey(icebergSource.getS3AccessKey());
    ref.setS3SecretKey(icebergSource.getS3SecretKey());
    return ref;
  }
}
