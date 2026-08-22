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
package org.apache.hop.datavault.metadata;

import java.util.Date;
import java.util.List;
import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.ICheckResultSource;
import org.apache.hop.core.changed.IChanged;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataObject;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadataObjectFactory;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.workflow.WorkflowMeta;

/**
 * Common interface for all Data Vault 2.0 table-like structures: Hubs, Links, and Satellites.
 *
 * <p>This allows generic code (transforms, generators, validators, DDL builders, etc.) to treat any
 * DV table uniformly for the most common attributes without caring about the specific subtype.
 *
 * <p>Extending IGuiPosition / IBaseMeta / IHasName / IChanged allows DV table definitions to be
 * used as first-class draggable/positionable objects inside a visual Data Vault modeling
 * perspective (analogous to TransformMeta / ActionMeta in pipelines/workflows).
 */
@HopMetadataObject(xmlKey = "tableType", objectFactory = IDvTable.DvTableFactory.class)
public interface IDvTable extends IGuiPosition, IBaseMeta, IHasName, IChanged, ICheckResultSource {

  /**
   * Logical / metadata name of this DV object. Usually matches the table name unless overridden.
   */
  String getName();

  /** The physical table name in the target database. */
  String getTableName();

  /** Human-readable description of this table / concept. */
  String getDescription();

  /** Returns the type of this Data Vault table (HUB, SATELLITE or LINK). */
  DvTableType getTableType();

  /**
   * Perform checks on this table definition and add results (errors, warnings, etc) to the provided
   * list.
   */
  default void check(
      List<ICheckResult> remarks, IHopMetadataProvider metadataProvider, IVariables variables) {
    check(remarks, metadataProvider, variables, DvModelCheckOptions.defaults(), null);
  }

  /**
   * Perform checks on this table definition and add results (errors, warnings, etc) to the provided
   * list.
   *
   * @param options controls detailed source schema resolution during type validation
   * @param model containing model (for cross-table checks); may be null
   */
  void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DvModelCheckOptions options,
      DataVaultModel model);

  /**
   * Generate "update" pipeline(s) for this table.
   *
   * <p>For Hubs with multiple sources, one pipeline is generated per source (each source can have
   * its own business key mappings via sourceSystem in BusinessKey, different source queries, etc.).
   *
   * @param metadataProvider for loading configuration, database connections, sources etc.
   * @param variables for variable resolution during SQL generation and quoting.
   * @param model the containing DataVaultModel (for config name etc.)
   * @param loadDate the static load date to use for all records in this batch update
   * @param recordSourceGroup optional group filter; when empty, all record sources are processed
   * @return list of generated PipelineMeta (one per source for hubs); caller does XML roundtrip
   *     etc.
   * @throws HopException on metadata load or other errors during generation
   */
  List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      String recordSourceGroup)
      throws HopException;

  /**
   * Generate workflow units that orchestrate update pipelines for this table.
   *
   * <p>Multi-source hubs and links return one workflow that runs per-source pipelines in series so
   * each subsequent source sees keys already inserted by earlier sources (required for safe
   * primary-key enforcement under bulk load). When this list is non-empty, update execution must
   * treat each workflow as the unit of work and must <em>not</em> schedule the nested pipelines as
   * independent parallel orchestrator jobs.
   *
   * <p>Default implementations return an empty list (pipeline-only tables).
   *
   * @param metadataProvider for loading configuration, database connections, sources etc.
   * @param variables for variable resolution
   * @param model the containing DataVaultModel
   * @param loadDate the static load date for this batch
   * @param recordSourceGroup optional group filter; when empty, all record sources are processed
   * @return generated workflows (often empty or a single multi-source serial workflow)
   * @throws HopException on generation errors
   */
  List<WorkflowMeta> generateUpdateWorkflows(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      String recordSourceGroup)
      throws HopException;

  /**
   * Generate the DDL (CREATE/ALTER) statements required to prepare or update the target database
   * structure for this DV table.
   *
   * <p>This is separated from {@link #generateUpdatePipelines} so that target schema management can
   * be controlled independently (e.g. via a workflow action option).
   *
   * @param metadataProvider for loading configuration, database connections, etc.
   * @param variables for variable resolution
   * @param model the containing DataVaultModel
   * @return DDL scripts (in execution order) for the model target database. An empty list means no
   *     DDL is required.
   * @throws HopException on errors while determining target layout or connections
   */
  List<String> generateUpdateDdl(
      IHopMetadataProvider metadataProvider, IVariables variables, DataVaultModel model)
      throws HopException;

  /**
   * Get the target table layout (IRowMeta) for this DV table. Used to define/create the physical
   * table in the target database before loading.
   *
   * <p>First column: the hub's hash key field name (surrogate key column name), with data type
   * (Binary/String) from DataVaultConfiguration.
   *
   * <p>Then: the business key source field names (type taken from the DataVaultSource fields).
   *
   * <p>Finally: the load date field name from config, as Hop Timestamp.
   *
   * @param metadataProvider to access for loading DataVaultConfiguration, DataVaultSource, etc.
   * @param variables for resolving names if needed
   * @param model the DataVaultModel (to resolve the configuration name)
   */
  IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DataVaultModel model)
      throws HopException;

  /**
   * Ensure unknown and invalid sentinel rows exist in the target table when enabled in the model's
   * Data Vault Configuration. Missing rows are inserted with {@link
   * org.apache.hop.core.database.Database#insertRow(String, IRowMeta, Object[])}.
   *
   * @return the number of rows inserted (0 if none were needed or this table type has no sentinel
   *     rows)
   */
  int ensureSpecialRecords(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      ILoggingObject loggingObject)
      throws HopException;

  /**
   * Factory used by the Hop metadata serializer to handle polymorphic serialization of {@link
   * IDvTable} implementations (DvHub, DvLink, DvSatellite) inside lists such as the one in {@link
   * DataVaultModel}.
   */
  final class DvTableFactory implements IHopMetadataObjectFactory {

    @Override
    public Object createObject(String id, Object parentObject) throws HopException {
      if (DvTableType.HUB.name().equals(id)) {
        return new DvHub();
      }
      if (DvTableType.SATELLITE.name().equals(id)) {
        return new DvSatellite();
      }
      if (DvTableType.LINK.name().equals(id)) {
        return new DvLink();
      }
      if (DvTableType.REFERENCE.name().equals(id)) {
        return new DvReferenceTable();
      }
      // LINKED_TABLE (current) and TABLE_REFERENCE (legacy .hdv dual-read)
      if (DvTableType.isLinkedTableCode(id)) {
        return new DvLinkedTable();
      }
      throw new HopException("Unable to recognize Data Vault table type with ID '" + id + "'");
    }

    @Override
    public String getObjectId(Object object) throws HopException {
      if (!(object instanceof IDvTable dvTable)) {
        throw new HopException(
            "Object is not of class IDvTable but of " + object.getClass().getName() + "'");
      }
      DvTableType tableType = dvTable.getTableType();
      if (tableType == null) {
        throw new HopException("Data Vault table has no table type set");
      }
      // Always persist the current code for linked tables (never re-write TABLE_REFERENCE).
      DvTableType normalized = DvTableType.normalize(tableType);
      return normalized.name();
    }
  }
}
