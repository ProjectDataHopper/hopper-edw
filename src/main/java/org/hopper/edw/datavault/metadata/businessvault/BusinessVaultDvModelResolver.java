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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvModelLoadSupport;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Loads Data Vault models for Business Vault use.
 *
 * <p>Authority for DV tables is the set of canvas {@link BvDvTableReference} aliases (optionally
 * multi-model via {@code referencedModelFilename}). A legacy optional {@code dataVaultModelPath} on
 * the BV is only a default when an alias has no model path of its own.
 */
public final class BusinessVaultDvModelResolver {

  private static final Class<?> PKG = BusinessVaultDvModelResolver.class;

  private BusinessVaultDvModelResolver() {}

  /**
   * Loads a single DV model file by path. Prefer {@link #buildEffectiveDataVaultModel} or {@link
   * #resolveDvTable} for Business Vault resolution.
   */
  public static DataVaultModel loadReferencedModel(
      String dataVaultModelPath, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(dataVaultModelPath)) {
      throw new HopException(
          BaseMessages.getString(PKG, "BusinessVaultDvModelResolver.Error.MissingPath"));
    }
    try {
      // Use DvModelLoadSupport so foreign absolute paths can be re-homed under PROJECT_HOME.
      String resolvedPath =
          DvModelLoadSupport.resolveModelPath(dataVaultModelPath, null, variables);
      Document document = XmlHandler.loadXmlFile(resolvedPath);
      Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      DataVaultModel model = new DataVaultModel();
      XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, metadataProvider);
      ModelConfigurationResolver.attach(model, metadataProvider);
      model.setFilename(resolvedPath);
      model.clearChanged();
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BusinessVaultDvModelResolver.Error.LoadFailed", dataVaultModelPath),
          e);
    }
  }

  /**
   * Optional legacy linked path only. Returns {@code null} when unset (no error).
   *
   * @deprecated Prefer canvas DV references; kept for older {@code .hbv} files that still set the
   *     path.
   */
  public static DataVaultModel loadOptionalLinkedModel(
      BusinessVaultModel bvModel, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (bvModel == null || Utils.isEmpty(bvModel.getDataVaultModelPath())) {
      return null;
    }
    return loadReferencedModel(bvModel.getDataVaultModelPath(), variables, metadataProvider);
  }

  /**
   * Builds a working {@link DataVaultModel} for SCD2/PIT/SQL that includes every table reachable
   * from canvas DV references (and the optional legacy linked path). Tables from multiple {@code
   * .hdv} files are unioned; the first loaded model's named and inline configuration is copied onto
   * the working model.
   *
   * @return non-null model (may be empty of tables)
   */
  public static DataVaultModel buildEffectiveDataVaultModel(
      BusinessVaultModel bvModel, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    DataVaultModel effective = new DataVaultModel();
    Map<String, IDvTable> byName = new LinkedHashMap<>();
    DataVaultModel firstConfigSource = null;

    // Legacy optional default model first (so its config wins when present).
    if (bvModel != null && !Utils.isEmpty(bvModel.getDataVaultModelPath())) {
      try {
        DataVaultModel linked =
            loadReferencedModel(bvModel.getDataVaultModelPath(), variables, metadataProvider);
        firstConfigSource = linked;
        for (IDvTable table : linked.getTables()) {
          if (table != null && !Utils.isEmpty(table.getName())) {
            byName.putIfAbsent(table.getName(), table);
          }
        }
        if (!Utils.isEmpty(linked.getFilename())) {
          effective.setFilename(linked.getFilename());
        }
      } catch (HopException e) {
        // Linked path broken — still try canvas aliases.
        throw e;
      }
    }

    if (bvModel != null) {
      String referring = bvModel.getFilename();
      Set<String> modelPaths = new LinkedHashSet<>();
      for (BvDvTableReference reference : bvModel.getDvReferences()) {
        if (reference == null || Utils.isEmpty(reference.getDvTableName())) {
          continue;
        }
        String path = reference.getReferencedModelFilename();
        if (Utils.isEmpty(path)) {
          path = bvModel.getDataVaultModelPath();
        }
        if (Utils.isEmpty(path)) {
          continue;
        }
        modelPaths.add(path);
      }
      for (String path : modelPaths) {
        DataVaultModel external =
            DvModelLoadSupport.loadDataVaultModel(path, referring, variables, metadataProvider);
        if (firstConfigSource == null) {
          firstConfigSource = external;
          if (!Utils.isEmpty(external.getFilename())) {
            effective.setFilename(external.getFilename());
          }
        }
        for (IDvTable table : external.getTables()) {
          if (table != null && !Utils.isEmpty(table.getName())) {
            byName.putIfAbsent(table.getName(), table);
          }
        }
      }
    }

    effective.getTables().addAll(byName.values());
    copyDvConfiguration(firstConfigSource, effective, metadataProvider);
    effective.clearChanged();
    return effective;
  }

  /**
   * Copies named and inline DV configuration onto the unioned working model.
   *
   * <p>Shared-config {@code .hdv} files store only {@code configurationName} (no inline {@code
   * <configuration>}). Copying {@link DataVaultModel#getConfiguration()} alone would leave the
   * working model on the {@code LOAD_DATE} default and break SCD2 timestamp checks for satellites
   * whose load-date column is {@code x_load_ts}.
   */
  static void copyDvConfiguration(
      DataVaultModel source, DataVaultModel target, IHopMetadataProvider metadataProvider) {
    if (source == null || target == null) {
      return;
    }
    target.setConfigurationName(source.getConfigurationName());
    IHopMetadataProvider provider =
        metadataProvider != null ? metadataProvider : source.getMetadataProvider();
    ModelConfigurationResolver.attach(target, provider);
    // Resolved config is also stored inline so later lookups still see load-date / hash
    // settings if the metadata provider is not available on a later call.
    target.setConfiguration(source.getConfigurationOrDefault());
  }

  /**
   * Resolves a DV table by canvas alias (and its model path), then optional legacy linked path.
   *
   * @return the table, or {@code null} if not found
   */
  public static IDvTable resolveDvTable(
      BusinessVaultModel bvModel,
      String dvTableName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (bvModel == null || Utils.isEmpty(dvTableName)) {
      return null;
    }
    BvDvTableReference alias =
        BusinessVaultDvReferenceSupport.findDvReference(bvModel, dvTableName);
    String path = null;
    if (alias != null) {
      path = alias.getReferencedModelFilename();
    }
    if (Utils.isEmpty(path)) {
      path = bvModel.getDataVaultModelPath();
    }
    if (Utils.isEmpty(path)) {
      return null;
    }
    DataVaultModel model =
        DvModelLoadSupport.loadDataVaultModel(
            path, bvModel.getFilename(), variables, metadataProvider);
    return model.findTable(dvTableName);
  }

  /**
   * Model path that owns a DV table name: alias path, else legacy linked path, else {@code null}.
   */
  public static String resolveModelPathForDvTable(BusinessVaultModel bvModel, String dvTableName) {
    if (bvModel == null || Utils.isEmpty(dvTableName)) {
      return null;
    }
    BvDvTableReference alias =
        BusinessVaultDvReferenceSupport.findDvReference(bvModel, dvTableName);
    if (alias != null && !Utils.isEmpty(alias.getReferencedModelFilename())) {
      return alias.getReferencedModelFilename();
    }
    if (!Utils.isEmpty(bvModel.getDataVaultModelPath())) {
      return bvModel.getDataVaultModelPath();
    }
    return null;
  }
}
