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
package org.hopper.edw.datavault.naming;

import org.apache.hop.ui.core.widget.NamingSchemeTypes;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.businessvault.BvTableType;
import org.hopper.edw.datavault.metadata.dimensional.DmTableType;

/**
 * Naming-scheme type codes registered by hopper-edw plus the built-in Hop codes this plugin reuses.
 *
 * <p>Values must match {@link org.apache.hop.core.naming.NamingSchemeTypePlugin#id()} on {@link
 * org.hopper.edw.datavault.naming.type.EdwNamingSchemeTypePlugins} (or Hop's built-in kinds).
 */
public final class EdwNamingSchemeTypes {

  public static final String EDW_SOURCE_MODEL = "edw-source-model";
  public static final String EDW_DV_MODEL = "edw-dv-model";
  public static final String EDW_BV_MODEL = "edw-bv-model";
  public static final String EDW_DM_MODEL = "edw-dm-model";
  public static final String EDW_EXECUTION_MAP = "edw-execution-map";
  public static final String EDW_LINEAGE_VIEW = "edw-lineage-view";

  public static final String SOURCE_TABLE = "source-table";
  public static final String SOURCE_QUERY = "source-query";
  public static final String SOURCE_PIPELINE = "source-pipeline";
  public static final String SOURCE_JSON = "source-json";

  public static final String DV_HUB = "dv-hub";
  public static final String DV_LINK = "dv-link";
  public static final String DV_SATELLITE = "dv-satellite";
  public static final String DV_REFERENCE = "dv-reference";

  public static final String BV_SCD2 = "bv-scd2";
  public static final String BV_PIT = "bv-pit";
  public static final String BV_BRIDGE = "bv-bridge";
  public static final String BV_BUSINESS_TABLE = "bv-business-table";
  public static final String BV_SOURCE_QUERY = "bv-source-query";

  public static final String DM_DIMENSION = "dm-dimension";
  public static final String DM_DIMENSION_ALIAS = "dm-dimension-alias";
  public static final String DM_JUNK_DIMENSION = "dm-junk-dimension";
  public static final String DM_RANGE_DIMENSION = "dm-range-dimension";
  public static final String DM_FACT = "dm-fact";
  public static final String DM_FACTLESS_FACT = "dm-factless-fact";
  public static final String DM_PERIODIC_SNAPSHOT_FACT = "dm-periodic-snapshot-fact";
  public static final String DM_ACCUMULATING_SNAPSHOT_FACT = "dm-accumulating-snapshot-fact";
  public static final String DM_AGGREGATE_FACT = "dm-aggregate-fact";
  public static final String DM_BRIDGE = "dm-bridge";

  public static final String EDW_RECORD_DEFINITION = "edw-record-definition";

  public static final String DATABASE_TABLE = NamingSchemeTypes.DATABASE_TABLE;
  public static final String DATABASE_COLUMN = NamingSchemeTypes.DATABASE_COLUMN;
  public static final String FILE = NamingSchemeTypes.FILE;
  public static final String HOP_METADATA = NamingSchemeTypes.HOP_METADATA;
  public static final String HOP_ACTION = NamingSchemeTypes.HOP_ACTION;
  public static final String HOP_PIPELINE = NamingSchemeTypes.HOP_PIPELINE;

  private EdwNamingSchemeTypes() {}

  public static String forDvTable(DvTableType type) {
    if (type == null) {
      return DV_HUB;
    }
    return switch (DvTableType.normalize(type)) {
      case LINK -> DV_LINK;
      case SATELLITE -> DV_SATELLITE;
      case REFERENCE -> DV_REFERENCE;
      default -> DV_HUB;
    };
  }

  public static String forBvTable(BvTableType type) {
    if (type == null) {
      return BV_SCD2;
    }
    return switch (type) {
      case PIT -> BV_PIT;
      case BRIDGE -> BV_BRIDGE;
      case BUSINESS_TABLE -> BV_BUSINESS_TABLE;
      case SOURCE_QUERY -> BV_SOURCE_QUERY;
      default -> BV_SCD2;
    };
  }

  public static String forDmTable(DmTableType type) {
    if (type == null) {
      return DM_DIMENSION;
    }
    return switch (type) {
      case DIMENSION_ALIAS -> DM_DIMENSION_ALIAS;
      case JUNK_DIMENSION -> DM_JUNK_DIMENSION;
      case RANGE_DIMENSION -> DM_RANGE_DIMENSION;
      case FACT -> DM_FACT;
      case FACTLESS_FACT -> DM_FACTLESS_FACT;
      case PERIODIC_SNAPSHOT_FACT -> DM_PERIODIC_SNAPSHOT_FACT;
      case ACCUMULATING_SNAPSHOT_FACT -> DM_ACCUMULATING_SNAPSHOT_FACT;
      case AGGREGATE_FACT -> DM_AGGREGATE_FACT;
      case BRIDGE -> DM_BRIDGE;
      default -> DM_DIMENSION;
    };
  }
}
