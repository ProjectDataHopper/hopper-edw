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
package org.hopper.edw.datavault.opsanalytics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.WorkflowHopMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.actions.start.ActionStart;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupModelDiscoverySupport;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitModel;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitOptions;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitResult;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitSupport;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.ActionUpdateResourceDefinitionGroup;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.semantic.model.SemanticModel;

/**
 * Writes the operational analytics project: configurations, models, resource definition group, and
 * the initial and incremental workflows.
 *
 * <p>The group-update action has no truncate switch. The initial workflow creates target tables and
 * loads them. The incremental workflow leaves the structure alone and loads again. Data Vault and
 * Business Vault loads insert only new or changed hashes. Dimensional facts insert each time.
 */
public final class OpsAnalyticsProjectBuilder {

  public static final String GROUP_NAME = "ops-analytics";
  public static final String DV_CONFIGURATION = "ops-analytics-dv";
  public static final String BV_CONFIGURATION = "ops-analytics-bv";
  public static final String DM_CONFIGURATION = "ops-analytics-dm";
  public static final String INITIAL_WORKFLOW = "ops-analytics-initial";
  public static final String INCREMENTAL_WORKFLOW = "ops-analytics-incremental";
  public static final String UPDATE_ACTION = "Update operational analytics";

  private OpsAnalyticsProjectBuilder() {}

  public static OpsAnalyticsProject write(
      OpsAnalyticsRequest request, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (request == null || Utils.isEmpty(request.getOutputFolder())) {
      throw new HopException("An output folder is required");
    }
    if (metadataProvider == null) {
      throw new HopException("A metadata provider is required");
    }
    saveConfigurations(request, metadataProvider);
    OpsAnalyticsRunConfigurationSupport.ensure(request, metadataProvider);
    String groupName = groupName(request);

    String root = request.getOutputFolder();
    String dvFolder = root + "/dv";
    ensureFolder(root);
    ensureFolder(dvFolder);
    ensureFolder(root + "/workflows");
    if (request.getVariables() == null) {
      request.setVariables(variables);
    }
    attachSourceDatabase(request, metadataProvider);
    SourceModel sourceModel = OpsAnalyticsSourceModelBuilder.build(request);
    String sourceFile = root + "/ops-analytics.hsm";
    sourceModel.setFilename(sourceFile);
    ModelXmlWriteSupport.writeModelXml(SourceModel.XML_TAG, sourceModel, sourceFile, variables);

    DataVaultModel vault = OpsAnalyticsVaultBuilder.apply(sourceModel, variables, metadataProvider);
    DimensionalModel dimensional =
        OpsAnalyticsDimensionalBuilder.shape(vault, dvTargetDatabase(request, metadataProvider));
    dimensional.setConfigurationName(DM_CONFIGURATION);

    SourceToVaultSplitOptions splitOptions = new SourceToVaultSplitOptions();
    splitOptions.setOutputFolder(dvFolder);
    splitOptions.setNamePrefix(OpsAnalyticsVaultBuilder.FILE_PREFIX);
    splitOptions.setExistingFilePolicy(SourceToVaultSplitOptions.ExistingFilePolicy.REPLACE);
    SourceToVaultSplitResult split =
        SourceToVaultSplitSupport.partition(vault, splitOptions, variables, metadataProvider);
    for (SourceToVaultSplitModel model : split.getModels()) {
      model.getModel().setConfigurationName(DV_CONFIGURATION);
    }
    SourceToVaultSplitSupport.write(
        split, SourceToVaultSplitOptions.ExistingFilePolicy.REPLACE, variables);

    BusinessVaultModel businessVault =
        OpsAnalyticsBusinessVaultBuilder.build(vault, split, request.getDatabaseMeta());
    businessVault.setConfigurationName(BV_CONFIGURATION);
    String businessVaultFile = root + "/ops-analytics.hbv";
    businessVault.setFilename(businessVaultFile);
    ModelXmlWriteSupport.writeModelXml(
        HopBusinessVaultFileType.XML_TAG, businessVault, businessVaultFile, variables);

    List<String> dimensionalFiles =
        writeDimensionalModels(dimensional, root, variables, metadataProvider);

    ResourceDefinitionGroupMeta group =
        group(groupName, split, businessVaultFile, dimensionalFiles, variables);
    metadataProvider.getSerializer(ResourceDefinitionGroupMeta.class).save(group);
    metadataProvider
        .getSerializer(
            org.hopper.edw.datavault.metadata.busmatrix.BusinessProcessCatalogMeta.class)
        .save(OpsAnalyticsDimensionalBuilder.busCatalog());

    String initialFile = root + "/workflows/" + INITIAL_WORKFLOW + ".hwf";
    String incrementalFile = root + "/workflows/" + INCREMENTAL_WORKFLOW + ".hwf";
    String storedSource = storedPath(sourceFile, variables);
    writeWorkflow(
        initialFile,
        INITIAL_WORKFLOW,
        groupName,
        true,
        storedSource,
        request.getPipelineRunConfiguration(),
        request.getWorkflowRunConfiguration(),
        variables);
    writeWorkflow(
        incrementalFile,
        INCREMENTAL_WORKFLOW,
        groupName,
        false,
        storedSource,
        request.getPipelineRunConfiguration(),
        request.getWorkflowRunConfiguration(),
        variables);
    return new OpsAnalyticsProject(
        storedPath(sourceFile, variables),
        group.getDataVaultModelFiles(),
        storedPath(businessVaultFile, variables),
        dimensionalFiles,
        storedPath(initialFile, variables),
        storedPath(incrementalFile, variables));
  }

