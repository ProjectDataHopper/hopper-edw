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
package org.hopper.edw.datavault.naming.type;

import org.apache.hop.core.naming.NamingSchemeTypePlugin;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;

/** EDW naming-scheme kinds registered next to Hop's built-in types. */
public final class EdwNamingSchemeTypePlugins {

  private EdwNamingSchemeTypePlugins() {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.EDW_SOURCE_MODEL, name = "Source model names")
  public static final class SourceModel extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.EDW_DV_MODEL, name = "Data Vault model names")
  public static final class DvModel extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(
      id = EdwNamingSchemeTypes.EDW_BV_MODEL,
      name = "Business Vault model names")
  public static final class BvModel extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.EDW_DM_MODEL, name = "Dimensional model names")
  public static final class DmModel extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.EDW_EXECUTION_MAP, name = "Execution map names")
  public static final class ExecutionMap extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.EDW_LINEAGE_VIEW, name = "Lineage view names")
  public static final class LineageView extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.SOURCE_TABLE, name = "Source table names")
  public static final class SourceTable extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.SOURCE_QUERY, name = "Source query names")
  public static final class SourceQuery extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.SOURCE_PIPELINE, name = "Source pipeline names")
  public static final class SourcePipeline extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.SOURCE_JSON, name = "Source JSON names")
  public static final class SourceJson extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.DV_HUB, name = "Data Vault hub names")
  public static final class DvHub extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.DV_LINK, name = "Data Vault link names")
  public static final class DvLink extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(
      id = EdwNamingSchemeTypes.DV_SATELLITE,
      name = "Data Vault satellite names")
  public static final class DvSatellite extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(
      id = EdwNamingSchemeTypes.DV_REFERENCE,
      name = "Data Vault reference names")
  public static final class DvReference extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.BV_SCD2, name = "Business Vault SCD2 names")
  public static final class BvScd2 extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.BV_PIT, name = "Business Vault PIT names")
  public static final class BvPit extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.BV_BRIDGE, name = "Business Vault bridge names")
  public static final class BvBridge extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(
      id = EdwNamingSchemeTypes.BV_BUSINESS_TABLE,
      name = "Business Vault business table names")
  public static final class BvBusinessTable extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(
      id = EdwNamingSchemeTypes.BV_SOURCE_QUERY,
      name = "Business Vault source query names")
  public static final class BvSourceQuery extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.DM_DIMENSION, name = "Dimension names")
  public static final class DmDimension extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(
      id = EdwNamingSchemeTypes.DM_DIMENSION_ALIAS,
      name = "Dimension alias names")
  public static final class DmDimensionAlias extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(
      id = EdwNamingSchemeTypes.DM_JUNK_DIMENSION,
      name = "Junk dimension names")
  public static final class DmJunkDimension extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(
      id = EdwNamingSchemeTypes.DM_RANGE_DIMENSION,
      name = "Range dimension names")
  public static final class DmRangeDimension extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.DM_FACT, name = "Fact names")
  public static final class DmFact extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.DM_FACTLESS_FACT, name = "Factless fact names")
  public static final class DmFactlessFact extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(
      id = EdwNamingSchemeTypes.DM_PERIODIC_SNAPSHOT_FACT,
      name = "Periodic snapshot fact names")
  public static final class DmPeriodicSnapshotFact extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(
      id = EdwNamingSchemeTypes.DM_ACCUMULATING_SNAPSHOT_FACT,
      name = "Accumulating snapshot fact names")
  public static final class DmAccumulatingSnapshotFact extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(
      id = EdwNamingSchemeTypes.DM_AGGREGATE_FACT,
      name = "Aggregate fact names")
  public static final class DmAggregateFact extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(id = EdwNamingSchemeTypes.DM_BRIDGE, name = "Dimensional bridge names")
  public static final class DmBridge extends EdwBuiltinNamingSchemeType {}

  @NamingSchemeTypePlugin(
      id = EdwNamingSchemeTypes.EDW_RECORD_DEFINITION,
      name = "Record definition names")
  public static final class RecordDefinition extends EdwBuiltinNamingSchemeType {}
}
