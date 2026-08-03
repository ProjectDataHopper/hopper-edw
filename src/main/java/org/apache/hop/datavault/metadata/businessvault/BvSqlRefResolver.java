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
package org.apache.hop.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvModelLoadSupport;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Resolves {@code ref()} / {@code source()} macros against Business Vault and Data Vault models and
 * rewrites authoring SQL to dialect-quoted physical names.
 *
 * <p>Two-arg {@code ref('model', 'object')} supports filesystem paths relative to the current BV
 * model (e.g. {@code ../models/retail-360}), with optional {@code .hdv}/{@code .hbv} and {@code
 * ${PROJECT_HOME}/} normalization.
 */
public final class BvSqlRefResolver {

  private BvSqlRefResolver() {}

  /**
   * Parses SQL on the business table, resolves each {@code ref()}, and replaces {@link
   * BvBusinessTable#getSqlRefs()}.
   */
  public static List<BvSqlRef> syncRefsFromSql(
      BvBusinessTable table, BusinessVaultModel bvModel, DataVaultModel dvModel) {
    return syncRefsFromSql(table, bvModel, dvModel, null, null);
  }

  public static List<BvSqlRef> syncRefsFromSql(
      BvBusinessTable table,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (table == null) {
      return List.of();
    }
    List<BvSqlRef> refs = BvSqlTemplateParser.extractRefs(table.getSqlQuery());
    for (BvSqlRef ref : refs) {
      resolveRef(ref, table, bvModel, dvModel, variables, metadataProvider);
    }
    table.setSqlRefs(refs);
    syncDerivativesFromResolvedRefs(table, refs);
    return refs;
  }

  public static void resolveRef(
      BvSqlRef ref, BvBusinessTable self, BusinessVaultModel bvModel, DataVaultModel dvModel) {
    resolveRef(ref, self, bvModel, dvModel, null, null);
  }

  public static void resolveRef(
      BvSqlRef ref,
      BvBusinessTable self,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (ref == null || Utils.isEmpty(ref.getObjectName())) {
      if (ref != null) {
        ref.setResolvedKind(BvSqlResolvedKind.UNKNOWN);
      }
      return;
    }
    ref.setResolvedKind(BvSqlResolvedKind.UNKNOWN);
    ref.setResolvedTableName(null);
    ref.setResolvedDvTableType(null);
    ref.setResolvedModelFilename(null);

    String objectName = ref.getObjectName().trim();
    String modelName = ref.getModelName();

    if (Utils.isEmpty(modelName)) {
      resolveOneArgRef(ref, self, bvModel, dvModel, objectName, variables, metadataProvider);
      return;
    }

    resolveTwoArgRef(
        ref, self, bvModel, dvModel, modelName.trim(), objectName, variables, metadataProvider);
  }

  private static void resolveOneArgRef(
      BvSqlRef ref,
      BvBusinessTable self,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      String objectName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    IBvTable bvTable = findBvTable(bvModel, objectName, self);
    if (bvTable != null) {
      applyBvResolution(ref, bvTable, bvModel, variables);
      return;
    }
    // Canvas Hub/Link/Satellite reference (multi-model path when set).
    if (bvModel != null) {
      BvDvTableReference alias =
          BusinessVaultDvReferenceSupport.findDvReference(bvModel, objectName);
      if (alias != null) {
        String modelPath =
            !Utils.isEmpty(alias.getReferencedModelFilename())
                ? alias.getReferencedModelFilename()
                : bvModel.getDataVaultModelPath();
        if (!Utils.isEmpty(modelPath) && metadataProvider != null) {
          try {
            IDvTable fromAlias =
                BusinessVaultDvModelResolver.resolveDvTable(
                    bvModel, objectName, variables, metadataProvider);
            if (fromAlias != null) {
              applyDvResolution(ref, fromAlias, modelPath, bvModel, variables);
              return;
            }
          } catch (Exception ignored) {
            // fall through to effective/linked model
          }
        } else if (alias.getDvTableType() != null) {
          // Alias present without loadable path: still record kind for dependency UI.
          ref.setResolvedKind(BvSqlResolvedKind.DV_TABLE);
          ref.setResolvedTableName(objectName);
          ref.setResolvedDvTableType(alias.getDvTableType());
          return;
        }
      }
    }
    IDvTable dvTable = findDvTable(dvModel, objectName);
    if (dvTable != null) {
      String modelPath =
          BusinessVaultDvModelResolver.resolveModelPathForDvTable(bvModel, objectName);
      if (Utils.isEmpty(modelPath) && dvModel != null) {
        modelPath = dvModel.getFilename();
      }
      applyDvResolution(ref, dvTable, modelPath, bvModel, variables);
    }
  }

