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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.datavault.catalog.CatalogModelRegistrySupport;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvModelLoadSupport;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;

/** Helpers for Business Vault canvas references to Data Vault tables (linked or multi-model). */
public final class BusinessVaultDvReferenceSupport {

  private BusinessVaultDvReferenceSupport() {}

  public static final String BROWSE_HDV_LABEL = "Browse Data Vault model file (.hdv)…";

  /** A selectable DV model source for alias pickers. */
  public record DvModelSource(
      String label,
      String modelFilename,
      String basename,
      boolean linkedModel,
      boolean browseFile) {

    public DvModelSource(String label, String modelFilename, String basename, boolean linkedModel) {
      this(label, modelFilename, basename, linkedModel, false);
    }
  }

  /**
   * Whether any canvas alias already uses this DV table name (path-agnostic). Used to avoid
   * duplicate aliases from SQL parse/sync.
   */
  public static boolean hasDvReference(BusinessVaultModel model, String dvTableName) {
    if (model == null || Utils.isEmpty(dvTableName)) {
      return false;
    }
    return model.getDvReferences().stream()
        .anyMatch(ref -> ref != null && dvTableName.equalsIgnoreCase(ref.getDvTableName()));
  }

  /**
   * Path-aware existence check for multi-model pickers.
   *
   * @param referencedModelFilename when non-empty, only matches aliases that point at the same
   *     model path; when empty/null, only matches aliases that also have no model path (linked
   *     model). For "is this table name already on the canvas at all?", use {@link
   *     #hasDvReference(BusinessVaultModel, String)}.
   */
  public static boolean hasDvReference(
      BusinessVaultModel model, String dvTableName, String referencedModelFilename) {
    if (model == null || Utils.isEmpty(dvTableName)) {
      return false;
    }
    for (BvDvTableReference ref : model.getDvReferences()) {
      if (ref == null || !dvTableName.equalsIgnoreCase(ref.getDvTableName())) {
        continue;
      }
      if (sameModelPath(ref.getReferencedModelFilename(), referencedModelFilename)) {
        return true;
      }
    }
    return false;
  }

  /** First canvas alias with this DV table name, or null. */
  public static BvDvTableReference findDvReference(BusinessVaultModel model, String dvTableName) {
    if (model == null || Utils.isEmpty(dvTableName)) {
      return null;
    }
    for (BvDvTableReference ref : model.getDvReferences()) {
      if (ref != null && dvTableName.equalsIgnoreCase(ref.getDvTableName())) {
        return ref;
      }
    }
    return null;
  }

  public static List<String> listAvailableDvTableNames(
      DataVaultModel dataVaultModel, BusinessVaultModel businessVaultModel, DvTableType tableType) {
    return listAvailableDvTableNames(dataVaultModel, businessVaultModel, tableType, null);
  }

