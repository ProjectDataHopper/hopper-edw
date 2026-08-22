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
package org.hopper.edw.datavault.catalog;

import java.util.Date;
import java.util.List;
import org.hopper.edw.catalog.model.DvCsvFormatRecord;
import org.hopper.edw.catalog.model.DvSourceRecord;
import org.hopper.edw.catalog.model.PhysicalFileRef;
import org.hopper.edw.catalog.model.PhysicalIcebergTableRef;
import org.hopper.edw.catalog.model.PhysicalTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.IDvSource;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.composite.DvCompositeSource;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSource;
import org.hopper.edw.datavault.metadata.file.DvCsvInputMode;
import org.hopper.edw.datavault.metadata.file.DvCsvSource;
import org.hopper.edw.datavault.metadata.file.DvFileLocationSupport;
import org.hopper.edw.datavault.metadata.file.IDvFileBasedSource;
import org.hopper.edw.datavault.metadata.iceberg.DvIcebergLocationSupport;
import org.hopper.edw.datavault.metadata.iceberg.DvIcebergSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Maps {@link DataVaultSource} to catalog {@link RecordDefinition} entries. */
public final class DvSourceCatalogMapper {

  private DvSourceCatalogMapper() {}

  public static RecordDefinition toRecordDefinition(
      DataVaultSource source,
      String namespace,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt,
      String workflowName)
      throws HopException {
    return toRecordDefinition(
        source,
        namespace,
        RecordDefinitionType.DV_SOURCE,
        model,
        variables,
        metadataProvider,
        updatedAt,
        workflowName,
        null);
  }

  public static RecordDefinition toRecordDefinition(
      DataVaultSource source,
      String namespace,
      RecordDefinitionType recordType,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt,
      String workflowName)
      throws HopException {
    return toRecordDefinition(
        source,
        namespace,
        recordType,
        model,
        variables,
        metadataProvider,
        updatedAt,
        workflowName,
        null);
  }