  private static void resolveTwoArgRef(
      BvSqlRef ref,
      BvBusinessTable self,
      BusinessVaultModel bvModel,
      DataVaultModel linkedDvModel,
      String modelArg,
      String objectName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    // 1) Current BV by basename / path match (no file I/O).
    if (bvModel != null
        && modelMatches(bvModel.getFilename(), bvModel.getName(), modelBasename(modelArg))) {
      IBvTable bvTable = findBvTable(bvModel, objectName, self);
      if (bvTable != null) {
        applyBvResolution(ref, bvTable, bvModel, variables);
        return;
      }
    }

    // 2) Config-linked DV by basename / path match.
    if (linkedDvModel != null
        && modelMatches(
            linkedDvModel.getFilename(), linkedDvModel.getName(), modelBasename(modelArg))) {
      IDvTable dvTable = findDvTable(linkedDvModel, objectName);
      if (dvTable != null) {
        applyDvResolution(ref, dvTable, linkedDvModel.getFilename(), bvModel, variables);
        return;
      }
    }

    // 3) Catalog model registry (short basename → registered model file).
    if (metadataProvider != null
        && variables != null
        && !BvSqlModelPathSupport.looksLikeFilesystemPath(modelArg)) {
      if (tryResolveFromCatalog(ref, modelArg, objectName, variables, metadataProvider)) {
        return;
      }
    }

    // 4) Filesystem path relative to current BV (and PROJECT_HOME fallbacks).
    String referring = bvModel != null ? bvModel.getFilename() : null;
    try {
      BvSqlModelPathSupport.ResolvedModelPath resolvedPath =
          BvSqlModelPathSupport.resolveExistingModelPath(modelArg, referring, variables);
      if (tryLoadExternalFromPath(ref, resolvedPath, objectName, variables, metadataProvider)) {
        return;
      }
      // File found but object missing: still record model path for diagnostics.
      ref.setResolvedModelFilename(resolvedPath.storedPath());
    } catch (HopException ignored) {
      // Path could not be resolved — try catalog even for path-like args, then UNKNOWN.
      if (metadataProvider != null && variables != null) {
        tryResolveFromCatalog(ref, modelArg, objectName, variables, metadataProvider);
      }
    }
  }

  /**
   * Resolves short catalog basenames by trying <strong>all</strong> candidate model files (DV then
   * BV). Same basename can map to both {@code retail-360.hdv} and {@code retail-360.hbv}; object
   * existence decides which wins.
   */
  private static boolean tryResolveFromCatalog(
      BvSqlRef ref,
      String modelArg,
      String objectName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    try {
      List<String> candidates =
          org.apache.hop.datavault.catalog.CatalogModelRegistrySupport
              .listModelFilenamesForBasename(modelArg, variables, metadataProvider);
      if (candidates.isEmpty()) {
        return false;
      }
      String lastTried = null;
      for (String filename : candidates) {
        if (Utils.isEmpty(filename)) {
          continue;
        }
        lastTried = filename;
        if (tryResolveInModelFile(ref, filename, objectName, variables, metadataProvider)) {
          return true;
        }
      }
      if (!Utils.isEmpty(lastTried)) {
        ref.setResolvedModelFilename(toPortableStoredPath(lastTried, null, variables));
      }
    } catch (Exception ignored) {
      // Catalog unavailable or load failed.
    }
    return false;
  }

  /** Loads a candidate path as HBV and/or HDV and returns true when {@code objectName} is found. */
  private static boolean tryResolveInModelFile(
      BvSqlRef ref,
      String filename,
      String objectName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (Utils.isEmpty(filename)) {
      return false;
    }
    String lower = filename.toLowerCase(java.util.Locale.ROOT);
    if (lower.endsWith(".hbv") || lower.contains(".hbv")) {
      if (tryResolveInHbvFile(ref, filename, objectName, variables, metadataProvider)) {
        return true;
      }
      // Explicit .hbv path: do not also force HDV.
      if (lower.endsWith(".hbv")) {
        return false;
      }
    }
    if (lower.endsWith(".hdv") || lower.contains(".hdv") || !lower.endsWith(".hbv")) {
      if (tryResolveInHdvFile(ref, filename, objectName, variables, metadataProvider)) {
        return true;
      }
    }
    return false;
  }

