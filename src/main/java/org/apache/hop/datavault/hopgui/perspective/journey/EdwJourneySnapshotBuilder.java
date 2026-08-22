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
package org.apache.hop.datavault.hopgui.perspective.journey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupModelDiscoverySupport;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.versioning.CatalogVersionEntry;
import org.apache.hop.catalog.versioning.CatalogVersionService;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.catalog.DvSourceCatalogMapper;
import org.apache.hop.datavault.hopgui.file.lineageview.HopLineageViewFileType;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneySnapshot.CatalogFeed;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneySnapshot.ModelRef;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneySnapshot.OutputRef;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneySnapshot.WorkflowRef;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.apache.hop.datavault.resourcedefinition.SourceUsage;
import org.apache.hop.datavault.resourcedefinition.SourceUsageIndexBuilder;
import org.apache.hop.datavault.resourcedefinition.ValidationModels;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Loads one resource definition group's journey snapshot from metadata, models, and the project.
 */
public final class EdwJourneySnapshotBuilder {

  private static final Class<?> PKG = EdwJourneySnapshotBuilder.class;

  private EdwJourneySnapshotBuilder() {}

  public static EdwJourneySnapshot build(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (group == null || Utils.isEmpty(group.getName())) {
      return EdwJourneySnapshot.empty();
    }

    List<String> warnings = new ArrayList<>();
    List<ModelRef> dvModels = new ArrayList<>();
    List<ModelRef> bvModels = new ArrayList<>();
    List<ModelRef> dmModels = new ArrayList<>();
    List<ValidationModels.LoadedDataVaultModel> loadedDv = new ArrayList<>();
    List<ValidationModels.LoadedBusinessVaultModel> loadedBv = new ArrayList<>();
    List<ValidationModels.LoadedDimensionalModel> loadedDm = new ArrayList<>();

    loadDataVaultModels(group, variables, metadataProvider, dvModels, loadedDv, warnings);
    loadBusinessVaultModels(group, variables, metadataProvider, bvModels, loadedBv, warnings);
    loadDimensionalModels(group, variables, metadataProvider, dmModels, loadedDm, warnings);

    ValidationModels models = new ValidationModels(group, loadedDv, loadedBv, loadedDm);
    List<CatalogFeed> feeds = new ArrayList<>();
    List<ModelRef> sourceModels = new ArrayList<>();
    collectSources(models, variables, metadataProvider, feeds, sourceModels, warnings);

    List<String> versionTags = listCatalogVersions(group, variables, metadataProvider, warnings);
    Path projectHome = projectHomePath(variables);
    List<WorkflowRef> workflows =
        EdwJourneyWorkflowScanner.scan(group.getName(), variables, projectHome);
    List<OutputRef> reports = listFolderOutputs(variables, "work/reports", List.of(".html", ".md"));
    List<OutputRef> executionMaps =
        listFolderOutputs(variables, "work/execution-maps", List.of(".hem"));
    List<OutputRef> lineageViews = listLineageViews(variables);

    return new EdwJourneySnapshot(
        group.getName(),
        group.getDataCatalogConnection(),
        sourceModels,
        feeds,
        dvModels,
        bvModels,
        dmModels,
        versionTags,
        workflows,
        reports,
        executionMaps,
        lineageViews,
        warnings);
  }

  private static void loadDataVaultModels(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      List<ModelRef> refs,
      List<ValidationModels.LoadedDataVaultModel> loaded,
      List<String> warnings) {
    for (String path : group.getDataVaultModelFiles()) {
      if (Utils.isEmpty(path)) {
        continue;
      }
      try {
        DataVaultModel model =
            ResourceDefinitionGroupResolver.loadDataVaultModel(path, variables, metadataProvider);
        List<String> tables = new ArrayList<>();
        for (IDvTable table : model.getTables()) {
          if (table != null && !Utils.isEmpty(table.getName())) {
            tables.add(table.getName());
          }
        }
        refs.add(
            new ModelRef(
                path,
                firstNonEmpty(
                    model.getName(), EdwJourneyDisplayNames.basenameWithoutExtension(path)),
                EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT,
                tables));
        loaded.add(
            new ValidationModels.LoadedDataVaultModel(model, group.getDataCatalogConnection()));
      } catch (Exception e) {
        refs.add(
            new ModelRef(
                path,
                EdwJourneyDisplayNames.basenameWithoutExtension(path),
                EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT,
                List.of()));
        warnings.add(
            BaseMessages.getString(
                PKG, "EdwJourneySnapshotBuilder.Warning.LoadModel", path, message(e)));
      }
    }
  }