  private static DatabaseMeta dvTargetDatabase(
      OpsAnalyticsRequest request, IHopMetadataProvider metadataProvider) throws HopException {
    if (request == null || Utils.isEmpty(request.getDvTargetConnection())) {
      return null;
    }
    return metadataProvider.getSerializer(DatabaseMeta.class).load(request.getDvTargetConnection());
  }

  /** Free SQL quoting follows the OPS connection dialect. */
  private static void attachSourceDatabase(
      OpsAnalyticsRequest request, IHopMetadataProvider metadataProvider) throws HopException {
    if (request.getDatabaseMeta() != null || Utils.isEmpty(request.getConnectionName())) {
      return;
    }
    DatabaseMeta databaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(request.getConnectionName());
    if (databaseMeta != null) {
      request.setDatabaseMeta(databaseMeta);
    }
  }

  private static void saveConfigurations(
      OpsAnalyticsRequest request, IHopMetadataProvider metadataProvider) throws HopException {
    DataVaultConfiguration dataVault = new DataVaultConfiguration();
    dataVault.setName(DV_CONFIGURATION);
    dataVault.setDescription("Raw vault target for operational analytics.");
    dataVault.setTargetDatabase(request.getDvTargetConnection());
    metadataProvider.getSerializer(DataVaultConfiguration.class).save(dataVault);

    BusinessVaultConfiguration businessVault = new BusinessVaultConfiguration();
    businessVault.setName(BV_CONFIGURATION);
    businessVault.setDescription("Business vault target for operational analytics.");
    businessVault.setTargetDatabase(request.getBvTargetConnection());
    metadataProvider.getSerializer(BusinessVaultConfiguration.class).save(businessVault);

    DimensionalConfiguration dimensional = new DimensionalConfiguration();
    dimensional.setName(DM_CONFIGURATION);
    dimensional.setDescription("Dimensional target for operational analytics.");
    dimensional.setTargetDatabase(request.getDmTargetConnection());
    metadataProvider.getSerializer(DimensionalConfiguration.class).save(dimensional);
  }

  static String groupName(OpsAnalyticsRequest request) {
    if (request == null || Utils.isEmpty(request.getGroupName())) {
      return GROUP_NAME;
    }
    return request.getGroupName().trim();
  }

  private static ResourceDefinitionGroupMeta group(
      String groupName,
      SourceToVaultSplitResult split,
      String businessVaultFile,
      List<String> dimensionalFiles,
      IVariables variables) {
    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta(groupName);
    group.setDescription(
        "Operational analytics. Data Vault files are listed hubs and references before links.");
    group.setBusinessProcessCatalog(OpsAnalyticsDimensionalBuilder.BUS_CATALOG);
    List<SourceToVaultSplitModel> ordered = new ArrayList<>(split.getModels());
    ordered.sort(
        Comparator.comparingInt(OpsAnalyticsProjectBuilder::kindOrder)
            .thenComparing(SourceToVaultSplitModel::getFilename));
    for (SourceToVaultSplitModel model : ordered) {
      group.getDataVaultModelFiles().add(storedPath(model.getFilename(), variables));
    }
    group.getBusinessVaultModelFiles().add(storedPath(businessVaultFile, variables));
    if (dimensionalFiles != null) {
      group.getDimensionalModelFiles().addAll(dimensionalFiles);
    }
    return group;
  }

  /**
   * Writes the dimensions file first, then one file and semantic layer per fact. Returns stored
   * paths in that order. Removes a previous single {@code ops-analytics.hdm}.
   */
  public static List<String> writeDimensionalModels(
      DimensionalModel shaped,
      String outputRoot,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    shaped.setConfigurationName(DM_CONFIGURATION);
    String dmFolder = outputRoot + "/" + OpsAnalyticsDimensionalBuilder.DM_FOLDER;
    ensureFolder(dmFolder);
    String dimensionsAbsolute = dmFolder + "/" + OpsAnalyticsDimensionalBuilder.DIMENSIONS_FILE;
    String dimensionsStored = storedPath(dimensionsAbsolute, variables);
    OpsAnalyticsDimensionalBuilder.OpsAnalyticsDimensionalModels split =
        OpsAnalyticsDimensionalBuilder.partition(shaped, dimensionsStored);
    List<String> files = new ArrayList<>();
    writeDimensionalFile(split.dimensions(), dimensionsAbsolute, variables);
    files.add(dimensionsStored);
    for (DimensionalModel factModel : split.facts()) {
      String physicalTable = physicalFactTable(factModel);
      String absolute = dmFolder + "/" + OpsAnalyticsDimensionalBuilder.factFileName(physicalTable);
      String stored = storedPath(absolute, variables);
      String semanticAbsolute = absolute.substring(0, absolute.length() - 4) + ".hsl";
      String semanticStored = storedPath(semanticAbsolute, variables);
      factModel.setConfigurationName(DM_CONFIGURATION);
      factModel.setSemanticModelFilename(semanticStored);
      factModel.setFilename(absolute);
      writeDimensionalFile(factModel, absolute, variables);
      SemanticModel semantic =
          OpsAnalyticsDimensionalBuilder.semanticLayer(factModel, variables, metadataProvider);
      semantic.setDimensionalModelFilename(stored);
      semantic.setFilename(semanticAbsolute);
      ModelXmlWriteSupport.writeModelXml(
          SemanticModel.XML_TAG, semantic, semanticAbsolute, variables);
      files.add(stored);
    }
    deleteIfExists(outputRoot + "/ops-analytics.hdm");
    deleteIfExists(outputRoot + "/ops-analytics.hsl");
    return files;
  }

