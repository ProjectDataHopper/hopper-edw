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
package org.hopper.edw.datavault.metadata.file;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.vfs2.FileObject;
import org.hopper.edw.catalog.discovery.PhysicalSourceRef;
import org.hopper.edw.catalog.discovery.RecordDefinitionCatalogWriter;
import org.hopper.edw.catalog.discovery.RecordDefinitionDiscoveryService;
import org.hopper.edw.catalog.hopgui.perspective.DataCatalogPerspective;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorOptions;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorSupport;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.hopper.edw.datavault.metadata.database.ImportDatabaseTablesCatalogDialog;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

/**
 * Imports a single CSV file as a catalog-backed record definition using File Metadata discovery.
 */
public final class DvCsvSourceImportSupport {

  private static final Class<?> PKG = DvCsvSourceImportSupport.class;

  private DvCsvSourceImportSupport() {}

  public static void importCsvFile(
      Shell shell,
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      String preferredCatalogConnectionName) {
    String selectedFile =
        BaseDialog.presentFileDialog(
            false,
            shell,
            new String[] {"*.csv", "*.txt", "*.*"},
            new String[] {
              BaseMessages.getString(PKG, "DvCsvSourceImportSupport.FileFilter.Csv"),
              BaseMessages.getString(PKG, "DvCsvSourceImportSupport.FileFilter.Text"),
              BaseMessages.getString(PKG, "DvCsvSourceImportSupport.FileFilter.All")
            },
            false);
    if (Utils.isEmpty(selectedFile)) {
      return;
    }

    String resolvedFile = variables != null ? variables.resolve(selectedFile) : selectedFile;

    RecordDefinitionDiscoveryService.DiscoveryResult discovery;
    try {
      discovery =
          RecordDefinitionDiscoveryService.discover(
              DvSourceType.CSV,
              PhysicalSourceRef.builder().filePath(resolvedFile).build(),
              variables,
              metadataProvider);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvCsvSourceImportSupport.Error.DialogTitle"),
          BaseMessages.getString(PKG, "DvCsvSourceImportSupport.Error.DialogMessage", resolvedFile),
          e);
      return;
    }

    List<String> discoveredFieldNames =
        discovery.fields().stream().map(SourceField::getName).collect(Collectors.toList());
    String suggestedSourceName = buildSuggestedCsvSourceName(resolvedFile);
    ImportCsvFileOptionsDialog optionsDialog =
        new ImportCsvFileOptionsDialog(
            shell, resolvedFile, suggestedSourceName, discoveredFieldNames);
    ImportCsvFileOptionsDialog.ImportCsvFileOptions options = optionsDialog.open();
    if (options == null) {
      return;
    }

    String sourceName = Const.NVL(options.getSourceName(), "").trim();
    if (Utils.isEmpty(sourceName)) {
      showError(
          shell,
          BaseMessages.getString(PKG, "DvCsvSourceImportSupport.NoSourceName.DialogTitle"),
          BaseMessages.getString(PKG, "DvCsvSourceImportSupport.NoSourceName.DialogMessage"));
      return;
    }

    String catalogConnectionName =
        new ImportDatabaseTablesCatalogDialog(
                shell,
                variables,
                metadataProvider,
                resolveDefaultCatalogConnectionName(
                    model, variables, preferredCatalogConnectionName))
            .open();
    if (Utils.isEmpty(catalogConnectionName)) {
      return;
    }

