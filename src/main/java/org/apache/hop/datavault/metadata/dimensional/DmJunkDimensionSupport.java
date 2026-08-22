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
package org.apache.hop.datavault.metadata.dimensional;

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/** Resolves junk dimension tables on a dimensional model canvas. */
public final class DmJunkDimensionSupport {

  private DmJunkDimensionSupport() {}

  public static DmJunkDimension resolveJunkDimension(
      DimensionalModel model, String tableName, IVariables variables) {
    if (model == null || Utils.isEmpty(tableName)) {
      return null;
    }
    String resolvedName = variables != null ? variables.resolve(tableName) : tableName;
    IDmTable table = model.findTable(resolvedName);
    if (table instanceof DmJunkDimension junkDimension) {
      return junkDimension;
    }
    return null;
  }

  public static String[] listJunkDimensionNames(DimensionalModel model, String excludeName) {
    if (model == null || model.getTables() == null) {
      return new String[0];
    }
    return model.getTables().stream()
        .filter(table -> table instanceof DmJunkDimension)
        .map(IDmTable::getName)
        .filter(name -> !Utils.isEmpty(name))
        .filter(name -> excludeName == null || !excludeName.equals(name))
        .toArray(String[]::new);
  }

  public static String[] listFactTableNames(DimensionalModel model) {
    if (model == null || model.getTables() == null) {
      return new String[0];
    }
    return model.getTables().stream()
        .filter(table -> table instanceof IDmFactLikeTable)
        .map(IDmTable::getName)
        .filter(name -> !Utils.isEmpty(name))
        .toArray(String[]::new);
  }

  public static boolean isFactEmbedded(DmJunkDimension junkDimension) {
    if (junkDimension == null) {
      return false;
    }
    if (junkDimension.getSourceOrDefault().isFactTableSource()) {
      return true;
    }
    return junkDimension.isLoadFromFactTable();
  }

  public static boolean requiresStandalonePipeline(DmJunkDimension junkDimension) {
    return !isFactEmbedded(junkDimension);
  }

  public static String resolveFactTableName(DmJunkDimension junkDimension, IVariables variables) {
    if (junkDimension == null) {
      return null;
    }
    String fromSource = junkDimension.getSourceOrDefault().resolveSourceFactTableName(variables);
    if (!Utils.isEmpty(fromSource)) {
      return fromSource;
    }
    String legacy = junkDimension.getFactTableName();
    if (variables != null && legacy != null) {
      legacy = variables.resolve(legacy);
    }
    return legacy;
  }

  /** Applies fact-table inline source settings on a junk dimension (graph + dialog). */
  public static void applyFactTableSource(DmJunkDimension junkDimension, String factTableName) {
    if (junkDimension == null) {
      return;
    }
    DmSourceConfiguration source = junkDimension.getSourceOrDefault();
    source.setSourceType(DmSourceType.FACT_TABLE);
    source.setSourceFactTableName(factTableName);
    junkDimension.setLoadFromFactTable(true);
    junkDimension.setFactTableName(factTableName);
  }

  /** Clears fact-table inline source and legacy flags when another source type is chosen. */
  public static void clearFactTableSource(DmJunkDimension junkDimension) {
    if (junkDimension == null) {
      return;
    }
    junkDimension.getSourceOrDefault().setSourceFactTableName(null);
    junkDimension.setLoadFromFactTable(false);
    junkDimension.setFactTableName(null);
  }

  public static String defaultForeignKeyForJunk(DmJunkDimension junkDimension) {
    if (junkDimension == null || Utils.isEmpty(junkDimension.getName())) {
      return null;
    }
    String base = junkDimension.getName();
    if (base.startsWith("dim_")) {
      base = base.substring(4);
    }
    return base + "_key";
  }

  public static IDmFactLikeTable resolveFactTable(
      DimensionalModel model, String factTableName, IVariables variables) {
    if (model == null || Utils.isEmpty(factTableName)) {
      return null;
    }
    String resolvedName = variables != null ? variables.resolve(factTableName) : factTableName;
    IDmTable table = model.findTable(resolvedName);
    if (table instanceof IDmFactLikeTable factLike) {
      return factLike;
    }
    return null;
  }

  public static String resolveJunkHashCodeField(
      DmJunkDimension junkDimension, DimensionalConfiguration config, IVariables variables) {
    if (junkDimension == null) {
      return null;
    }
    if (junkDimension.isUseSurrogateKeyAsHashCodeField()) {
      return DmSurrogateKeySupport.resolveJunkSurrogateKeyField(junkDimension, config, variables);
    }
    String explicit = resolve(variables, junkDimension.getHashCodeField());
    if (!Utils.isEmpty(explicit)) {
      return explicit;
    }
    return "hashcode";
  }

  public static boolean sharesHashAndSurrogateColumn(
      DmJunkDimension junkDimension, DimensionalConfiguration config, IVariables variables) {
    if (junkDimension == null) {
      return false;
    }
    DmJunkHashCodeStrategy hashStrategy = junkDimension.getHashCodeStrategyOrDefault();
    if (!hashStrategy.usesHashColumn()) {
      return false;
    }
    String surrogate =
        DmSurrogateKeySupport.resolveJunkSurrogateKeyField(junkDimension, config, variables);
    String hash = resolveJunkHashCodeField(junkDimension, config, variables);
    return !Utils.isEmpty(surrogate) && surrogate.equals(hash);
  }

  private static String resolve(IVariables variables, String value) {
    if (variables != null && value != null) {
      return variables.resolve(value);
    }
    return value;
  }
}