  public static RecordDefinition toRecordDefinition(
      DataVaultSource source,
      String namespace,
      RecordDefinitionType recordType,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt,
      String workflowName,
      String pipelineName)
      throws HopException {
    if (source == null || Utils.isEmpty(source.getName())) {
      throw new HopException("Data Vault source is missing a name");
    }

    List<SourceField> sourceFields = source.getFields(metadataProvider);
    RecordDefinitionType effectiveType =
        recordType != null ? recordType : RecordDefinitionType.DV_SOURCE;

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, source.getName()));
    definition.setType(effectiveType);
    definition.setDescription(source.getDvSourceOrDefault().getDescription());
    if (effectiveType == RecordDefinitionType.DV_SOURCE) {
      DvSourceRecord dvSourceRecord = new DvSourceRecord();
      dvSourceRecord.setSourceType(source.getSourceType().name());
      dvSourceRecord.setSourceIndicator(source.getSourceIndicator());
      dvSourceRecord.setSourceIndicatorField(source.getSourceIndicatorField());
      dvSourceRecord.setGroup(source.getGroup());
      dvSourceRecord.setDeliveryType(source.getDeliveryTypeOrDefault().name());
      if (source.getSourceType() == DvSourceType.COMPOSITE
          && source.getDvSourceOrDefault() instanceof DvCompositeSource composite) {
        dvSourceRecord.setCompositeSourceModelFilename(composite.getSourceModelFilename());
        dvSourceRecord.setCompositeSourceQueryName(composite.getSourceQueryName());
        dvSourceRecord.setCompositeGeneratedSql(composite.getGeneratedSql());
      }
      if (source.getSourceType() == DvSourceType.JSON
          && source.getDvSourceOrDefault()
              instanceof org.hopper.edw.datavault.metadata.json.DvJsonSource jsonSrc) {
        dvSourceRecord.setJsonSourceModelFilename(jsonSrc.getSourceModelFilename());
        dvSourceRecord.setJsonSourceName(jsonSrc.getSourceJsonName());
      }
      if (source.getSourceType() == DvSourceType.PIPELINE
          && source.getDvSourceOrDefault()
              instanceof org.hopper.edw.datavault.metadata.pipeline.DvPipelineSource pipeSrc) {
        dvSourceRecord.setPipelineFilename(pipeSrc.getPipelineFilename());
        dvSourceRecord.setPipelineTransformName(pipeSrc.getOutputTransformName());
        dvSourceRecord.setPipelineRunConfiguration(pipeSrc.getPipelineRunConfiguration());
        dvSourceRecord.setPipelineSourceModelFilename(pipeSrc.getSourceModelFilename());
        dvSourceRecord.setPipelineSourceName(pipeSrc.getSourcePipelineName());
      }
      definition.setDvSource(dvSourceRecord);
    }
    // Single writer: structured fields + derived row meta.
    DvSourceFieldSupport.applyLayoutToDefinition(definition, sourceFields, variables);
    definition.setOrigin(
        buildOrigin(source, model, variables, updatedAt, workflowName, pipelineName));
    IDvSource dvSource = source.getDvSourceOrDefault();
    if (source.getSourceType() == DvSourceType.CSV) {
      definition.setPhysicalFile(buildPhysicalFileRef(dvSource));
      definition.setPhysicalTable(null);
      definition.setPhysicalIcebergTable(null);
      if (definition.getDvSource() != null && dvSource instanceof DvCsvSource csvSource) {
        definition.getDvSource().setCsvFormat(buildCsvFormatRecord(csvSource));
      }
    } else if (source.getSourceType() == DvSourceType.PARQUET) {
      definition.setPhysicalFile(buildPhysicalFileRef(dvSource));
      definition.setPhysicalTable(null);
      definition.setPhysicalIcebergTable(null);
    } else if (source.getSourceType() == DvSourceType.ICEBERG) {
      definition.setPhysicalIcebergTable(buildPhysicalIcebergTableRef(dvSource));
      definition.setPhysicalTable(null);
      definition.setPhysicalFile(null);
    } else if (source.getSourceType() == DvSourceType.COMPOSITE
        || source.getSourceType() == DvSourceType.JSON) {
      definition.setPhysicalTable(null);
      definition.setPhysicalFile(null);
      definition.setPhysicalIcebergTable(null);
    } else {
      definition.setPhysicalTable(buildPhysicalTableRef(dvSource));
      definition.setPhysicalFile(null);
      definition.setPhysicalIcebergTable(null);
    }
    if (definition.getType() == RecordDefinitionType.DV_SOURCE) {
      definition.getTags().add("DV Source");
      definition.getTags().add(source.getDeliveryTypeOrDefault().name());
    } else if (definition.getType() != null) {
      definition.getTags().add(definition.getType().name());
    }

    return definition;
  }

  public static RecordDefinition toRecordDefinition(
      DataVaultSource source,
      String namespace,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return toRecordDefinition(
        source, namespace, null, variables, metadataProvider, new Date(), null);
  }

  /**
   * Catalog model type for feeds published from a source model ({@code .hsm}). Kept as a string
   * constant so this mapper does not depend on Hop GUI navigation classes.
   */
  public static final String ORIGIN_MODEL_TYPE_SOURCE_MODEL = "SOURCE_MODEL";

  private static RecordOrigin buildOrigin(
      DataVaultSource source,
      DataVaultModel model,
      IVariables variables,
      Date updatedAt,
      String workflowName,
      String pipelineName) {
    RecordOrigin origin = new RecordOrigin();
    origin.setUpdatedAt(updatedAt);
    origin.setLastDiscoveredAt(updatedAt);
    origin.setLastWorkflow(workflowName);
    origin.setLastPipeline(pipelineName);

    // Prefer .hsm provenance stored on composite / JSON / pipeline physical sources (source modeler
    // publish). That enables catalog "Go to origin" into the source model graph.
    SourceModelProvenance sourceModelProvenance = extractSourceModelProvenance(source);
    if (sourceModelProvenance != null && !Utils.isEmpty(sourceModelProvenance.modelFilename())) {
      origin.setModelType(ORIGIN_MODEL_TYPE_SOURCE_MODEL);
      origin.setModelFilename(
          CatalogModelRegistrySupport.portableModelPath(
              sourceModelProvenance.modelFilename(), variables));
      origin.setModelElementName(
          !Utils.isEmpty(sourceModelProvenance.elementName())
              ? sourceModelProvenance.elementName()
              : source.getName());
      if (!Utils.isEmpty(sourceModelProvenance.modelName())) {
        origin.setModelName(sourceModelProvenance.modelName());
      }
      return origin;
    }

    origin.setModelType("DATA_VAULT_SOURCE");
    if (model != null) {
      origin.setModelName(model.getName());
      origin.setModelFilename(
          CatalogModelRegistrySupport.portableModelPath(model.getFilename(), variables));
      origin.setHopProject(model.getName());
    }
    origin.setModelElementName(source.getName());
    return origin;
  }

  private record SourceModelProvenance(
      String modelFilename, String elementName, String modelName) {}

  private static SourceModelProvenance extractSourceModelProvenance(DataVaultSource source) {
    if (source == null) {
      return null;
    }
    IDvSource dv = source.getDvSourceOrDefault();
    if (dv instanceof org.hopper.edw.datavault.metadata.pipeline.DvPipelineSource pipeSrc
        && !Utils.isEmpty(pipeSrc.getSourceModelFilename())) {
      return new SourceModelProvenance(
          pipeSrc.getSourceModelFilename(), pipeSrc.getSourcePipelineName(), null);
    }
    if (dv instanceof org.hopper.edw.datavault.metadata.json.DvJsonSource jsonSrc
        && !Utils.isEmpty(jsonSrc.getSourceModelFilename())) {
      return new SourceModelProvenance(
          jsonSrc.getSourceModelFilename(), jsonSrc.getSourceJsonName(), null);
    }
    if (dv instanceof DvCompositeSource composite
        && !Utils.isEmpty(composite.getSourceModelFilename())) {
      return new SourceModelProvenance(
          composite.getSourceModelFilename(), composite.getSourceQueryName(), null);
    }
    return null;
  }

  private static PhysicalTableRef buildPhysicalTableRef(IDvSource dvSource) {
    if (!(dvSource instanceof DvDatabaseSource dbSource)) {
      return null;
    }
    PhysicalTableRef ref = new PhysicalTableRef();
    ref.setDatabaseMetaName(dbSource.getDatabaseName());
    ref.setSchemaName(dbSource.getSchemaName());
    ref.setTableName(dbSource.getTableName());
    return ref;
  }

  private static PhysicalIcebergTableRef buildPhysicalIcebergTableRef(IDvSource dvSource) {
    if (!(dvSource instanceof DvIcebergSource icebergSource)) {
      return null;
    }
    return DvIcebergLocationSupport.toPhysicalIcebergTableRef(icebergSource);
  }

  private static PhysicalFileRef buildPhysicalFileRef(IDvSource dvSource) {
    if (!(dvSource instanceof IDvFileBasedSource fileSource)) {
      return null;
    }
    return DvFileLocationSupport.toPhysicalFileRef(fileSource);
  }

  private static DvCsvFormatRecord buildCsvFormatRecord(DvCsvSource csvSource) {
    DvCsvFormatRecord format = new DvCsvFormatRecord();
    format.setDelimiter(csvSource.getDelimiter());
    format.setEnclosure(csvSource.getEnclosure());
    format.setEscapeCharacter(csvSource.getEscapeCharacter());
    format.setEncoding(csvSource.getEncoding());
    format.setHeaderPresent(csvSource.isHeaderPresent());
    format.setHeaderLines(csvSource.getHeaderLines());
    format.setFileFormat("CSV");
    format.setInputTransform(
        csvSource.getInputMode() == DvCsvInputMode.CSV_INPUT ? "CSV_INPUT" : "TEXT_FILE_INPUT");
    format.setSingleFilename(csvSource.getSingleFilename());
    return format;
  }
}