    try {
      if (DvSourceCatalogService.exists(
          sourceName, catalogConnectionName, variables, metadataProvider)) {
        showError(
            shell,
            BaseMessages.getString(PKG, "DvCsvSourceImportSupport.Exists.DialogTitle"),
            BaseMessages.getString(
                PKG, "DvCsvSourceImportSupport.Exists.DialogMessage", sourceName));
        return;
      }

      DvCsvSource csvSource = createCsvSource(resolvedFile, discovery.csvDiscovery(), variables);
      DataVaultSource source =
          createDataVaultSource(
              sourceName, csvSource, discovery.fields(), options.getRecordSourceOptions());
      RecordDefinitionCatalogWriter.upsertDataVaultSource(
          source, catalogConnectionName, model, variables, metadataProvider, null, null, null);

      DvDatabaseSourceImportSupport.refreshCatalogPerspective();

      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(
              PKG,
              "DvCsvSourceImportSupport.Success.Message",
              sourceName,
              discovery.fields().size()));
      mb.setText(BaseMessages.getString(PKG, "DvCsvSourceImportSupport.Success.Title"));
      mb.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvCsvSourceImportSupport.Error.DialogTitle"),
          BaseMessages.getString(PKG, "DvCsvSourceImportSupport.Error.DialogMessage", resolvedFile),
          e);
    }
  }

  public static String buildSuggestedCsvSourceName(String filePath) {
    if (Utils.isEmpty(filePath)) {
      return "";
    }
    String normalized = filePath.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
    int dot = fileName.lastIndexOf('.');
    String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
    return baseName.replaceAll("[^A-Za-z0-9_-]+", "-");
  }

  private static DvCsvSource createCsvSource(
      String filePath, CsvFileMetadataDiscovery.DiscoveryResult discovery, IVariables variables)
      throws HopException {
    String folder;
    String baseName;
    try {
      FileObject fileObject = HopVfs.getFileObject(filePath, variables);
      FileObject parent = fileObject.getParent();
      if (parent == null) {
        throw new HopException(
            BaseMessages.getString(PKG, "DvCsvSourceImportSupport.Error.MissingParentFolder"));
      }
      folder = HopVfs.getFilename(parent);
      baseName = fileObject.getName().getBaseName();
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvCsvSourceImportSupport.Error.DialogMessage", filePath), e);
    }

    DvCsvSource csvSource = new DvCsvSource();
    csvSource.setFolder(folder);
    csvSource.setIncludeFileMask(toIncludeFileMask(baseName));
    csvSource.setExcludeFileMask("");
    csvSource.setIncludeSubfolders(false);
    csvSource.setDelimiter(Utils.isEmpty(discovery.delimiter()) ? "," : discovery.delimiter());
    csvSource.setEnclosure(discovery.enclosure() != null ? discovery.enclosure() : "\"");
    csvSource.setEncoding(discovery.charset());
    csvSource.setHeaderPresent(discovery.headerPresent());
    csvSource.setHeaderLines(discovery.headerLines());
    csvSource.setDescription(
        BaseMessages.getString(PKG, "DvCsvSourceImportSupport.DefaultDescription", baseName));
    return csvSource;
  }

  private static String toIncludeFileMask(String baseName) {
    return baseName.replace(".", "\\.");
  }

  public static DataVaultSource createDataVaultSource(
      String metadataName, DvCsvSource csvSource, List<SourceField> fields) {
    return createDataVaultSource(metadataName, csvSource, fields, null);
  }

  public static DataVaultSource createDataVaultSource(
      String metadataName,
      DvCsvSource csvSource,
      List<SourceField> fields,
      RecordSourceIndicatorOptions recordSourceOptions) {
    csvSource.setFields(fields);
    DataVaultSource source = new DataVaultSource(metadataName);
    source.setSource(csvSource);
    if (recordSourceOptions != null) {
      RecordSourceIndicatorSupport.applyRecordSource(source, recordSourceOptions);
    }
    return source;
  }

  private static String resolveDefaultCatalogConnectionName(
      DataVaultModel model, IVariables variables, String preferredCatalogConnectionName) {
    if (model != null && model.getConfigurationOrDefault() != null) {
      String configured = model.getConfigurationOrDefault().getDataCatalogConnection();
      if (variables != null) {
        configured = variables.resolve(configured);
      }
      if (!Utils.isEmpty(configured)) {
        return configured;
      }
    }
    if (variables != null && !Utils.isEmpty(preferredCatalogConnectionName)) {
      preferredCatalogConnectionName = variables.resolve(preferredCatalogConnectionName);
    }
    return Utils.isEmpty(preferredCatalogConnectionName) ? null : preferredCatalogConnectionName;
  }

  private static void showError(Shell shell, String title, String message) {
    MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
    mb.setMessage(message);
    mb.setText(title);
    mb.open();
  }

  public static void refreshCatalogPerspective() {
    DataCatalogPerspective perspective = DataCatalogPerspective.getInstance();
    if (perspective != null) {
      perspective.refresh();
    }
  }
}