  private static boolean tryResolveInHbvFile(
      BvSqlRef ref,
      String filename,
      String objectName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    try {
      String loadPath = variables != null ? variables.resolve(filename) : filename;
      BusinessVaultModel externalBv =
          BvSqlModelPathSupport.loadBusinessVaultModelUncached(loadPath, metadataProvider);
      IBvTable bvTable = findBvTable(externalBv, objectName, null);
      if (bvTable != null) {
        ref.setResolvedKind(BvSqlResolvedKind.BV_TABLE);
        ref.setResolvedTableName(physicalName(bvTable));
        ref.setResolvedModelFilename(toPortableStoredPath(filename, null, variables));
        return true;
      }
    } catch (Exception ignored) {
      // not a loadable HBV or object missing
    }
    return false;
  }

  private static boolean tryResolveInHdvFile(
      BvSqlRef ref,
      String filename,
      String objectName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    try {
      DataVaultModel externalDv =
          BvSqlModelPathSupport.loadDataVaultModel(filename, null, variables, metadataProvider);
      IDvTable dvTable = findDvTable(externalDv, objectName);
      if (dvTable != null) {
        applyDvResolution(ref, dvTable, filename, null, variables);
        return true;
      }
    } catch (Exception ignored) {
      // not a loadable HDV or object missing
    }
    return false;
  }

  private static boolean tryLoadExternalFromPath(
      BvSqlRef ref,
      BvSqlModelPathSupport.ResolvedModelPath resolvedPath,
      String objectName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (resolvedPath == null) {
      return false;
    }
    if (resolvedPath.kind() == BvSqlModelPathSupport.ModelFileKind.HDV
        || resolvedPath.kind() == BvSqlModelPathSupport.ModelFileKind.UNKNOWN) {
      try {
        DataVaultModel externalDv =
            BvSqlModelPathSupport.loadDataVaultModel(
                resolvedPath.loadPath(), null, variables, metadataProvider);
        IDvTable dvTable = findDvTable(externalDv, objectName);
        if (dvTable != null) {
          applyDvResolution(ref, dvTable, resolvedPath.storedPath(), null, variables);
          return true;
        }
      } catch (HopException ignored) {
        // try HBV if kind unknown or sibling
      }
    }
    if (resolvedPath.kind() == BvSqlModelPathSupport.ModelFileKind.HBV
        || resolvedPath.kind() == BvSqlModelPathSupport.ModelFileKind.UNKNOWN) {
      try {
        BusinessVaultModel externalBv =
            BvSqlModelPathSupport.loadBusinessVaultModel(resolvedPath, metadataProvider);
        IBvTable bvTable = findBvTable(externalBv, objectName, null);
        if (bvTable != null) {
          ref.setResolvedKind(BvSqlResolvedKind.BV_TABLE);
          ref.setResolvedTableName(physicalName(bvTable));
          ref.setResolvedModelFilename(resolvedPath.storedPath());
          return true;
        }
      } catch (HopException ignored) {
        // leave for sibling try
      }
    }
    // Same basename sibling: .hdv found but object missing → try .hbv (and reverse).
    return trySiblingModelExtension(ref, resolvedPath, objectName, variables, metadataProvider);
  }

