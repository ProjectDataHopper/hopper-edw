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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.composite.DvCompositeHubSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.composite.DvCompositeLinkSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.composite.DvCompositeSatelliteSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.file.DvCsvHubSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.file.DvCsvLinkSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.file.DvCsvReferenceSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.file.DvCsvSatelliteSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.file.DvParquetHubSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.file.DvParquetLinkSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.file.DvParquetSatelliteSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.iceberg.DvIcebergHubSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.iceberg.DvIcebergLinkSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.iceberg.DvIcebergSatelliteSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.json.DvJsonHubSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.json.DvJsonLinkSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.json.DvJsonSatelliteSourcePipelineBuilder;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Creates the correct {@link DvSourcePipelineBuilder} for a record source type and DV table. */
public final class DvSourcePipelineBuilderFactory {

  private DvSourcePipelineBuilderFactory() {}

  public static DvSourcePipelineBuilder forHub(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvHub hub,
      Point startPoint)
      throws HopException {
    return switch (recordSource.getSourceType()) {
      case DATABASE ->
          new DvDatabaseHubSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              hub,
              startPoint);
      case CSV ->
          new DvCsvHubSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              hub,
              startPoint);
      case PARQUET ->
          new DvParquetHubSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              hub,
              startPoint);
      case ICEBERG ->
          new DvIcebergHubSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              hub,
              startPoint);
      case COMPOSITE ->
          new DvCompositeHubSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              hub,
              startPoint);
      case JSON ->
          new DvJsonHubSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              hub,
              startPoint);
    };
  }

  public static DvSourcePipelineBuilder forLink(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvLink link,
      Point startPoint)
      throws HopException {
    return switch (recordSource.getSourceType()) {
      case DATABASE ->
          new DvDatabaseLinkSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              link,
              startPoint);
      case CSV ->
          new DvCsvLinkSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              link,
              startPoint);
      case PARQUET ->
          new DvParquetLinkSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              link,
              startPoint);
      case ICEBERG ->
          new DvIcebergLinkSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              link,
              startPoint);
      case COMPOSITE ->
          new DvCompositeLinkSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              link,
              startPoint);
      case JSON ->
          new DvJsonLinkSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              link,
              startPoint);
    };
  }

  public static DvSourcePipelineBuilder forSatellite(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvSatellite satellite,
      Point startPoint)
      throws HopException {
    return switch (recordSource.getSourceType()) {
      case DATABASE ->
          new DvDatabaseSatelliteSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              satellite,
              startPoint);
      case CSV ->
          new DvCsvSatelliteSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              satellite,
              startPoint);
      case PARQUET ->
          new DvParquetSatelliteSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              satellite,
              startPoint);
      case ICEBERG ->
          new DvIcebergSatelliteSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              satellite,
              startPoint);
      case COMPOSITE ->
          new DvCompositeSatelliteSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              satellite,
              startPoint);
      case JSON ->
          new DvJsonSatelliteSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              satellite,
              startPoint);
    };
  }

  public static DvSourcePipelineBuilder forReference(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvReferenceTable reference,
      Point startPoint)
      throws HopException {
    return switch (recordSource.getSourceType()) {
      case DATABASE ->
          new DvDatabaseReferenceSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              reference,
              startPoint);
      case CSV ->
          new DvCsvReferenceSourcePipelineBuilder(
              variables,
              metadataProvider,
              model,
              pipelineMeta,
              recordSource,
              dvSource,
              reference,
              startPoint);
      case PARQUET, ICEBERG, COMPOSITE, JSON ->
          throw new HopException(
              "Reference table FULL_REPLACE currently supports DATABASE and CSV sources only (got "
                  + recordSource.getSourceType()
                  + " for "
                  + recordSource.getName()
                  + ")");
    };
  }
}
