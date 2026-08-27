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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.datavault.catalog.CatalogModelRegistrySupport;

/** Helpers for Business Vault canvas references to tables in other BV models. */
public final class BusinessVaultBvReferenceSupport {

  public static final String BROWSE_HBV_LABEL = "Browse Business Vault model file (.hbv)…";

  public record BvModelSource(
      String label, String modelFilename, String basename, boolean browseFile) {

    public BvModelSource(String label, String modelFilename, String basename) {
      this(label, modelFilename, basename, false);
    }
  }

  private BusinessVaultBvReferenceSupport() {}

  public static boolean hasBvReference(BusinessVaultModel model, String bvTableName) {
    return findBvReference(model, bvTableName) != null;
  }

  public static boolean hasBvReference(
      BusinessVaultModel model, String bvTableName, String referencedModelFilename) {
    if (model == null || Utils.isEmpty(bvTableName)) {
      return false;
    }
    for (BvBvTableReference ref : model.getBvReferences()) {
      if (ref == null || !bvTableName.equalsIgnoreCase(ref.getBvTableName())) {
        continue;
      }
      if (sameModelPath(ref.getReferencedModelFilename(), referencedModelFilename)) {
        return true;
      }
    }
    return false;
  }

  public static BvBvTableReference findBvReference(BusinessVaultModel model, String bvTableName) {
    if (model == null || Utils.isEmpty(bvTableName)) {
      return null;
    }
    for (BvBvTableReference ref : model.getBvReferences()) {
      if (ref != null && bvTableName.equalsIgnoreCase(ref.getBvTableName())) {
        return ref;
      }
    }
    return null;
  }