  /**
   * When the first resolved file exists but does not contain the object, try the opposite vault
   * extension (e.g. {@code retail-360.hdv} → {@code retail-360.hbv}).
   */
  private static boolean trySiblingModelExtension(
      BvSqlRef ref,
      BvSqlModelPathSupport.ResolvedModelPath resolvedPath,
      String objectName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (resolvedPath == null || Utils.isEmpty(resolvedPath.loadPath())) {
      return false;
    }
    String loadPath = resolvedPath.loadPath();
    String lower = loadPath.toLowerCase(java.util.Locale.ROOT);
    String siblingLoad;
    if (lower.endsWith(".hdv")) {
      siblingLoad = loadPath.substring(0, loadPath.length() - 4) + ".hbv";
    } else if (lower.endsWith(".hbv")) {
      siblingLoad = loadPath.substring(0, loadPath.length() - 4) + ".hdv";
    } else {
      return false;
    }
    if (!BvSqlModelPathSupport.fileExists(siblingLoad)) {
      return false;
    }
    String storedSibling = siblingLoad;
    try {
      storedSibling =
          org.apache.hop.datavault.metadata.DvModelLoadSupport.toStoredModelPath(
              siblingLoad, null, variables);
    } catch (Exception ignored) {
      // keep absolute sibling path
    }
    if (siblingLoad.toLowerCase(java.util.Locale.ROOT).endsWith(".hbv")) {
      return tryResolveInHbvFile(ref, storedSibling, objectName, variables, metadataProvider)
          || tryResolveInHbvFile(ref, siblingLoad, objectName, variables, metadataProvider);
    }
    return tryResolveInHdvFile(ref, storedSibling, objectName, variables, metadataProvider)
        || tryResolveInHdvFile(ref, siblingLoad, objectName, variables, metadataProvider);
  }

  private static void applyBvResolution(
      BvSqlRef ref, IBvTable bvTable, BusinessVaultModel bvModel, IVariables variables) {
    ref.setResolvedKind(BvSqlResolvedKind.BV_TABLE);
    ref.setResolvedTableName(physicalName(bvTable));
    if (bvModel != null && !Utils.isEmpty(bvModel.getFilename())) {
      ref.setResolvedModelFilename(
          toPortableStoredPath(bvModel.getFilename(), bvModel.getFilename(), variables));
    }
  }

  private static void applyDvResolution(
      BvSqlRef ref,
      IDvTable dvTable,
      String modelFilename,
      BusinessVaultModel referringBv,
      IVariables variables) {
    ref.setResolvedKind(BvSqlResolvedKind.DV_TABLE);
    ref.setResolvedTableName(physicalName(dvTable));
    ref.setResolvedDvTableType(dvTable.getTableType());
    if (!Utils.isEmpty(modelFilename)) {
      String referring = referringBv != null ? referringBv.getFilename() : null;
      ref.setResolvedModelFilename(toPortableStoredPath(modelFilename, referring, variables));
    }
  }

  /** Prefer {@code ${PROJECT_HOME}/…} (or relative) paths for persistence in model XML. */
  private static String toPortableStoredPath(
      String modelFilename, String referringFilename, IVariables variables) {
    if (Utils.isEmpty(modelFilename) || variables == null) {
      return modelFilename;
    }
    try {
      return DvModelLoadSupport.toStoredModelPath(modelFilename, referringFilename, variables);
    } catch (Exception e) {
      return modelFilename;
    }
  }

  /** Ensures DV derivatives list includes resolved DV sqlRefs for canvas links. */
  public static void syncDerivativesFromResolvedRefs(BvBusinessTable table, List<BvSqlRef> refs) {
    if (table == null || refs == null) {
      return;
    }
    for (BvSqlRef ref : refs) {
      if (ref == null
          || ref.getResolvedKind() != BvSqlResolvedKind.DV_TABLE
          || Utils.isEmpty(ref.getObjectName())
          || ref.getResolvedDvTableType() == null) {
        continue;
      }
      String derivativeName = ref.getObjectName();
      if (!BusinessVaultDerivativeSupport.hasDerivative(table, derivativeName)) {
        table
            .getDerivatives()
            .add(new BvDerivativeRef(derivativeName, ref.getResolvedDvTableType()));
      }
    }
  }