  private static String physicalFactTable(DimensionalModel factModel) {
    for (IDmTable table : factModel.getTables()) {
      if (table instanceof DmFact fact && !Utils.isEmpty(fact.getTableName())) {
        return fact.getTableName();
      }
    }
    return factModel.getName();
  }

  private static void writeDimensionalFile(
      DimensionalModel model, String absolutePath, IVariables variables) throws HopException {
    model.setFilename(absolutePath);
    ModelXmlWriteSupport.writeModelXml(
        HopDimensionalFileType.XML_TAG, model, absolutePath, variables);
  }

  private static void deleteIfExists(String filename) throws HopException {
    try {
      FileObject file = HopVfs.getFileObject(filename);
      if (file.exists()) {
        file.delete();
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to delete " + filename, e);
    }
  }

  private static int kindOrder(SourceToVaultSplitModel model) {
    return switch (model.getKind()) {
      case HUB -> 0;
      case REFERENCE -> 1;
      case LINK -> 2;
    };
  }

  private static void writeWorkflow(
      String filename,
      String name,
      String groupName,
      boolean createTargetStructure,
      String sourceModelFilename,
      String pipelineRunConfiguration,
      String workflowRunConfiguration,
      IVariables variables)
      throws HopException {
    WorkflowMeta workflow = new WorkflowMeta();
    workflow.setName(name);
    workflow.setNameSynchronizedWithFilename(false);
    workflow.setDescription(
        createTargetStructure
            ? "Create operational analytics tables and load OPS history."
            : "Load OPS history again without changing analytics table structure.");

    ActionStart start = new ActionStart("Start");
    ActionMeta startMeta = new ActionMeta(start);
    startMeta.setLocation(50, 80);
    workflow.addAction(startMeta);

    ActionUpdateResourceDefinitionGroup update = new ActionUpdateResourceDefinitionGroup();
    update.setName(UPDATE_ACTION);
    update.setResourceDefinitionGroup(groupName);
    update.setIncludeDataVault(true);
    update.setIncludeBusinessVault(true);
    update.setIncludeDimensional(true);
    update.setUpdateTargetDatabaseStructure(createTargetStructure);
    update.setCreateMissingTargetTables(true);
    update.setDoNotUpdateTargetDatabase(false);
    update.setPublishToCatalog(false);
    update.setSourceModelFilename(sourceModelFilename);
    update.setManageVaultUpdateMetrics(false);
    update.setEnsureSpecialRecords(true);
    update.setIgnoreModelCheckWarnings(true);
    update.setPipelineRunConfiguration(pipelineRunConfiguration);
    update.setWorkflowRunConfiguration(workflowRunConfiguration);
    update.setDescription(workflow.getDescription());
    ActionMeta updateMeta = new ActionMeta(update);
    updateMeta.setLocation(280, 80);
    workflow.addAction(updateMeta);

    WorkflowHopMeta hop = new WorkflowHopMeta(startMeta, updateMeta);
    hop.setUnconditional(true);
    workflow.addWorkflowHop(hop);

    try (var out = HopVfs.getOutputStream(filename, false)) {
      out.write(workflow.getXml(variables).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to write workflow " + filename, e);
    }
  }

  private static void ensureFolder(String folder) throws HopException {
    try {
      FileObject file = HopVfs.getFileObject(folder);
      List<FileObject> missing = new ArrayList<>();
      FileObject current = file;
      while (current != null && !current.exists()) {
        missing.add(current);
        current = current.getParent();
      }
      for (int i = missing.size() - 1; i >= 0; i--) {
        missing.get(i).createFolder();
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to create folder " + folder, e);
    }
  }

  private static String storedPath(String filename, IVariables variables) {
    String relative =
        ResourceDefinitionGroupModelDiscoverySupport.toProjectRelativePath(filename, variables);
    if (!Utils.isEmpty(relative)) {
      return relative;
    }
    return filename.replace('\\', '/');
  }

  public record OpsAnalyticsProject(
      String sourceModel,
      List<String> dataVaultModels,
      String businessVaultModel,
      List<String> dimensionalModels,
      String initialWorkflow,
      String incrementalWorkflow) {}
}
