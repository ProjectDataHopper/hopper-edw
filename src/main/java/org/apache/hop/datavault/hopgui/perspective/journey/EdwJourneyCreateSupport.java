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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.catalog.hopgui.perspective.importmenu.DataCatalogImportMenu;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupModelDiscoverySupport;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.hopgui.StandardProjectElementsOfferSupport;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.hopgui.file.sourcemodel.HopGuiSourceModelGraph;
import org.apache.hop.datavault.hopgui.file.sourcemodel.HopSourceModelFileType;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.hopgui.search.ModelSearchOpenSupport;
import org.apache.hop.datavault.hopgui.tovault.SourceToVaultGenerationSupport;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.ModelConfigurationResolver;
import org.apache.hop.datavault.metadata.ModelXmlWriteSupport;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.quality.metadata.DataQualityRuleSetMeta;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.eclipse.swt.SWT;

/** Empty-stage create / add-to-group actions for the EDW Journey perspective. */
public final class EdwJourneyCreateSupport {

  public static final String LAYER_DATA_VAULT = "dv";
  public static final String LAYER_BUSINESS_VAULT = "bv";
  public static final String LAYER_DIMENSIONAL = "dm";

  private static final Class<?> PKG = EdwJourneyCreateSupport.class;

  private EdwJourneyCreateSupport() {}

  public static boolean addPathIfAbsent(
      ResourceDefinitionGroupMeta group, String storedPath, String layer) {
    if (group == null || Utils.isEmpty(storedPath) || Utils.isEmpty(layer)) {
      return false;
    }
    List<String> list = listForLayer(group, layer);
    if (list == null) {
      return false;
    }
    for (String existing : list) {
      if (storedPath.equals(existing)) {
        return false;
      }
    }
    list.add(storedPath);
    return true;
  }

  public static List<String> listForLayer(ResourceDefinitionGroupMeta group, String layer) {
    if (group == null || Utils.isEmpty(layer)) {
      return null;
    }
    return switch (layer) {
      case LAYER_DATA_VAULT -> group.getDataVaultModelFiles();
      case LAYER_BUSINESS_VAULT -> group.getBusinessVaultModelFiles();
      case LAYER_DIMENSIONAL -> group.getDimensionalModelFiles();
      default -> null;
    };
  }

  public static void newSourceModel(HopGui hopGui) {
    if (hopGui == null) {
      return;
    }
    try {
      new HopSourceModelFileType().newFile(hopGui, hopGui.getVariables());
    } catch (Exception e) {
      error(hopGui, e);
    }
  }

  public static void newWarehouseModel(
      HopGui hopGui, ResourceDefinitionGroupMeta group, String layer, Runnable onChanged) {
    if (hopGui == null || group == null || Utils.isEmpty(layer)) {
      return;
    }
    try {
      IVariables variables = hopGui.getVariables();
      ModelSpec spec = specForLayer(layer);
      String filename = askSaveFilename(hopGui, variables, spec, group.getName());
      if (Utils.isEmpty(filename)) {
        return;
      }
      String resolved = HopVfs.normalize(variables.resolve(filename));
      ensureParentFolder(resolved);
      Object model = newEmptyModel(spec, hopGui.getMetadataProvider(), resolved);
      ModelXmlWriteSupport.writeModelXml(spec.xmlTag(), model, resolved, variables);
      String stored =
          Const.NVL(
              ResourceDefinitionGroupModelDiscoverySupport.toProjectRelativePath(
                  resolved, variables),
              filename);
      boolean added = addPathIfAbsent(group, stored, layer);
      if (added) {
        saveGroup(group, hopGui.getMetadataProvider());
      }
      hopGui.fileDelegate.fileOpen(resolved);
      if (onChanged != null) {
        onChanged.run();
      }
    } catch (Exception e) {
      error(hopGui, e);
    }
  }

  public static void addExistingModels(
      HopGui hopGui, ResourceDefinitionGroupMeta group, String layer, Runnable onChanged) {
    if (hopGui == null || group == null || Utils.isEmpty(layer)) {
      return;
    }
    try {
      ModelSpec spec = specForLayer(layer);
      List<String> discovered =
          ResourceDefinitionGroupModelDiscoverySupport.findProjectModelFiles(
              hopGui.getVariables(), spec.extension(), true);
      List<String> already = listForLayer(group, layer);
      List<String> choices = new ArrayList<>();
      for (String path : discovered) {
        if (already == null || !already.contains(path)) {
          choices.add(path);
        }
      }
      if (choices.isEmpty()) {
        info(
            hopGui,
            BaseMessages.getString(PKG, "EdwJourneyCreateSupport.AddExisting.None.Title"),
            BaseMessages.getString(
                PKG, "EdwJourneyCreateSupport.AddExisting.None.Message", spec.extension()));
        return;
      }
      EnterSelectionDialog dialog =
          new EnterSelectionDialog(
              hopGui.getShell(),
              choices.toArray(new String[0]),
              BaseMessages.getString(
                  PKG, "EdwJourneyCreateSupport.AddExisting.Title", spec.extension()),
              BaseMessages.getString(PKG, "EdwJourneyCreateSupport.AddExisting.Message"));
      dialog.setMulti(true);
      if (dialog.open() == null) {
        return;
      }
      int[] indices = dialog.getSelectionIndeces();
      if (indices == null || indices.length == 0) {
        return;
      }
      boolean added = false;
      for (int index : indices) {
        if (index >= 0 && index < choices.size()) {
          added |= addPathIfAbsent(group, choices.get(index), layer);
        }
      }
      if (added) {
        saveGroup(group, hopGui.getMetadataProvider());
        if (onChanged != null) {
          onChanged.run();
        }
      }
    } catch (Exception e) {
      error(hopGui, e);
    }
  }