  /**
   * Ensures the BV model has canvas {@link BvDvTableReference} aliases for each resolved DV ref.
   * Does not create a second alias when the table name is already on the canvas (avoids stacking
   * cards at 0,0 on every dialog OK).
   */
  public static void ensureDvCanvasAliases(
      BusinessVaultModel bvModel, List<BvSqlRef> refs, DataVaultModel dvModel) {
    if (bvModel == null || refs == null) {
      return;
    }
    for (BvSqlRef ref : refs) {
      if (ref == null
          || ref.getResolvedKind() != BvSqlResolvedKind.DV_TABLE
          || Utils.isEmpty(ref.getObjectName())) {
        continue;
      }
      BvDvTableReference existing =
          BusinessVaultDvReferenceSupport.findDvReference(bvModel, ref.getObjectName());
      if (existing != null) {
        // Upgrade existing alias with model path when missing; never add a duplicate.
        if (Utils.isEmpty(existing.getReferencedModelFilename())
            && !Utils.isEmpty(ref.getResolvedModelFilename())) {
          // resolvedModelFilename is already portable when set via applyDvResolution.
          existing.setReferencedModelFilename(ref.getResolvedModelFilename());
        }
        continue;
      }
      // Also skip if path-aware match would find a multi-model alias under another key style.
      if (BusinessVaultDvReferenceSupport.hasDvReference(
          bvModel, ref.getObjectName(), ref.getResolvedModelFilename())) {
        continue;
      }
      IDvTable dvTable = findDvTable(dvModel, ref.getObjectName());
      BvDvTableReference alias;
      if (dvTable != null) {
        alias =
            BusinessVaultDvReferenceSupport.createReference(
                dvTable, null, ref.getResolvedModelFilename());
      } else if (ref.getResolvedDvTableType() != null) {
        alias = new BvDvTableReference(ref.getObjectName(), ref.getResolvedDvTableType());
        if (!Utils.isEmpty(ref.getResolvedModelFilename())) {
          alias.setReferencedModelFilename(ref.getResolvedModelFilename());
        }
      } else {
        continue;
      }
      if (alias != null) {
        placeNewAlias(bvModel, alias);
        bvModel.getDvReferences().add(alias);
      }
    }
  }

  /**
   * Ensures the BV model has canvas {@link BvBvTableReference} aliases for each resolved external
   * BV table ref (multi-step BV layers). Same-model BV tables are already on the canvas and are
   * skipped. Does not create a second alias when the table name is already present.
   */
  public static void ensureBvCanvasAliases(BusinessVaultModel bvModel, List<BvSqlRef> refs) {
    if (bvModel == null || refs == null) {
      return;
    }
    for (BvSqlRef ref : refs) {
      if (ref == null
          || ref.getResolvedKind() != BvSqlResolvedKind.BV_TABLE
          || Utils.isEmpty(ref.getObjectName())) {
        continue;
      }
      // Same-model tables live as IBvTable nodes, not external aliases.
      if (Utils.isEmpty(ref.getResolvedModelFilename())
          || sameBvModel(bvModel, ref.getResolvedModelFilename())) {
        continue;
      }
      BvBvTableReference existing =
          BusinessVaultBvReferenceSupport.findBvReference(bvModel, ref.getObjectName());
      if (existing != null) {
        if (Utils.isEmpty(existing.getReferencedModelFilename())
            && !Utils.isEmpty(ref.getResolvedModelFilename())) {
          existing.setReferencedModelFilename(ref.getResolvedModelFilename());
        }
        continue;
      }
      if (BusinessVaultBvReferenceSupport.hasBvReference(
          bvModel, ref.getObjectName(), ref.getResolvedModelFilename())) {
        continue;
      }
      BvBvTableReference alias =
          new BvBvTableReference(ref.getObjectName(), null, ref.getResolvedModelFilename());
      if (!Utils.isEmpty(ref.getResolvedTableName())) {
        alias.setPhysicalTableName(ref.getResolvedTableName());
      }
      placeNewBvAlias(bvModel, alias);
      bvModel.getBvReferences().add(alias);
    }
  }

  private static boolean sameBvModel(BusinessVaultModel bvModel, String resolvedModelFilename) {
    if (bvModel == null || Utils.isEmpty(resolvedModelFilename)) {
      return false;
    }
    return BusinessVaultBvReferenceSupport.sameModelPath(
        bvModel.getFilename(), resolvedModelFilename);
  }

  /** Offset new aliases so they are not stacked at (0,0). */
  private static void placeNewAlias(BusinessVaultModel bvModel, BvDvTableReference alias) {
    if (alias == null) {
      return;
    }
    if (alias.getLocation() != null && (alias.getLocation().x != 0 || alias.getLocation().y != 0)) {
      return;
    }
    int index = bvModel != null ? bvModel.getDvReferences().size() : 0;
    int x = 80 + (index % 4) * 160;
    int y = 80 + (index / 4) * 100;
    alias.setLocation(new Point(x, y));
  }