  public static List<String> listAvailableBvTableNames(
      BusinessVaultModel sourceModel,
      BusinessVaultModel currentModel,
      String referencedModelFilename) {
    if (sourceModel == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (IBvTable table : sourceModel.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      if (hasBvReference(currentModel, table.getName(), referencedModelFilename)) {
        continue;
      }
      // Do not offer the same file's own tables as "external" when paths match current.
      if (currentModel != null
          && sameModelPath(currentModel.getFilename(), referencedModelFilename)
          && currentModel.findTable(table.getName()) != null) {
        continue;
      }
      names.add(table.getName());
    }
    Collections.sort(names);
    return names;
  }

  public static BvBvTableReference createReference(
      IBvTable table, Point location, String referencedModelFilename) {
    if (table == null || Utils.isEmpty(table.getName())) {
      return null;
    }
    BvBvTableReference reference =
        new BvBvTableReference(table.getName(), table.getTableType(), referencedModelFilename);
    if (!Utils.isEmpty(table.getTableName())) {
      reference.setPhysicalTableName(table.getTableName());
    }
    reference.setDescription(table.getDescription());
    if (location != null) {
      reference.setLocation(new Point(location.x, location.y));
    }
    return reference;
  }

  public static BusinessVaultModel loadBvModel(
      String modelFilename,
      String referringBvFilename,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws Exception {
    if (Utils.isEmpty(modelFilename)) {
      return null;
    }
    String resolved =
        org.hopper.edw.datavault.metadata.DvModelLoadSupport.resolveModelPath(
            modelFilename, referringBvFilename, variables);
    if (variables != null) {
      resolved = variables.resolve(resolved);
    }
    return BvSqlModelPathSupport.loadBusinessVaultModelUncached(resolved, metadataProvider);
  }

  /** Catalog-registered BV models plus browse option. */
  public static List<BvModelSource> listBvModelSources(
      BusinessVaultModel currentModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    List<BvModelSource> sources = new ArrayList<>();
    String currentBase =
        currentModel != null
            ? CatalogModelRegistrySupport.sanitizeBasename(
                !Utils.isEmpty(currentModel.getName())
                    ? currentModel.getName()
                    : currentModel.getFilename())
            : null;

    if (metadataProvider != null) {
      try {
        for (RecordDefinition def : listCatalogBvModels(variables, metadataProvider)) {
          if (def == null || def.getKey() == null || def.getOrigin() == null) {
            continue;
          }
          String basename = def.getKey().getName();
          String filename = def.getOrigin().getModelFilename();
          if (Utils.isEmpty(basename) || Utils.isEmpty(filename)) {
            continue;
          }
          if (currentBase != null && currentBase.equalsIgnoreCase(basename)) {
            continue;
          }
          if (!isBusinessVaultModelFile(filename, def)) {
            continue;
          }
          sources.add(
              new BvModelSource("Catalog: " + basename + "  →  " + filename, filename, basename));
        }
      } catch (Exception ignored) {
        // catalog optional
      }
    }
    sources.add(new BvModelSource(BROWSE_HBV_LABEL, null, null, true));
    return sources;
  }

  public static List<RecordDefinition> listCatalogBvModels(
      IVariables variables, IHopMetadataProvider metadataProvider) throws Exception {
    var registry = org.hopper.edw.catalog.registry.RecordDefinitionRegistry.getInstance();
    List<RecordDefinition> definitions = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    org.hopper.edw.catalog.model.RecordDefinitionQuery projectQuery =
        new org.hopper.edw.catalog.model.RecordDefinitionQuery();
    projectQuery.setNamespacePrefix(CatalogModelRegistrySupport.modelsRegistryNamespace(variables));
    projectQuery.getTypes().add(RecordDefinitionType.BV_MODEL);
    collect(
        definitions,
        seen,
        registry.listAll(projectQuery, variables, metadataProvider),
        registry,
        variables,
        metadataProvider);

    if (definitions.isEmpty()) {
      org.hopper.edw.catalog.model.RecordDefinitionQuery any =
          new org.hopper.edw.catalog.model.RecordDefinitionQuery();
      any.getTypes().add(RecordDefinitionType.BV_MODEL);
      collect(
          definitions,
          seen,
          registry.listAll(any, variables, metadataProvider),
          registry,
          variables,
          metadataProvider);
    }

    if (definitions.isEmpty()) {
      org.hopper.edw.catalog.model.RecordDefinitionQuery tagQuery =
          new org.hopper.edw.catalog.model.RecordDefinitionQuery();
      tagQuery.getTags().add("MODEL_REGISTRY");
      for (var ref : registry.listAll(tagQuery, variables, metadataProvider)) {
        if (ref == null || ref.getKey() == null) {
          continue;
        }
        if (ref.getKey().getNamespace() == null
            || !ref.getKey().getNamespace().contains("models-registry")) {
          continue;
        }
        RecordDefinition def =
            registry.read(
                ref.getCatalogConnectionName(), ref.getKey(), variables, metadataProvider);
        if (def != null
            && def.getOrigin() != null
            && isBusinessVaultModelFile(def.getOrigin().getModelFilename(), def)) {
          String sk =
              ref.getCatalogConnectionName()
                  + "|"
                  + ref.getKey().getNamespace()
                  + "|"
                  + ref.getKey().getName();
          if (seen.add(sk.toLowerCase(Locale.ROOT))) {
            definitions.add(def);
          }
        }
      }
    }

    definitions.sort(
        (a, b) -> {
          String an = a.getKey() != null ? a.getKey().getName() : "";
          String bn = b.getKey() != null ? b.getKey().getName() : "";
          return an.compareToIgnoreCase(bn);
        });
    return definitions;
  }

  private static void collect(
      List<RecordDefinition> definitions,
      Set<String> seen,
      List<org.hopper.edw.catalog.model.RecordDefinitionRef> refs,
      org.hopper.edw.catalog.registry.RecordDefinitionRegistry registry,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws Exception {
    if (refs == null) {
      return;
    }
    for (var ref : refs) {
      if (ref == null || ref.getKey() == null) {
        continue;
      }
      String sk =
          ref.getCatalogConnectionName()
              + "|"
              + ref.getKey().getNamespace()
              + "|"
              + ref.getKey().getName();
      if (!seen.add(sk.toLowerCase(Locale.ROOT))) {
        continue;
      }
      RecordDefinition def =
          registry.read(ref.getCatalogConnectionName(), ref.getKey(), variables, metadataProvider);
      if (def != null) {
        definitions.add(def);
      }
    }
  }

  static boolean isBusinessVaultModelFile(String filename, RecordDefinition def) {
    if (def != null && def.getType() == RecordDefinitionType.BV_MODEL) {
      return true;
    }
    if (def != null
        && (def.getType() == RecordDefinitionType.DV_MODEL
            || def.getType() == RecordDefinitionType.DM_MODEL)) {
      return false;
    }
    if (Utils.isEmpty(filename)) {
      return false;
    }
    return filename.toLowerCase(Locale.ROOT).endsWith(".hbv")
        || filename.toLowerCase(Locale.ROOT).contains(".hbv");
  }

  static boolean sameModelPath(String a, String b) {
    if (Utils.isEmpty(a) && Utils.isEmpty(b)) {
      return true;
    }
    if (Utils.isEmpty(a) || Utils.isEmpty(b)) {
      return false;
    }
    return CatalogModelRegistrySupport.sanitizeBasename(a)
        .equalsIgnoreCase(CatalogModelRegistrySupport.sanitizeBasename(b));
  }
}