  private static void loadBusinessVaultModels(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      List<ModelRef> refs,
      List<ValidationModels.LoadedBusinessVaultModel> loaded,
      List<String> warnings) {
    for (String path : group.getBusinessVaultModelFiles()) {
      if (Utils.isEmpty(path)) {
        continue;
      }
      try {
        BusinessVaultModel model =
            ResourceDefinitionGroupResolver.loadBusinessVaultModel(
                path, variables, metadataProvider);
        List<String> tables = new ArrayList<>();
        for (IBvTable table : model.getTables()) {
          if (table != null && !Utils.isEmpty(table.getName())) {
            tables.add(table.getName());
          }
        }
        refs.add(
            new ModelRef(
                path,
                firstNonEmpty(
                    model.getName(), EdwJourneyDisplayNames.basenameWithoutExtension(path)),
                EdwJourneySnapshot.MODEL_TYPE_BUSINESS_VAULT,
                tables));
        DataVaultModel dvModel = null;
        try {
          dvModel =
              BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(
                  model, variables, metadataProvider);
        } catch (Exception ignored) {
          // Usage index skips BV rows without a resolved DV model.
        }
        loaded.add(
            new ValidationModels.LoadedBusinessVaultModel(
                model, dvModel, group.getDataCatalogConnection()));
      } catch (Exception e) {
        refs.add(
            new ModelRef(
                path,
                EdwJourneyDisplayNames.basenameWithoutExtension(path),
                EdwJourneySnapshot.MODEL_TYPE_BUSINESS_VAULT,
                List.of()));
        warnings.add(
            BaseMessages.getString(
                PKG, "EdwJourneySnapshotBuilder.Warning.LoadModel", path, message(e)));
      }
    }
  }

  private static void loadDimensionalModels(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      List<ModelRef> refs,
      List<ValidationModels.LoadedDimensionalModel> loaded,
      List<String> warnings) {
    for (String path : group.getDimensionalModelFiles()) {
      if (Utils.isEmpty(path)) {
        continue;
      }
      try {
        DimensionalModel model =
            ResourceDefinitionGroupResolver.loadDimensionalModel(path, variables, metadataProvider);
        List<String> tables = new ArrayList<>();
        for (IDmTable table : model.getTables()) {
          if (table != null && !Utils.isEmpty(table.getName())) {
            tables.add(table.getName());
          }
        }
        refs.add(
            new ModelRef(
                path,
                firstNonEmpty(
                    model.getName(), EdwJourneyDisplayNames.basenameWithoutExtension(path)),
                EdwJourneySnapshot.MODEL_TYPE_DIMENSIONAL,
                tables));
        loaded.add(
            new ValidationModels.LoadedDimensionalModel(model, group.getDataCatalogConnection()));
      } catch (Exception e) {
        refs.add(
            new ModelRef(
                path,
                EdwJourneyDisplayNames.basenameWithoutExtension(path),
                EdwJourneySnapshot.MODEL_TYPE_DIMENSIONAL,
                List.of()));
        warnings.add(
            BaseMessages.getString(
                PKG, "EdwJourneySnapshotBuilder.Warning.LoadModel", path, message(e)));
      }
    }
  }

  private static void collectSources(
      ValidationModels models,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      List<CatalogFeed> feeds,
      List<ModelRef> sourceModels,
      List<String> warnings) {
    Map<RecordDefinitionKey, List<SourceUsage>> index =
        SourceUsageIndexBuilder.build(models, variables);
    Map<String, ModelRef> sourcesByPath = new LinkedHashMap<>();
    for (Map.Entry<RecordDefinitionKey, List<SourceUsage>> entry : index.entrySet()) {
      RecordDefinitionKey key = entry.getKey();
      if (key == null) {
        continue;
      }
      String catalogConnection = firstUsageCatalog(entry.getValue());
      if (Utils.isEmpty(catalogConnection)
          && models.group() != null
          && !Utils.isEmpty(models.group().getDataCatalogConnection())) {
        catalogConnection = models.group().getDataCatalogConnection();
      }
      String originFilename = null;
      String originModelType = null;
      if (!Utils.isEmpty(catalogConnection) && metadataProvider != null) {
        try {
          RecordDefinition definition =
              RecordDefinitionRegistry.getInstance()
                  .read(catalogConnection, key, variables, metadataProvider);
          RecordOrigin origin = definition != null ? definition.getOrigin() : null;
          if (origin != null) {
            originFilename = origin.getModelFilename();
            originModelType = origin.getModelType();
            if (DvSourceCatalogMapper.ORIGIN_MODEL_TYPE_SOURCE_MODEL.equals(originModelType)
                && !Utils.isEmpty(originFilename)) {
              sourcesByPath.putIfAbsent(
                  EdwJourneyIds.normalize(originFilename),
                  new ModelRef(
                      originFilename,
                      firstNonEmpty(
                          origin.getModelName(),
                          EdwJourneyDisplayNames.basenameWithoutExtension(originFilename)),
                      EdwJourneySnapshot.MODEL_TYPE_SOURCE,
                      List.of()));
            }
          }
        } catch (Exception e) {
          warnings.add(
              BaseMessages.getString(
                  PKG,
                  "EdwJourneySnapshotBuilder.Warning.LoadCatalogFeed",
                  key.toString(),
                  message(e)));
        }
      }
      feeds.add(new CatalogFeed(catalogConnection, key, originFilename, originModelType));
    }
    sourceModels.addAll(sourcesByPath.values());
  }

