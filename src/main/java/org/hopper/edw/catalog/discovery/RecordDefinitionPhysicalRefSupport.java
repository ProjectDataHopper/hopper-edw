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
package org.hopper.edw.catalog.discovery;

import org.hopper.edw.catalog.model.DvSourceRecord;
import org.hopper.edw.catalog.model.PhysicalFileRef;
import org.hopper.edw.catalog.model.PhysicalIcebergTableRef;
import org.hopper.edw.catalog.model.PhysicalTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.apache.hop.i18n.BaseMessages;

/** Builds physical discovery references from catalog record definitions. */
public final class RecordDefinitionPhysicalRefSupport {

  private static final Class<?> PKG = RecordDefinitionPhysicalRefSupport.class;

  private RecordDefinitionPhysicalRefSupport() {}

  public static boolean supportsRefreshFromSource(RecordDefinition definition) {
    if (definition == null || definition.getType() != RecordDefinitionType.DV_SOURCE) {
      return false;
    }
    DvSourceType sourceType = resolveSourceType(definition);
    if (sourceType == null) {
      return false;
    }
    return switch (sourceType) {
      case DATABASE -> definition.getPhysicalTable() != null;
      case CSV, PARQUET -> definition.getPhysicalFile() != null;
      case ICEBERG -> definition.getPhysicalIcebergTable() != null;
      case COMPOSITE -> hasCompositeSourceRef(definition);
      case JSON -> hasJsonSourceRef(definition);
      case PIPELINE -> hasPipelineSourceRef(definition);
    };
  }

  /** COMPOSITE feeds are refreshable when they point at a source model query. */
  static boolean hasCompositeSourceRef(RecordDefinition definition) {
    if (definition == null || definition.getDvSource() == null) {
      return false;
    }
    DvSourceRecord dvSource = definition.getDvSource();
    return !Utils.isEmpty(dvSource.getCompositeSourceModelFilename())
        && !Utils.isEmpty(dvSource.getCompositeSourceQueryName());
  }

  /** JSON feeds are refreshable when they point at a source model JSON object. */
  static boolean hasJsonSourceRef(RecordDefinition definition) {
    if (definition == null || definition.getDvSource() == null) {
      return false;
    }
    DvSourceRecord dvSource = definition.getDvSource();
    return !Utils.isEmpty(dvSource.getJsonSourceModelFilename())
        && !Utils.isEmpty(dvSource.getJsonSourceName());
  }

  /**
   * PIPELINE feeds are refreshable when they point at a source-model pipeline card and/or a
   * pipeline file + output transform (same fields set at publish time).
   */
  static boolean hasPipelineSourceRef(RecordDefinition definition) {
    if (definition == null || definition.getDvSource() == null) {
      return false;
    }
    DvSourceRecord dvSource = definition.getDvSource();
    boolean fromSourceModel =
        !Utils.isEmpty(dvSource.getPipelineSourceModelFilename())
            && !Utils.isEmpty(dvSource.getPipelineSourceName());
    boolean fromPipelineFile =
        !Utils.isEmpty(dvSource.getPipelineFilename())
            && !Utils.isEmpty(dvSource.getPipelineTransformName());
    return fromSourceModel || fromPipelineFile;
  }