  public static void generateDataVault(
      HopGui hopGui,
      ResourceDefinitionGroupMeta group,
      EdwJourneySnapshot snapshot,
      Runnable onChanged) {
    if (hopGui == null || snapshot == null) {
      return;
    }
    try {
      List<EdwJourneySnapshot.ModelRef> sources = snapshot.sourceModels();
      if (sources == null || sources.isEmpty()) {
        info(
            hopGui,
            BaseMessages.getString(PKG, "EdwJourneyCreateSupport.GenerateDv.NoHsm.Title"),
            BaseMessages.getString(PKG, "EdwJourneyCreateSupport.GenerateDv.NoHsm.Message"));
        return;
      }
      String hsmPath = sources.get(0).storedPath();
      if (sources.size() > 1) {
        String[] choices = new String[sources.size()];
        for (int i = 0; i < sources.size(); i++) {
          choices[i] = sources.get(i).storedPath();
        }
        EnterSelectionDialog dialog =
            new EnterSelectionDialog(
                hopGui.getShell(),
                choices,
                BaseMessages.getString(PKG, "EdwJourneyCreateSupport.GenerateDv.Pick.Title"),
                BaseMessages.getString(PKG, "EdwJourneyCreateSupport.GenerateDv.Pick.Message"));
        String selected = dialog.open();
        if (Utils.isEmpty(selected)) {
          return;
        }
        hsmPath = selected;
      }
      IHopFileTypeHandler handler =
          ModelSearchOpenSupport.openModelFile(hsmPath, new HopSourceModelFileType());
      if (!(handler instanceof HopGuiSourceModelGraph graph)) {
        throw new HopException("Opened file is not a source model: " + hsmPath);
      }
      SourceToVaultGenerationSupport.generateFromSourceModel(hopGui, graph);
      addOpenVaultToGroup(hopGui, group, onChanged);
    } catch (Exception e) {
      error(hopGui, e);
    }
  }

  public static void importCatalogFeeds(
      HopGui hopGui, ResourceDefinitionGroupMeta group, Runnable onChanged) {
    if (hopGui == null) {
      return;
    }
    String catalog = group != null ? group.getDataCatalogConnection() : null;
    DataCatalogImportMenu.open(hopGui, null, catalog, onChanged);
  }

  public static void configureEdw(HopGui hopGui) {
    StandardProjectElementsOfferSupport.openFromMenu(hopGui);
  }

  public static void newQualityRuleSet(HopGui hopGui) {
    if (hopGui == null) {
      return;
    }
    MetadataManager<DataQualityRuleSetMeta> manager =
        new MetadataManager<>(
            hopGui.getVariables(),
            hopGui.getMetadataProvider(),
            DataQualityRuleSetMeta.class,
            hopGui.getShell());
    manager.newMetadata();
  }

  static void addOpenVaultToGroup(
      HopGui hopGui, ResourceDefinitionGroupMeta group, Runnable onChanged) throws HopException {
    if (hopGui == null || group == null) {
      return;
    }
    IHopFileTypeHandler handler = hopGui.getActiveFileTypeHandler();
    String filename = handler != null ? handler.getFilename() : null;
    if (Utils.isEmpty(filename) || !filename.toLowerCase().endsWith(".hdv")) {
      return;
    }
    String stored =
        Const.NVL(
            ResourceDefinitionGroupModelDiscoverySupport.toProjectRelativePath(
                HopVfs.normalize(hopGui.getVariables().resolve(filename)), hopGui.getVariables()),
            filename);
    if (addPathIfAbsent(group, stored, LAYER_DATA_VAULT)) {
      saveGroup(group, hopGui.getMetadataProvider());
      if (onChanged != null) {
        onChanged.run();
      }
    }
  }

  private static void saveGroup(
      ResourceDefinitionGroupMeta group, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (group == null || Utils.isEmpty(group.getName()) || metadataProvider == null) {
      return;
    }
    metadataProvider.getSerializer(ResourceDefinitionGroupMeta.class).save(group);
  }

