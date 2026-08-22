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
package org.apache.hop.catalog.transform.recorddatainput;

import org.apache.hop.catalog.hopgui.preview.RecordDefinitionPreviewSupport;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.datavault.metadata.DvSourcePreviewInputSupport;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Shared helpers for reading actual data rows from a catalog record definition. */
public final class RecordDefinitionDataInputSupport {

  private static final Class<?> PKG = RecordDefinitionDataInputMeta.class;

  private RecordDefinitionDataInputSupport() {}

  public static RecordDefinition loadDefinition(
      String catalogConnectionName,
      String namespace,
      String name,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String connection =
        variables != null ? variables.resolve(catalogConnectionName) : catalogConnectionName;
    String ns = variables != null ? variables.resolve(namespace) : namespace;
    String nm = variables != null ? variables.resolve(name) : name;
    if (Utils.isEmpty(connection)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionDataInput.Error.MissingCatalogConnection"));
    }
    if (Utils.isEmpty(ns) || Utils.isEmpty(nm)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionDataInput.Error.MissingDefinitionKey"));
    }
    RecordDefinition definition =
        RecordDefinitionRegistry.getInstance()
            .read(connection, new RecordDefinitionKey(ns, nm), variables, metadataProvider);
    if (definition == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionDataInput.Error.DefinitionNotFound", connection, ns, nm));
    }
    return definition;
  }

  public static IRowMeta resolveOutputRowMeta(
      RecordDefinition definition, IVariables variables, String origin) throws HopException {
    if (definition == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionDataInput.Error.NoDefinition"));
    }
    java.util.List<SourceField> fields =
        DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
    if (fields == null || fields.isEmpty()) {
      IRowMeta fromDefinition = definition.getFields();
      if (fromDefinition != null && !fromDefinition.isEmpty()) {
        IRowMeta clone = fromDefinition.clone();
        for (int i = 0; i < clone.size(); i++) {
          clone.getValueMeta(i).setOrigin(origin);
        }
        return clone;
      }
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "RecordDefinitionDataInput.Error.NoFields",
              definition.getKey() != null ? definition.getKey().toString() : "?"));
    }
    IRowMeta rowMeta = DvSourceFieldSupport.toRowMeta(fields, variables);
    for (int i = 0; i < rowMeta.size(); i++) {
      rowMeta.getValueMeta(i).setOrigin(origin);
    }
    return rowMeta;
  }

  public static DvSourcePreviewInputSupport.PreviewPipeline buildSourcePipeline(
      RecordDefinition definition,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    if (!RecordDefinitionPreviewSupport.supportsPreview(definition)) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "RecordDefinitionDataInput.Error.UnsupportedPreview",
              definition != null && definition.getKey() != null
                  ? definition.getKey().toString()
                  : "?"));
    }
    return RecordDefinitionPreviewSupport.buildPreviewPipeline(
        definition, variables, metadataProvider, rowLimit);
  }
}
