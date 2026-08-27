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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.hopper.edw.datavault.metadata.DataVaultModel;

/**
 * SQL-sourced business table materialised as a database view or table ({@code CREATE OR REPLACE
 * VIEW|TABLE … AS query}) with dbt-style {@code ref()} / {@code source()} templates.
 */
@Getter
@Setter
public class BvBusinessTable extends BvTableBase {

  /**
   * Authoring SQL containing optional {@code {{ ref(...) }}} / {@code {{ source(...) }}} macros.
   */
  @HopMetadataProperty private String sqlQuery;

  @HopMetadataProperty(storeWithCode = true)
  private BvSqlReferenceStyle referenceStyle = BvSqlReferenceStyle.DBT;

  @HopMetadataProperty(storeWithCode = true)
  private BvSqlMaterialization materialization = BvSqlMaterialization.VIEW;

  /** Optional target schema (dbt {@code schema} / {@code +schema}). */
  @HopMetadataProperty private String schemaName;

  /**
   * Portable relative path of the originating dbt model, e.g. {@code models/marts/customers.sql}.
   */
  @HopMetadataProperty private String originDbtPath;

  @HopMetadataProperty(key = "column_note", groupKey = "column_notes")
  private List<BvSqlColumnNote> columnNotes = new ArrayList<>();

  @HopMetadataProperty(key = "sql_source", groupKey = "sql_sources")
  private List<BvSqlSource> sources = new ArrayList<>();

  @HopMetadataProperty(key = "sql_ref", groupKey = "sql_refs")
  private List<BvSqlRef> sqlRefs = new ArrayList<>();

  public BvBusinessTable() {
    super(BvTableType.BUSINESS_TABLE);
  }

  public List<BvSqlSource> getSources() {
    if (sources == null) {
      sources = new ArrayList<>();
    }
    return sources;
  }

  public List<BvSqlRef> getSqlRefs() {
    if (sqlRefs == null) {
      sqlRefs = new ArrayList<>();
    }
    return sqlRefs;
  }

  public List<BvSqlColumnNote> getColumnNotes() {
    if (columnNotes == null) {
      columnNotes = new ArrayList<>();
    }
    return columnNotes;
  }

  public BvSqlReferenceStyle getReferenceStyleOrDefault() {
    return referenceStyle != null ? referenceStyle : BvSqlReferenceStyle.DBT;
  }

  public BvSqlMaterialization getMaterializationOrDefault() {
    return materialization != null ? materialization : BvSqlMaterialization.VIEW;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel) {
    super.check(remarks, metadataProvider, variables, model, dataVaultModel);
    BvSqlValidationSupport.validate(
        remarks, this, model, dataVaultModel, metadataProvider, variables);
  }

  @Override
  public List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    return BvSqlViewPipelineSupport.generateBuildPipelines(
        metadataProvider, variables, model, dataVaultModel, this);
  }

  @Override
  public List<String> generateBuildDdl(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    // CREATE OR REPLACE VIEW|TABLE runs in the build pipeline after dependent SCD2/PIT loads.
    return List.of();
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    // Layout is defined by the SQL query result; no static column model in MVP.
    return new RowMeta();
  }
}
