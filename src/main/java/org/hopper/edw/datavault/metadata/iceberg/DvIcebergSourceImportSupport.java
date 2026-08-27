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
package org.hopper.edw.datavault.metadata.iceberg;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.catalog.discovery.PhysicalSourceRef;
import org.hopper.edw.catalog.discovery.RecordDefinitionCatalogWriter;
import org.hopper.edw.catalog.discovery.RecordDefinitionDiscoveryService;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorOptions;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorSupport;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.hopper.edw.datavault.metadata.database.ImportDatabaseTablesCatalogDialog;

/** Imports an Iceberg table as a catalog-backed record definition. */
public final class DvIcebergSourceImportSupport {

  private static final Class<?> PKG = DvIcebergSourceImportSupport.class;

  private DvIcebergSourceImportSupport() {}

  public static void importIcebergTable(
      Shell shell,
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      String preferredCatalogConnectionName) {
    ImportIcebergTableDialog.ImportIcebergTableSettings tableSettings =
        new ImportIcebergTableDialog(shell).open();
    if (tableSettings == null) {
      return;
    }

    PhysicalSourceRef physicalRef =
        PhysicalSourceRef.builder()
            .catalogUri(tableSettings.getCatalogUri())
            .warehouse(tableSettings.getWarehouse())
            .icebergNamespace(tableSettings.getNamespace())
            .icebergTableName(tableSettings.getTableName())
            .snapshotId(tableSettings.getSnapshotId())
            .branch(tableSettings.getBranch())
            .s3Endpoint(tableSettings.getS3Endpoint())
            .s3AccessKey(tableSettings.getS3AccessKey())
            .s3SecretKey(tableSettings.getS3SecretKey())
            .build();

    String tableIdentifier =
        tableSettings.toConnectionSettings(variables).namespace()
            + "."
            + tableSettings.toConnectionSettings(variables).tableName();

    RecordDefinitionDiscoveryService.DiscoveryResult discovery;
    try {
      discovery =
          RecordDefinitionDiscoveryService.discover(
              DvSourceType.ICEBERG, physicalRef, variables, metadataProvider);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvIcebergSourceImportSupport.Error.DialogTitle"),
          BaseMessages.getString(
              PKG, "DvIcebergSourceImportSupport.Error.DialogMessage", tableIdentifier),
          e);
      return;
    }

    List<String> discoveredFieldNames =
        discovery.fields().stream().map(SourceField::getName).collect(Collectors.toList());
    String suggestedSourceName = buildSuggestedSourceName(tableSettings);
    ImportIcebergTableOptionsDialog.ImportIcebergTableOptions options =
        new ImportIcebergTableOptionsDialog(
                shell, tableIdentifier, suggestedSourceName, discoveredFieldNames)
            .open();
    if (options == null) {
      return;
    }

    String sourceName = Const.NVL(options.getSourceName(), "").trim();
    if (Utils.isEmpty(sourceName)) {
      showError(
          shell,
          BaseMessages.getString(PKG, "DvIcebergSourceImportSupport.NoSourceName.DialogTitle"),
          BaseMessages.getString(PKG, "DvIcebergSourceImportSupport.NoSourceName.DialogMessage"));
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
            BaseMessages.getString(PKG, "DvIcebergSourceImportSupport.Exists.DialogTitle"),
            BaseMessages.getString(
                PKG, "DvIcebergSourceImportSupport.Exists.DialogMessage", sourceName));
        return;
      }

      DvIcebergSource icebergSource = createIcebergSource(tableSettings);
      DataVaultSource source =
          createDataVaultSource(
              sourceName, icebergSource, discovery.fields(), options.getRecordSourceOptions());
      RecordDefinitionCatalogWriter.upsertDataVaultSource(
          source, catalogConnectionName, model, variables, metadataProvider, null, null, null);

      DvDatabaseSourceImportSupport.refreshCatalogPerspective();

      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(
              PKG,
              "DvIcebergSourceImportSupport.Success.Message",
              sourceName,
              discovery.fields().size()));
      mb.setText(BaseMessages.getString(PKG, "DvIcebergSourceImportSupport.Success.Title"));
      mb.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvIcebergSourceImportSupport.Error.DialogTitle"),
          BaseMessages.getString(
              PKG, "DvIcebergSourceImportSupport.Error.DialogMessage", tableIdentifier),
          e);
    }
  }

  private static String buildSuggestedSourceName(
      ImportIcebergTableDialog.ImportIcebergTableSettings tableSettings) {
    String namespace = Const.NVL(tableSettings.getNamespace(), "").trim();
    String tableName = Const.NVL(tableSettings.getTableName(), "").trim();
    if (Utils.isEmpty(namespace)) {
      return tableName;
    }
    if (Utils.isEmpty(tableName)) {
      return namespace;
    }
    return namespace + "-" + tableName;
  }

  private static DvIcebergSource createIcebergSource(
      ImportIcebergTableDialog.ImportIcebergTableSettings tableSettings) {
    DvIcebergSource icebergSource = new DvIcebergSource();
    icebergSource.setCatalogUri(tableSettings.getCatalogUri());
    icebergSource.setWarehouse(tableSettings.getWarehouse());
    icebergSource.setNamespace(tableSettings.getNamespace());
    icebergSource.setTableName(tableSettings.getTableName());
    icebergSource.setSnapshotId(tableSettings.getSnapshotId());
    icebergSource.setBranch(tableSettings.getBranch());
    icebergSource.setS3Endpoint(tableSettings.getS3Endpoint());
    icebergSource.setS3AccessKey(tableSettings.getS3AccessKey());
    icebergSource.setS3SecretKey(tableSettings.getS3SecretKey());
    icebergSource.setDescription(
        BaseMessages.getString(
            PKG,
            "DvIcebergSourceImportSupport.DefaultDescription",
            tableSettings.getNamespace(),
            tableSettings.getTableName()));
    return icebergSource;
  }

  public static DataVaultSource createDataVaultSource(
      String metadataName, DvIcebergSource icebergSource, List<SourceField> fields) {
    return createDataVaultSource(metadataName, icebergSource, fields, null);
  }

  public static DataVaultSource createDataVaultSource(
      String metadataName,
      DvIcebergSource icebergSource,
      List<SourceField> fields,
      RecordSourceIndicatorOptions recordSourceOptions) {
    icebergSource.setFields(fields);
    DataVaultSource source = new DataVaultSource(metadataName);
    source.setSource(icebergSource);
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
}
