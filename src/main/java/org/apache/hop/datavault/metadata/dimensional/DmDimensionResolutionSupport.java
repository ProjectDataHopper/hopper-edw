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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Resolves dimension aliases and conformed names to physical {@link DmDimension} tables. */
public final class DmDimensionResolutionSupport {

  private DmDimensionResolutionSupport() {}

  public static DmDimension resolveDimension(DimensionalModel model, String tableName) {
    return resolveDimension(model, tableName, null, null);
  }

  public static DmDimension resolveDimension(
      DimensionalModel model, String tableName, IVariables variables) {
    return resolveDimension(model, tableName, variables, null);
  }

  public static DmDimension resolveDimension(
      DimensionalModel model,
      String tableName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (model == null || Utils.isEmpty(tableName)) {
      return null;
    }
    String resolvedName = resolve(tableName, variables);
    IDmTable table = model.findTable(resolvedName);
    if (table instanceof DmDimension dimension) {
      return dimension;
    }
    if (table instanceof DmDimensionAlias alias) {
      return resolveAliasTarget(model, alias, variables, metadataProvider);
    }
    return model.findConformedDimension(resolvedName);
  }

  public static DmDimension resolveAliasTarget(
      DimensionalModel model, DmDimensionAlias alias, IVariables variables) {
    return resolveAliasTarget(model, alias, variables, null);
  }

  public static DmDimension resolveAliasTarget(
      DimensionalModel model,
      DmDimensionAlias alias,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (model == null || alias == null) {
      return null;
    }
    String referencedName = resolve(alias.getReferencedDimensionName(), variables);
    if (Utils.isEmpty(referencedName)) {
      return null;
    }
    if (!Utils.isEmpty(alias.getReferencedModelFilename())) {
      try {
        DimensionalModel externalModel =
            DmModelLoadSupport.loadDimensionalModel(
                alias.getReferencedModelFilename(),
                model.getFilename(),
                variables,
                metadataProvider);
        IDmTable referenced = externalModel.findTable(referencedName);
        if (referenced instanceof DmDimension dimension) {
          return dimension;
        }
        return null;
      } catch (HopException ignored) {
        return null;
      }
    }
    IDmTable referenced = model.findTable(referencedName);
    if (referenced instanceof DmDimension dimension) {
      return dimension;
    }
    return null;
  }

  public static String resolvePhysicalTableName(
      DimensionalModel model, String tableName, IVariables variables) {
    return resolvePhysicalTableName(model, tableName, variables, null);
  }

  public static String resolvePhysicalTableName(
      DimensionalModel model,
      String tableName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    DmDimension dimension = resolveDimension(model, tableName, variables, metadataProvider);
    if (dimension == null) {
      return null;
    }
    if (!Utils.isEmpty(dimension.getTableName())) {
      return resolve(dimension.getTableName(), variables);
    }
    return resolve(dimension.getName(), variables);
  }

  public static boolean isDimensionAlias(DimensionalModel model, String tableName) {
    if (model == null || Utils.isEmpty(tableName)) {
      return false;
    }
    IDmTable table = model.findTable(tableName);
    return table instanceof DmDimensionAlias;
  }

  public static boolean isExternalDimensionAlias(DmDimensionAlias alias) {
    return alias != null && !Utils.isEmpty(alias.getReferencedModelFilename());
  }

  /**
   * Label for the model that owns the alias target dimension (external path basename or this
   * model).
   */
  public static String resolveAliasSourceModelDisplayName(
      DimensionalModel model, DmDimensionAlias alias, IVariables variables) {
    if (alias == null) {
      return "";
    }
    if (isExternalDimensionAlias(alias)) {
      return modelBasename(resolve(alias.getReferencedModelFilename(), variables));
    }
    return resolveModelDisplayName(model, variables);
  }

  public static String resolveModelDisplayName(DimensionalModel model, IVariables variables) {
    if (model == null) {
      return "";
    }
    String name = resolve(model.getName(), variables);
    if (!Utils.isEmpty(name)) {
      return name;
    }
    return modelBasename(resolve(model.getFilename(), variables));
  }

  public static boolean isDimensionLike(DimensionalModel model, String tableName) {
    if (model == null || Utils.isEmpty(tableName)) {
      return false;
    }
    IDmTable table = model.findTable(tableName);
    return table instanceof DmDimension
        || table instanceof DmDimensionAlias
        || table instanceof DmJunkDimension;
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static String modelBasename(String path) {
    if (Utils.isEmpty(path)) {
      return "";
    }
    String normalized = path.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    if (slash >= 0) {
      normalized = normalized.substring(slash + 1);
    }
    if (normalized.endsWith(".hdm")) {
      normalized = normalized.substring(0, normalized.length() - 4);
    }
    return normalized;
  }
}
