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
package org.hopper.edw.datavault.hopgui.file.dimensional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.hopper.edw.catalog.hopgui.preview.RecordDefinitionPreviewRunner;
import org.hopper.edw.catalog.hopgui.preview.RecordDefinitionPreviewSupport;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionQuery;
import org.hopper.edw.catalog.model.RecordDefinitionRef;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceRecordDefinitionSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.EnterTextDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.eclipse.swt.widgets.Shell;

/** GUI helpers for dimensional record-definition staging sources. */
public final class DmSourceRecordDefinitionGuiSupport {

  private static final Class<?> PKG = DmSourceRecordDefinitionGuiSupport.class;

  private DmSourceRecordDefinitionGuiSupport() {}

  public record PreviewableRecordRef(String namespace, String name, String label) {}

  public static List<PreviewableRecordRef> listPreviewableRecordDefinitions(
      String catalogConnection, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(catalogConnection)) {
      return List.of();
    }
    String resolvedConnection =
        variables != null ? variables.resolve(catalogConnection) : catalogConnection;
    List<RecordDefinitionRef> refs =
        RecordDefinitionRegistry.getInstance()
            .listAll(new RecordDefinitionQuery(), variables, metadataProvider);
    List<PreviewableRecordRef> previewable = new ArrayList<>();
    for (RecordDefinitionRef ref : refs) {
      if (ref == null
          || ref.getKey() == null
          || !resolvedConnection.equals(ref.getCatalogConnectionName())) {
        continue;
      }
      RecordDefinition definition =
          RecordDefinitionRegistry.getInstance()
              .read(resolvedConnection, ref.getKey(), variables, metadataProvider);
      if (definition == null || !RecordDefinitionPreviewSupport.supportsPreview(definition)) {
        continue;
      }
      String namespace = ref.getKey().getNamespace();
      String name = ref.getKey().getName();
      String type = definition.getType() != null ? definition.getType().name() : "";
      String label = namespace + " / " + name + (Utils.isEmpty(type) ? "" : " (" + type + ")");
      previewable.add(new PreviewableRecordRef(namespace, name, label));
    }
    previewable.sort(
        Comparator.comparing(PreviewableRecordRef::label, String.CASE_INSENSITIVE_ORDER));
    return previewable;
  }

  public static List<String> resolveFieldNames(
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String catalogConnection,
      String namespace,
      String recordName)
      throws HopException {
    DmSourceConfiguration source = toSourceConfiguration(catalogConnection, namespace, recordName);
    IRowMeta rowMeta =
        DmSourceRecordDefinitionSupport.resolveSourceRowMeta(
            source, config, variables, metadataProvider);
    List<String> fieldNames = new ArrayList<>();
    for (int i = 0; i < rowMeta.size(); i++) {
      String name = rowMeta.getValueMeta(i).getName();
      if (!Utils.isEmpty(name)) {
        fieldNames.add(name);
      }
    }
    if (fieldNames.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourceRecordDefinitionGuiSupport.Error.NoFields"));
    }
    return fieldNames;
  }

  public static void previewFields(
      Shell shell,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String catalogConnection,
      String namespace,
      String recordName) {
    try {
      validateRecordInput(catalogConnection, namespace, recordName);
      List<String> fieldNames =
          resolveFieldNames(
              config, variables, metadataProvider, catalogConnection, namespace, recordName);
      String message =
          String.join(
              System.lineSeparator(), fieldNames.stream().map(name -> "- " + name).toList());
      EnterTextDialog dialog =
          new EnterTextDialog(
              shell,
              BaseMessages.getString(PKG, "DmSourceRecordDefinitionGuiSupport.PreviewFields.Title"),
              BaseMessages.getString(
                  PKG, "DmSourceRecordDefinitionGuiSupport.PreviewFields.Message"),
              message,
              true);
      dialog.setReadOnly();
      dialog.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DmSourceRecordDefinitionGuiSupport.Error.PreviewTitle"),
          BaseMessages.getString(PKG, "DmSourceRecordDefinitionGuiSupport.Error.PreviewMessage"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  public static void previewData(
      Shell shell,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String catalogConnection,
      String namespace,
      String recordName) {
    try {
      validateRecordInput(catalogConnection, namespace, recordName);
      DmSourceConfiguration source =
          toSourceConfiguration(catalogConnection, namespace, recordName);
      RecordDefinition definition =
          DmSourceRecordDefinitionSupport.loadRecordDefinition(
              source, config, variables, metadataProvider);
      RecordDefinitionPreviewRunner.run(shell, definition, variables, metadataProvider);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DmSourceRecordDefinitionGuiSupport.Error.PreviewDataTitle"),
          BaseMessages.getString(
              PKG, "DmSourceRecordDefinitionGuiSupport.Error.PreviewDataMessage"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  public static DmSourceConfiguration toSourceConfiguration(
      String catalogConnection, String namespace, String recordName) {
    DmSourceConfiguration source = new DmSourceConfiguration();
    source.setSourceType(
        org.hopper.edw.datavault.metadata.dimensional.DmSourceType.RECORD_DEFINITION);
    source.setSourceCatalogConnection(catalogConnection);
    source.setSourceRecordNamespace(namespace);
    source.setSourceRecordName(recordName);
    return source;
  }

  public static RecordDefinitionKey toRecordKey(String namespace, String recordName)
      throws HopException {
    if (Utils.isEmpty(namespace) || Utils.isEmpty(recordName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourceRecordDefinitionGuiSupport.Error.MissingRecordKey"));
    }
    return new RecordDefinitionKey(namespace, recordName);
  }

  private static void validateRecordInput(
      String catalogConnection, String namespace, String recordName) throws HopException {
    if (Utils.isEmpty(catalogConnection)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DmSourceRecordDefinitionGuiSupport.Error.MissingCatalogConnection"));
    }
    toRecordKey(namespace, recordName);
  }
}