  private static void placeNewBvAlias(BusinessVaultModel bvModel, BvBvTableReference alias) {
    if (alias == null) {
      return;
    }
    if (alias.getLocation() != null && (alias.getLocation().x != 0 || alias.getLocation().y != 0)) {
      return;
    }
    int index = bvModel != null ? bvModel.getBvReferences().size() : 0;
    // Place below DV alias grid so the two kinds do not stack on top of each other.
    int x = 80 + (index % 4) * 160;
    int y = 280 + (index / 4) * 100;
    alias.setLocation(new Point(x, y));
  }

  public static BvSqlSource findSource(BvBusinessTable table, String sourceName, String tableName) {
    if (table == null || Utils.isEmpty(sourceName) || Utils.isEmpty(tableName)) {
      return null;
    }
    for (BvSqlSource source : table.getSources()) {
      if (source == null) {
        continue;
      }
      if (sourceName.equalsIgnoreCase(source.getSourceName())
          && tableName.equalsIgnoreCase(source.getTableName())) {
        return source;
      }
    }
    return null;
  }

  /**
   * Rewrites authoring SQL replacing macros with quoted schema.table names using BV/DV target
   * connections when available.
   */
  public static String resolveSql(
      BvBusinessTable table,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DatabaseMeta defaultDatabaseMeta)
      throws HopException {
    if (table == null || Utils.isEmpty(table.getSqlQuery())) {
      return table != null ? table.getSqlQuery() : null;
    }
    List<BvSqlRef> refs = table.getSqlRefs();
    if (refs == null || refs.isEmpty()) {
      refs = syncRefsFromSql(table, bvModel, dvModel, variables, metadataProvider);
    } else {
      for (BvSqlRef ref : refs) {
        resolveRef(ref, table, bvModel, dvModel, variables, metadataProvider);
      }
    }

    DatabaseMeta bvDb = defaultDatabaseMeta;
    if (bvDb == null && metadataProvider != null && bvModel != null) {
      bvDb =
          BvTargetDatabaseSupport.loadTargetDatabase(
              metadataProvider, bvModel.getConfigurationOrDefault());
    }
    DatabaseMeta dvDb = null;
    if (metadataProvider != null && dvModel != null) {
      try {
        dvDb =
            org.apache.hop.datavault.metadata.DvSpecialRecordSupport.loadTargetDatabase(
                metadataProvider, dvModel.getConfigurationOrDefault());
      } catch (HopException ignored) {
        // Fall back to BV database for qualification.
      }
    }

    final DatabaseMeta bvDatabase = bvDb;
    final DatabaseMeta dvDatabase = dvDb != null ? dvDb : bvDb;
    final List<BvSqlRef> resolvedRefs = refs;

    try {
      return BvSqlTemplateParser.rewrite(
          table.getSqlQuery(),
          macro ->
              rewriteMacro(
                  macro, table, resolvedRefs, bvDatabase, dvDatabase, metadataProvider, variables));
    } catch (SqlRewriteException e) {
      throw new HopException(e.getMessage(), e.getCause());
    }
  }

  private static String rewriteMacro(
      BvSqlTemplateParser.MacroOccurrence macro,
      BvBusinessTable table,
      List<BvSqlRef> resolvedRefs,
      DatabaseMeta bvDatabase,
      DatabaseMeta dvDatabase,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    try {
      if (macro.kind() == BvSqlTemplateParser.MacroKind.REF) {
        BvSqlRef match = findMatchingRef(resolvedRefs, macro);
        String physical =
            match != null && !Utils.isEmpty(match.getResolvedTableName())
                ? match.getResolvedTableName()
                : macro.refObjectName();
        DatabaseMeta db =
            match != null && match.getResolvedKind() == BvSqlResolvedKind.DV_TABLE
                ? dvDatabase
                : bvDatabase;
        return quoteTable(db, variables, null, physical);
      }
      BvSqlSource source = findSource(table, macro.sourceName(), macro.sourceTableName());
      String schema = source != null ? source.getSchemaName() : null;
      String physical =
          source != null && !Utils.isEmpty(source.getTableName())
              ? source.getTableName()
              : macro.sourceTableName();
      DatabaseMeta db = bvDatabase;
      if (source != null && !Utils.isEmpty(source.getDatabaseName()) && metadataProvider != null) {
        DatabaseMeta sourceDb =
            metadataProvider.getSerializer(DatabaseMeta.class).load(source.getDatabaseName());
        if (sourceDb != null) {
          db = sourceDb;
        }
      }
      return quoteTable(db, variables, schema, physical);
    } catch (HopException e) {
      throw new SqlRewriteException(e.getMessage(), e);
    }
  }

