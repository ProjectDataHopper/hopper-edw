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
package org.hopper.edw.datavault.hopgui.file.modelgraph;

import org.apache.hop.core.Const;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmRangeDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.hopper.edw.datavault.metadata.dimensional.DmTableBase;
import org.hopper.edw.datavault.metadata.dimensional.DmTableType;

/** Resolves dimensional table display metadata for model graph painters. */
public final class DmTableDisplaySupport {

  public static final String DIMENSION_ICON = "dimension.svg";
  public static final String DIMENSION_ALIAS_ICON = "dimension-alias.svg";
  public static final String JUNK_DIMENSION_ICON = "dimension-junk.svg";
  public static final String RANGE_DIMENSION_ICON = "dimension-range.svg";
  public static final String FACT_ICON = "fact.svg";
  public static final String BRIDGE_ICON = "bridge.svg";

  private DmTableDisplaySupport() {}

  public static String resolveTableIconPath(DmTableType tableType) {
    if (tableType == null) {
      return DIMENSION_ICON;
    }
    return switch (tableType) {
      case FACT,
              FACTLESS_FACT,
              PERIODIC_SNAPSHOT_FACT,
              ACCUMULATING_SNAPSHOT_FACT,
              AGGREGATE_FACT ->
          FACT_ICON;
      case DIMENSION_ALIAS -> DIMENSION_ALIAS_ICON;
      case JUNK_DIMENSION -> JUNK_DIMENSION_ICON;
      case RANGE_DIMENSION -> RANGE_DIMENSION_ICON;
      case BRIDGE -> BRIDGE_ICON;
      case DIMENSION -> DIMENSION_ICON;
    };
  }

  public static String resolveDisplayName(DmTableBase table) {
    if (table == null) {
      return "?";
    }
    return Const.NVL(table.getName(), resolveTypeLabel(table));
  }

  public static String resolveTypeLabel(DmTableBase table) {
    if (table == null || table.getTableType() == null) {
      return "";
    }
    return table.getTableType().getDescription();
  }

  public static String resolveSecondaryFieldName(
      DmTableBase table,
      DimensionalModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (table == null || model == null) {
      return null;
    }
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    if (table instanceof DmDimension dimension) {
      return DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, config, variables);
    }
    if (table instanceof DmJunkDimension junkDimension) {
      return DmSurrogateKeySupport.resolveJunkSurrogateKeyField(junkDimension, config, variables);
    }
    if (table instanceof DmRangeDimension rangeDimension) {
      int bandCount = rangeDimension.getBandsOrEmpty().size();
      if (bandCount == 0) {
        return null;
      }
      return bandCount == 1 ? "1 band" : bandCount + " bands";
    }
    if (table instanceof DmDimensionAlias alias) {
      DmDimension target =
          DmDimensionResolutionSupport.resolveAliasTarget(
              model, alias, variables, metadataProvider);
      if (target == null) {
        return null;
      }
      return DmSurrogateKeySupport.resolveSurrogateKeyField(target, config, variables);
    }
    return null;
  }

  public static String resolveAliasSourceModelDisplayName(
      DmDimensionAlias alias, DimensionalModel model, IVariables variables) {
    if (alias == null) {
      return null;
    }
    return DmDimensionResolutionSupport.resolveAliasSourceModelDisplayName(model, alias, variables);
  }
}