  private static String firstUsageCatalog(List<SourceUsage> usages) {
    if (usages == null) {
      return null;
    }
    for (SourceUsage usage : usages) {
      if (usage != null && !Utils.isEmpty(usage.catalogConnection())) {
        return usage.catalogConnection();
      }
    }
    return null;
  }

  private static List<String> listCatalogVersions(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      List<String> warnings) {
    if (group == null || Utils.isEmpty(group.getDataCatalogConnection())) {
      return List.of();
    }
    try {
      List<CatalogVersionEntry> versions =
          CatalogVersionService.listVersions(
              group.getDataCatalogConnection(), variables, metadataProvider);
      if (versions == null || versions.isEmpty()) {
        return List.of();
      }
      List<String> tags = new ArrayList<>();
      for (CatalogVersionEntry entry : versions) {
        if (entry != null && !Utils.isEmpty(entry.getTag())) {
          tags.add(entry.getTag());
        }
      }
      return tags;
    } catch (Exception e) {
      warnings.add(
          BaseMessages.getString(
              PKG, "EdwJourneySnapshotBuilder.Warning.CatalogVersions", message(e)));
      return List.of();
    }
  }

  private static List<OutputRef> listFolderOutputs(
      IVariables variables, String relativeFolder, List<String> extensions) {
    List<OutputRef> files = new ArrayList<>();
    String projectHome = ResourceDefinitionGroupModelDiscoverySupport.resolveProjectHome(variables);
    if (Utils.isEmpty(projectHome)) {
      return files;
    }
    String folder = projectHome.replace('\\', '/') + "/" + relativeFolder;
    try {
      FileObject dir = HopVfs.getFileObject(folder);
      if (dir == null || !dir.exists() || !dir.isFolder()) {
        return files;
      }
      FileObject[] children = dir.getChildren();
      if (children == null) {
        return files;
      }
      for (FileObject child : children) {
        if (child == null || !child.isFile()) {
          continue;
        }
        String name = child.getName().getBaseName();
        if (!matchesExtension(name, extensions)) {
          continue;
        }
        String stored = "${PROJECT_HOME}/" + relativeFolder + "/" + name;
        files.add(new OutputRef(stored, name));
      }
    } catch (Exception ignored) {
      return files;
    }
    files.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.displayName(), b.displayName()));
    return files;
  }

  private static List<OutputRef> listLineageViews(IVariables variables) {
    List<String> paths =
        ResourceDefinitionGroupModelDiscoverySupport.findProjectModelFiles(
            variables, HopLineageViewFileType.FILE_EXTENSION);
    List<OutputRef> files = new ArrayList<>();
    for (String path : paths) {
      files.add(new OutputRef(path, EdwJourneyDisplayNames.basename(path)));
    }
    return files;
  }

  private static boolean matchesExtension(String filename, List<String> extensions) {
    if (Utils.isEmpty(filename)) {
      return false;
    }
    String lower = filename.toLowerCase(Locale.ROOT);
    for (String extension : extensions) {
      if (lower.endsWith(extension)) {
        return true;
      }
    }
    return false;
  }

  private static Path projectHomePath(IVariables variables) {
    String projectHome = ResourceDefinitionGroupModelDiscoverySupport.resolveProjectHome(variables);
    if (Utils.isEmpty(projectHome)) {
      return null;
    }
    try {
      Path path = Path.of(projectHome).toAbsolutePath().normalize();
      return Files.isDirectory(path) ? path : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String firstNonEmpty(String first, String second) {
    if (!Utils.isEmpty(first)) {
      return first;
    }
    return second;
  }

  private static String message(Exception e) {
    if (e == null) {
      return "";
    }
    if (e instanceof HopException hop && !Utils.isEmpty(hop.getMessage())) {
      return hop.getMessage();
    }
    return Const.NVL(e.getMessage(), e.getClass().getSimpleName());
  }
}
