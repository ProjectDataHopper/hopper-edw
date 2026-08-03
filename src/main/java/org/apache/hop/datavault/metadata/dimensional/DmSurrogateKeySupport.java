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
package org.apache.hop.datavault.metadata.dimensional;

import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/**
 * Resolves per-dimension and junk-dimension surrogate key settings with backward-compatible
 * defaults.
 */
public final class DmSurrogateKeySupport {

  private DmSurrogateKeySupport() {}

  public static DmSurrogateKeyStrategy resolveStrategy(DmDimension dimension) {
    if (dimension == null) {
      return DmSurrogateKeyStrategy.AUTO_INCREMENT;
    }
    if (dimension.getSurrogateKeyStrategy() != null) {
      return dimension.getSurrogateKeyStrategy();
    }
    if (DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension)
        == DmDimensionLoadStrategySupport.DmDimensionLoadStrategy.PURE_TYPE1) {
      return DmSurrogateKeyStrategy.NONE;
    }
    return DmSurrogateKeyStrategy.AUTO_INCREMENT;
  }

  public static DmJunkSurrogateKeyStrategy resolveJunkStrategy(DmJunkDimension junkDimension) {
    if (junkDimension == null || junkDimension.getSurrogateKeyStrategy() == null) {
      return DmJunkSurrogateKeyStrategy.AUTO_INCREMENT;
    }
    return junkDimension.getSurrogateKeyStrategy();
  }

  public static boolean usesSurrogateColumn(DmDimension dimension) {
    return resolveStrategy(dimension) != DmSurrogateKeyStrategy.NONE;
  }

  public static boolean usesSurrogateColumn(DmJunkDimension junkDimension) {
    DmJunkSurrogateKeyStrategy strategy = resolveJunkStrategy(junkDimension);
    return strategy != null;
  }

  public static String resolveSurrogateKeyField(
      DmDimension dimension, DimensionalConfiguration config, IVariables variables) {
    if (!usesSurrogateColumn(dimension)) {
      return null;
    }
    String explicit = resolve(dimension.getSurrogateKeyField(), variables);
    if (!Utils.isEmpty(explicit)) {
      return explicit;
    }
    DimensionalConfiguration resolvedConfig =
        config != null ? config : new DimensionalConfiguration();
    return resolvedConfig.resolveDimKeyField(variables);
  }

  public static String resolveSurrogateKeySourceField(
      DmDimension dimension, DimensionalConfiguration config, IVariables variables) {
    String explicit = resolve(dimension.getSurrogateKeySourceField(), variables);
    if (!Utils.isEmpty(explicit)) {
      return explicit;
    }
    return resolveSurrogateKeyField(dimension, config, variables);
  }

  public static String resolveJunkSurrogateKeyField(
      DmJunkDimension junkDimension, DimensionalConfiguration config, IVariables variables) {
    String explicit = resolve(junkDimension.getSurrogateKeyField(), variables);
    if (!Utils.isEmpty(explicit)) {
      return explicit;
    }
    DimensionalConfiguration resolvedConfig =
        config != null ? config : new DimensionalConfiguration();
    return resolvedConfig.resolveDimKeyField(variables);
  }

  public static String resolveJunkSurrogateKeySourceField(
      DmJunkDimension junkDimension, DimensionalConfiguration config, IVariables variables) {
    String explicit = resolve(junkDimension.getSurrogateKeySourceField(), variables);
    if (!Utils.isEmpty(explicit)) {
      return explicit;
    }
    return resolveJunkSurrogateKeyField(junkDimension, config, variables);
  }

  public static boolean usesStringSurrogate(DmDimension dimension) {
    return resolveStrategy(dimension) == DmSurrogateKeyStrategy.USE_SOURCE_FIELD;
  }

  public static boolean supportsExplicitSkipDimensionLookup(DmDimension dimension) {
    if (dimension == null) {
      return false;
    }
    DmSurrogateKeyStrategy strategy = resolveStrategy(dimension);
    if (strategy == DmSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      return true;
    }
    return isNaturalKeyOnlyDimension(dimension);
  }

  public static boolean isNaturalKeyOnlyDimension(DmDimension dimension) {
    if (dimension == null) {
      return false;
    }
    if (resolveStrategy(dimension) != DmSurrogateKeyStrategy.NONE) {
      return false;
    }
    if (DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension)
        != DmDimensionLoadStrategySupport.DmDimensionLoadStrategy.PURE_TYPE1) {
      return false;
    }
    return dimension.getNaturalKeysOrEmpty().stream()
        .anyMatch(key -> key != null && !Utils.isEmpty(key.getFieldName()));
  }

  public static boolean shouldSkipFactDimensionLookup(
      DmFactDimensionRole role,
      DmDimension dimension,
      DimensionalConfiguration config,
      IVariables variables) {
    if (role == null || dimension == null || role.isTruncateToDateKey()) {
      return false;
    }
    if (role.isForceDimensionLookup()) {
      return false;
    }
    if (role.isSkipDimensionLookup()) {
      return true;
    }
    return autoDetectSurrogateKeyPassthrough(role, dimension, config, variables)
        || autoDetectNaturalKeyPassthrough(role, dimension, config, variables);
  }

  public static boolean autoDetectSurrogateKeyPassthrough(
      DmFactDimensionRole role,
      DmDimension dimension,
      DimensionalConfiguration config,
      IVariables variables) {
    if (role == null
        || dimension == null
        || resolveStrategy(dimension) != DmSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      return false;
    }
    String surrogateSource = resolveSurrogateKeySourceField(dimension, config, variables);
    if (Utils.isEmpty(surrogateSource)) {
      return false;
    }
    String sourceField = resolveFactRoleSourceField(role, dimension, variables);
    return !Utils.isEmpty(sourceField) && sourceField.equals(surrogateSource);
  }

  public static boolean autoDetectNaturalKeyPassthrough(
      DmFactDimensionRole role,
      DmDimension dimension,
      DimensionalConfiguration config,
      IVariables variables) {
    if (!isNaturalKeyOnlyDimension(dimension)) {
      return false;
    }
    String lookupKey = DmLayoutSupport.resolveDimensionLookupKeyField(dimension, config, variables);
    if (Utils.isEmpty(lookupKey)) {
      return false;
    }
    String sourceField = resolveFactRoleSourceField(role, dimension, variables);
    if (Utils.isEmpty(sourceField) || !sourceField.equals(lookupKey)) {
      return false;
    }
    String fkColumn = resolve(role.getForeignKeyColumn(), variables);
    if (Utils.isEmpty(fkColumn)) {
      fkColumn = lookupKey;
    }
    return fkColumn.equals(lookupKey);
  }

  public static String resolveFactRoleSourceField(
      DmFactDimensionRole role, DmDimension dimension, IVariables variables) {
    if (role == null || dimension == null) {
      return null;
    }
    List<String> naturalKeys =
        dimension.getNaturalKeysOrEmpty().stream()
            .filter(key -> key != null && !Utils.isEmpty(key.getFieldName()))
            .map(key -> resolve(key.getFieldName(), variables))
            .toList();
    String naturalKey = naturalKeys.isEmpty() ? null : naturalKeys.get(0);
    return DmFactDimensionJoinValidationSupport.resolveSourceFieldName(role, naturalKey, variables);
  }

  public static boolean usesStringSurrogate(DmJunkDimension junkDimension) {
    DmJunkSurrogateKeyStrategy strategy = resolveJunkStrategy(junkDimension);
    return strategy == DmJunkSurrogateKeyStrategy.USE_SOURCE_FIELD
        || strategy == DmJunkSurrogateKeyStrategy.COMPUTE_HASH_KEY;
  }

  public static String deriveEntitySurrogateName(String dimensionName) {
    if (Utils.isEmpty(dimensionName)) {
      return null;
    }
    String base = dimensionName;
    if (base.startsWith("dim_")) {
      base = base.substring(4);
    }
    return base + "_key";
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