  public static DvSourceType resolveSourceType(RecordDefinition definition) {
    if (definition == null || definition.getDvSource() == null) {
      return null;
    }
    String sourceType = definition.getDvSource().getSourceType();
    if (Utils.isEmpty(sourceType)) {
      return null;
    }
    try {
      return DvSourceType.valueOf(sourceType.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static PhysicalSourceRef toPhysicalSourceRef(RecordDefinition definition)
      throws HopException {
    if (definition == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionPhysicalRefSupport.Error.MissingDefinition"));
    }
    DvSourceType sourceType = resolveSourceType(definition);
    if (sourceType == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionPhysicalRefSupport.Error.MissingSourceType"));
    }

    return switch (sourceType) {
      case DATABASE -> fromPhysicalTable(definition.getPhysicalTable());
      case CSV, PARQUET -> fromPhysicalFile(definition.getPhysicalFile());
      case ICEBERG -> fromPhysicalIcebergTable(definition.getPhysicalIcebergTable());
      case COMPOSITE -> fromCompositeSource(definition.getDvSource());
      case JSON -> fromJsonSource(definition.getDvSource());
      case PIPELINE -> fromPipelineSource(definition.getDvSource());
    };
  }

  private static PhysicalSourceRef fromPipelineSource(DvSourceRecord dvSource) throws HopException {
    if (dvSource == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionPhysicalRefSupport.Error.MissingPipelineRef"));
    }
    boolean fromSourceModel =
        !Utils.isEmpty(dvSource.getPipelineSourceModelFilename())
            && !Utils.isEmpty(dvSource.getPipelineSourceName());
    boolean fromPipelineFile =
        !Utils.isEmpty(dvSource.getPipelineFilename())
            && !Utils.isEmpty(dvSource.getPipelineTransformName());
    if (!fromSourceModel && !fromPipelineFile) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionPhysicalRefSupport.Error.MissingPipelineRef"));
    }
    return PhysicalSourceRef.builder()
        .pipelineSourceModelFilename(dvSource.getPipelineSourceModelFilename())
        .pipelineSourceName(dvSource.getPipelineSourceName())
        .pipelineFilename(dvSource.getPipelineFilename())
        .pipelineTransformName(dvSource.getPipelineTransformName())
        .build();
  }

  private static PhysicalSourceRef fromCompositeSource(DvSourceRecord dvSource)
      throws HopException {
    if (dvSource == null
        || Utils.isEmpty(dvSource.getCompositeSourceModelFilename())
        || Utils.isEmpty(dvSource.getCompositeSourceQueryName())) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionPhysicalRefSupport.Error.MissingCompositeRef"));
    }
    return PhysicalSourceRef.builder()
        .compositeSourceModelFilename(dvSource.getCompositeSourceModelFilename())
        .compositeSourceQueryName(dvSource.getCompositeSourceQueryName())
        .build();
  }

  private static PhysicalSourceRef fromJsonSource(DvSourceRecord dvSource) throws HopException {
    if (dvSource == null
        || Utils.isEmpty(dvSource.getJsonSourceModelFilename())
        || Utils.isEmpty(dvSource.getJsonSourceName())) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionPhysicalRefSupport.Error.MissingCompositeRef"));
    }
    return PhysicalSourceRef.builder()
        .jsonSourceModelFilename(dvSource.getJsonSourceModelFilename())
        .jsonSourceName(dvSource.getJsonSourceName())
        .build();
  }

  private static PhysicalSourceRef fromPhysicalTable(PhysicalTableRef physicalTable)
      throws HopException {
    if (physicalTable == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionPhysicalRefSupport.Error.MissingPhysicalRef"));
    }
    return PhysicalSourceRef.builder()
        .databaseConnectionName(physicalTable.getDatabaseMetaName())
        .schemaName(physicalTable.getSchemaName())
        .tableName(physicalTable.getTableName())
        .build();
  }

  private static PhysicalSourceRef fromPhysicalFile(PhysicalFileRef physicalFile)
      throws HopException {
    if (physicalFile == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionPhysicalRefSupport.Error.MissingPhysicalRef"));
    }
    return PhysicalSourceRef.builder()
        .folder(physicalFile.getFolder())
        .includeFileMask(physicalFile.getIncludeFileMask())
        .excludeFileMask(physicalFile.getExcludeFileMask())
        .includeSubfolders(physicalFile.isIncludeSubfolders())
        .build();
  }

  private static PhysicalSourceRef fromPhysicalIcebergTable(
      PhysicalIcebergTableRef physicalIcebergTable) throws HopException {
    if (physicalIcebergTable == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionPhysicalRefSupport.Error.MissingPhysicalRef"));
    }
    PhysicalSourceRef physicalRef =
        PhysicalSourceRef.fromPhysicalIcebergTableRef(physicalIcebergTable);
    if (physicalRef == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionPhysicalRefSupport.Error.MissingPhysicalRef"));
    }
    return physicalRef;
  }

  public static DvSourceRecord requireDvSource(RecordDefinition definition) throws HopException {
    if (definition == null || definition.getDvSource() == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionPhysicalRefSupport.Error.MissingDvSource"));
    }
    return definition.getDvSource();
  }
}
