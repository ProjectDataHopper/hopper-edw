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

import org.hopper.edw.catalog.discovery.HopVariableResolutionSupport;
import org.hopper.edw.catalog.discovery.PhysicalSourceRef;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Resolves live Iceberg table column metadata for {@link DvIcebergSource}. */
public final class DvIcebergSourceLiveSchemaSupport {

  private DvIcebergSourceLiveSchemaSupport() {}

  public static IRowMeta resolveLiveFields(
      DvIcebergSource source, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (source == null) {
      throw new HopException("Iceberg source is required.");
    }
    PhysicalSourceRef physicalRef =
        PhysicalSourceRef.builder()
            .catalogUri(source.getCatalogUri())
            .warehouse(source.getWarehouse())
            .icebergNamespace(source.getNamespace())
            .icebergTableName(source.getTableName())
            .snapshotId(source.getSnapshotId())
            .branch(source.getBranch())
            .s3Endpoint(source.getS3Endpoint())
            .s3AccessKey(source.getS3AccessKey())
            .s3SecretKey(source.getS3SecretKey())
            .build();
    physicalRef.validateIcebergLocation(variables);

    IcebergTableMetadataDiscovery.DiscoveryResult discovery =
        IcebergTableMetadataDiscovery.discover(physicalRef, variables);
    if (discovery.fields() == null || discovery.fields().isEmpty()) {
      throw new HopException(
          "No fields were discovered from Iceberg table "
              + variables.resolve(source.getNamespace())
              + "."
              + variables.resolve(source.getTableName())
              + ".");
    }
    return DvSourceFieldSupport.toRowMeta(discovery.fields(), variables);
  }

  public static Long resolveLiveSchemaId(DvIcebergSource source, IVariables variables)
      throws HopException {
    if (source == null) {
      return null;
    }
    IcebergConnectionSettings settings = IcebergConnectionSettings.from(source, variables);
    settings.validate();
    return IcebergTableMetadataDiscovery.loadSchemaMetadata(settings).schemaId();
  }

  static boolean hasResolvableLocation(DvIcebergSource source, IVariables variables) {
    if (source == null) {
      return false;
    }
    String catalogUri = HopVariableResolutionSupport.resolve(variables, source.getCatalogUri());
    String namespace = HopVariableResolutionSupport.resolve(variables, source.getNamespace());
    String tableName = HopVariableResolutionSupport.resolve(variables, source.getTableName());
    return !Utils.isEmpty(catalogUri)
        && !Utils.isEmpty(namespace)
        && !Utils.isEmpty(tableName)
        && !HopVariableResolutionSupport.containsUnresolvedVariables(catalogUri)
        && !HopVariableResolutionSupport.containsUnresolvedVariables(namespace)
        && !HopVariableResolutionSupport.containsUnresolvedVariables(tableName);
  }
}