  private static final class SqlRewriteException extends RuntimeException {
    SqlRewriteException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static BvSqlRef findMatchingRef(
      List<BvSqlRef> refs, BvSqlTemplateParser.MacroOccurrence macro) {
    if (refs == null) {
      return null;
    }
    String model = macro.refModelName();
    String object = macro.refObjectName();
    for (BvSqlRef ref : refs) {
      if (ref == null || Utils.isEmpty(ref.getObjectName())) {
        continue;
      }
      if (!ref.getObjectName().equalsIgnoreCase(object)) {
        continue;
      }
      boolean modelEmpty = Utils.isEmpty(model) && Utils.isEmpty(ref.getModelName());
      boolean modelMatch =
          !Utils.isEmpty(model)
              && !Utils.isEmpty(ref.getModelName())
              && model.equalsIgnoreCase(ref.getModelName());
      if (modelEmpty || modelMatch) {
        return ref;
      }
    }
    return null;
  }

  static String quoteTable(
      DatabaseMeta databaseMeta, IVariables variables, String schema, String tableName) {
    if (Utils.isEmpty(tableName)) {
      return tableName;
    }
    if (databaseMeta == null) {
      if (!Utils.isEmpty(schema)) {
        return schema + "." + tableName;
      }
      return tableName;
    }
    return databaseMeta.getQuotedSchemaTableCombination(variables, schema, tableName);
  }

  static IBvTable findBvTable(BusinessVaultModel model, String name, BvBusinessTable excludeSelf) {
    if (model == null || Utils.isEmpty(name)) {
      return null;
    }
    for (IBvTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      if (excludeSelf != null && table == excludeSelf) {
        continue;
      }
      if (name.equalsIgnoreCase(table.getName()) || name.equalsIgnoreCase(table.getTableName())) {
        return table;
      }
    }
    return null;
  }

  static IDvTable findDvTable(DataVaultModel model, String name) {
    if (model == null || Utils.isEmpty(name)) {
      return null;
    }
    for (IDvTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      if (name.equalsIgnoreCase(table.getName()) || name.equalsIgnoreCase(table.getTableName())) {
        return table;
      }
    }
    return null;
  }

  private static String physicalName(IBvTable table) {
    if (table == null) {
      return null;
    }
    return !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
  }

  private static String physicalName(IDvTable table) {
    if (table == null) {
      return null;
    }
    return !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
  }

  static String modelBasename(String modelNameOrPath) {
    if (Utils.isEmpty(modelNameOrPath)) {
      return "";
    }
    String name = modelNameOrPath.trim().replace('\\', '/');
    int slash = name.lastIndexOf('/');
    if (slash >= 0 && slash < name.length() - 1) {
      name = name.substring(slash + 1);
    }
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      String ext = name.substring(dot).toLowerCase();
      if (".hdv".equals(ext) || ".hbv".equals(ext)) {
        name = name.substring(0, dot);
      }
    }
    return name;
  }

  static boolean modelMatches(String filename, String modelName, String key) {
    if (Utils.isEmpty(key)) {
      return false;
    }
    if (!Utils.isEmpty(modelName) && key.equalsIgnoreCase(modelBasename(modelName))) {
      return true;
    }
    return !Utils.isEmpty(filename) && key.equalsIgnoreCase(modelBasename(filename));
  }

  /** Returns unresolved ref descriptions for validation messages. */
  public static List<String> listUnresolvedRefLabels(List<BvSqlRef> refs) {
    List<String> labels = new ArrayList<>();
    if (refs == null) {
      return labels;
    }
    for (BvSqlRef ref : refs) {
      if (ref == null) {
        continue;
      }
      if (ref.getResolvedKind() == null || ref.getResolvedKind() == BvSqlResolvedKind.UNKNOWN) {
        if (!Utils.isEmpty(ref.getModelName())) {
          labels.add("ref('" + ref.getModelName() + "', '" + ref.getObjectName() + "')");
        } else {
          labels.add("ref('" + ref.getObjectName() + "')");
        }
      }
    }
    return labels;
  }
}
