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
package org.apache.hop.datavault.catalog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.catalog.model.CatalogCustomProperty;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvModelLoadSupport;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Project-wide catalog index of DV / BV / DM model files for multi-model {@code ref()} resolution
 * and alias tooling.
 *
 * <p>Registry key shape: {@code hop/{project}/models-registry}/{basename} with type {@link
 * RecordDefinitionType#DV_MODEL}, {@link RecordDefinitionType#BV_MODEL}, or {@link
 * RecordDefinitionType#DM_MODEL}.
 */
public final class CatalogModelRegistrySupport {

  public static final String PROP_FILE_EXTENSION = "fileExtension";
  public static final String PROP_TABLE_NAMESPACE = "tableNamespace";
  public static final String PROP_MODEL_KIND = "modelKind";

  public static final String MODEL_KIND_DV = "DATA_VAULT_MODEL";
  public static final String MODEL_KIND_BV = "BUSINESS_VAULT_MODEL";
  public static final String MODEL_KIND_DM = "DIMENSIONAL_MODEL";

  private CatalogModelRegistrySupport() {}

  public static String modelsRegistryNamespace(IVariables variables) {
    return "hop/" + DvCatalogNamespaces.resolveProjectKey(variables) + "/models-registry";
  }

  /**
   * Type-specific registry namespace so DV / BV / DM models with the same basename do not overwrite
   * each other (e.g. {@code retail-360.hdv} and {@code retail-360.hbv}).
   */
  public static String modelsRegistryKindNamespace(
      IVariables variables, RecordDefinitionType type) {
    String kind =
        type == RecordDefinitionType.DV_MODEL
            ? "dv"
            : type == RecordDefinitionType.BV_MODEL
                ? "bv"
                : type == RecordDefinitionType.DM_MODEL ? "dm" : "other";
    return modelsRegistryNamespace(variables) + "/" + kind;
  }

  /**
   * @deprecated Prefer {@link #modelRegistryKey(IVariables, String, RecordDefinitionType)} so
   *     DV/BV/DM basenames do not collide.
   */
  @Deprecated
  public static RecordDefinitionKey modelRegistryKey(IVariables variables, String modelBasename) {
    return new RecordDefinitionKey(
        modelsRegistryNamespace(variables), sanitizeBasename(modelBasename));
  }

  public static RecordDefinitionKey modelRegistryKey(
      IVariables variables, String modelBasename, RecordDefinitionType type) {
    return new RecordDefinitionKey(
        modelsRegistryKindNamespace(variables, type), sanitizeBasename(modelBasename));
  }

  /**
   * Upserts a DV model registry entry. Preferred portable filename uses {@code ${PROJECT_HOME}}
   * when possible.
   */
  public static void registerDataVaultModel(
      String catalogConnectionName,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String workflowName)
      throws HopException {
    if (model == null) {
      throw new HopException("Data Vault model is required");
    }
    String basename = DvCatalogNamespaces.resolveModelBasename(model);
    String storedPath = portableModelPath(model.getFilename(), variables);
    String tableNamespace = DvCatalogNamespaces.projectModelsNamespace(variables, model);
    registerModel(
        catalogConnectionName,
        basename,
        RecordDefinitionType.DV_MODEL,
        MODEL_KIND_DV,
        storedPath,
        "hdv",
        tableNamespace,
        model.getDescription(),
        workflowName,
        variables,
        metadataProvider);
  }

  public static void registerBusinessVaultModel(
      String catalogConnectionName,
      BusinessVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String workflowName)
      throws HopException {
    if (model == null) {
      throw new HopException("Business Vault model is required");
    }
    String basename = BvCatalogNamespaces.resolveModelBasename(model);
    String storedPath = portableModelPath(model.getFilename(), variables);
    String tableNamespace =
        BvCatalogNamespaces.projectBusinessVaultModelsNamespace(variables, model);
    registerModel(
        catalogConnectionName,
        basename,
        RecordDefinitionType.BV_MODEL,
        MODEL_KIND_BV,
        storedPath,
        "hbv",
        tableNamespace,
        model.getDescription(),
        workflowName,
        variables,
        metadataProvider);
  }

  public static void registerDimensionalModel(
      String catalogConnectionName,
      DimensionalModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String workflowName)
      throws HopException {
    if (model == null) {
      throw new HopException("Dimensional model is required");
    }
    String basename = DmCatalogNamespaces.resolveModelBasename(model);
    String storedPath = portableModelPath(model.getFilename(), variables);
    String tableNamespace = DmCatalogNamespaces.projectDimensionalModelsNamespace(variables, model);
    registerModel(
        catalogConnectionName,
        basename,
        RecordDefinitionType.DM_MODEL,
        MODEL_KIND_DM,
        storedPath,
        "hdm",
        tableNamespace,
        model.getDescription(),
        workflowName,
        variables,
        metadataProvider);
  }

  public static void registerModel(
      String catalogConnectionName,
      String modelBasename,
      RecordDefinitionType type,
      String modelKind,
      String modelFilename,
      String fileExtension,
      String tableNamespace,
      String description,
      String workflowName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(catalogConnectionName)) {
      throw new HopException("Data catalog connection name is required");
    }
    if (Utils.isEmpty(modelBasename)) {
      throw new HopException("Model basename is required");
    }
    if (Utils.isEmpty(modelFilename)) {
      throw new HopException(
          "Model filename is required for registry entry '" + modelBasename + "'");
    }

    Date updatedAt = new Date();
    RecordDefinition definition = new RecordDefinition();
    RecordDefinitionType resolvedType = type != null ? type : RecordDefinitionType.UNKNOWN;
    definition.setKey(modelRegistryKey(variables, modelBasename, resolvedType));
    definition.setType(resolvedType);
    definition.setDescription(description);

    RecordOrigin origin = new RecordOrigin();
    origin.setModelType(modelKind);
    origin.setModelName(sanitizeBasename(modelBasename));
    origin.setModelFilename(modelFilename);
    origin.setHopProject(DvCatalogNamespaces.resolveProjectKey(variables));
    origin.setUpdatedAt(updatedAt);
    origin.setLastWorkflow(workflowName);
    definition.setOrigin(origin);

    definition.getTags().add("MODEL_REGISTRY");
    if (!Utils.isEmpty(modelKind)) {
      definition.getTags().add(modelKind);
    }
    definition
        .getCustomProperties()
        .put(PROP_FILE_EXTENSION, CatalogCustomProperty.string(fileExtension));
    definition
        .getCustomProperties()
        .put(PROP_TABLE_NAMESPACE, CatalogCustomProperty.string(tableNamespace));
    definition.getCustomProperties().put(PROP_MODEL_KIND, CatalogCustomProperty.string(modelKind));

    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    RecordDefinition existing =
        registry.read(catalogConnectionName, definition.getKey(), variables, metadataProvider);
    if (existing != null
        && existing.getOrigin() != null
        && existing.getOrigin().getCreatedAt() != null) {
      origin.setCreatedAt(existing.getOrigin().getCreatedAt());
    } else {
      origin.setCreatedAt(updatedAt);
    }
    CatalogPublishMergeSupport.mergePreservedCatalogFields(definition, existing);
    registry.upsert(catalogConnectionName, definition, variables, metadataProvider);
  }

  /**
   * Looks up a registered model by basename across all enabled catalog connections. Prefers {@link
   * RecordDefinitionType#DV_MODEL} when {@code preferredKind} is null.
   *
   * @return portable or absolute model filename, or null when not found
   */
  public static String resolveModelFilename(
      String modelBasename,
      RecordDefinitionType preferredType,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    RecordDefinition definition =
        findModelDefinition(modelBasename, preferredType, variables, metadataProvider);
    if (definition == null || definition.getOrigin() == null) {
      return null;
    }
    return definition.getOrigin().getModelFilename();
  }

  /**
   * Ordered candidate model files for a short basename (DV first, then BV). Used by SQL {@code
   * ref('basename', 'object')} so the same name can resolve to either {@code .hdv} or {@code .hbv}
   * depending on where the object lives.
   *
   * @return unique portable/absolute paths in try order (never null)
   */
  public static List<String> listModelFilenamesForBasename(
      String modelBasename, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    List<String> ordered = new ArrayList<>();
    if (Utils.isEmpty(modelBasename) || metadataProvider == null) {
      return ordered;
    }
    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();

    addFilenameCandidate(
        ordered,
        seen,
        resolveModelFilename(
            modelBasename, RecordDefinitionType.DV_MODEL, variables, metadataProvider));
    addFilenameCandidate(
        ordered,
        seen,
        resolveModelFilenameFromPublishedTables(modelBasename, variables, metadataProvider));
    addFilenameCandidate(
        ordered,
        seen,
        resolveModelFilename(
            modelBasename, RecordDefinitionType.BV_MODEL, variables, metadataProvider));
    addFilenameCandidate(
        ordered,
        seen,
        resolveModelFilenameFromPublishedBvTables(modelBasename, variables, metadataProvider));
    return ordered;
  }

  private static void addFilenameCandidate(
      List<String> ordered, java.util.Set<String> seen, String filename) {
    if (Utils.isEmpty(filename)) {
      return;
    }
    // Path-based de-dupe so .hdv and .hbv of same basename both remain.
    String pathKey = filename.replace('\\', '/').toLowerCase(Locale.ROOT);
    if (seen.add(pathKey)) {
      ordered.add(filename);
    }
  }

  public static RecordDefinition findModelDefinition(
      String modelBasename,
      RecordDefinitionType preferredType,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(modelBasename) || metadataProvider == null) {
      return null;
    }
    String basename = sanitizeBasename(modelBasename);
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();

    // 1) Type-specific registry namespace (current layout).
    if (preferredType != null) {
      RecordDefinitionKey typedKey = modelRegistryKey(variables, basename, preferredType);
      RecordDefinition typed = readKeyAcrossCatalogs(typedKey, variables, metadataProvider);
      if (typed != null) {
        return typed;
      }
    }

    // 2) List models-registry (typed + legacy flat) and pick best match.
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.setNamespacePrefix(modelsRegistryNamespace(variables));
    List<RecordDefinitionType> types = new ArrayList<>();
    types.add(RecordDefinitionType.DV_MODEL);
    types.add(RecordDefinitionType.BV_MODEL);
    types.add(RecordDefinitionType.DM_MODEL);
    query.setTypes(types);

    List<RecordDefinitionRef> refs = registry.listAll(query, variables, metadataProvider);
    RecordDefinition preferred = null;
    RecordDefinition any = null;
    for (RecordDefinitionRef ref : refs) {
      if (ref == null || ref.getKey() == null) {
        continue;
      }
      if (!basename.equalsIgnoreCase(ref.getKey().getName())) {
        continue;
      }
      RecordDefinition def =
          registry.read(ref.getCatalogConnectionName(), ref.getKey(), variables, metadataProvider);
      if (def == null) {
        continue;
      }
      if (any == null) {
        any = def;
      }
      if (preferredType != null && def.getType() == preferredType) {
        preferred = def;
        break;
      }
      if (preferredType == null && def.getType() == RecordDefinitionType.DV_MODEL) {
        preferred = def;
        break;
      }
    }
    if (preferred != null) {
      return preferred;
    }
    if (any != null) {
      return any;
    }

    // 3) Legacy flat key + common connection names.
    RecordDefinitionKey legacyKey = modelRegistryKey(variables, basename);
    RecordDefinition legacy = readKeyAcrossCatalogs(legacyKey, variables, metadataProvider);
    if (legacy != null) {
      return legacy;
    }
    return null;
  }

  private static RecordDefinition readKeyAcrossCatalogs(
      RecordDefinitionKey key, IVariables variables, IHopMetadataProvider metadataProvider) {
    if (key == null || metadataProvider == null) {
      return null;
    }
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    for (String connection : List.of("local-catalog", "vault-catalog")) {
      try {
        RecordDefinition def = registry.read(connection, key, variables, metadataProvider);
        if (def != null) {
          return def;
        }
      } catch (Exception ignored) {
        // connection may not exist
      }
    }
    try {
      RecordDefinitionQuery q = new RecordDefinitionQuery();
      q.setNamespacePrefix(key.getNamespace());
      for (RecordDefinitionRef ref : registry.listAll(q, variables, metadataProvider)) {
        if (ref == null || ref.getKey() == null) {
          continue;
        }
        if (!key.getName().equalsIgnoreCase(ref.getKey().getName())) {
          continue;
        }
        if (key.getNamespace() != null
            && !key.getNamespace().equalsIgnoreCase(ref.getKey().getNamespace())) {
          continue;
        }
        RecordDefinition def =
            registry.read(
                ref.getCatalogConnectionName(), ref.getKey(), variables, metadataProvider);
        if (def != null) {
          return def;
        }
      }
    } catch (Exception ignored) {
      // optional
    }
    return null;
  }

  /**
   * Lists distinct Data Vault models discoverable from a catalog: models-registry DV_MODEL entries
   * plus basenames inferred from published DV table definitions under {@code
   * hop/{project}/models/}.
   *
   * @param preferredCatalogConnection when non-empty, query that catalog first (and only fall back
   *     to other catalogs if it yields nothing)
   */
  public static List<RecordDefinition> listDiscoverableDataVaultModels(
      String preferredCatalogConnection,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (metadataProvider == null) {
      return List.of();
    }
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    Map<String, RecordDefinition> byBasename = new java.util.LinkedHashMap<>();

    // A) Explicit DV_MODEL registry entries (typed + legacy namespaces).
    collectDvRegistryModels(
        byBasename, preferredCatalogConnection, variables, metadataProvider, registry);

    // B) Published table namespaces hop/{project}/models/{basename}/ (always useful when registry
    // was overwritten by a same-named BV publish, or registry entry missing).
    collectDvModelsFromPublishedTables(
        byBasename, preferredCatalogConnection, variables, metadataProvider, registry);

    List<RecordDefinition> definitions = new ArrayList<>(byBasename.values());
    definitions.sort(
        (a, b) -> {
          String an = a.getKey() != null ? a.getKey().getName() : "";
          String bn = b.getKey() != null ? b.getKey().getName() : "";
          return an.compareToIgnoreCase(bn);
        });
    return definitions;
  }

  private static void collectDvRegistryModels(
      Map<String, RecordDefinition> byBasename,
      String preferredCatalogConnection,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      RecordDefinitionRegistry registry)
      throws HopException {
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.setNamespacePrefix(modelsRegistryNamespace(variables));
    query.getTypes().add(RecordDefinitionType.DV_MODEL);

    List<RecordDefinitionRef> refs =
        listRefsPreferringCatalog(
            preferredCatalogConnection, query, variables, metadataProvider, registry);
    if (refs.isEmpty()) {
      // Broader: any DV_MODEL in any namespace
      RecordDefinitionQuery any = new RecordDefinitionQuery();
      any.getTypes().add(RecordDefinitionType.DV_MODEL);
      refs =
          listRefsPreferringCatalog(
              preferredCatalogConnection, any, variables, metadataProvider, registry);
    }
    for (RecordDefinitionRef ref : refs) {
      if (ref == null || ref.getKey() == null) {
        continue;
      }
      RecordDefinition def =
          registry.read(ref.getCatalogConnectionName(), ref.getKey(), variables, metadataProvider);
      if (def == null
          || def.getOrigin() == null
          || Utils.isEmpty(def.getOrigin().getModelFilename())) {
        continue;
      }
      String base = sanitizeBasename(def.getKey().getName());
      byBasename.putIfAbsent(base.toLowerCase(Locale.ROOT), def);
    }
  }

  private static void collectDvModelsFromPublishedTables(
      Map<String, RecordDefinition> byBasename,
      String preferredCatalogConnection,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      RecordDefinitionRegistry registry)
      throws HopException {
    String modelsPrefix = "hop/" + DvCatalogNamespaces.resolveProjectKey(variables) + "/models/";
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.setNamespacePrefix(modelsPrefix);
    // DV table types under models/
    query.getTypes().add(RecordDefinitionType.DV_HUB);
    query.getTypes().add(RecordDefinitionType.DV_LINK);
    query.getTypes().add(RecordDefinitionType.DV_SATELLITE);

    List<RecordDefinitionRef> refs =
        listRefsPreferringCatalog(
            preferredCatalogConnection, query, variables, metadataProvider, registry);
    for (RecordDefinitionRef ref : refs) {
      if (ref == null || ref.getKey() == null || Utils.isEmpty(ref.getKey().getNamespace())) {
        continue;
      }
      String ns = ref.getKey().getNamespace();
      if (!ns.startsWith(modelsPrefix) || ns.length() <= modelsPrefix.length()) {
        continue;
      }
      String remainder = ns.substring(modelsPrefix.length());
      int slash = remainder.indexOf('/');
      String basename = slash >= 0 ? remainder.substring(0, slash) : remainder;
      if (Utils.isEmpty(basename)) {
        continue;
      }
      String baseKey = sanitizeBasename(basename).toLowerCase(Locale.ROOT);
      if (byBasename.containsKey(baseKey)) {
        continue;
      }
      RecordDefinition def =
          registry.read(ref.getCatalogConnectionName(), ref.getKey(), variables, metadataProvider);
      if (def == null
          || def.getOrigin() == null
          || Utils.isEmpty(def.getOrigin().getModelFilename())) {
        continue;
      }
      // Synthetic registry-shaped definition for the picker (not written back).
      RecordDefinition synthetic = new RecordDefinition();
      synthetic.setKey(modelRegistryKey(variables, basename, RecordDefinitionType.DV_MODEL));
      synthetic.setType(RecordDefinitionType.DV_MODEL);
      synthetic.setDescription("Data Vault model inferred from published tables in " + ns);
      RecordOrigin origin = new RecordOrigin();
      origin.setModelType(MODEL_KIND_DV);
      origin.setModelName(sanitizeBasename(basename));
      origin.setModelFilename(def.getOrigin().getModelFilename());
      origin.setHopProject(DvCatalogNamespaces.resolveProjectKey(variables));
      synthetic.setOrigin(origin);
      synthetic.getTags().add("MODEL_REGISTRY");
      synthetic.getTags().add(MODEL_KIND_DV);
      byBasename.put(baseKey, synthetic);
    }
  }

  private static List<RecordDefinitionRef> listRefsPreferringCatalog(
      String preferredCatalogConnection,
      RecordDefinitionQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      RecordDefinitionRegistry registry)
      throws HopException {
    if (!Utils.isEmpty(preferredCatalogConnection)) {
      try {
        List<RecordDefinitionRef> preferred =
            registry.list(preferredCatalogConnection.trim(), query, variables, metadataProvider);
        if (preferred != null && !preferred.isEmpty()) {
          return preferred;
        }
      } catch (Exception ignored) {
        // fall through to all catalogs
      }
    }
    List<RecordDefinitionRef> all = registry.listAll(query, variables, metadataProvider);
    return all != null ? all : List.of();
  }

  /**
   * Fallback when no registry entry exists: use any published table under {@code
   * hop/{project}/models/{basename}/} and take {@code origin.modelFilename}.
   */
  public static String resolveModelFilenameFromPublishedTables(
      String modelBasename, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return resolveModelFilenameFromPublishedNamespace(
        modelBasename,
        "hop/" + DvCatalogNamespaces.resolveProjectKey(variables) + "/models/",
        variables,
        metadataProvider);
  }

  /**
   * Fallback for Business Vault models: published tables under {@code
   * hop/{project}/business-vault/{basename}/}.
   */
  public static String resolveModelFilenameFromPublishedBvTables(
      String modelBasename, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return resolveModelFilenameFromPublishedNamespace(
        modelBasename,
        "hop/" + DvCatalogNamespaces.resolveProjectKey(variables) + "/business-vault/",
        variables,
        metadataProvider);
  }

  private static String resolveModelFilenameFromPublishedNamespace(
      String modelBasename,
      String namespacePrefixBeforeBasename,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(modelBasename) || metadataProvider == null) {
      return null;
    }
    String basename = sanitizeBasename(modelBasename);
    String tableNsPrefix = namespacePrefixBeforeBasename + basename;
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.setNamespacePrefix(tableNsPrefix);
    List<RecordDefinitionRef> refs =
        RecordDefinitionRegistry.getInstance().listAll(query, variables, metadataProvider);
    for (RecordDefinitionRef ref : refs) {
      if (ref == null || ref.getKey() == null) {
        continue;
      }
      RecordDefinition def =
          RecordDefinitionRegistry.getInstance()
              .read(ref.getCatalogConnectionName(), ref.getKey(), variables, metadataProvider);
      if (def != null
          && def.getOrigin() != null
          && !Utils.isEmpty(def.getOrigin().getModelFilename())) {
        return def.getOrigin().getModelFilename();
      }
    }
    return null;
  }

  /**
   * Converts a runtime absolute model path into a portable stored value (typically {@code
   * ${PROJECT_HOME}/...}) for catalog origins and model registry entries.
   */
  public static String portableModelPath(String filename, IVariables variables) {
    if (Utils.isEmpty(filename)) {
      return filename;
    }
    try {
      return DvModelLoadSupport.toStoredModelPath(filename, null, variables);
    } catch (HopException e) {
      return filename;
    }
  }

  public static String sanitizeBasename(String value) {
    if (Utils.isEmpty(value)) {
      return "unknown";
    }
    String name = value.trim().replace('\\', '/');
    int slash = name.lastIndexOf('/');
    if (slash >= 0 && slash < name.length() - 1) {
      name = name.substring(slash + 1);
    }
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      String ext = name.substring(dot).toLowerCase(Locale.ROOT);
      if (".hdv".equals(ext) || ".hbv".equals(ext) || ".hdm".equals(ext)) {
        name = name.substring(0, dot);
      }
    }
    return name.replace(' ', '_');
  }
}