  public static List<String> listAvailableDvTableNames(
      DataVaultModel dataVaultModel,
      BusinessVaultModel businessVaultModel,
      DvTableType tableType,
      String referencedModelFilename) {
    if (dataVaultModel == null || tableType == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (IDvTable table : dataVaultModel.getTables()) {
      if (table == null
          || Utils.isEmpty(table.getName())
          || table.getTableType() != tableType
          || hasDvReference(businessVaultModel, table.getName(), referencedModelFilename)) {
        continue;
      }
      names.add(table.getName());
    }
    Collections.sort(names);
    return names;
  }

  public static BvDvTableReference createReference(IDvTable dvTable, Point location) {
    return createReference(dvTable, location, null);
  }

  public static BvDvTableReference createReference(
      IDvTable dvTable, Point location, String referencedModelFilename) {
    if (dvTable == null || Utils.isEmpty(dvTable.getName()) || dvTable.getTableType() == null) {
      return null;
    }
    BvDvTableReference reference =
        new BvDvTableReference(dvTable.getName(), dvTable.getTableType());
    if (!Utils.isEmpty(referencedModelFilename)) {
      reference.setReferencedModelFilename(referencedModelFilename);
    }
    if (location != null) {
      reference.setLocation(new Point(location.x, location.y));
    }
    return reference;
  }

  /**
   * Builds picker sources: catalog-discovered <strong>DV</strong> models (from the BV configuration
   * data catalog when set, including models inferred from published DV tables), optional legacy
   * linked path, and browse-.hdv.
   */
  public static List<DvModelSource> listDvModelSources(
      BusinessVaultModel bvModel,
      DataVaultModel linkedDvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    List<DvModelSource> sources = new ArrayList<>();
    String linkedBasename = null;
    String linkedPath = null;
    if (bvModel != null && !Utils.isEmpty(bvModel.getDataVaultModelPath())) {
      linkedPath = bvModel.getDataVaultModelPath();
      linkedBasename =
          linkedDvModel != null && !Utils.isEmpty(linkedDvModel.getName())
              ? linkedDvModel.getName()
              : CatalogModelRegistrySupport.sanitizeBasename(linkedPath);
      String label =
          "Linked: " + linkedBasename + (Utils.isEmpty(linkedPath) ? "" : " (" + linkedPath + ")");
      sources.add(new DvModelSource(label, linkedPath, linkedBasename, true));
    }

    String preferredCatalog = null;
    if (bvModel != null && bvModel.getConfigurationOrDefault() != null) {
      preferredCatalog = bvModel.getConfigurationOrDefault().getDataCatalogConnection();
    }

    if (metadataProvider != null) {
      try {
        List<RecordDefinition> models =
            listCatalogDvModels(preferredCatalog, variables, metadataProvider);
        for (RecordDefinition def : models) {
          if (def == null || def.getKey() == null || def.getOrigin() == null) {
            continue;
          }
          String basename = def.getKey().getName();
          String filename = def.getOrigin().getModelFilename();
          if (Utils.isEmpty(basename) || Utils.isEmpty(filename)) {
            continue;
          }
          // Only offer Data Vault files for hub/sat/link aliases.
          if (!isDataVaultModelFile(filename, def)) {
            continue;
          }
          if (linkedBasename != null && linkedBasename.equalsIgnoreCase(basename)) {
            continue;
          }
          if (linkedPath != null && pathsLikelySame(linkedPath, filename)) {
            continue;
          }
          String catalogLabel = !Utils.isEmpty(preferredCatalog) ? preferredCatalog : "catalog";
          sources.add(
              new DvModelSource(
                  "Catalog (" + catalogLabel + "): " + basename + "  →  " + filename,
                  filename,
                  basename,
                  false));
        }
      } catch (Exception ignored) {
        // Catalog optional; browse still available.
      }
    }

    sources.add(new DvModelSource(BROWSE_HDV_LABEL, null, null, false, true));
    return sources;
  }

  /**
   * Lists Data Vault models from the preferred catalog (BV configuration) when set: registry {@link
   * RecordDefinitionType#DV_MODEL} entries and models inferred from published DV tables.
   */
  public static List<RecordDefinition> listCatalogDvModels(
      IVariables variables, IHopMetadataProvider metadataProvider) throws Exception {
    return listCatalogDvModels(null, variables, metadataProvider);
  }

  public static List<RecordDefinition> listCatalogDvModels(
      String preferredCatalogConnection,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws Exception {
    return CatalogModelRegistrySupport.listDiscoverableDataVaultModels(
        preferredCatalogConnection, variables, metadataProvider);
  }

  static boolean isDataVaultModelFile(String filename, RecordDefinition def) {
    if (def != null && def.getType() == RecordDefinitionType.DV_MODEL) {
      return true;
    }
    if (def != null && def.getType() == RecordDefinitionType.BV_MODEL) {
      return false;
    }
    if (def != null && def.getType() == RecordDefinitionType.DM_MODEL) {
      return false;
    }
    if (Utils.isEmpty(filename)) {
      return false;
    }
    String lower = filename.toLowerCase(Locale.ROOT);
    return lower.endsWith(".hdv") || lower.contains(".hdv");
  }

  private static boolean pathsLikelySame(String a, String b) {
    if (Utils.isEmpty(a) || Utils.isEmpty(b)) {
      return false;
    }
    return CatalogModelRegistrySupport.sanitizeBasename(a)
        .equalsIgnoreCase(CatalogModelRegistrySupport.sanitizeBasename(b));
  }

  public static DataVaultModel loadDvModel(
      String modelFilename,
      String referringBvFilename,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws Exception {
    return DvModelLoadSupport.loadDataVaultModelFresh(
        modelFilename, referringBvFilename, variables, metadataProvider);
  }

  static boolean sameModelPath(String a, String b) {
    if (Utils.isEmpty(a) && Utils.isEmpty(b)) {
      return true;
    }
    if (Utils.isEmpty(a) || Utils.isEmpty(b)) {
      // Legacy alias (no path) matches linked-model pick (null path only when both empty —
      // when one is empty and other is not, treat as different sources.
      return false;
    }
    String na = normalizePathKey(a);
    String nb = normalizePathKey(b);
    return na.equalsIgnoreCase(nb);
  }

  private static String normalizePathKey(String path) {
    String p = path.replace('\\', '/');
    // Compare by basename for portable vs absolute equivalence when basenames match.
    int slash = p.lastIndexOf('/');
    if (slash >= 0 && slash < p.length() - 1) {
      p = p.substring(slash + 1);
    }
    return p.toLowerCase(Locale.ROOT);
  }
}
