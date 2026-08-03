/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metadata.dimensional.publish;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.ModelXmlWriteSupport;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Load and save helpers for DV → dimensional publish. */
public final class DvPublishModelSupport {

  private static final Class<?> PKG = DvPublishModelSupport.class;

  private DvPublishModelSupport() {}

  public static DataVaultModel loadDataVaultModel(
      String dataVaultModelPath, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(dataVaultModelPath)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvPublishModelSupport.Error.MissingDataVaultPath"));
    }
    try {
      String resolvedPath = HopVfs.normalize(variables.resolve(dataVaultModelPath));
      Document document = XmlHandler.loadXmlFile(resolvedPath);
      Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      DataVaultModel model = new DataVaultModel();
      XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, metadataProvider);
      model.setFilename(resolvedPath);
      model.clearChanged();
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvPublishModelSupport.Error.LoadDataVaultFailed", dataVaultModelPath),
          e);
    }
  }

  public static DimensionalModel loadDimensionalModel(
      String dimensionalModelPath, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(dimensionalModelPath)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvPublishModelSupport.Error.MissingOutputPath"));
    }
    try {
      String resolvedPath = HopVfs.normalize(variables.resolve(dimensionalModelPath));
      Document document = XmlHandler.loadXmlFile(resolvedPath);
      Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      DimensionalModel model = new DimensionalModel();
      XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, metadataProvider);
      model.setFilename(resolvedPath);
      model.clearChanged();
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvPublishModelSupport.Error.LoadDimensionalFailed", dimensionalModelPath),
          e);
    }
  }

  public static void saveDimensionalModel(
      DimensionalModel model,
      String filename,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvPublishModelSupport.Error.MissingDimensionalModel"));
    }
    if (Utils.isEmpty(filename)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvPublishModelSupport.Error.MissingOutputPath"));
    }
    try {
      String resolvedFilename = HopVfs.normalize(variables.resolve(filename));
      model.setFilename(resolvedFilename);
      ModelXmlWriteSupport.writeModelXml(
          HopDimensionalFileType.XML_TAG, model, resolvedFilename, variables);
      model.clearChanged();
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvPublishModelSupport.Error.SaveDimensionalFailed", filename),
          e);
    }
  }
}
