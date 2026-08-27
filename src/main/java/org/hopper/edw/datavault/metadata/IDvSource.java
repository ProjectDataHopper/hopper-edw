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
package org.hopper.edw.datavault.metadata;

import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.changed.IChanged;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataObject;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadataObjectFactory;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.composite.DvCompositeSource;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSource;
import org.hopper.edw.datavault.metadata.file.DvCsvSource;
import org.hopper.edw.datavault.metadata.file.DvParquetSource;
import org.hopper.edw.datavault.metadata.iceberg.DvIcebergSource;
import org.hopper.edw.datavault.metadata.json.DvJsonSource;

/**
 * Common interface for Data Vault source system definitions.
 *
 * <p>A source describes where raw data comes from (initially: an RDBMS table or query via a
 * registered DatabaseMeta / "rdbms" connection). Every source also carries the expected logical row
 * layout (list of {@link SourceField}) so that loading logic, lineage, and code generators know the
 * columns, types, and which columns form the natural key on the source.
 *
 * <p>Supports multiple source kinds via polymorphism (DATABASE today; CSV, Parquet, Avro, Kafka,
 * ... tomorrow). When sources are embedded in a {@link DataVaultModel} (or other containers) the
 * {@link DvSourceFactory} ensures correct de-serialization based on the persisted sourceType.
 */
@HopMetadataObject(xmlKey = "sourceType", objectFactory = IDvSource.DvSourceFactory.class)
public interface IDvSource extends IHasName, IChanged {

  /** Optional human-readable description of this source system / feed. */
  String getDescription();

  /** The row layout (columns) we expect to receive from this source. Order is significant. */
  List<SourceField> getFields();

  void setFields(List<SourceField> fields);

  /** Discriminator for polymorphic serialization and runtime handling. */
  DvSourceType getSourceType();

  /**
   * Read up to {@code rowLimit} rows from this source for interactive preview.
   *
   * @param queryTimeoutSeconds JDBC statement timeout in seconds; 0 uses the driver default
   */
  List<RowMetaAndData> previewRecords(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit,
      int queryTimeoutSeconds)
      throws HopException;

  /** Whether {@link #resolveLiveFields} can read the current physical source schema. */
  default boolean supportsLiveFieldResolution() {
    return false;
  }

  /**
   * Returns the current row layout from the physical source (e.g. live database columns). Returns
   * null when not supported or unavailable.
   */
  default IRowMeta resolveLiveFields(IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return null;
  }

  /**
   * Factory used by the Hop metadata serializer (and when serializing lists of sources inside
   * DataVaultModel etc.) to reconstruct the correct concrete implementation.
   */
  final class DvSourceFactory implements IHopMetadataObjectFactory {

    @Override
    public Object createObject(String id, Object parentObject) throws HopException {
      if (DvSourceType.DATABASE.name().equals(id)) {
        return new DvDatabaseSource();
      }
      if (DvSourceType.CSV.name().equals(id)) {
        return new DvCsvSource();
      }
      if (DvSourceType.PARQUET.name().equals(id)) {
        return new DvParquetSource();
      }
      if (DvSourceType.ICEBERG.name().equals(id)) {
        return new DvIcebergSource();
      }
      if (DvSourceType.COMPOSITE.name().equals(id)) {
        return new DvCompositeSource();
      }
      if (DvSourceType.JSON.name().equals(id)) {
        return new DvJsonSource();
      }
      if (DvSourceType.PIPELINE.name().equals(id)) {
        return new org.hopper.edw.datavault.metadata.pipeline.DvPipelineSource();
      }
      throw new HopException("Unable to recognize Data Vault source type with ID '" + id + "'");
    }

    @Override
    public String getObjectId(Object object) throws HopException {
      if (!(object instanceof IDvSource source)) {
        throw new HopException(
            "Object is not of class IDvSource but of " + object.getClass().getName());
      }
      DvSourceType sourceType = source.getSourceType();
      if (sourceType == null) {
        throw new HopException("Data Vault source has no source type set");
      }
      return sourceType.name();
    }
  }
}