  private static String askSaveFilename(
      HopGui hopGui, IVariables variables, ModelSpec spec, String groupName) throws Exception {
    String base =
        Utils.isEmpty(groupName) ? spec.defaultBasename() : groupName + spec.defaultSuffix();
    String proposed = proposedModelsPath(variables, base + spec.extension());
    return BaseDialog.presentFileDialog(
        true,
        hopGui.getShell(),
        null,
        variables,
        HopVfs.getFileObject(proposed),
        new String[] {"*" + spec.extension()},
        new String[] {spec.filterName()},
        true);
  }

  static String proposedModelsPath(IVariables variables, String filename) {
    String home = ResourceDefinitionGroupModelDiscoverySupport.resolveProjectHome(variables);
    if (!Utils.isEmpty(home)) {
      return home.replace('\\', '/') + "/models/" + filename;
    }
    String userHome = variables != null ? variables.getVariable("user.home") : null;
    if (!Utils.isEmpty(userHome)) {
      return userHome + java.io.File.separator + filename;
    }
    return filename;
  }

  private static void ensureParentFolder(String resolvedFilename) throws Exception {
    FileObject file = HopVfs.getFileObject(resolvedFilename);
    FileObject parent = file.getParent();
    if (parent != null && !parent.exists()) {
      parent.createFolder();
    }
  }

  private static Object newEmptyModel(
      ModelSpec spec, IHopMetadataProvider metadataProvider, String filename) throws HopException {
    return switch (spec.layer()) {
      case LAYER_DATA_VAULT -> {
        DataVaultModel model = new DataVaultModel();
        model.setName(stripExtension(filename));
        model.setFilename(filename);
        ModelConfigurationResolver.attach(model, metadataProvider);
        ModelConfigurationResolver.applyDefaultNameIfPresent(model, metadataProvider);
        yield model;
      }
      case LAYER_BUSINESS_VAULT -> {
        BusinessVaultModel model = new BusinessVaultModel();
        model.setName(stripExtension(filename));
        model.setFilename(filename);
        ModelConfigurationResolver.attach(model, metadataProvider);
        ModelConfigurationResolver.applyDefaultNameIfPresent(model, metadataProvider);
        yield model;
      }
      case LAYER_DIMENSIONAL -> {
        DimensionalModel model = new DimensionalModel();
        model.setName(stripExtension(filename));
        model.setFilename(filename);
        ModelConfigurationResolver.attach(model, metadataProvider);
        ModelConfigurationResolver.applyDefaultNameIfPresent(model, metadataProvider);
        yield model;
      }
      default -> throw new HopException("Unsupported model layer: " + spec.layer());
    };
  }

  private static ModelSpec specForLayer(String layer) throws HopException {
    return switch (layer) {
      case LAYER_DATA_VAULT ->
          new ModelSpec(
              LAYER_DATA_VAULT,
              HopVaultFileType.VAULT_FILE_EXTENSION,
              HopVaultFileType.XML_TAG,
              HopVaultFileType.VAULT_FILE_TYPE_DESCRIPTION,
              "data-vault-model",
              "-dv");
      case LAYER_BUSINESS_VAULT ->
          new ModelSpec(
              LAYER_BUSINESS_VAULT,
              HopBusinessVaultFileType.BUSINESS_VAULT_FILE_EXTENSION,
              HopBusinessVaultFileType.XML_TAG,
              HopBusinessVaultFileType.BUSINESS_VAULT_FILE_TYPE_DESCRIPTION,
              "business-vault-model",
              "-bv");
      case LAYER_DIMENSIONAL ->
          new ModelSpec(
              LAYER_DIMENSIONAL,
              HopDimensionalFileType.DIMENSIONAL_FILE_EXTENSION,
              HopDimensionalFileType.XML_TAG,
              HopDimensionalFileType.DIMENSIONAL_FILE_TYPE_DESCRIPTION,
              "dimensional-model",
              "-dm");
      default -> throw new HopException("Unsupported model layer: " + layer);
    };
  }

  private static String stripExtension(String filename) {
    String base = EdwJourneyDisplayNames.basenameWithoutExtension(filename);
    return Utils.isEmpty(base) ? "model" : base;
  }

  private static void error(HopGui hopGui, Exception e) {
    new ErrorDialog(
        hopGui.getShell(),
        BaseMessages.getString(PKG, "EdwJourneyCreateSupport.Error.Title"),
        BaseMessages.getString(PKG, "EdwJourneyCreateSupport.Error.Message"),
        e);
  }

  private static void info(HopGui hopGui, String title, String message) {
    MessageBox box = new MessageBox(hopGui.getShell(), SWT.OK | SWT.ICON_INFORMATION);
    box.setText(title);
    box.setMessage(message);
    box.open();
  }

  private record ModelSpec(
      String layer,
      String extension,
      String xmlTag,
      String filterName,
      String defaultBasename,
      String defaultSuffix) {}
}
